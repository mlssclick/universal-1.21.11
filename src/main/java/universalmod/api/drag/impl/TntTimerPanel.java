package universalmod.api.drag.impl;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimatedNumber;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.item.RenderItem;
import universalmod.utils.render.item.RenderItemOptions;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.theme.ThemeColors;
import universalmod.utils.world.TntTimerTracker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TntTimerPanel extends HudPanel {
    private static final float HEADER_HEIGHT = 17.0F;
    private static final float HEADER_GAP = 3.0F;
    private static final float MIN_WIDTH = 96.0F;
    private static final float MIN_HEIGHT = 20.0F;
    private static final float ROW_STEP = 11.0F;
    private static final float PADDING = 5.0F;
    private static final float BLUR_RADIUS = 4.0F;
    private static final float BLUR_SMOOTHNESS = 0.55F;
    private static final float TEXT_SIZE = 6.0F;
    private static final float HEADER_TEXT_SIZE = 8.0F;
    private static final ItemStack TNT_STACK = Items.TNT.getDefaultInstance();
    private static final UUID PREVIEW_ID = new UUID(0L, 0x544E54L);

    private final Map<UUID, SmoothAnimatedNumber> timerAnimations = new HashMap<>();

    public TntTimerPanel() {
        super("tnt_timer", "TNT Timer", 10.0F, 180.0F, MIN_WIDTH, MIN_HEIGHT);
    }

    @Override
    public void render() {
        boolean preview = editPreview();
        if (mc != null) {
            TntTimerTracker.tick(mc);
        }

        List<TntTimerTracker.Entry> entries = TntTimerTracker.getEntries();
        boolean usePreview = preview && entries.isEmpty();
        int count = usePreview ? 1 : entries.size();
        float alpha = contentAlpha(preview || !entries.isEmpty());
        if (alpha <= 0.0F) {
            return;
        }

        float headerExpansion = ThemeColors.isHudWithoutName()
                ? 0.0F
                : HEADER_HEIGHT + (ThemeColors.isHudSplit() ? HEADER_GAP : 0.0F);
        hitExpansion(0.0F, headerExpansion, 0.0F, 0.0F);

        float width = resolveWidth(entries, usePreview);
        float height = Math.max(MIN_HEIGHT, PADDING * 2.0F + Math.max(1, count) * ROW_STEP - 1.0F);
        size(width, height);

        float x = drag.x();
        float y = drag.y();
        width = logicalWidth();
        float bodyHeight = logicalHeight();
        int backgroundColor = ColorUtil.rgba(0, 0, 0, Math.round(alpha * 255.0F));
        String title = "Active TNT";
        boolean withoutName = ThemeColors.isHudWithoutName();
        boolean split = ThemeColors.isHudSplit();

        if (withoutName) {
            HudRenderCompat.background(x, y, width, bodyHeight, 4.0F, BLUR_RADIUS, BLUR_SMOOTHNESS, backgroundColor);
        } else if (split) {
            boolean showIcon = ThemeColors.showSplitIcon();
            float titleWidth = Render2D.textWidth(FontType.BOLD, title, HEADER_TEXT_SIZE);
            float headerPadding = 6.0F;
            float headerIconSize = 8.0F;
            float headerIconGap = 4.0F;
            float iconPart = showIcon ? headerIconSize + headerIconGap : 0.0F;
            float headerWidth = titleWidth + iconPart + headerPadding * 2.0F;
            float headerX = x + (width - headerWidth) * 0.5F;
            float headerY = y - HEADER_HEIGHT - HEADER_GAP;
            float titleY = headerY + (HEADER_HEIGHT - Render2D.textHeight(FontType.BOLD, title, HEADER_TEXT_SIZE)) * 0.5F;
            HudRenderCompat.splitHeader(headerX, headerY, headerWidth, HEADER_HEIGHT, ThemeColors.splitHeaderRounding(), BLUR_RADIUS, BLUR_SMOOTHNESS, backgroundColor);
            HudRenderCompat.background(x, y, width, bodyHeight, 4.0F, BLUR_RADIUS, BLUR_SMOOTHNESS, backgroundColor);
            if (showIcon && alpha > 0.001F) {
                float iconY = headerY + (HEADER_HEIGHT - headerIconSize) * 0.5F;
                RenderItem.item(TNT_STACK, headerX + headerPadding, iconY, headerIconSize, RenderItemOptions.noDecorations(alpha));
            }
            Render2D.text(FontType.BOLD, title, headerX + headerPadding + iconPart, titleY, HEADER_TEXT_SIZE,
                    ThemeColors.hudTextColor(Math.round(255.0F * alpha)));
        } else {
            float headerY = y - HEADER_HEIGHT;
            float titleY = headerY + (HEADER_HEIGHT - Render2D.textHeight(FontType.BOLD, title, HEADER_TEXT_SIZE)) * 0.5F;
            HudRenderCompat.background(x, headerY, width, HEADER_HEIGHT + bodyHeight, 4.0F, BLUR_RADIUS, BLUR_SMOOTHNESS, backgroundColor);
            Render2D.text(FontType.BOLD, title, x + 3.0F, titleY, HEADER_TEXT_SIZE,
                    ThemeColors.hudTextColor(Math.round(255.0F * alpha)));
        }

        Set<UUID> visibleIds = new HashSet<>();
        float rowY = y + PADDING;
        if (usePreview) {
            visibleIds.add(PREVIEW_ID);
            renderTntRow(PREVIEW_ID, "TNT", 8.4, 72, x, rowY, width, alpha);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                TntTimerTracker.Entry entry = entries.get(i);
                visibleIds.add(entry.id());
                renderTntRow(entry.id(), entry.name(), entry.distance(), entry.remainingFuse(), x, rowY + i * ROW_STEP, width, alpha);
            }
        }
        timerAnimations.keySet().removeIf(id -> !visibleIds.contains(id));
    }

    private void renderTntRow(UUID id, String name, double distance, int remainingFuse, float x, float y, float width, float alpha) {
        float iconX = x + PADDING;
        float iconY = y + 0.5F;
        float iconSize = 8.0F;
        float nameX = iconX + iconSize + 4.0F;
        float rightX = x + width - PADDING;

        int seconds = Math.max(0, (int) Math.ceil(Math.max(0, remainingFuse) / 20.0F));
        int timerColor = withAlpha(colorForFuse(remainingFuse), alpha);
        int nameColor = ThemeColors.hudTextColor(Math.round(245.0F * alpha));

        RenderItem.item(TNT_STACK, iconX, iconY, iconSize, RenderItemOptions.noDecorations(alpha));

        SmoothAnimatedNumber animation = timerAnimations.computeIfAbsent(id, ignored ->
                new SmoothAnimatedNumber(FontType.BOLD, TEXT_SIZE, 3.0F, 500L, Easings.BAKEK, false));
        animation.update(seconds);

        String suffix = "s";
        String distanceText = String.format(Locale.US, "%.1fm", Math.max(0.0, distance));
        float suffixWidth = Render2D.textWidth(FontType.BOLD, suffix, TEXT_SIZE);
        float distanceWidth = Render2D.textWidth(FontType.BOLD, distanceText, TEXT_SIZE);
        float timerX = rightX - suffixWidth - animation.width();
        float distanceX = timerX - 6.0F - distanceWidth;
        float maxNameWidth = Math.max(8.0F, distanceX - nameX - 5.0F);
        String visibleName = trimToWidth(name == null || name.isBlank() ? "TNT" : name, FontType.BOLD, TEXT_SIZE, maxNameWidth);

        Render2D.text(FontType.BOLD, visibleName, nameX, y + 1.5F, TEXT_SIZE, nameColor);
        Render2D.text(FontType.BOLD, distanceText, distanceX, y + 1.5F, TEXT_SIZE,
                ThemeColors.hudMutedColor(Math.round(235.0F * alpha)));
        animation.render(timerX, y + 1.5F, timerColor);
        Render2D.text(FontType.BOLD, suffix, timerX + animation.width(), y + 1.5F, TEXT_SIZE, timerColor);
    }

    private float resolveWidth(List<TntTimerTracker.Entry> entries, boolean usePreview) {
        float widestName = Render2D.textWidth(FontType.BOLD, "TNT", TEXT_SIZE);
        float widestDistance = Render2D.textWidth(FontType.BOLD, "8.4m", TEXT_SIZE);
        if (!usePreview) {
            for (TntTimerTracker.Entry entry : entries) {
                String name = entry.name() == null || entry.name().isBlank() ? "TNT" : entry.name();
                widestName = Math.max(widestName, Render2D.textWidth(FontType.BOLD, name, TEXT_SIZE));
                String distanceText = String.format(Locale.US, "%.1fm", Math.max(0.0, entry.distance()));
                widestDistance = Math.max(widestDistance, Render2D.textWidth(FontType.BOLD, distanceText, TEXT_SIZE));
            }
        }

        float titleWidth = ThemeColors.isHudMerge()
                ? Render2D.textWidth(FontType.BOLD, "Active TNT", HEADER_TEXT_SIZE) + 6.0F
                : 0.0F;
        
        float timerReserve = Render2D.textWidth(FontType.BOLD, "99s", TEXT_SIZE) + 3.0F;
        float rowsWidth = PADDING + 8.0F + 4.0F + widestName + 5.0F + widestDistance + 6.0F + timerReserve + PADDING;
        return Math.max(MIN_WIDTH, (float) Math.ceil(Math.max(titleWidth, rowsWidth)));
    }

    private int colorForFuse(int fuse) {
        if (fuse < 20) {
            return 0xFFFF5555;
        }
        if (fuse < 40) {
            return 0xFFFFAA00;
        }
        return ThemeColors.hudTextColor(255);
    }

    private int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
