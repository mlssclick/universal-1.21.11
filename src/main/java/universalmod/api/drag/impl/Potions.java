package universalmod.api.drag.impl;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import universalmod.api.module.impl.render.Hud;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimatedNumber;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.theme.ThemeColors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Potions extends HudPanel {
    private static final String HEADER_ICON_TEXTURE = "universalmod:textures/hud/header_potions.png";
    private static final float HEADER_HEIGHT = 18.0F;
    private static final float HEADER_GAP = 5.0F;
    private static final float BODY_PADDING_Y = 5.0F;
    private static final float BODY_PADDING_X = 6.0F;
    private static final float ROW_HEIGHT = 10.0F;
    private static final float ROW_GAP = 2.0F;
    private static final float ROW_STEP = ROW_HEIGHT + ROW_GAP;
    private static final float ICON_SIZE = 10.0F;
    private static final float ICON_TEXT_GAP = 5.0F;
    private static final float LABEL_TIMER_GAP = 10.0F;
    private static final float PANEL_ANIM = 0.24F;
    private static final float ROW_ANIM = 0.22F;
    private static final float PANEL_WIDTH = 92.0F;
    private static final float PANEL_HEIGHT = BODY_PADDING_Y * 2.0F + ROW_HEIGHT;
    private static final float BODY_BLUR_RADIUS = 4.0F;
    private static final float BODY_BLUR_SMOOTHNESS = 0.55F;
    private static final float TEXT_SIZE = 6.0F;
    private static final float HEADER_TEXT_SIZE = 9.0F;
    private static final float HEADER_ICON_EDGE_PADDING = 4.0F;
    private static final float HEADER_TEXT_GAP = 4.0F;
    private static final float HEADER_ICON_SIZE = 8.0F;

    private final List<PotionRow> rows = new ArrayList<>();
    private final List<PotionState> states = new ArrayList<>();
    private final SmoothAnimation panelAnimation = new SmoothAnimation();

    public Potions() {
        super("potions", "Potions", 118.0F, 40.0F, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render() {
        if (mc.player == null) {
            rows.clear();
            contentVisible(false);
            return;
        }

        List<MobEffectInstance> active = new ArrayList<>(mc.player.getActiveEffects());
        active.sort(Comparator
                .comparingInt(Potions::durationSortKey)
                .thenComparing(effect -> effect.getEffect().value().getDisplayName().getString(), String.CASE_INSENSITIVE_ORDER));
        boolean preview = active.isEmpty() && editPreview();
        boolean targetVisible = !active.isEmpty() || preview;

        float headerExpansion = ThemeColors.isHudWithoutName()
                ? 0.0F
                : HEADER_HEIGHT + (ThemeColors.isHudSplit() ? HEADER_GAP : 0.0F);
        hitExpansion(0.0F, headerExpansion, 0.0F, 0.0F);
        panelAnimation.update();
        panelAnimation.run(targetVisible ? 1.0F : 0.0F, PANEL_ANIM,
                targetVisible ? Easings.EXPO_OUT : Easings.EXPO_IN, true);

        for (PotionRow row : rows) {
            row.active = false;
            row.alpha.update();
            row.y.update();
        }

        int targetRows = 0;
        if (preview) {
            PotionRow row = row(MobEffects.SPEED.value(), MobEffects.SPEED, "Speed", 2, 84 * 20, 0.0F);
            activate(row, targetRows++);
        } else {
            for (MobEffectInstance instance : active) {
                Holder<MobEffect> holder = instance.getEffect();
                MobEffect effect = holder.value();
                String name = effect.getDisplayName().getString();
                PotionRow row = row(effect, holder, name, instance.getAmplifier() + 1, instance.getDuration(), targetRows * ROW_STEP);
                activate(row, targetRows++);
            }
        }

        for (PotionRow row : rows) {
            if (!row.active) {
                row.alpha.run(0.0F, ROW_ANIM, Easings.EXPO_IN, true);
            }
        }
        rows.removeIf(row -> !row.active && row.alpha.get() <= 0.01F && !row.alpha.isAlive());

        float panelAlpha = panelAnimation.get();
        boolean visible = targetVisible || panelAlpha > 0.01F || !rows.isEmpty();
        contentVisible(visible);
        if (!visible) {
            return;
        }

        float width = PANEL_WIDTH;
        for (PotionRow row : rows) {
            if (row.alpha.get() > 0.01F || row.active) {
                width = Math.max(width, rowWidth(row));
            }
        }
        size(width, bodyHeight(Math.max(1, targetRows)));

        states.clear();
        for (PotionRow row : rows) {
            float rowAlpha = row.alpha.get();
            if (rowAlpha > 0.01F || row.active) {
                states.add(new PotionState(row.effect, row.name, row.level, row.durationTicks, row.y.get(), rowAlpha,
                        row.levelAnimation, row.secondsAnimation));
            }
        }
        states.sort(Comparator.comparingDouble(PotionState::offset));

        renderPotions(new PotionsState(List.copyOf(states), panelAlpha, drag.x(), drag.y(), logicalWidth(), logicalHeight()));
    }

    private void activate(PotionRow row, int index) {
        float targetY = index * ROW_STEP;
        row.active = true;
        row.alpha.run(1.0F, ROW_ANIM, Easings.EXPO_OUT, true);
        row.y.run(targetY, ROW_ANIM, Easings.EXPO_OUT, true);
    }

    private PotionRow row(MobEffect key, Holder<MobEffect> effect, String name, int level, int durationTicks, float targetY) {
        for (PotionRow row : rows) {
            if (row.key == key || row.key.equals(key)) {
                row.effect = effect;
                row.name = name;
                row.level = Math.max(1, level);
                row.durationTicks = durationTicks;
                row.updateNumbers();
                return row;
            }
        }

        PotionRow row = new PotionRow(key, effect, name, level, durationTicks);
        row.alpha.set(0.0F);
        row.y.set(targetY + 4.0F);
        rows.add(row);
        return row;
    }

    private float rowWidth(PotionRow row) {
        float nameWidth = Render2D.textWidth(TEXT_FONT, row.name, TEXT_SIZE);
        float levelWidth = Render2D.textWidth(TEXT_FONT, levelText(row.level), TEXT_SIZE);
        float labelWidth = nameWidth + levelGap() + levelWidth;
        return Math.max(
                PANEL_WIDTH,
                BODY_PADDING_X + ICON_SIZE + ICON_TEXT_GAP + labelWidth
                        + LABEL_TIMER_GAP + timerWidth(row.durationTicks) + BODY_PADDING_X
        );
    }

    private float levelGap() {
        return Render2D.textWidth(TEXT_FONT, " ", TEXT_SIZE);
    }

    private float timerWidth(int durationTicks) {
        if (durationTicks < 0 || durationTicks >= 999_999_999) {
            return Render2D.textWidth(TEXT_FONT, "∞", TEXT_SIZE);
        }
        int totalSeconds = Math.max(0, durationTicks / 20);
        int minutes = Math.min(999, totalSeconds / 60);
        return Render2D.textWidth(TEXT_FONT, minutes + ":" + twoDigits(totalSeconds % 60), TEXT_SIZE);
    }

    private static float bodyHeight(int rowCount) {
        int rows = Math.max(1, rowCount);
        return BODY_PADDING_Y * 2.0F
                + rows * ROW_HEIGHT
                + Math.max(0, rows - 1) * ROW_GAP;
    }

    private static int durationSortKey(MobEffectInstance effect) {
        int duration = effect.getDuration();
        return duration < 0 || duration >= 999_999_999 ? Integer.MAX_VALUE : Math.max(0, duration);
    }

    private static boolean isExpiringSoon(int durationTicks) {
        return durationTicks >= 0 && durationTicks < 999_999_999 && durationTicks <= 15 * 20;
    }

    private String levelText(int level) {
        return useRomanLevels() ? romanLevel(level) : String.valueOf(Math.max(1, level));
    }

    private void renderPotions(PotionsState state) {
        float alpha = state.alpha;
        String headerText = "Potions";
        int backgroundColor = ColorUtil.rgba(0, 0, 0, Math.round(255.0F * alpha));
        boolean withoutName = ThemeColors.isHudWithoutName();
        boolean split = ThemeColors.isHudSplit();

        if (withoutName) {
            HudRenderCompat.background(state.x, state.y, state.width, state.height, 4.0F,
                    BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
        } else if (split) {
            boolean showIcon = ThemeColors.showSplitIcon();
            float headerTextWidth = Render2D.textWidth(TEXT_FONT, headerText, HEADER_TEXT_SIZE);
            float headerPadding = 6.0F;
            float iconPart = showIcon ? HEADER_ICON_SIZE + HEADER_TEXT_GAP : 0.0F;
            float headerWidth = headerTextWidth + iconPart + headerPadding * 2.0F;
            float headerX = state.x + (state.width - headerWidth) * 0.5F;
            float headerY = state.y - HEADER_HEIGHT - HEADER_GAP;
            float headerTextY = headerY + (HEADER_HEIGHT - Render2D.textHeight(TEXT_FONT, headerText, HEADER_TEXT_SIZE)) * 0.5F;
            float headerRounding = ThemeColors.splitHeaderRounding();
            float textX = headerX + headerPadding + iconPart;

            HudRenderCompat.splitHeader(headerX, headerY, headerWidth, HEADER_HEIGHT, headerRounding,
                    BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
            HudRenderCompat.background(state.x, state.y, state.width, state.height, 4.0F,
                    BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
            if (showIcon && alpha > 0.001F) {
                float iconX = headerX + headerPadding;
                float iconY = headerTextY + (Render2D.textHeight(TEXT_FONT, headerText, HEADER_TEXT_SIZE) - HEADER_ICON_SIZE) * 0.5F;
                Render2D.image(HEADER_ICON_TEXTURE, iconX, iconY, HEADER_ICON_SIZE, HEADER_ICON_SIZE, 0.0F,
                        hudTextColor(Math.round(255.0F * alpha)));
            }
            Render2D.text(TEXT_FONT, headerText, textX, headerTextY, HEADER_TEXT_SIZE,
                    hudTextColor(Math.round(255.0F * alpha)));
        } else {
            float headerY = state.y - HEADER_HEIGHT;
            float headerTextY = headerY + (HEADER_HEIGHT - Render2D.textHeight(TEXT_FONT, headerText, HEADER_TEXT_SIZE)) * 0.5F;
            float textX = state.x + HEADER_ICON_EDGE_PADDING;
            float iconX = state.x + state.width - HEADER_ICON_EDGE_PADDING - HEADER_ICON_SIZE;
            float iconY = headerTextY + (Render2D.textHeight(TEXT_FONT, headerText, HEADER_TEXT_SIZE) - HEADER_ICON_SIZE) * 0.5F;
            HudRenderCompat.background(state.x, headerY, state.width, HEADER_HEIGHT + state.height, 4.0F,
                    BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
            int headerIconColor = hudTextColor(Math.round(255.0F * alpha));
            if (((headerIconColor >>> 24) & 0xFF) > 0) {
                Render2D.image(HEADER_ICON_TEXTURE, iconX, iconY, HEADER_ICON_SIZE, HEADER_ICON_SIZE, 0.0F,
                        headerIconColor);
            }
            Render2D.text(TEXT_FONT, headerText, textX, headerTextY, HEADER_TEXT_SIZE,
                    hudTextColor(Math.round(255.0F * alpha)));
        }

        for (PotionState row : state.rows) {
            float rowAlpha = alpha * row.alpha;
            float rowCenterY = state.y + BODY_PADDING_Y + ROW_HEIGHT * 0.5F + row.offset;
            float iconX = state.x + BODY_PADDING_X;
            float iconY = rowCenterY - ICON_SIZE * 0.5F;
            float textY = rowCenterY - TEXT_SIZE * 0.5F - 0.15F;
            float timerRightX = state.x + state.width - BODY_PADDING_X;
            int textAlpha = Math.round(245.0F * rowAlpha);
            int textColor = hudTextColor(textAlpha);
            int timerColor = isExpiringSoon(row.durationTicks)
                    ? ColorUtil.rgba(255, 70, 70, textAlpha)
                    : textColor;

            float timerW = timerWidth(row.durationTicks);
            float timerLeftX = timerRightX - timerW;
            float nameX = iconX + ICON_SIZE + ICON_TEXT_GAP;
            String visibleName = row.name;
            float levelX = nameX + Render2D.textWidth(TEXT_FONT, visibleName + " ", TEXT_SIZE);

            Render2D.effectIcon(row.effect, iconX, iconY, ICON_SIZE,
                    ColorUtil.rgba(255, 255, 255, Math.round(255.0F * rowAlpha)));
            Render2D.text(TEXT_FONT, visibleName, nameX, textY, TEXT_SIZE, textColor);
            renderLevel(row, levelX, textY, textColor);
            renderDuration(row, timerRightX, textY, timerColor);
        }
    }

    private void renderLevel(PotionState row, float x, float y, int color) {
        if (useRomanLevels()) {
            Render2D.text(TEXT_FONT, romanLevel(row.level), x, y, TEXT_SIZE, color);
            return;
        }
        row.levelAnimation.update(row.level);
        row.levelAnimation.render(x, y, color);
    }

    private void renderDuration(PotionState row, float rightX, float y, int color) {
        if (row.durationTicks < 0 || row.durationTicks >= 999_999_999) {
            String infinite = "∞";
            Render2D.text(TEXT_FONT, infinite,
                    rightX - Render2D.textWidth(TEXT_FONT, infinite, TEXT_SIZE), y, TEXT_SIZE, color);
            return;
        }
        int totalSeconds = Math.max(0, row.durationTicks / 20);
        int minutes = Math.min(999, totalSeconds / 60);
        int seconds = totalSeconds % 60;
        String prefix = minutes + ":";
        row.secondsAnimation.update(seconds);
        float timerWidth = row.secondsAnimation.timerWidth(prefix);
        row.secondsAnimation.renderTimer(prefix, rightX - timerWidth, y, color);
    }

    private boolean useRomanLevels() {
        Hud hud = Hud.getInstance();
        return hud == null || hud.useRomanPotionLevels();
    }

    private static String romanLevel(int level) {
        int safe = Math.max(1, level);
        if (safe > 3999) {
            return String.valueOf(safe);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        int remaining = safe;
        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                builder.append(numerals[i]);
                remaining -= values[i];
            }
        }
        return builder.toString();
    }

    private static final class PotionRow {
        private final MobEffect key;
        private Holder<MobEffect> effect;
        private String name;
        private int level;
        private int durationTicks;
        private final SmoothAnimation alpha = new SmoothAnimation();
        private final SmoothAnimation y = new SmoothAnimation();
        private final SmoothAnimatedNumber levelAnimation = new SmoothAnimatedNumber(TEXT_FONT, TEXT_SIZE, 4.0F, 300L, Easings.FIGMA_EASE_IN_OUT, false);
        private final SmoothAnimatedNumber secondsAnimation = new SmoothAnimatedNumber(TEXT_FONT, TEXT_SIZE, 3.5F, 300L, Easings.FIGMA_EASE_IN_OUT, true);
        private boolean active;

        private PotionRow(MobEffect key, Holder<MobEffect> effect, String name, int level, int durationTicks) {
            this.key = key;
            this.effect = effect;
            this.name = name;
            this.level = Math.max(1, level);
            this.durationTicks = durationTicks;
            updateNumbers();
        }

        private void updateNumbers() {
            levelAnimation.update(level);
            if (durationTicks >= 0 && durationTicks < 999_999_999) {
                secondsAnimation.update(Math.max(0, (durationTicks / 20) % 60));
            }
        }
    }

    private record PotionState(
            Holder<MobEffect> effect, String name, int level, int durationTicks, float offset, float alpha,
            SmoothAnimatedNumber levelAnimation, SmoothAnimatedNumber secondsAnimation
    ) {
    }

    private record PotionsState(List<PotionState> rows, float alpha, float x, float y, float width, float height) {
    }
}
