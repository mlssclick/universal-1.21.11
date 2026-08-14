package universalmod.api.module.impl.render;

import net.minecraft.client.Minecraft;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.DrawEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.utils.waypoints.WaypointManager;
import universalmod.utils.waypoints.WaypointMessageParser;

public final class Waypoints extends Module {
    private static final long EVENT_RESPONSE_HIDE_WINDOW_MS = 2_000L;
    private static Waypoints instance;

    private final BooleanSetting autoEventWaypoints = register(new BooleanSetting(
            "Auto Event WayPoints",
            "Creates automatic waypoints from HolyWorld event messages.",
            true
    ));
    private final BooleanSetting autoTreasureWaypoints = register(new BooleanSetting(
            "Auto Treasure WayPoints",
            "Creates automatic waypoints from HolyWorld treasure messages.",
            true
    ));

    private String lastWorldKey = "";
    private int eventRequestDelay = -1;
    private long lastEventRequestMillis;
    private long hideEventResponsesUntilMillis;
    private int hideNextEventResponses;
    private boolean deathWaypointRecorded;

    public Waypoints() {
        super("Waypoints", "Anoma-style GPS waypoints.", ModuleCategory.MISC);
        instance = this;
        setEnabled(true);
        WaypointManager.getInstance().initialize();
    }

    public static Waypoints getInstance() {
        return instance;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            lastWorldKey = "";
            eventRequestDelay = -1;
            deathWaypointRecorded = false;
            WaypointManager.getInstance().clearAutoEvents();
            return;
        }

        if (client.player.getHealth() > 0.0f) {
            deathWaypointRecorded = false;
        }

        String worldKey = WaypointManager.currentServerKey(client) + "|" + WaypointManager.currentDimension(client);
        if (!worldKey.equals(lastWorldKey)) {
            lastWorldKey = worldKey;
            WaypointManager.getInstance().clearAutoEvents();
            eventRequestDelay = shouldUseHolyWorldAuto(client) ? 20 : -1;
        }

        if (eventRequestDelay >= 0 && --eventRequestDelay <= 0) {
            eventRequestDelay = -1;
            requestEvent(client);
        }
    }

    @SubscribeEvent
    private void onDraw(DrawEvent event) {
        WaypointManager.getInstance().render(event, autoEventWaypoints.getValue(), autoTreasureWaypoints.getValue());
    }

    public static void handleServerMessage(String message) {
        Waypoints module = instance;
        Minecraft client = Minecraft.getInstance();
        if (module == null || !module.isEnabled() || !module.shouldUseHolyWorldAuto(client)) {
            return;
        }
        WaypointMessageParser.ParseResult result = WaypointMessageParser.parse(message, client);
        if (!result.matched()) {
            return;
        }
        if (result.source() == universalmod.utils.waypoints.WaypointDefinition.Source.AUTO_EVENT) {
            if (module.autoEventWaypoints.getValue()) {
                WaypointManager.getInstance().setAutoEvent(result.name(), result.x(), result.y(), result.z(), client);
            }
        } else if (result.source() == universalmod.utils.waypoints.WaypointDefinition.Source.AUTO_TREASURE
                && module.autoTreasureWaypoints.getValue()) {
            WaypointManager.getInstance().addAutoTreasure(result.name(), result.x(), result.y(), result.z(), client);
        }
    }

    public static boolean shouldHideChatMessage(String message) {
        Waypoints module = instance;
        if (module == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now > module.hideEventResponsesUntilMillis) {
            module.hideNextEventResponses = 0;
            return false;
        }
        if (module.hideNextEventResponses > 0) {
            module.hideNextEventResponses--;
            return true;
        }
        return WaypointMessageParser.isEventResponse(message);
    }

    public static void handleDeath() {
        Waypoints module = instance;
        Minecraft client = Minecraft.getInstance();
        if (module == null || !module.isEnabled() || client == null || client.player == null || client.level == null || module.deathWaypointRecorded) {
            return;
        }
        module.deathWaypointRecorded = true;
        WaypointManager.getInstance().addDeath(client.player.getX(), client.player.getY(), client.player.getZ(), client);
    }

    private boolean shouldUseHolyWorldAuto(Minecraft client) {
        return client != null
                && client.player != null
                && client.level != null
                && WaypointManager.isHolyWorld(client)
                && (autoEventWaypoints.getValue() || autoTreasureWaypoints.getValue());
    }

    private void requestEvent(Minecraft client) {
        if (client == null || client.getConnection() == null || !shouldUseHolyWorldAuto(client)) {
            return;
        }
        lastEventRequestMillis = System.currentTimeMillis();
        hideEventResponsesUntilMillis = lastEventRequestMillis + EVENT_RESPONSE_HIDE_WINDOW_MS;
        hideNextEventResponses = 1;
        client.getConnection().sendCommand("event");
    }
}
