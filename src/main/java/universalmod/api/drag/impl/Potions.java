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
import universalmod.utils.render.ui.font.FontType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.awt.Color;

public final class Potions extends HudPanel {
    private static final float HEADER_HEIGHT = 18.0F;
    private static final float HEADER_GAP = 3.0F;
    private static final float BODY_PADDING_Y = 5.5F;
    private static final float BODY_PADDING_X = 7.0F;
    private static final float ROW_HEIGHT = 12.0F;
    private static final float ROW_GAP = 2.0F;
    private static final float ROW_STEP = ROW_HEIGHT + ROW_GAP;
    private static final float ICON_SIZE = 7.0F;
    private static final float ICON_SEPARATOR_GAP = 2.4F;
    private static final float SEPARATOR_WIDTH = 0.8F;
    private static final float SEPARATOR_TEXT_GAP = 3.6F;
    private static final float LABEL_TIMER_GAP = 7.0F;
    private static final float TIMER_BOX_HEIGHT = 8.6F;
    private static final float TIMER_CENTER_OFFSET_Y = 0.0F;
    private static final float PANEL_ANIM = 0.24F;
    private static final float ROW_ANIM = 0.22F;
    private static final float PANEL_WIDTH = 102.0F;
    private static final float PANEL_HEIGHT = BODY_PADDING_Y * 2.0F + ROW_HEIGHT;
    private static final float BODY_BLUR_RADIUS = 4.0F;
    private static final float BODY_BLUR_SMOOTHNESS = 0.55F;
    private static final float TEXT_SIZE = 6.6F;
    private static final float HEADER_TEXT_SIZE = 7.7F;

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
        boolean preview = active.isEmpty() && editPreview();
        boolean targetVisible = !active.isEmpty() || preview;

