package universalmod.api.drag.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.Render2DCoordinateSpace;
import universalmod.utils.world.TntTimerTracker;

import java.util.List;

/** A compact, centered TNT fuse indicator with vanilla item and text rendering. */
public final class TntTimerPanel extends HudPanel {
    private static final int MAX_TNT = 5;
    private static final float ICON_MAX_SIZE = 32.0F;
    private static final float ICON_GAP = 4.0F;
    private static final float SLOT_SIZE = ICON_MAX_SIZE + ICON_GAP;
    private static final float PANEL_WIDTH = ICON_MAX_SIZE * MAX_TNT + ICON_GAP * (MAX_TNT - 1);
    private static final float PANEL_HEIGHT = ICON_MAX_SIZE;
    private static final String TNT_SIDE_TEXTURE = "minecraft:textures/block/tnt_side.png";

    public TntTimerPanel() {
        super("tnt_timer", "TNT Timer", 10.0F, 180.0F, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render() {
        boolean preview = editPreview();
        if (mc != null) {
            TntTimerTracker.tick(mc);
        }

        List<TntTimerTracker.Entry> entries = TntTimerTracker.getEntries();
        int count = Math.min(MAX_TNT, entries.size());
        boolean visible = preview || count > 0;
        float alpha = contentAlpha(visible);
        if (alpha <= 0.001F) {
            return;
        }

        size(PANEL_WIDTH, PANEL_HEIGHT);
        float centerX = drag.x() + PANEL_WIDTH * 0.5F;
        float centerY = drag.y() + PANEL_HEIGHT * 0.5F;
        int renderCount = preview && count == 0 ? 1 : count;
        for (int index = 0; index < renderCount; index++) {
            int remainingFuse = preview && count == 0 ? 72 : entries.get(index).remainingFuse();
            long remainingMillis = preview && count == 0 ? 3_600L : entries.get(index).remainingMillis();
            float slotX = centerX + slotOffset(index) * SLOT_SIZE;
            float iconSize = ICON_MAX_SIZE;
            float iconX = slotX - iconSize * 0.5F;
            float iconY = centerY - iconSize * 0.5F;
            Render2D.imageUvNearest(
                    TNT_SIDE_TEXTURE, iconX, iconY, iconSize, iconSize,
                    0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F,
                    ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha))
            );
            if (shouldVanillaBlink(remainingFuse)) {
                Render2D.rect(iconX, iconY, iconSize, iconSize, 0.0F,
                        ColorUtil.rgba(255, 255, 255, Math.round(192.0F * alpha)));
            }
            renderFuseTimer(slotX, centerY, iconSize, remainingMillis, alpha);
        }
    }

    private void renderFuseTimer(float centerX, float centerY, float iconSize, long remainingMillis, float alpha) {
        GuiGraphics graphics = Render2D.currentGraphics();
        if (graphics == null || mc == null) {
            return;
        }

        long safeMillis = Math.max(0L, remainingMillis);
        String timer = String.format(java.util.Locale.ROOT, "%d.%d", safeMillis / 1000L, (safeMillis % 1000L) / 100L);
        Component timerText = Component.literal(timer).withStyle(ChatFormatting.BOLD);
        float scale = iconSize / ICON_MAX_SIZE;
        int color = ColorUtil.rgba(255, 32, 32, Math.round(255.0F * alpha));
        int textWidth = mc.font.width(timerText);

        graphics.pose().pushMatrix();
        try {
            Render2DCoordinateSpace.applyGuiScaleIndependence(graphics.pose());
            graphics.pose().translate(centerX, centerY);
            graphics.pose().scale(scale);
            graphics.drawString(mc.font, timerText, -textWidth / 2, -4, color, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static int slotOffset(int index) {
        if (index == 0) {
            return 0;
        }
        int distance = (index + 1) / 2;
        return (index & 1) == 1 ? -distance : distance;
    }

    private static boolean shouldVanillaBlink(int fuseTicks) {
        return fuseTicks >= 0 && (fuseTicks / 5) % 2 == 0;
    }
}
