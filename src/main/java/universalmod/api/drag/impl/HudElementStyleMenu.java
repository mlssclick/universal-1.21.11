package universalmod.api.drag.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import universalmod.api.drag.core.ElementScreen;
import universalmod.api.module.impl.render.Hud;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.Render2DCoordinateSpace;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.theme.HudStyleOverrides;
import universalmod.utils.theme.ThemeColors;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public final class HudElementStyleMenu {
    private static final HudElementStyleMenu INSTANCE = new HudElementStyleMenu();
    private static final float PANEL_WIDTH = 148.0F;
    private static final float HEADER_HEIGHT = 17.0F;
    private static final float ROW_HEIGHT = 15.0F;
    private static final float OPTION_HEIGHT = 13.0F;
    private static final float SIZE_ROW_HEIGHT = 22.0F;
    private static final float SIZE_SLIDER_HEIGHT = 3.0F;
    private static final float PADDING = 6.0F;
    private static final float VALUE_BOX_WIDTH = 66.0F;
    private static final float ELEMENT_GAP = 8.0F;
    private static final float SCREEN_MARGIN = 4.0F;
    private static final float COLOR_ROW_HEIGHT = 18.0F;
    private static final float COLOR_PICKER_WIDTH = 84.0F;
    private static final float COLOR_PICKER_HEIGHT = 86.0F;
    private static final float COLOR_PICKER_GAP = 6.0F;
    private static final int[] HUE_COLORS = {
            Color.HSBtoRGB(0.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(1.0F / 6.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(2.0F / 6.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(3.0F / 6.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(4.0F / 6.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(5.0F / 6.0F, 1.0F, 1.0F),
            Color.HSBtoRGB(1.0F, 1.0F, 1.0F)
    };

    private final SmoothAnimation appearAnimation = new SmoothAnimation();
    private final SmoothAnimation viewDropdownAnimation = new SmoothAnimation();
    private final SmoothAnimation sizeSliderAnimation = new SmoothAnimation();
    private final SmoothAnimation colorPickerAnimation = new SmoothAnimation();

    private HudPanel anchorPanel;
    private String elementId;
    private String elementTitle;
    private float x;
    private float y;
    private float anchorX;
    private float anchorY;
    private float anchorWidth;
    private float anchorHeight;
    private float enterDirection = 1.0F;
    private Side requestedSide = Side.RIGHT;
    private Side placedSide = Side.RIGHT;
    private Dropdown openDropdown = Dropdown.NONE;
    private boolean sizeDragging;
    private float sizeDragStartMouseX;
    private float sizeDragStartPercent;
    private float sizeDragTravelWidth = VALUE_BOX_WIDTH;
    private ColorSetting openColor;
    private int colorDragMode;
    private float hue;
    private float saturation;
    private float brightness;
    private float colorAlpha = 1.0F;
    private float pickerX;
    private float pickerY;
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

    private HudElementStyleMenu() {
        appearAnimation.set(0.0);
        viewDropdownAnimation.set(0.0);
        sizeSliderAnimation.set(0.5);
        colorPickerAnimation.set(0.0);
    }

    public static HudElementStyleMenu getInstance() {
        return INSTANCE;
    }

    public void open(HudPanel panel, float mouseX, float mouseY) {
        if (panel == null) {
            return;
        }

        Side side = mouseX < panel.x() + panel.width() * 0.5F ? Side.LEFT : Side.RIGHT;
        String nextElementId = panel.elementId();

        if (isOpen() && nextElementId.equalsIgnoreCase(elementId) && requestedSide == side) {
            return;
        }

        boolean sameElement = isOpen() && nextElementId.equalsIgnoreCase(elementId);
        this.anchorPanel = panel;
        this.elementId = nextElementId;
        this.elementTitle = panel.elementName();
        this.anchorX = panel.x();
        this.anchorY = panel.y();
        this.anchorWidth = panel.width();
        this.anchorHeight = panel.height();
        this.requestedSide = side;
        this.openDropdown = Dropdown.NONE;
        this.sizeDragging = false;
        this.openColor = null;
        this.colorDragMode = 0;

        appearAnimation.set(0.0);
        viewDropdownAnimation.set(0.0);
        colorPickerAnimation.set(0.0);
        sizeSliderAnimation.set(sizeProgress());
        placedSide = requestedSide;
        placeNextToElement(true);

        appearAnimation.run(1.0, sameElement ? 0.18 : 0.20, Easings.BAKEK);
    }

    public void close() {
        finishSizeDrag();
        anchorPanel = null;
        elementId = null;
        elementTitle = null;
        openDropdown = Dropdown.NONE;
        appearAnimation.set(0.0);
        viewDropdownAnimation.set(0.0);
        colorPickerAnimation.set(0.0);
        openColor = null;
        colorDragMode = 0;
    }

    public boolean isOpen() {
        return elementId != null && !elementId.isBlank();
    }

    public void updateBeforeHudRender() {
        updateSizeDragFromMouse();
        updateColorDragFromMouse();
    }

    public void render(GuiGraphics graphics) {
        if (!isOpen()) {
            return;
        }
        renderScoped(graphics);
    }

    private void renderScoped(GuiGraphics graphics) {
        updateAnimations();
        placeNextToElement();

        float appear = clamp01(appearAnimation.get());
        if (appear <= 0.001F) {
            return;
        }

        Layout layout = layout();
        float renderX = layout.x - enterDirection * (1.0F - appear) * 7.0F;
        float renderY = layout.y + (1.0F - appear) * 2.0F;
        Layout animatedLayout = new Layout(renderX, renderY, layout.height);
        int panelColor = alphaColor(ColorUtil.rgba(12, 14, 18, 232), appear);

        Hud.renderHudBackground(animatedLayout.x, animatedLayout.y, PANEL_WIDTH, animatedLayout.height,
                5.0F, 5.0F, 0.55F, panelColor);
        Render2D.text(FontType.BOLD, elementTitle, animatedLayout.x + PADDING,
                animatedLayout.y + 4.3F, 7.6F, alphaColor(0xFFF3F6FA, appear));

        float rowY = animatedLayout.y + HEADER_HEIGHT;
        if (isMusicPlayerHud()) {
            renderRow(animatedLayout, rowY, "View", HudStyleOverrides.getInstance().getMusicPlayerView(elementId),
                    openDropdown == Dropdown.VIEW, appear);
            rowY += ROW_HEIGHT;

            float viewDrop = dropdownProgress(Dropdown.VIEW);
            if (viewDrop > 0.001F) {
                String[] options = musicPlayerViewOptions();
                renderOptions(graphics, animatedLayout, rowY, Dropdown.VIEW, options, viewDrop, appear);
                rowY += optionAreaHeight(options.length) * viewDrop;
            }
        }

        if (hasSizeRow()) {
            renderSizeRow(animatedLayout, rowY, appear);
            rowY += SIZE_ROW_HEIGHT;
        }

        if (isKeystrokesHud()) {
            Hud hud = Hud.getInstance();
            if (hud != null) {
                renderColorRow(animatedLayout, rowY, "Color", hud.keystrokesNormalColorSetting(), appear);
                rowY += COLOR_ROW_HEIGHT;
                renderColorRow(animatedLayout, rowY, "Pressed Color", hud.keystrokesPressedColorSetting(), appear);
                rowY += COLOR_ROW_HEIGHT;
                renderColorRow(animatedLayout, rowY, "Letter Color", hud.keystrokesLetterColorSetting(), appear);
            }
        }

        if (openColor != null && colorPickerAnimation.get() > 0.001F) {
            renderColorPicker(graphics, animatedLayout, clamp01(colorPickerAnimation.get()), appear);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (!isOpen() || event == null) {
            return false;
        }

        updateAnimations();
        placeNextToElement();
        float mx = (float) event.x();
        float my = (float) event.y();
        Layout layout = layout();

        if (openColor != null && colorPickerAnimation.get() > 0.20F) {
            updateColorPickerBounds(layout);
            updateColorPickerControls();
            if (inside(mx, my, pickerX, pickerY, COLOR_PICKER_WIDTH, COLOR_PICKER_HEIGHT)) {
                if (inside(mx, my, paletteX, paletteY, paletteSize, paletteSize)) {
                    colorDragMode = 1;
                    updatePalette(mx, my);
                } else if (inside(mx, my, hueX, hueY, hueWidth, hueHeight)) {
                    colorDragMode = 2;
                    updateHue(my);
                } else if (inside(mx, my, alphaX, alphaY - 2.0F, alphaWidth, alphaHeight + 4.0F)) {
                    colorDragMode = 3;
                    updateAlpha(mx);
                }
                return true;
            }
        }

        if (!inside(mx, my, layout.x, layout.y, PANEL_WIDTH, layout.height)) {
            if (event.button() == 1) {
                return false;
            }
            close();
            return false;
        }
        if (event.button() != 0) {
            return true;
        }

        float rowY = layout.y + HEADER_HEIGHT;

        if (isMusicPlayerHud()) {
            if (insideValue(layout, mx, my, rowY)) {
                toggle(Dropdown.VIEW);
                return true;
            }
            rowY += ROW_HEIGHT;

            float viewDrop = dropdownProgress(Dropdown.VIEW);
            if (viewDrop > 0.55F) {
                String[] options = musicPlayerViewOptions();
                int index = optionIndex(layout, mx, my, rowY, options.length, viewDrop);
                if (index >= 0) {
                    HudStyleOverrides.getInstance().setMusicPlayerView(elementId, options[index]);
                    setDropdown(Dropdown.NONE);
                    return true;
                }
            }
            rowY += optionAreaHeight(musicPlayerViewOptions().length) * viewDrop;
        }

        if (hasSizeRow()) {
            if (insideSizeSlider(layout, mx, my, rowY)) {
                setDropdown(Dropdown.NONE);
                closeColorPicker();
                sizeDragging = true;
                updateSizeFromMouse(mx, layout);
                beginSizeDrag(mx);
                return true;
            }
            rowY += SIZE_ROW_HEIGHT;
        }

        if (isKeystrokesHud()) {
            Hud hud = Hud.getInstance();
            if (hud != null) {
                if (insideColorSwatch(layout, mx, my, rowY)) {
                    toggleColorPicker(hud.keystrokesNormalColorSetting());
                    return true;
                }
                rowY += COLOR_ROW_HEIGHT;
                if (insideColorSwatch(layout, mx, my, rowY)) {
                    toggleColorPicker(hud.keystrokesPressedColorSetting());
                    return true;
                }
                rowY += COLOR_ROW_HEIGHT;
                if (insideColorSwatch(layout, mx, my, rowY)) {
                    toggleColorPicker(hud.keystrokesLetterColorSetting());
                    return true;
                }
            }
        }

        setDropdown(Dropdown.NONE);
        closeColorPicker();
        return true;
    }

    private void renderColorRow(Layout layout, float y, String label, ColorSetting setting, float alpha) {
        if (setting == null || alpha <= 0.001F) {
            return;
        }
        Render2D.text(FontType.BOLD, label, layout.x + PADDING, y + 5.0F, 6.7F,
                alphaColor(0xFFDDE3EA, alpha));
        Color color = setting.getValue();
        float size = 12.0F;
        float sx = layout.x + PANEL_WIDTH - PADDING - size - 1.0F;
        float sy = y + 3.0F;
        float outerSize = size + 4.0F;
        Render2D.rect(sx - 2.0F, sy - 2.0F, outerSize, outerSize, outerSize * 0.5F,
                ColorUtil.rgba(255, 255, 255, Math.round(30.0F * alpha)));
        Render2D.outline(sx - 2.0F, sy - 2.0F, outerSize, outerSize, outerSize * 0.5F, 0.75F,
                ColorUtil.rgba(255, 255, 255, Math.round(42.0F * alpha)));
        Render2D.rect(sx, sy, size, size, size * 0.5F,
                ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * alpha)));
    }

    private boolean insideColorSwatch(Layout layout, float mouseX, float mouseY, float rowY) {
        float size = 12.0F;
        float sx = layout.x + PANEL_WIDTH - PADDING - size - 1.0F;
        return inside(mouseX, mouseY, sx - 3.0F, rowY + 1.0F, size + 6.0F, size + 6.0F);
    }

    private void toggleColorPicker(ColorSetting setting) {
        setDropdown(Dropdown.NONE);
        if (openColor == setting) {
            closeColorPicker();
            return;
        }
        openColor = setting;
        colorDragMode = 0;
        loadColor(setting.getValue());
        colorPickerAnimation.set(0.0);
        colorPickerAnimation.run(1.0, 0.16, Easings.CUBIC_OUT, true);
    }

    private void closeColorPicker() {
        colorDragMode = 0;
        if (openColor != null) {
            colorPickerAnimation.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        }
        if (colorPickerAnimation.get() <= 0.001F && !colorPickerAnimation.isAlive()) {
            openColor = null;
        }
    }

    private void renderColorPicker(GuiGraphics graphics, Layout layout, float progress, float parentAlpha) {
        if (openColor == null) {
            return;
        }
        if (colorDragMode == 0) {
            loadColor(openColor.getValue());
        }
        updateColorPickerBounds(layout);
        updateColorPickerControls();
        float alpha = parentAlpha * progress;
        float slide = (1.0F - progress) * 4.0F;
        float drawY = pickerY + slide;
        float offset = drawY - pickerY;
        paletteY += offset;
        hueY += offset;
        alphaY += offset;

        Hud.renderHudBackground(pickerX, drawY, COLOR_PICKER_WIDTH, COLOR_PICKER_HEIGHT, 4.0F, 5.0F, 0.55F,
                ColorUtil.rgba(18, 20, 25, Math.round(230.0F * alpha)));
        drawColorPalette(alpha);
        drawHue(alpha);
        drawAlpha(alpha);
    }

    private void updateColorPickerBounds(Layout layout) {
        ElementScreen screen = ElementScreen.current();
        float right = layout.x + PANEL_WIDTH + COLOR_PICKER_GAP;
        float left = layout.x - COLOR_PICKER_WIDTH - COLOR_PICKER_GAP;
        float maxX = screen.width() - COLOR_PICKER_WIDTH - SCREEN_MARGIN;
        if (right <= maxX) {
            pickerX = right;
        } else if (left >= SCREEN_MARGIN) {
            pickerX = left;
        } else {
            pickerX = clamp(right, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, maxX));
        }
        pickerY = clamp(layout.y + HEADER_HEIGHT + 6.0F, SCREEN_MARGIN,
                Math.max(SCREEN_MARGIN, screen.height() - COLOR_PICKER_HEIGHT - SCREEN_MARGIN));
    }

    private void updateColorPickerControls() {
        paletteX = pickerX + 7.0F;
        paletteY = pickerY + 7.0F;
        paletteSize = 58.0F;
        hueX = paletteX + paletteSize + 5.0F;
        hueY = paletteY;
        hueWidth = 7.0F;
        hueHeight = paletteSize;
        alphaX = paletteX;
        alphaY = paletteY + paletteSize + 7.0F;
        alphaWidth = paletteSize + 12.0F;
        alphaHeight = 7.0F;
    }

    private void drawColorPalette(float alpha) {
        int hueColor = Color.HSBtoRGB(hue, 1.0F, 1.0F);
        int topLeft = ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha));
        int topRight = ColorUtil.rgba(red(hueColor), green(hueColor), blue(hueColor), Math.round(255.0F * alpha));
        int bottom = ColorUtil.rgba(0, 0, 0, Math.round(255.0F * alpha));
        Render2D.rect(paletteX, paletteY, paletteSize, paletteSize, 4.0F, topLeft, topRight, bottom, bottom);
        float selectorX = paletteX + saturation * paletteSize;
        float selectorY = paletteY + (1.0F - brightness) * paletteSize;
        Render2D.outline(selectorX - 2.5F, selectorY - 2.5F, 5.0F, 5.0F, 1.5F, 1.0F,
                ColorUtil.rgba(255, 255, 255, Math.round(245.0F * alpha)));
        Render2D.rect(selectorX - 1.0F, selectorY - 1.0F, 2.0F, 2.0F, 0.5F,
                ColorUtil.rgba(20, 24, 32, Math.round(190.0F * alpha)));
    }

    private void drawHue(float alpha) {
        float segmentHeight = hueHeight / (HUE_COLORS.length - 1);
        for (int i = 0; i < HUE_COLORS.length - 1; i++) {
            int top = HUE_COLORS[i];
            int bottom = HUE_COLORS[i + 1];
            float sy = hueY + i * segmentHeight;
            float radius = 2.0F;
            Render2D.rect(hueX, sy, hueWidth, segmentHeight + 0.5F,
                    i == 0 ? radius : 0.0F,
                    i == 0 ? radius : 0.0F,
                    i == HUE_COLORS.length - 2 ? radius : 0.0F,
                    i == HUE_COLORS.length - 2 ? radius : 0.0F,
                    ColorUtil.rgba(red(top), green(top), blue(top), Math.round(255.0F * alpha)),
                    ColorUtil.rgba(red(top), green(top), blue(top), Math.round(255.0F * alpha)),
                    ColorUtil.rgba(red(bottom), green(bottom), blue(bottom), Math.round(255.0F * alpha)),
                    ColorUtil.rgba(red(bottom), green(bottom), blue(bottom), Math.round(255.0F * alpha)));
        }
        float selectorY = hueY + hue * hueHeight;
        Render2D.rect(hueX - 1.0F, selectorY - 1.0F, hueWidth + 2.0F, 2.0F,
                ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha)));
    }

    private void drawAlpha(float alpha) {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        int transparent = ColorUtil.rgba(red(rgb), green(rgb), blue(rgb), 0);
        int opaque = ColorUtil.rgba(red(rgb), green(rgb), blue(rgb), Math.round(255.0F * alpha));
        Render2D.rect(alphaX, alphaY, alphaWidth, alphaHeight, 2.5F,
                transparent, opaque, opaque, transparent);
        float selectorX = alphaX + alphaWidth * colorAlpha;
        Render2D.rect(selectorX - 1.0F, alphaY - 1.0F, 2.0F, alphaHeight + 2.0F, 1.0F,
                ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha)));
    }

    private void updateColorDragFromMouse() {
        if (!isOpen() || openColor == null || colorDragMode == 0) {
            if (openColor != null && colorPickerAnimation.get() <= 0.001F && !colorPickerAnimation.isAlive()) {
                openColor = null;
            }
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            colorDragMode = 0;
            return;
        }
        if (GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            colorDragMode = 0;
            return;
        }
        float coordinateScale = Math.max(0.0001F, Render2DCoordinateSpace.guiIndependentScale());
        float mouseX = (float) client.mouseHandler.getScaledXPos(client.getWindow()) / coordinateScale;
        float mouseY = (float) client.mouseHandler.getScaledYPos(client.getWindow()) / coordinateScale;
        if (colorDragMode == 1) {
            updatePalette(mouseX, mouseY);
        } else if (colorDragMode == 2) {
            updateHue(mouseY);
        } else if (colorDragMode == 3) {
            updateAlpha(mouseX);
        }
    }

    private void loadColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        colorAlpha = color.getAlpha() / 255.0F;
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
        colorAlpha = clamp01((mouseX - alphaX) / alphaWidth);
        syncColor();
    }

    private void syncColor() {
        if (openColor == null) {
            return;
        }
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        openColor.setValue(new Color(red(rgb), green(rgb), blue(rgb), Math.round(colorAlpha * 255.0F)));
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

    private void renderSizeRow(Layout layout, float y, float alpha) {
        if (alpha <= 0.001F || !hasSizeRow()) {
            return;
        }

        float sliderX = sizeSliderX(layout);
        float sliderY = y + 13.0F;
        float sliderWidth = VALUE_BOX_WIDTH;
        float targetProgress = sizeProgress();
        sizeSliderAnimation.run(targetProgress, sizeDragging ? 0.08 : 0.18, Easings.CUBIC_OUT, true);
        sizeSliderAnimation.update();
        float progress = clamp01(sizeSliderAnimation.get());

        int percent = Math.round(HudStyleOverrides.getInstance().getSizePercent(elementId));
        String valueText = percent + "%";
        Render2D.text(FontType.BOLD, "Size", layout.x + PADDING, y + 6.0F, 6.8F,
                alphaColor(0xFFDDE3EA, alpha));

        float valueWidth = Render2D.textWidth(FontType.BOLD, valueText, 6.2F);
        Render2D.text(FontType.BOLD, valueText, sliderX + sliderWidth - valueWidth, y + 3.0F, 6.2F,
                alphaColor(0xFFF2F5F8, alpha));

        Render2D.rect(sliderX, sliderY, sliderWidth, SIZE_SLIDER_HEIGHT, 1.0F,
                ColorUtil.rgba(255, 255, 255, Math.round(30.0F * alpha)));
        Render2D.rect(sliderX, sliderY, sliderWidth * progress, SIZE_SLIDER_HEIGHT, 1.0F,
                ThemeColors.clickGuiSliderFillColor(alpha));

        float knobSize = 7.0F;
        float knobX = clamp(sliderX + sliderWidth * progress - knobSize * 0.5F,
                sliderX, sliderX + sliderWidth - knobSize);
        Render2D.rect(knobX, sliderY - 2.0F, knobSize, knobSize, 2.5F,
                ThemeColors.clickGuiSliderKnobColor(alpha));
    }

    private boolean insideSizeSlider(Layout layout, float mouseX, float mouseY, float rowY) {
        float sliderX = sizeSliderX(layout);
        return inside(mouseX, mouseY, sliderX - 3.0F, rowY + 6.0F,
                VALUE_BOX_WIDTH + 6.0F, 15.0F);
    }

    private float sizeSliderX(Layout layout) {
        return layout.x + PANEL_WIDTH - PADDING - VALUE_BOX_WIDTH;
    }

    private float sizeProgress() {
        if (!isOpen() || !hasSizeRow()) {
            return 0.5F;
        }
        float value = HudStyleOverrides.getInstance().getSizePercent(elementId);
        return clamp01((value - HudStyleOverrides.SIZE_MIN_PERCENT)
                / (HudStyleOverrides.SIZE_MAX_PERCENT - HudStyleOverrides.SIZE_MIN_PERCENT));
    }

    private void updateSizeFromMouse(float mouseX, Layout layout) {
        if (!isOpen() || !hasSizeRow()) {
            return;
        }
        float progress = clamp01((mouseX - sizeSliderX(layout)) / VALUE_BOX_WIDTH);
        float value = HudStyleOverrides.SIZE_MIN_PERCENT
                + progress * (HudStyleOverrides.SIZE_MAX_PERCENT - HudStyleOverrides.SIZE_MIN_PERCENT);
        HudStyleOverrides.getInstance().previewSizePercent(elementId, Math.round(value));
    }

    private void beginSizeDrag(float mouseX) {
        sizeDragStartMouseX = mouseX;
        sizeDragStartPercent = HudStyleOverrides.getInstance().getSizePercent(elementId);
        float baseWidth = anchorWidth;
        if (anchorPanel != null) {
            float scale = Math.max(0.0001F, anchorPanel.hudScale());
            baseWidth = anchorPanel.width() / scale;
        }
        sizeDragTravelWidth = Math.max(1.0F, VALUE_BOX_WIDTH + (placedSide == Side.RIGHT ? baseWidth : 0.0F));
    }

    private void updateSizeDragFromMouse() {
        if (!sizeDragging || !isOpen() || !hasSizeRow()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            finishSizeDrag();
            return;
        }
        if (GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            finishSizeDrag();
            return;
        }

        float coordinateScale = Math.max(0.0001F, Render2DCoordinateSpace.guiIndependentScale());
        float mouseX = (float) client.mouseHandler.getScaledXPos(client.getWindow()) / coordinateScale;
        float range = HudStyleOverrides.SIZE_MAX_PERCENT - HudStyleOverrides.SIZE_MIN_PERCENT;
        float value = sizeDragStartPercent + (mouseX - sizeDragStartMouseX) / sizeDragTravelWidth * range;
        HudStyleOverrides.getInstance().previewSizePercent(elementId, Math.round(value));
    }

    private void finishSizeDrag() {
        if (!sizeDragging) {
            return;
        }
        sizeDragging = false;
        if (elementId != null && !elementId.isBlank()) {
            HudStyleOverrides.getInstance().commitSizePercent(elementId);
        }
    }

    private void renderRow(Layout layout, float y, String label, String value, boolean expanded, float alpha) {
        if (alpha <= 0.001F) {
            return;
        }
        int labelColor = alphaColor(0xFFDDE3EA, alpha);
        Render2D.text(FontType.BOLD, label, layout.x + PADDING, y + 3.6F, 6.8F, labelColor);

        float boxX = layout.x + PANEL_WIDTH - PADDING - VALUE_BOX_WIDTH;
        int boxAlpha = Math.round((expanded ? 42.0F : 27.0F) * alpha);
        Render2D.rect(boxX, y + 1.0F, VALUE_BOX_WIDTH, ROW_HEIGHT - 2.0F, 3.5F,
                ColorUtil.rgba(255, 255, 255, boxAlpha));
        Render2D.text(FontType.BOLD, value, boxX + 4.0F, y + 3.4F, 6.4F,
                alphaColor(expanded ? 0xFFFFFFFF : 0xFFCDD3DA, alpha));
    }

    private void renderOptions(GuiGraphics graphics, Layout layout, float y, Dropdown dropdown,
                               String[] options, float progress, float parentAlpha) {
        float p = clamp01(progress);
        float alpha = parentAlpha * p;
        float fullHeight = optionAreaHeight(options.length) - 2.0F;
        float visibleHeight = fullHeight * p;
        if (visibleHeight <= 0.2F) {
            return;
        }

        float boxX = layout.x + PANEL_WIDTH - PADDING - VALUE_BOX_WIDTH;
        float boxY = y + 1.0F;
        Hud.renderHudBackground(boxX, boxY, VALUE_BOX_WIDTH, visibleHeight, 3.5F, 4.0F, 0.55F,
                ColorUtil.rgba(20, 23, 28, Math.round(220.0F * alpha)));

        Render2D.pushScissor(graphics, boxX, boxY, VALUE_BOX_WIDTH, visibleHeight);
        try {
            float slide = (1.0F - p) * 4.0F;
            for (int i = 0; i < options.length; i++) {
                float optionY = y + 2.0F + i * OPTION_HEIGHT - slide;
                boolean selected = selectedValue(dropdown).equalsIgnoreCase(options[i]);
                if (selected) {
                    Render2D.rect(boxX + 2.0F, optionY + 1.0F, VALUE_BOX_WIDTH - 4.0F, OPTION_HEIGHT - 2.0F,
                            2.5F, ColorUtil.rgba(255, 255, 255, Math.round(22.0F * alpha)));
                }
                Render2D.text(FontType.BOLD, options[i], boxX + 4.0F, optionY + 2.1F, 6.2F,
                        alphaColor(selected ? 0xFFFFFFFF : 0xFFBFC7CF, alpha));
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private void updateAnimations() {
        appearAnimation.update();
        viewDropdownAnimation.update();
        colorPickerAnimation.update();
    }

    private void toggle(Dropdown dropdown) {
        closeColorPicker();
        setDropdown(openDropdown == dropdown ? Dropdown.NONE : dropdown);
    }

    private void setDropdown(Dropdown dropdown) {
        openDropdown = dropdown == null ? Dropdown.NONE : dropdown;
        viewDropdownAnimation.run(openDropdown == Dropdown.VIEW ? 1.0 : 0.0, 0.16, Easings.CUBIC_OUT, true);
    }

    private float dropdownProgress(Dropdown dropdown) {
        return switch (dropdown) {
            case VIEW -> clamp01(viewDropdownAnimation.get());
            default -> 0.0F;
        };
    }

    private static String[] musicPlayerViewOptions() {
        return new String[]{HudStyleOverrides.MUSIC_PLAYER_VIEW_1, HudStyleOverrides.MUSIC_PLAYER_VIEW_2};
    }


    private boolean hasSizeRow() {
        return true;
    }

    private boolean isKeystrokesHud() {
        return "hud.keystrokes".equalsIgnoreCase(elementId) || "keystrokes".equalsIgnoreCase(elementId);
    }

    private boolean isMusicPlayerHud() {
        return "hud.music_player".equalsIgnoreCase(elementId) || "music_player".equalsIgnoreCase(elementId);
    }

    private String selectedValue(Dropdown dropdown) {
        HudStyleOverrides overrides = HudStyleOverrides.getInstance();
        return switch (dropdown) {
            case VIEW -> overrides.getMusicPlayerView(elementId);
            default -> "";
        };
    }

    private int optionIndex(Layout layout, float mouseX, float mouseY, float y, int count, float progress) {
        float boxX = layout.x + PANEL_WIDTH - PADDING - VALUE_BOX_WIDTH;
        float visibleHeight = (optionAreaHeight(count) - 2.0F) * clamp01(progress);
        if (!inside(mouseX, mouseY, boxX, y + 1.0F, VALUE_BOX_WIDTH, visibleHeight)) {
            return -1;
        }
        float slide = (1.0F - clamp01(progress)) * 4.0F;
        int index = (int) ((mouseY - (y + 2.0F) + slide) / OPTION_HEIGHT);
        return index >= 0 && index < count ? index : -1;
    }

    private boolean insideValue(Layout layout, float mouseX, float mouseY, float y) {
        float boxX = layout.x + PANEL_WIDTH - PADDING - VALUE_BOX_WIDTH;
        return inside(mouseX, mouseY, boxX, y + 1.0F, VALUE_BOX_WIDTH, ROW_HEIGHT - 2.0F);
    }

    private float optionAreaHeight(int count) {
        return count * OPTION_HEIGHT + 4.0F;
    }

    private void placeNextToElement() {
        placeNextToElement(false);
    }

    private void placeNextToElement(boolean resolveSide) {
        if (!isOpen()) {
            return;
        }
        if (anchorPanel != null) {
            anchorX = anchorPanel.x();
            anchorY = anchorPanel.y();
            anchorWidth = anchorPanel.width();
            anchorHeight = anchorPanel.height();
        }
        ElementScreen screen = ElementScreen.current();
        float height = layoutHeight();
        float rightX = anchorX + anchorWidth + ELEMENT_GAP;
        float leftX = anchorX - PANEL_WIDTH - ELEMENT_GAP;

        Side side = resolveSide ? requestedSide : placedSide;
        float candidateX = side == Side.RIGHT ? rightX : leftX;
        boolean fitsSide = candidateX >= SCREEN_MARGIN
                && candidateX + PANEL_WIDTH <= screen.width() - SCREEN_MARGIN;

        if (resolveSide && !fitsSide) {
            Side opposite = side == Side.RIGHT ? Side.LEFT : Side.RIGHT;
            float oppositeX = opposite == Side.RIGHT ? rightX : leftX;
            boolean fitsOpposite = oppositeX >= SCREEN_MARGIN
                    && oppositeX + PANEL_WIDTH <= screen.width() - SCREEN_MARGIN;
            if (fitsOpposite) {
                side = opposite;
                candidateX = oppositeX;
                fitsSide = true;
            }
        }
        if (!fitsSide) {
            candidateX = clamp(candidateX, SCREEN_MARGIN, screen.width() - PANEL_WIDTH - SCREEN_MARGIN);
        }

        x = candidateX;
        placedSide = side;
        enterDirection = placedSide == Side.RIGHT ? 1.0F : -1.0F;

        float idealY = anchorY + Math.min(4.0F, Math.max(0.0F, anchorHeight * 0.12F));
        y = clamp(idealY, SCREEN_MARGIN, screen.height() - height - SCREEN_MARGIN);
    }

    private Layout layout() {
        return new Layout(x, y, layoutHeight());
    }

    private float layoutHeight() {
        float height = HEADER_HEIGHT + PADDING;
        if (isMusicPlayerHud()) {
            height += ROW_HEIGHT;
            height += optionAreaHeight(musicPlayerViewOptions().length) * dropdownProgress(Dropdown.VIEW);
        }
        if (hasSizeRow()) {
            height += SIZE_ROW_HEIGHT;
        }
        if (isKeystrokesHud()) {
            height += COLOR_ROW_HEIGHT * 3.0F;
        }

        return Math.max(HEADER_HEIGHT + PADDING, height);
    }

    private static int alphaColor(int color, float alphaMultiplier) {
        int alpha = (color >>> 24) & 0xFF;
        int scaled = Math.round(alpha * clamp01(alphaMultiplier));
        return (color & 0x00FFFFFF) | (scaled << 24);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private enum Side {
        LEFT, RIGHT
    }

    private enum Dropdown {
        NONE, VIEW
    }

    private record Layout(float x, float y, float height) {
    }
}