        hitExpansion(0.0F, HEADER_HEIGHT + HEADER_GAP, 0.0F, 0.0F);
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
        float nameWidth = Render2D.textWidth(TEXT_FONT, effectLabel(row.name, row.level), TEXT_SIZE);
        float labelWidth = nameWidth;
        return Math.max(
                PANEL_WIDTH,
                BODY_PADDING_X + ICON_SIZE + ICON_SEPARATOR_GAP + SEPARATOR_WIDTH + SEPARATOR_TEXT_GAP + labelWidth
                        + LABEL_TIMER_GAP + timerBoxWidth(row.durationTicks) + BODY_PADDING_X
        );
    }

    private float timerWidth(int durationTicks) {
        if (durationTicks < 0 || durationTicks >= 999_999_999) {
            return Render2D.textWidth(TEXT_FONT, "∞", TEXT_SIZE);
        }
        int totalSeconds = Math.max(0, durationTicks / 20);
        int minutes = Math.min(999, totalSeconds / 60);
        int minuteDigits = Math.max(1, String.valueOf(minutes).length());
        String stableTimer = "8".repeat(minuteDigits) + ":88";
        return Render2D.textWidth(TEXT_FONT, stableTimer, TEXT_SIZE);
    }

    private static float bodyHeight(int rowCount) {
        int rows = Math.max(1, rowCount);
        return BODY_PADDING_Y * 2.0F
                + rows * ROW_HEIGHT
                + Math.max(0, rows - 1) * ROW_GAP;
    }

    private static String effectLabel(String name, int level) {
        return name + " " + Math.max(1, level);
    }

    private void renderPotions(PotionsState state) {
        float alpha = state.alpha;
        String headerText = "Potions";
        int backgroundColor = ColorUtil.rgba(0, 0, 0, Math.round(255.0F * alpha));
        float headerY = state.y - HEADER_HEIGHT - HEADER_GAP;
        float titleY = centeredTextY(TEXT_FONT, headerText, HEADER_TEXT_SIZE, headerY + HEADER_HEIGHT * 0.5F);
        HudRenderCompat.background(state.x, headerY, state.width, HEADER_HEIGHT, 5.0F,
                BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
        HudRenderCompat.background(state.x, state.y, state.width, state.height, 5.0F,
                BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
        float markerHeight = 6.0F;
        float markerY = headerY + (HEADER_HEIGHT - markerHeight) * 0.5F;
        Render2D.rect(state.x + 6.0F, markerY, 1.4F, markerHeight, 0.7F, accentColor(214.0F * alpha));
        Render2D.text(TEXT_FONT, headerText, state.x + 11.0F, titleY, HEADER_TEXT_SIZE,
                ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha)));
        String headerIcon = "C";
        float headerIconSize = 7.0F;
        float headerIconX = state.x + state.width - 7.0F
                - Render2D.textWidth(FontType.VIREX_WONDERFUL, headerIcon, headerIconSize);
        float headerIconY = centeredTextY(FontType.VIREX_WONDERFUL, headerIcon, headerIconSize,
                headerY + HEADER_HEIGHT * 0.5F);
        Render2D.text(FontType.VIREX_WONDERFUL, headerIcon, headerIconX, headerIconY, headerIconSize,
                accentColor(255.0F * alpha));

        for (PotionState row : state.rows) {
            float rowAlpha = alpha * row.alpha;
            float rowCenterY = state.y + BODY_PADDING_Y + ROW_HEIGHT * 0.5F + row.offset;
            float iconX = state.x + BODY_PADDING_X;
            float iconY = rowCenterY - ICON_SIZE * 0.5F;
            float timerRightX = state.x + state.width - BODY_PADDING_X;
            int textAlpha = Math.round(214.0F * rowAlpha);
            int textColor = ColorUtil.rgba(255, 255, 255, textAlpha);
            int timerColor = accentColor(textAlpha);

            float timerW = timerBoxWidth(row.durationTicks);
            float timerLeftX = timerRightX - timerW;
            float separatorX = iconX + ICON_SIZE + ICON_SEPARATOR_GAP;
            float nameX = separatorX + SEPARATOR_WIDTH + SEPARATOR_TEXT_GAP;
            String visibleName = trimToWidth(effectLabel(row.name, row.level), TEXT_FONT, TEXT_SIZE,
                    Math.max(8.0F, timerLeftX - LABEL_TIMER_GAP - nameX));
            float textY = centeredTextY(TEXT_FONT, visibleName, TEXT_SIZE, rowCenterY);

            Render2D.effectIcon(row.effect, iconX, iconY, ICON_SIZE,
                    ColorUtil.rgba(255, 255, 255, Math.round(255.0F * rowAlpha)));
            Render2D.rect(separatorX, rowCenterY - 3.0F, SEPARATOR_WIDTH, 6.0F, 0.5F,
                    ColorUtil.rgba(255, 255, 255, Math.round(72.0F * rowAlpha)));
            Render2D.text(TEXT_FONT, visibleName, nameX, textY, TEXT_SIZE, textColor);
            renderDuration(row, timerRightX, rowCenterY + TIMER_CENTER_OFFSET_Y, timerColor, rowAlpha);
        }
    }

    private void renderDuration(PotionState row, float rightX, float centerY, int color, float alpha) {
        float boxWidth = timerBoxWidth(row.durationTicks);
        float boxLeftX = rightX - boxWidth;
        Render2D.rect(boxLeftX, centerY - TIMER_BOX_HEIGHT * 0.5F, boxWidth, TIMER_BOX_HEIGHT, 2.0F,
                accentBackgroundColor(112.0F * alpha));
        if (row.durationTicks < 0 || row.durationTicks >= 999_999_999) {
            float y = centerY - TEXT_SIZE * 0.5F;
            String infinite = "∞";
            float textWidth = Render2D.textWidth(TEXT_FONT, infinite, TEXT_SIZE);
            float textX = boxLeftX + (boxWidth - textWidth) * 0.5F;
            Render2D.text(TEXT_FONT, infinite,
                    textX, y, TEXT_SIZE, color);
            return;
        }
        int totalSeconds = Math.max(0, row.durationTicks / 20);
        int minutes = Math.min(999, totalSeconds / 60);
        int seconds = totalSeconds % 60;
        String prefix = minutes + ":";
        row.secondsAnimation.update(seconds);
        String timerText = prefix + twoDigits(seconds);
        Render2D.TextVisualBounds timerBounds = Render2D.textVisualBounds(TEXT_FONT, timerText, TEXT_SIZE);
        float timerWidth = row.secondsAnimation.timerWidth(prefix);
        float timerX = timerBounds.empty()
                ? boxLeftX + (boxWidth - timerWidth) * 0.5F
                : boxLeftX + boxWidth * 0.5F - (timerBounds.minX() + timerBounds.maxX()) * 0.5F;
        float y = timerBounds.empty()
                ? centerY - TEXT_SIZE * 0.5F
                : centerY - timerBounds.centerY();
        row.secondsAnimation.renderTimer(prefix, timerX, y, color);
    }

    private float timerBoxWidth(int durationTicks) {
        return timerWidth(durationTicks) + 7.0F;
    }

    private static int accentColor(float alpha) {
        Color color = Hud.getInstance() == null ? new Color(244, 176, 101) : Hud.getInstance().potionsColor();
        return ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * alpha / 255.0F));
    }

    private static int accentBackgroundColor(float alpha) {
        Color color = Hud.getInstance() == null ? new Color(126, 72, 39) : Hud.getInstance().potionsColor();
        return ColorUtil.rgba(Math.round(color.getRed() * 0.52F), Math.round(color.getGreen() * 0.52F),
                Math.round(color.getBlue() * 0.52F), Math.round(color.getAlpha() * alpha / 255.0F));
    }

    private static float centeredTextY(FontType font, String text, float size, float centerY) {
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(font, text, size);
        return bounds.empty() ? centerY - size * 0.5F : centerY - bounds.centerY();
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
