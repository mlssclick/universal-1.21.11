package universalmod.utils.serverhelper;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import universalmod.api.events.impl.DrawEvent;
import universalmod.api.events.impl.WorldRenderEvent;
import universalmod.api.module.impl.misc.ServerHelper;
import universalmod.utils.render.Render3D;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;

import java.util.List;
import java.util.Locale;

public final class ServerHelperRenderer {
    private static final int TIMER_BACKGROUND = 0xB8000000;
    private static final int TIMER_TEXT = 0xFFFFFFFF;
    private static final float TIMER_TEXT_SCALE = 0.95F;
    private static final int TIMER_BADGE_SIZE = 36;
    private static final int TIMER_RADIUS = 9;
    private static final int TIMER_Y_OFFSET = 7;
    private static final float BOX_FILL_ALPHA = 0.18F;

    private ServerHelperRenderer() {
    }

    public static void renderWorld(Minecraft mc, WorldRenderEvent event, ServerHelper module) {
        if (!isReady(mc, module)) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        renderHeldPreview(mc, camera, module);
        for (ServerHelperDetectionTracker.TrackedZone zone : ServerHelperDetectionTracker.getActiveZones()) {
            if (zone.getType() == ServerHelperDetectionTracker.TrapType.STUN && !module.remembersStun()) {
                continue;
            }
            if (!shouldRender(zone.getType(), module)) {
                continue;
            }
            renderBox(mc, camera, zone.getBox(), module);
        }
    }

    public static void renderHud(Minecraft mc, DrawEvent event, ServerHelper module) {
        if (event.getLayer() != DrawEvent.Layer.GAME || !isReady(mc, module) || mc.options.hideGui || !module.rendersStun() || !module.remembersStun()) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        long now = System.currentTimeMillis();

        for (ServerHelperDetectionTracker.TrackedZone zone : ServerHelperDetectionTracker.getActiveZones()) {
            if (zone.getType() != ServerHelperDetectionTracker.TrapType.STUN) {
                continue;
            }

            ScreenPoint point = projectToScreen(mc, center(zone.getBox()));
            if (point == null) {
                continue;
            }

            long remainingMs = zone.getExpiresAt() - now;
            if (remainingMs <= 0L) {
                continue;
            }

            String text = String.format(Locale.ROOT, "%.1f", Math.max(0.0D, remainingMs / 1000.0D));
            drawTimerBadge(event, point.x, point.y + TIMER_Y_OFFSET, text);
        }
    }

    private static boolean isReady(Minecraft mc, ServerHelper module) {
        return mc != null
                && mc.level != null
                && mc.player != null
                && mc.gameRenderer != null
                && module != null
                && module.isEnabled()
                && ServerHelper.isTrackerEnabled();
    }

    private static void renderHeldPreview(Minecraft mc, Camera camera, ServerHelper module) {
        Player player = mc.player;
        if (player == null) {
            return;
        }

        ServerHelperDetectionTracker.TrapType type = heldType(player);
        if (type == null || !shouldRender(type, module)) {
            return;
        }

        AABB box = ServerHelperDetectionTracker.createPreviewBox(player.getPosition(1.0F), type);
        if (box != null) {
            renderBox(mc, camera, box, module);
        }
    }

    private static ServerHelperDetectionTracker.TrapType heldType(Player player) {
        ServerHelperDetectionTracker.TrapType main = ServerHelperDetectionTracker.detectType(player.getMainHandItem());
        return main != null ? main : ServerHelperDetectionTracker.detectType(player.getOffhandItem());
    }

    private static boolean shouldRender(ServerHelperDetectionTracker.TrapType type, ServerHelper module) {
        return switch (type) {
            case TRAPKA -> module.rendersTrapka();
            case EXPLOSION_TRAP -> module.rendersExplosionTrap();
            case STUN -> module.rendersStun();
        };
    }

    private static void renderBox(Minecraft mc, Camera camera, AABB box, ServerHelper module) {
        int rgb = hasVisibleOtherPlayerInside(mc, camera, mc.player, box) ? module.insideColor() : module.outlineColor();
        int opaque = (rgb & 0x00FFFFFF) | 0xFF000000;
        if (module.fillsBoxes()) {
            int fill = ColorUtil.multAlpha(opaque, BOX_FILL_ALPHA);
            Render3D.drawBox(box, fill, 1.0F, false, true, false);
        }
        Render3D.drawBox(box, opaque, 2.0F, true, false, false);
    }

