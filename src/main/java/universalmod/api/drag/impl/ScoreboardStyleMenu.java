package universalmod.api.drag.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.api.drag.core.ElementScreen;
import universalmod.api.module.impl.render.Scoreboard;
import universalmod.api.settings.Setting;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.Render2DCoordinateSpace;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.theme.ThemeColors;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScoreboardStyleMenu {
    private static final ScoreboardStyleMenu INSTANCE = new ScoreboardStyleMenu();
    private static final float WIDTH = 170.0F;
    private static final float HEADER = 18.0F;
    private static final float BOOLEAN_ROW = 18.0F;
    private static final float MODE_ROW = 18.0F;
    private static final float NUMBER_ROW = 24.0F;
    private static final float COLOR_ROW = 18.0F;
    private static final float OPTION = 14.0F;
    private static final float PICKER_WIDTH = 84.0F;
    private static final float PICKER_HEIGHT = 86.0F;
    private static final float PICKER_GAP = 6.0F;
    private static final float PADDING = 6.0F;
    private static final float CONTROL_WIDTH = 72.0F;
    private static final float SIZE_CONTROL_WIDTH = 142.0F;
    private static final float GAP = 8.0F;
    private static final float MARGIN = 4.0F;
    private static final float SCROLL_STEP = 28.0F;
    private static final float MAX_VIEWPORT = 238.0F;
    private static final int[] HUE_COLORS = {
            Color.HSBtoRGB(0.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(1.0F / 6.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(2.0F / 6.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(3.0F / 6.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(4.0F / 6.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(5.0F / 6.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(1.0F, 1.0F, 1.0F)
    };

    private final SmoothAnimation appear = new SmoothAnimation();
    private final SmoothAnimation dropdown = new SmoothAnimation();
    private final SmoothAnimation picker = new SmoothAnimation();
    private final SmoothAnimation scroll = new SmoothAnimation();
    private final Map<BooleanSetting, SmoothAnimation> booleanAnimations = new HashMap<>();
    private final Map<NumberSetting, SmoothAnimation> numberAnimations = new HashMap<>();
    private Scoreboard scoreboard;
    private ModeSetting openMode;
    private boolean modeExpanded;
    private ColorSetting openColor;
    private NumberSetting activeSlider;
    private float activeSliderTrackX;
    private float activeSliderTrackWidth;
    private Side side = Side.RIGHT;
    private float x;
    private float y;
    private float scrollTarget;
    private float hue;
    private float saturation;
    private float brightness;
    private float alphaValue;
    private int colorDragMode;
    private float pickerPanelX;
    private float pickerPanelY;
    private float paletteX;
    private float paletteY;
    private float paletteSize;
    private float hueX;
    private float hueY;
    private float hueWidth;
    private float hueHeight;
    private float alphaX;
    private float alphaY;
    private float alphaWidth;
    private float alphaHeight;

    private ScoreboardStyleMenu() {
        appear.set(0.0);
        dropdown.set(0.0);
        picker.set(0.0);
        scroll.set(0.0);
    }

    public static ScoreboardStyleMenu getInstance() {
        return INSTANCE;
    }

    public boolean isOpen() {
        return scoreboard != null;
    }

    public void open(Scoreboard scoreboard, float mouseX) {
        if (scoreboard == null) {
            return;
        }
        Side next = mouseX < scoreboard.editorX() + scoreboard.editorWidth() * 0.5F ? Side.LEFT : Side.RIGHT;
        if (this.scoreboard == scoreboard && side == next) {
            return;
        }
        this.scoreboard = scoreboard;
        side = next;
        openMode = null;
        modeExpanded = false;
        openColor = null;
        activeSlider = null;
        colorDragMode = 0;
        scrollTarget = 0.0F;
        appear.set(0.0);
        dropdown.set(0.0);
        picker.set(0.0);
        scroll.set(0.0);
        place(true);
        appear.run(1.0, 0.20, Easings.BAKEK);
    }

    public void close() {
        scoreboard = null;
        openMode = null;
        modeExpanded = false;
        openColor = null;
        activeSlider = null;
        colorDragMode = 0;
        scrollTarget = 0.0F;
        appear.set(0.0);
        dropdown.set(0.0);
        picker.set(0.0);
        scroll.set(0.0);
    }

    public void render(GuiGraphics graphics) {
        if (!isOpen()) {
            return;
        }
        appear.update();
        dropdown.update();
        picker.update();
        if (openMode != null && !openMode.isVisible() && modeExpanded) {
            modeExpanded = false;
            dropdown.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        }
        if (openMode != null && !modeExpanded && dropdown.get() <= 0.001F && !dropdown.isAlive()) {
            openMode = null;
        }
        if (openColor != null && !openColor.isVisible()) {
            openColor = null;
            colorDragMode = 0;
            picker.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        } else if (openColor != null && colorDragMode == 0) {
            loadColor(openColor.getValue());
        }
        updatePointerDrag();
        clampScroll();
        scroll.run(scrollTarget, activeSlider != null || colorDragMode != 0 ? 0.08 : 0.18, Easings.CUBIC_OUT, true);
        scroll.update();
        place(false);
        float a = clamp01(appear.get());
        if (a <= 0.001F) {
            return;
        }
        float height = popupHeight();
        scoreboard.renderEditorPopupBackground(graphics, x, y, WIDTH, height, 5.0F);
        Render2D.text(FontType.BOLD, "Custom Scoreboard", x + PADDING, y + 4.4F, 7.6F, alpha(0xFFF3F6FA, a));
        float viewportY = y + HEADER;
        float viewportHeight = height - HEADER - PADDING;
        Render2D.pushScissor(graphics, x + 1.0F, viewportY, WIDTH - 2.0F, viewportHeight);
        try {
            float rowY = viewportY - scroll.get();
            for (Setting<?> setting : visibleSettings()) {
                renderSetting(graphics, setting, rowY, a);
                rowY += settingHeight(setting);
            }
        } finally {
            Render2D.popScissor(graphics);
        }
        renderScrollIndicator(a, viewportY, viewportHeight);
        if (openColor != null && picker.get() > 0.001F && colorAnchorVisible()) {
            updatePickerBounds();
            renderColorPicker(graphics, clamp01(picker.get()), a);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (!isOpen() || event == null) {
            return false;
        }
        place(false);
        float mx = (float) event.x();
        float my = (float) event.y();
        boolean pickerVisible = openColor != null && picker.get() > 0.20F && colorAnchorVisible();
        if (pickerVisible) {
            updatePickerBounds();
            if (inside(mx, my, pickerPanelX, pickerPanelY, PICKER_WIDTH, PICKER_HEIGHT)) {
                if (event.button() == 0) {
                    handleColorPickerClick(mx, my);
                }
                return true;
            }
        }
        if (!inside(mx, my, x, y, WIDTH, popupHeight())) {
            if (event.button() == 1) {
                return false;
            }
            close();
            return false;
        }
        if (event.button() != 0) {
            return true;
        }
        float viewportY = y + HEADER;
        float viewportHeight = popupHeight() - HEADER - PADDING;
        if (!inside(mx, my, x, viewportY, WIDTH, viewportHeight)) {
            return true;
        }
        float rowY = viewportY - scroll.get();
        for (Setting<?> setting : visibleSettings()) {
            float h = settingHeight(setting);
            if (setting instanceof BooleanSetting booleanSetting && inside(mx, my, x + PADDING, rowY, WIDTH - PADDING * 2.0F, BOOLEAN_ROW)) {
                closeExpanded();
                booleanSetting.setValue(!booleanSetting.getValue());
                return true;
            }
            if (setting instanceof ModeSetting modeSetting) {
                if (inside(mx, my, controlX(), rowY + 2.0F, CONTROL_WIDTH, 13.0F)) {
                    toggleMode(modeSetting);
                    return true;
                }
                if (openMode == modeSetting && modeExpanded && dropdown.get() > 0.20F) {
                    int index = modeOptionIndex(modeSetting, mx, my, rowY + MODE_ROW);
                    if (index >= 0) {
                        modeSetting.setValue(modeSetting.getModes().get(index));
                        modeExpanded = false;
                        dropdown.run(0.0, 0.16, Easings.CUBIC_OUT, true);
                        clampScroll();
                        return true;
                    }
                }
            }
            if (setting instanceof NumberSetting numberSetting) {
                float sliderX = numberSliderX(numberSetting);
                float sliderWidth = numberSliderWidth(numberSetting);
                if (inside(mx, my, sliderX - 3.0F, rowY + 6.0F, sliderWidth + 6.0F, 17.0F)) {
                    closeExpanded();
                    activeSlider = numberSetting;
                    activeSliderTrackX = sliderX;
                    activeSliderTrackWidth = sliderWidth;
                    updateNumber(numberSetting, mx);
                    return true;
                }
            }
            if (setting instanceof ColorSetting colorSetting && inside(mx, my, controlX() + CONTROL_WIDTH - 17.0F, rowY + 2.0F, 18.0F, 14.0F)) {
                toggleColor(colorSetting);
                return true;
            }
            rowY += h;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!isOpen()) {
            return false;
        }
        if (openColor != null && picker.get() > 0.20F && colorAnchorVisible()) {
            updatePickerBounds();
            if (inside((float) mouseX, (float) mouseY, pickerPanelX, pickerPanelY, PICKER_WIDTH, PICKER_HEIGHT)) {
                return true;
            }
        }
        if (!inside((float) mouseX, (float) mouseY, x, y, WIDTH, popupHeight())) {
            return false;
        }
        float max = maxScroll();
        if (max <= 0.01F) {
            return true;
        }
        scrollTarget = clamp(scrollTarget - (float) amount * SCROLL_STEP, 0.0F, max);
        scroll.run(scrollTarget, 0.18, Easings.CUBIC_OUT, true);
        return true;
    }

    private void renderSetting(GuiGraphics graphics, Setting<?> setting, float rowY, float a) {
        if (setting instanceof BooleanSetting booleanSetting) {
            renderBoolean(rowY, booleanSetting, a);
            return;
        }
        if (setting instanceof ModeSetting modeSetting) {
            renderMode(graphics, rowY, modeSetting, a);
            return;
        }
        if (setting instanceof NumberSetting numberSetting) {
            renderNumber(rowY, numberSetting, a);
            return;
        }
        if (setting instanceof ColorSetting colorSetting) {
            renderColor(graphics, rowY, colorSetting, a);
        }
    }

    private void renderBoolean(float rowY, BooleanSetting setting, float a) {
        Render2D.text(FontType.BOLD, setting.getDisplayName(), x + PADDING, rowY + 5.0F, 6.7F, alpha(0xFFDDE3EA, a));
        float sw = 22.0F;
        float sh = 12.0F;
        float sx = x + WIDTH - PADDING - sw;
        float sy = rowY + 3.0F;
        SmoothAnimation animation = booleanAnimation(setting);
        animation.run(setting.getValue() ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT, true);
        animation.update();
        float progress = clamp01(animation.get());
        float knobX = sx + 2.0F + 10.0F * progress;

        renderBooleanGlass(sx, sy, sw, sh, 5.0F, a, progress);
        Render2D.rect(knobX, sy + 2.0F, 8.0F, 8.0F, 3.0F,
                ColorUtil.rgba(255, 255, 255, Math.round(245.0F * a)));
    }

    private void renderMode(GuiGraphics graphics, float rowY, ModeSetting setting, float a) {
        Render2D.text(FontType.BOLD, setting.getDisplayName(), x + PADDING, rowY + 5.0F, 6.7F, alpha(0xFFDDE3EA, a));
        float bx = controlX();
        boolean expanded = openMode == setting && modeExpanded;
        boolean dropdownVisible = openMode == setting && dropdown.get() > 0.001F;
        Render2D.rect(bx, rowY + 2.0F, CONTROL_WIDTH, 13.0F, 3.5F,
                ColorUtil.rgba(255, 255, 255, Math.round((expanded ? 40.0F : 24.0F) * a)));
        renderClippedText(graphics, setting.getDisplayValue(), bx + 4.0F, rowY + 4.4F, CONTROL_WIDTH - 8.0F, 6.2F,
                alpha(expanded ? 0xFFFFFFFF : 0xFFCDD3DA, a));
        if (dropdownVisible) {
            renderModeOptions(graphics, rowY + MODE_ROW, setting, clamp01(dropdown.get()), a);
        }
    }

    private void renderModeOptions(GuiGraphics graphics, float optionY, ModeSetting setting, float progress, float a) {
        float bx = controlX();
        float fullHeight = modeOptionsHeight(setting);
        float visible = fullHeight * progress;
        scoreboard.renderEditorPopupBackground(graphics, bx, optionY, CONTROL_WIDTH, visible, 3.5F);
        Render2D.pushScissor(graphics, bx, optionY, CONTROL_WIDTH, visible);
        try {
            float slide = (1.0F - progress) * 4.0F;
            List<String> modes = setting.getModes();
            for (int i = 0; i < modes.size(); i++) {
                float oy = optionY + 1.0F + i * OPTION - slide;
                String mode = modes.get(i);
                boolean selected = setting.is(mode);
                if (selected) {
                    Render2D.rect(bx + 2.0F, oy + 1.0F, CONTROL_WIDTH - 4.0F, OPTION - 2.0F, 2.5F,
                            ColorUtil.rgba(255, 255, 255, Math.round(22.0F * a * progress)));
                }
                renderClippedText(graphics, setting.getDisplayMode(mode), bx + 4.0F, oy + 3.0F, CONTROL_WIDTH - 8.0F, 6.1F,
                        alpha(selected ? 0xFFFFFFFF : 0xFFBFC7CF, a * progress));
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private void renderNumber(float rowY, NumberSetting setting, float a) {
        boolean size = setting == scoreboard.editorScaleSetting();
        String label = size ? "Size" : setting.getDisplayName();
        Render2D.text(FontType.BOLD, label, x + PADDING, rowY + 7.0F, 6.7F, alpha(0xFFDDE3EA, a));
        float sx = numberSliderX(setting);
        float sw = numberSliderWidth(setting);
        float sy = rowY + 16.0F;
        SmoothAnimation animation = numberAnimation(setting);
        animation.run(numberProgress(setting), activeSlider == setting ? 0.10 : 0.18, Easings.CUBIC_OUT, true);
        animation.update();
        float p = clamp01(animation.get());
        String value = formatNumber(setting, size);
        float tw = Render2D.textWidth(FontType.BOLD, value, 6.1F);
        Render2D.text(FontType.BOLD, value, x + WIDTH - PADDING - tw, rowY + 4.0F, 6.1F, alpha(0xFFF2F5F8, a));
        renderSliderGlass(sx, sy - 0.5F, sw, 4.0F, 1.0F, a);
        Render2D.rect(sx, sy, sw * p, 3.0F, 1.0F, ThemeColors.clickGuiSliderFillColor(a));
        float knob = 7.0F;
        float kx = clamp(sx + sw * p - knob * 0.5F, sx, sx + sw - knob);
        Render2D.rect(kx, sy - 2.0F, knob, knob, 2.5F, ThemeColors.clickGuiSliderKnobColor(a));
    }

    private void renderColor(GuiGraphics graphics, float rowY, ColorSetting setting, float a) {
        Render2D.text(FontType.BOLD, setting.getDisplayName(), x + PADDING, rowY + 5.0F, 6.7F, alpha(0xFFDDE3EA, a));
        Color color = setting.getValue();
        float size = 12.0F;
        float sx = controlX() + CONTROL_WIDTH - size - 1.0F;
        float sy = rowY + 3.0F;
        float outerSize = size + 4.0F;
        float outerX = sx - 2.0F;
        float outerY = sy - 2.0F;
        Render2D.rect(outerX, outerY, outerSize, outerSize, outerSize * 0.5F,
                ColorUtil.rgba(255, 255, 255, Math.round(30.0F * a)));
        Render2D.outline(outerX, outerY, outerSize, outerSize, outerSize * 0.5F, 0.75F,
                ColorUtil.rgba(255, 255, 255, Math.round(42.0F * a)));
        Render2D.rect(sx, sy, size, size, size * 0.5F,
                ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * a)));
    }

    private void renderColorPicker(GuiGraphics graphics, float progress, float a) {
        float popupAlpha = a * progress;
        float slide = (1.0F - progress) * 4.0F;
        float py = pickerPanelY + slide;
        scoreboard.renderEditorPopupBackground(graphics, pickerPanelX, py, PICKER_WIDTH, PICKER_HEIGHT, 4.0F);
        paletteX = pickerPanelX + 7.0F;
        paletteY = py + 7.0F;
        paletteSize = 58.0F;
        hueX = paletteX + paletteSize + 5.0F;
        hueY = paletteY;
        hueWidth = 7.0F;
        hueHeight = paletteSize;
        alphaX = paletteX;
        alphaY = paletteY + paletteSize + 7.0F;
        alphaWidth = paletteSize + 12.0F;
        alphaHeight = 7.0F;
        drawPalette(popupAlpha);
        drawHue(popupAlpha);
        drawAlpha(popupAlpha);
    }

    private void drawPalette(float a) {
        int hueColor = Color.HSBtoRGB(hue, 1.0F, 1.0F);
        int topLeft = ColorUtil.rgba(255, 255, 255, Math.round(255.0F * a));
        int topRight = ColorUtil.rgba(red(hueColor), green(hueColor), blue(hueColor), Math.round(255.0F * a));
        int bottom = ColorUtil.rgba(0, 0, 0, Math.round(255.0F * a));
        Render2D.rect(paletteX, paletteY, paletteSize, paletteSize, 4.0F, topLeft, topRight, bottom, bottom);
        float selectorX = paletteX + saturation * paletteSize;
        float selectorY = paletteY + (1.0F - brightness) * paletteSize;
        Render2D.outline(selectorX - 2.5F, selectorY - 2.5F, 5.0F, 5.0F, 1.5F, 1.0F,
                ColorUtil.rgba(255, 255, 255, Math.round(245.0F * a)));
        Render2D.rect(selectorX - 1.0F, selectorY - 1.0F, 2.0F, 2.0F, 0.5F,
                ColorUtil.rgba(20, 24, 32, Math.round(190.0F * a)));
    }

    private void drawHue(float a) {
        float segmentHeight = hueHeight / (HUE_COLORS.length - 1);
        for (int i = 0; i < HUE_COLORS.length - 1; i++) {
            int top = HUE_COLORS[i];
            int bottom = HUE_COLORS[i + 1];
            float sy = hueY + i * segmentHeight;
            Render2D.rect(hueX, sy, hueWidth, segmentHeight + 0.5F,
                    i == 0 ? 2.0F : 0.0F,
                    i == 0 ? 2.0F : 0.0F,
                    i == HUE_COLORS.length - 2 ? 2.0F : 0.0F,
                    i == HUE_COLORS.length - 2 ? 2.0F : 0.0F,
                    ColorUtil.rgba(red(top), green(top), blue(top), Math.round(255.0F * a)),
                    ColorUtil.rgba(red(top), green(top), blue(top), Math.round(255.0F * a)),
                    ColorUtil.rgba(red(bottom), green(bottom), blue(bottom), Math.round(255.0F * a)),
                    ColorUtil.rgba(red(bottom), green(bottom), blue(bottom), Math.round(255.0F * a)));
        }
        float selectorY = hueY + hue * hueHeight;
        Render2D.rect(hueX - 1.0F, selectorY - 1.0F, hueWidth + 2.0F, 2.0F, 0.0F,
                ColorUtil.rgba(255, 255, 255, Math.round(255.0F * a)));
    }

    private void drawAlpha(float a) {
        int rgb = currentRgb();
        int transparent = ColorUtil.rgba(red(rgb), green(rgb), blue(rgb), 0);
        int opaque = ColorUtil.rgba(red(rgb), green(rgb), blue(rgb), Math.round(255.0F * a));
        Render2D.rect(alphaX, alphaY, alphaWidth, alphaHeight, 2.5F, transparent, opaque, opaque, transparent);
        float selectorX = alphaX + alphaWidth * alphaValue;
        Render2D.rect(selectorX - 1.0F, alphaY - 1.0F, 2.0F, alphaHeight + 2.0F, 1.0F,
                ColorUtil.rgba(255, 255, 255, Math.round(255.0F * a)));
    }

    private void renderScrollIndicator(float a, float viewportY, float viewportHeight) {
        float max = maxScroll();
        if (max <= 0.5F) {
            return;
        }
        float content = contentHeight();
        float thumbHeight = Math.max(18.0F, viewportHeight * viewportHeight / content);
        float track = viewportHeight - thumbHeight;
        float p = max <= 0.0F ? 0.0F : clamp01(scroll.get() / max);
        Render2D.rect(x + WIDTH - 2.2F, viewportY + track * p, 1.2F, thumbHeight, 0.6F,
                ColorUtil.rgba(235, 239, 244, Math.round(95.0F * a)));
    }

    private void renderClippedText(GuiGraphics graphics, String text, float tx, float ty, float width, float size, int color) {
        Render2D.pushScissor(graphics, tx, ty - 1.0F, Math.max(1.0F, width), size + 4.0F);
        try {
            Render2D.text(FontType.BOLD, text, tx, ty, size, color);
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private SmoothAnimation booleanAnimation(BooleanSetting setting) {
        return booleanAnimations.computeIfAbsent(setting, key -> {
            SmoothAnimation animation = new SmoothAnimation();
            animation.set(key.getValue() ? 1.0 : 0.0);
            return animation;
        });
    }

    private SmoothAnimation numberAnimation(NumberSetting setting) {
        return numberAnimations.computeIfAbsent(setting, key -> {
            SmoothAnimation animation = new SmoothAnimation();
            animation.set(numberProgress(key));
            return animation;
        });
    }

    private void renderBooleanGlass(float x, float y, float width, float height, float radius, float alpha, float progress) {
        if (aSupports(alpha)) {
            Render2D.glass(x, y, width, height, radius,
                    ColorUtil.rgba(255, 255, 255, Math.round(72.0F * alpha)),
                    0.64f * alpha,
                    42.0f,
                    ColorUtil.rgba(255, 255, 255, Math.round(150.0F * alpha)),
                    0.16f * alpha,
                    true,
                    0.22f,
                    0.003f,
                    1.0f,
                    0.0f);
            Render2D.glassOutline(x, y, width, height, radius, 0.9f,
                    ColorUtil.rgba(255, 255, 255, Math.round(176.0F * alpha)),
                    1.0f,
                    35.0f,
                    ColorUtil.rgba(168, 168, 174, Math.round(118.0F * alpha)),
                    0.42f * alpha,
                    false,
                    0.45f,
                    0.0015f,
                    1.0f,
                    0.0f);
        }
        Render2D.rect(x, y, width, height, radius,
                ColorUtil.rgba(255, 255, 255, Math.round((18.0F + 26.0F * progress) * alpha)));
    }

    private void renderSliderGlass(float x, float y, float width, float height, float radius, float alpha) {
        if (!aSupports(alpha)) {
            return;
        }
        Render2D.blur(x, y, width, height, radius, 1.0f, 1.0f,
                ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha)));
        Render2D.rect(
                x, y, width, height, radius,
                ColorUtil.rgba(75, 75, 75, Math.round(140.0F * alpha)),
                ColorUtil.rgba(75, 75, 75, Math.round(136.0F * alpha)),
                ColorUtil.rgba(75, 75, 75, Math.round(132.0F * alpha)),
                ColorUtil.rgba(75, 75, 75, Math.round(140.0F * alpha))
        );
        Render2D.glass(x, y, width, height, radius,
                ColorUtil.rgba(255, 255, 255, Math.round(62.0F * alpha)),
                0.64f * alpha,
                42.0f,
                ColorUtil.rgba(255, 255, 255, Math.round(126.0F * alpha)),
                0.16f * alpha,
                true,
                0.22f,
                0.003f,
                1.0f,
                0.0f);
    }

    private static boolean aSupports(float alpha) {
        return alpha > 0.001F;
    }

    private void updatePointerDrag() {
        if (!isOpen() || activeSlider == null && colorDragMode == 0) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            activeSlider = null;
            colorDragMode = 0;
            return;
        }
        if (GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            activeSlider = null;
            colorDragMode = 0;
            return;
        }
        float coordinateScale = Math.max(0.0001F, Render2DCoordinateSpace.guiIndependentScale());
        float mouseX = (float) client.mouseHandler.getScaledXPos(client.getWindow()) / coordinateScale;
        float mouseY = (float) client.mouseHandler.getScaledYPos(client.getWindow()) / coordinateScale;
        if (activeSlider != null) {
            updateNumber(activeSlider, mouseX);
        }
        if (openColor != null) {
            if (colorDragMode == 1) {
                updatePalette(mouseX, mouseY);
            } else if (colorDragMode == 2) {
                updateHue(mouseY);
            } else if (colorDragMode == 3) {
                updateAlpha(mouseX);
            }
        }
    }

    private boolean handleColorPickerClick(float mx, float my) {
        if (picker.get() <= 0.45F) {
            return false;
        }
        if (inside(mx, my, paletteX, paletteY, paletteSize, paletteSize)) {
            colorDragMode = 1;
            updatePalette(mx, my);
            return true;
        }
        if (inside(mx, my, hueX, hueY, hueWidth, hueHeight)) {
            colorDragMode = 2;
            updateHue(my);
            return true;
        }
        if (inside(mx, my, alphaX, alphaY - 2.0F, alphaWidth, alphaHeight + 4.0F)) {
            colorDragMode = 3;
            updateAlpha(mx);
            return true;
        }
        return false;
    }

    private void toggleMode(ModeSetting setting) {
        if (openMode == setting) {
            modeExpanded = !modeExpanded;
            dropdown.run(modeExpanded ? 1.0 : 0.0, 0.16, Easings.CUBIC_OUT);
            return;
        }
        openColor = null;
        picker.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        openMode = setting;
        modeExpanded = true;
        dropdown.set(0.0);
        dropdown.run(1.0, 0.16, Easings.CUBIC_OUT);
    }

    private void toggleColor(ColorSetting setting) {
        if (openColor == setting) {
            openColor = null;
            colorDragMode = 0;
            picker.run(0.0, 0.16, Easings.CUBIC_OUT, true);
            return;
        }
        if (openMode != null) {
            modeExpanded = false;
            dropdown.run(0.0, 0.16, Easings.CUBIC_OUT);
        }
        openColor = setting;
        colorDragMode = 0;
        loadColor(setting.getValue());
        picker.set(0.0);
        picker.run(1.0, 0.16, Easings.CUBIC_OUT, true);
    }

    private void closeExpanded() {
        if (openMode != null) {
            modeExpanded = false;
            dropdown.run(0.0, 0.16, Easings.CUBIC_OUT);
        }
        if (openColor != null) {
            openColor = null;
            colorDragMode = 0;
            picker.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        }
    }

    private void loadColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        alphaValue = color.getAlpha() / 255.0F;
    }

    private void updatePalette(float mouseX, float mouseY) {
        saturation = clamp01((mouseX - paletteX) / paletteSize);
        brightness = 1.0F - clamp01((mouseY - paletteY) / paletteSize);
        syncColor();
    }

    private void updateHue(float mouseY) {
        hue = clamp01((mouseY - hueY) / hueHeight);
        syncColor();
    }

    private void updateAlpha(float mouseX) {
        alphaValue = clamp01((mouseX - alphaX) / alphaWidth);
        syncColor();
    }

    private void syncColor() {
        if (openColor == null) {
            return;
        }
        int rgb = currentRgb();
        openColor.setValue(new Color(red(rgb), green(rgb), blue(rgb), Math.round(alphaValue * 255.0F)));
    }

    private int currentRgb() {
        return Color.HSBtoRGB(hue, saturation, brightness);
    }

    private void updateNumber(NumberSetting setting, float mouseX) {
        float sliderX = activeSlider == setting ? activeSliderTrackX : numberSliderX(setting);
        float sliderWidth = activeSlider == setting ? activeSliderTrackWidth : numberSliderWidth(setting);
        float p = clamp01((mouseX - sliderX) / Math.max(1.0F, sliderWidth));
        double raw = setting.getMin() + (setting.getMax() - setting.getMin()) * p;
        setting.setValue(raw);
    }

    private int modeOptionIndex(ModeSetting setting, float mx, float my, float optionY) {
        float height = modeOptionsHeight(setting) * clamp01(dropdown.get());
        if (!inside(mx, my, controlX(), optionY, CONTROL_WIDTH, height)) {
            return -1;
        }
        float slide = (1.0F - clamp01(dropdown.get())) * 4.0F;
        int index = (int) ((my - optionY - 1.0F + slide) / OPTION);
        return index >= 0 && index < setting.getModes().size() ? index : -1;
    }


    private float settingRowY(Setting<?> target) {
        float rowY = y + HEADER - scroll.get();
        for (Setting<?> setting : visibleSettings()) {
            if (setting == target) {
                return rowY;
            }
            rowY += settingHeight(setting);
        }
        return Float.NaN;
    }

    private boolean colorAnchorVisible() {
        if (openColor == null) {
            return false;
        }
        float rowY = settingRowY(openColor);
        if (!Float.isFinite(rowY)) {
            return false;
        }
        float top = y + HEADER;
        float bottom = top + popupHeight() - HEADER - PADDING;
        return rowY + COLOR_ROW > top + 1.0F && rowY < bottom - 1.0F;
    }

    private void updatePickerBounds() {
        if (openColor == null) {
            return;
        }
        ElementScreen screen = ElementScreen.current();
        float rowY = settingRowY(openColor);
        float right = x + WIDTH + PICKER_GAP;
        float left = x - PICKER_WIDTH - PICKER_GAP;
        float maxX = screen.width() - PICKER_WIDTH - MARGIN;
        if (right <= maxX) {
            pickerPanelX = right;
        } else if (left >= MARGIN) {
            pickerPanelX = left;
        } else {
            pickerPanelX = clamp(right, MARGIN, Math.max(MARGIN, maxX));
        }
        pickerPanelY = clamp(rowY - 4.0F, MARGIN, Math.max(MARGIN, screen.height() - PICKER_HEIGHT - MARGIN));
    }

    private float settingHeight(Setting<?> setting) {
        if (setting instanceof BooleanSetting) {
            return BOOLEAN_ROW;
        }
        if (setting instanceof NumberSetting) {
            return NUMBER_ROW;
        }
        if (setting instanceof ColorSetting) {
            return COLOR_ROW;
        }
        if (setting instanceof ModeSetting modeSetting) {
            float extra = openMode == modeSetting ? modeOptionsHeight(modeSetting) * clamp01(dropdown.get()) : 0.0F;
            return MODE_ROW + extra;
        }
        return 0.0F;
    }

    private float modeOptionsHeight(ModeSetting setting) {
        return setting.getModes().size() * OPTION + 2.0F;
    }

    private List<Setting<?>> visibleSettings() {
        return scoreboard.editorSettings().stream().filter(Setting::isVisible).toList();
    }

    private float contentHeight() {
        float height = 0.0F;
        for (Setting<?> setting : visibleSettings()) {
            height += settingHeight(setting);
        }
        return height;
    }

    private float popupHeight() {
        ElementScreen screen = ElementScreen.current();
        float maxViewport = Math.min(MAX_VIEWPORT, Math.max(80.0F, screen.height() - HEADER - PADDING - MARGIN * 2.0F));
        return HEADER + Math.min(contentHeight(), maxViewport) + PADDING;
    }

    private float maxScroll() {
        return Math.max(0.0F, contentHeight() - (popupHeight() - HEADER - PADDING));
    }

    private void clampScroll() {
        scrollTarget = clamp(scrollTarget, 0.0F, maxScroll());
    }

    private float controlX() {
        return x + WIDTH - PADDING - CONTROL_WIDTH;
    }

    private float numberSliderX(NumberSetting setting) {
        return setting == scoreboard.editorScaleSetting() ? x + WIDTH - PADDING - SIZE_CONTROL_WIDTH : controlX();
    }

    private float numberSliderWidth(NumberSetting setting) {
        return setting == scoreboard.editorScaleSetting() ? SIZE_CONTROL_WIDTH : CONTROL_WIDTH;
    }

    private float numberProgress(NumberSetting setting) {
        double range = setting.getMax() - setting.getMin();
        if (Math.abs(range) <= 0.000001D) {
            return 0.0F;
        }
        return clamp01((setting.getValue() - setting.getMin()) / range);
    }

    private String formatNumber(NumberSetting setting, boolean size) {
        if (size) {
            return Math.round(setting.getFloat() * 100.0F) + "%";
        }
        double value = setting.getValue();
        double step = setting.getStep();
        if (step >= 1.0D) {
            return Long.toString(Math.round(value));
        }
        if (step >= 0.1D) {
            return String.format(java.util.Locale.ROOT, "%.1f", value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private void place(boolean resolveSide) {
        ElementScreen screen = ElementScreen.current();
        float right = scoreboard.editorX() + scoreboard.editorWidth() + GAP;
        float left = scoreboard.editorX() - WIDTH - GAP;
        Side next = side;
        float px = next == Side.RIGHT ? right : left;
        if (resolveSide && (px < MARGIN || px + WIDTH > screen.width() - MARGIN)) {
            next = next == Side.RIGHT ? Side.LEFT : Side.RIGHT;
            px = next == Side.RIGHT ? right : left;
        }
        side = next;
        x = clamp(px, MARGIN, screen.width() - WIDTH - MARGIN);
        y = clamp(scoreboard.editorY() + Math.min(4.0F, scoreboard.editorHeight() * 0.12F), MARGIN, screen.height() - popupHeight() - MARGIN);
    }

    private static int alpha(int color, float a) {
        int value = Math.round(((color >>> 24) & 0xFF) * clamp01(a));
        return (color & 0x00FFFFFF) | (value << 24);
    }

    private static int red(int color) {
        return (color >>> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >>> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(double value) {
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private enum Side {
        LEFT, RIGHT
    }
}
