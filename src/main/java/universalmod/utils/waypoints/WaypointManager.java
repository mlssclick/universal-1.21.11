package universalmod.utils.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import universalmod.api.events.impl.DrawEvent;
import universalmod.utils.render.Render3D;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.repository.RepositoryStorage;
import universalmod.utils.theme.ThemeColors;
import universalmod.mixin.accessor.GameRendererAccessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class WaypointManager {
    private static final WaypointManager INSTANCE = new WaypointManager();
    private static final String STORAGE_FILE = "waypoints";
    private static final String STORAGE_KEY = "waypoints";
    private static final double MAX_DISTANCE = 131_072.0d;
    private static final float SCREEN_MARGIN = 8.0f;
    private static final UUID AUTO_EVENT_ID = UUID.fromString("e314b7ef-b916-4e1d-8dcb-5d76f3ac4014");

    private final Map<UUID, WaypointDefinition> manualWaypoints = new LinkedHashMap<>();
    private final Map<UUID, WaypointDefinition> automaticWaypoints = new LinkedHashMap<>();
    private final Map<UUID, ScreenPoint> smoothedScreenPoints = new LinkedHashMap<>();
    private boolean loaded;

    private WaypointManager() {
    }

    public static WaypointManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (loaded) {
            return;
        }
        loaded = true;
        loadManualWaypoints();
    }

    public synchronized WaypointDefinition addManual(double x, double y, double z, String name, Minecraft client) {
        return addPersistent(x, y, z, name, WaypointDefinition.Source.MANUAL, client);
    }

    public synchronized WaypointDefinition addDeath(double x, double y, double z, Minecraft client) {
        return addPersistent(x, y, z, "Death", WaypointDefinition.Source.DEATH, client);
    }

    private synchronized WaypointDefinition addPersistent(double x, double y, double z, String name, WaypointDefinition.Source source, Minecraft client) {
        initialize();
        WaypointDefinition waypoint = new WaypointDefinition(
                UUID.randomUUID(),
                name,
                x,
                y,
                z,
                currentDimension(client),
                currentServerKey(client),
                source,
                System.currentTimeMillis()
        );
        manualWaypoints.put(waypoint.id(), waypoint);
        persist();
        return waypoint.copy();
    }

    public synchronized int removeManual(String query) {
        initialize();
        if (query == null || query.isBlank()) {
            return 0;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        int removed = 0;
        Iterator<Map.Entry<UUID, WaypointDefinition>> iterator = manualWaypoints.entrySet().iterator();
        while (iterator.hasNext()) {
            WaypointDefinition waypoint = iterator.next().getValue();
            if (waypoint.id().toString().equalsIgnoreCase(normalized)
                    || waypoint.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            persist();
        }
        return removed;
    }

    public synchronized int clearManual() {
        initialize();
        int count = manualWaypoints.size();
        if (count > 0) {
            manualWaypoints.clear();
            persist();
        }
        return count;
    }

    public synchronized List<WaypointDefinition> manualWaypoints() {
        initialize();
        return manualWaypoints.values().stream()
                .map(WaypointDefinition::copy)
                .sorted(Comparator.comparingLong(WaypointDefinition::createdAtMillis))
                .toList();
    }

    public synchronized void setAutoEvent(String name, double x, double y, double z, Minecraft client) {
        automaticWaypoints.put(AUTO_EVENT_ID, new WaypointDefinition(
                AUTO_EVENT_ID,
                name,
                x,
                y,
                z,
                currentDimension(client),
                currentServerKey(client),
                WaypointDefinition.Source.AUTO_EVENT,
                System.currentTimeMillis()
        ));
    }

    public synchronized void addAutoTreasure(String name, double x, double y, double z, Minecraft client) {
        String dimension = currentDimension(client);
        String serverKey = currentServerKey(client);
        automaticWaypoints.values().removeIf(waypoint -> waypoint.source() == WaypointDefinition.Source.AUTO_TREASURE
                && waypoint.dimension().equals(dimension)
                && waypoint.serverKey().equals(serverKey)
                && Math.round(waypoint.x()) == Math.round(x)
                && Math.round(waypoint.z()) == Math.round(z));
        UUID id = UUID.randomUUID();
        automaticWaypoints.put(id, new WaypointDefinition(
                id,
                name,
                x,
                y,
                z,
                dimension,
                serverKey,
                WaypointDefinition.Source.AUTO_TREASURE,
                System.currentTimeMillis()
        ));
    }

    public synchronized void clearAutoEvents() {
        automaticWaypoints.values().removeIf(waypoint -> waypoint.source() == WaypointDefinition.Source.AUTO_EVENT);
        smoothedScreenPoints.keySet().removeIf(id -> !manualWaypoints.containsKey(id) && !automaticWaypoints.containsKey(id));
    }

    public synchronized int clearAutomatic() {
        int count = automaticWaypoints.size();
        automaticWaypoints.clear();
        smoothedScreenPoints.keySet().removeIf(id -> !manualWaypoints.containsKey(id));
        return count;
    }

    public void render(DrawEvent event, boolean renderAutoEvents, boolean renderAutoTreasures) {
        Minecraft client = Minecraft.getInstance();
        if (event.getLayer() != DrawEvent.Layer.GAME
                || client.player == null
                || client.level == null
                || client.options.hideGui
                || (client.screen != null && !(client.screen instanceof ChatScreen))) {
            return;
        }

        List<WaypointDefinition> visible = visibleWaypoints(client, renderAutoEvents, renderAutoTreasures);
        if (visible.isEmpty()) {
            return;
        }

        Vec3 playerPos = client.player.position();
        int viewportWidth = Render2D.getFixedScaledWidth();
        int viewportHeight = Render2D.getFixedScaledHeight();
        for (WaypointDefinition waypoint : visible) {
            Vec3 anchor = waypoint.anchorPosition();
            if (client.player.getEyePosition().distanceToSqr(anchor) > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            ScreenPoint point = project(anchor, viewportWidth, viewportHeight);
            if (point == null) {
                continue;
            }
            renderLabel(waypoint, playerPos, smoothPoint(waypoint.id(), point), viewportWidth, viewportHeight);
        }
    }

    private synchronized ScreenPoint smoothPoint(UUID id, ScreenPoint target) {
        ScreenPoint previous = smoothedScreenPoints.get(id);
        if (previous == null || previous.distanceSquared(target) > 1600.0f) {
            smoothedScreenPoints.put(id, target);
            return target.pixelSnap();
        }
        ScreenPoint smoothed = new ScreenPoint(
                previous.x() + (target.x() - previous.x()) * 0.35f,
                previous.y() + (target.y() - previous.y()) * 0.35f
        );
        smoothedScreenPoints.put(id, smoothed);
        return smoothed.pixelSnap();
    }

    private synchronized List<WaypointDefinition> visibleWaypoints(Minecraft client, boolean renderAutoEvents, boolean renderAutoTreasures) {
        initialize();
        String dimension = currentDimension(client);
        String serverKey = currentServerKey(client);
        List<WaypointDefinition> result = new ArrayList<>();
        for (WaypointDefinition waypoint : manualWaypoints.values()) {
            if (isVisibleInScope(waypoint, dimension, serverKey)) {
                result.add(waypoint.copy());
            }
        }
        for (WaypointDefinition waypoint : automaticWaypoints.values()) {
            if (waypoint.source() == WaypointDefinition.Source.AUTO_EVENT && !renderAutoEvents) {
                continue;
            }
            if (waypoint.source() == WaypointDefinition.Source.AUTO_TREASURE && !renderAutoTreasures) {
                continue;
            }
            if (isVisibleInScope(waypoint, dimension, serverKey)) {
                result.add(waypoint.copy());
            }
        }
        result.sort(Comparator.comparingLong(WaypointDefinition::createdAtMillis));
        return result;
    }

    private static boolean isVisibleInScope(WaypointDefinition waypoint, String dimension, String serverKey) {
        return waypoint.dimension().equals(dimension) && waypoint.serverKey().equals(serverKey);
    }

    private static void renderLabel(WaypointDefinition waypoint, Vec3 playerPos, ScreenPoint point, int viewportWidth, int viewportHeight) {
        float screenX = Math.clamp(point.x(), SCREEN_MARGIN, viewportWidth - SCREEN_MARGIN);
        float screenY = Math.clamp(point.y(), SCREEN_MARGIN, viewportHeight - SCREEN_MARGIN);
        double dx = playerPos.x - waypoint.x();
        double dy = playerPos.y - waypoint.y();
        double dz = playerPos.z - waypoint.z();
        String label = waypoint.name() + " [" + Math.max(0, Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz))) + "m]";
        float textSize = 7.7f;
        float iconSize = 9.5f;
        float paddingX = 5.5f;
        float paddingY = 3.5f;
        float gap = 4.0f;
        float textWidth = Render2D.textWidth(FontType.SEMIBOLD, label, textSize);
        float textHeight = Math.max(textSize, Render2D.textHeight(FontType.SEMIBOLD, label, textSize));
        float width = paddingX * 2.0f + iconSize + gap + textWidth;
        float height = Math.max(iconSize + paddingY * 2.0f, textHeight + paddingY * 2.0f);
        float x = Math.clamp(screenX - width * 0.5f, SCREEN_MARGIN, viewportWidth - width - SCREEN_MARGIN);
        float y = Math.clamp(screenY - height - 6.0f, SCREEN_MARGIN, viewportHeight - height - SCREEN_MARGIN);

        int accent = switch (waypoint.source()) {
            case AUTO_EVENT -> ColorUtil.rgba(255, 214, 102, 235);
            case AUTO_TREASURE -> ColorUtil.rgba(92, 221, 255, 235);
            case DEATH -> ColorUtil.rgba(255, 84, 84, 235);
            case MANUAL -> ThemeColors.hudAccentColor(235);
        };

        Render2D.blur(x, y, width, height, 4.0f, 10.0f, 1.0f, ThemeColors.hudBlurColor(ColorUtil.rgba(5, 5, 5, 125)));
        Render2D.rect(x, y, width, height, 4.0f, ColorUtil.rgba(5, 5, 5, 88));
        Render2D.outline(x, y, width, height, 4.0f, 0.75f, ColorUtil.rgba(255, 255, 255, 34));
        float pinX = x + paddingX + iconSize * 0.5f;
        float pinY = y + height * 0.5f;
        Render2D.rect(pinX - iconSize * 0.32f, pinY - iconSize * 0.58f, iconSize * 0.64f, iconSize * 0.64f, iconSize * 0.22f, accent);
        Render2D.rect(pinX - 1.0f, pinY - iconSize * 0.02f, 2.0f, iconSize * 0.54f, 1.0f, accent);
        Render2D.text(FontType.SEMIBOLD, label, x + paddingX + iconSize + gap, y + (height - textHeight) * 0.5f, textSize, ThemeColors.hudTextColor(245));
    }

    private static ScreenPoint project(Vec3 position, int viewportWidth, int viewportHeight) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameRenderer == null) {
            return null;
        }

        GameRenderer gameRenderer = client.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        float fov = ((GameRendererAccessor) gameRenderer).universalmod$getFov(camera, Render3D.lastTickDelta, true);
        Matrix4f projectionMatrix = gameRenderer.getProjectionMatrix(fov);
        Matrix4f viewMatrix = new Matrix4f()
                .identity()
                .rotateX((float) Math.toRadians(camera.xRot()))
                .rotateY((float) Math.toRadians(camera.yRot()))
                .translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);

        Vector4f clip = new Vector4f(
                (float) position.x,
                (float) (position.y - 2.0d * (position.y - cameraPos.y)),
                (float) position.z,
                1.0f
        );
        viewMatrix.transform(clip);
        projectionMatrix.transform(clip);
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || !Float.isFinite(clip.w) || clip.w >= 0.0f) {
            return null;
        }
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) {
            return null;
        }
        return new ScreenPoint((ndcX * 0.5f + 0.5f) * viewportWidth, (1.0f - (ndcY * 0.5f + 0.5f)) * viewportHeight);
    }

    public static String currentDimension(Minecraft client) {
        ClientLevel level = client == null ? null : client.level;
        if (level == null) {
            return "minecraft:overworld";
        }
        return WaypointDefinition.sanitizeDimension(level.dimension().identifier().toString());
    }

    public static String currentServerKey(Minecraft client) {
        if (client == null) {
            return "singleplayer";
        }
        ServerData server = client.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return server.ip.trim().toLowerCase(Locale.ROOT);
        }
        return client.isLocalServer() || client.hasSingleplayerServer() ? "singleplayer" : "unknown";
    }

    public static boolean isHolyWorld(Minecraft client) {
        return currentServerKey(client).contains("holyworld");
    }

    private void loadManualWaypoints() {
        manualWaypoints.clear();
        JsonObject root = RepositoryStorage.readObject(STORAGE_FILE);
        JsonArray array = root.has(STORAGE_KEY) && root.get(STORAGE_KEY).isJsonArray() ? root.getAsJsonArray(STORAGE_KEY) : new JsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            WaypointDefinition waypoint = fromJson(element.getAsJsonObject());
            if (waypoint != null && (waypoint.source() == WaypointDefinition.Source.MANUAL || waypoint.source() == WaypointDefinition.Source.DEATH)) {
                manualWaypoints.put(waypoint.id(), waypoint);
            }
        }
    }

    private void persist() {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (WaypointDefinition waypoint : manualWaypoints.values()) {
            array.add(toJson(waypoint));
        }
        root.add(STORAGE_KEY, array);
        RepositoryStorage.write(STORAGE_FILE, root);
    }

    private static JsonObject toJson(WaypointDefinition waypoint) {
        JsonObject object = new JsonObject();
        object.addProperty("id", waypoint.id().toString());
        object.addProperty("name", waypoint.name());
        object.addProperty("x", waypoint.x());
        object.addProperty("y", waypoint.y());
        object.addProperty("z", waypoint.z());
        object.addProperty("dimension", waypoint.dimension());
        object.addProperty("serverKey", waypoint.serverKey());
        object.addProperty("source", waypoint.source().name());
        object.addProperty("createdAtMillis", waypoint.createdAtMillis());
        return object;
    }

    private static WaypointDefinition fromJson(JsonObject object) {
        try {
            UUID id = UUID.fromString(readString(object, "id", UUID.randomUUID().toString()));
            String name = readString(object, "name", "Waypoint");
            double x = readDouble(object, "x", 0.0d);
            double y = readDouble(object, "y", 64.0d);
            double z = readDouble(object, "z", 0.0d);
            String dimension = readString(object, "dimension", "minecraft:overworld");
            String serverKey = readString(object, "serverKey", "unknown");
            long createdAtMillis = readLong(object, "createdAtMillis", System.currentTimeMillis());
            WaypointDefinition.Source source;
            try {
                source = WaypointDefinition.Source.valueOf(readString(object, "source", WaypointDefinition.Source.MANUAL.name()));
            } catch (RuntimeException ignored) {
                source = WaypointDefinition.Source.MANUAL;
            }
            if (source != WaypointDefinition.Source.DEATH) {
                source = WaypointDefinition.Source.MANUAL;
            }
            return new WaypointDefinition(id, name, x, y, z, dimension, serverKey, source, createdAtMillis);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String readString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static double readDouble(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsDouble() : fallback;
    }

    private static long readLong(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsLong() : fallback;
    }

    public static Component formatWaypoint(WaypointDefinition waypoint) {
        return Component.literal(waypoint.name()).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" [" + Math.round(waypoint.x()) + " " + Math.round(waypoint.y()) + " " + Math.round(waypoint.z()) + "]").withStyle(ChatFormatting.GRAY));
    }

    private record ScreenPoint(float x, float y) {
        private float distanceSquared(ScreenPoint other) {
            float dx = x - other.x;
            float dy = y - other.y;
            return dx * dx + dy * dy;
        }

        private ScreenPoint pixelSnap() {
            return new ScreenPoint(Math.round(x * 2.0f) * 0.5f, Math.round(y * 2.0f) * 0.5f);
        }
    }
}