    private static Vec3 center(AABB box) {
        return new Vec3((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
    }

    private static ScreenPoint projectToScreen(Minecraft mc, Vec3 worldPos) {
        Matrix4f combined = new Matrix4f(Render3D.lastProjMat).mul(Render3D.lastModMat);
        Vector4f clip = combined.transform(new Vector4f((float) worldPos.x, (float) worldPos.y, (float) worldPos.z, 1.0F));
        if (!Float.isFinite(clip.w) || clip.w <= 0.01F) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float ndcZ = clip.z / clip.w;
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY) || !Float.isFinite(ndcZ) || ndcZ < -1.0F || ndcZ > 1.0F) {
            return null;
        }

        float screenX = (float) ((ndcX * 0.5D + 0.5D) * mc.getWindow().getGuiScaledWidth());
        float screenY = (float) ((1.0D - (ndcY * 0.5D + 0.5D)) * mc.getWindow().getGuiScaledHeight());
        return new ScreenPoint(screenX, screenY);
    }

    private static void drawTimerBadge(DrawEvent event, float centerX, float centerY, String text) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float width = TIMER_BADGE_SIZE;
        float height = TIMER_BADGE_SIZE;
        float x = Math.round(centerX - width * 0.5F);
        float y = Math.round(centerY - height * 0.5F);
        int textWidth = font.width(text);
        float scaledTextWidth = textWidth * TIMER_TEXT_SCALE;
        float scaledTextHeight = font.lineHeight * TIMER_TEXT_SCALE;

        Render2D.rect(x, y, width, height, TIMER_RADIUS, TIMER_BACKGROUND);
        event.getGraphics().pose().pushMatrix();
        try {
            event.getGraphics().pose().translate(x + (width - scaledTextWidth) * 0.5F, y + (height - scaledTextHeight) * 0.5F);
            event.getGraphics().pose().scale(TIMER_TEXT_SCALE);
            event.getGraphics().drawString(font, text, 0, 0, TIMER_TEXT, false);
        } finally {
            event.getGraphics().pose().popMatrix();
        }
    }

    private static boolean hasVisibleOtherPlayerInside(Minecraft mc, Camera camera, Player self, AABB box) {
        List<Player> players = mc.level.getEntitiesOfClass(Player.class, box, candidate -> candidate != self);
        for (Player other : players) {
            if (other != null && shouldCountPlayer(other) && isPlayerVisible(mc, camera, other)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldCountPlayer(Player player) {
        if (player.isSpectator() || player.isCreative()) {
            return false;
        }
        return !player.isInvisible() || hasArmor(player);
    }

    private static boolean isPlayerVisible(Minecraft mc, Camera camera, Player player) {
        Vec3 cameraPos = camera.position();
        Vec3 look = cameraLookVector(camera);
        AABB bounds = player.getBoundingBox();
        Vec3 center = bounds.getCenter();
        double halfX = Math.max(0.12D, (bounds.maxX - bounds.minX) * 0.35D);
        double halfZ = Math.max(0.12D, (bounds.maxZ - bounds.minZ) * 0.35D);

        Vec3[] samples = new Vec3[] {
                new Vec3(center.x, bounds.maxY - 0.10D, center.z),
                new Vec3(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.66D, center.z),
                new Vec3(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.33D, center.z),
                new Vec3(center.x - halfX, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z),
                new Vec3(center.x + halfX, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z),
                new Vec3(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z - halfZ),
                new Vec3(center.x, bounds.minY + (bounds.maxY - bounds.minY) * 0.58D, center.z + halfZ)
        };

        for (Vec3 sample : samples) {
            Vec3 toSample = sample.subtract(cameraPos);
            if (toSample.dot(look) <= 0.0D) {
                continue;
            }
            if (canSeePoint(mc, cameraPos, sample, player)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canSeePoint(Minecraft mc, Vec3 from, Vec3 to, Player ignoredPlayer) {
        HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ignoredPlayer));
        return hit == null || hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(from) + 0.01D >= to.distanceToSqr(from);
    }

    private static Vec3 cameraLookVector(Camera camera) {
        double yaw = Math.toRadians(camera.yRot());
        double pitch = Math.toRadians(camera.xRot());
        double horizontal = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal).normalize();
    }

    private static boolean hasArmor(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.isArmor() && !player.getItemBySlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private record ScreenPoint(float x, float y) {
    }
}
