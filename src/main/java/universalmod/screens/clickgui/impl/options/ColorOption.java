package universalmod.screens.clickgui.impl.options;

import universalmod.utils.theme.ThemeRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;

import java.awt.Color;

public class ColorOption extends ClickGuiOption {
    private static final float PICKER_SCALE = 1.30f;
    private static final float POPUP_WIDTH = 87.0f * PICKER_SCALE;
    private static final float POPUP_HEIGHT = 76.0f * PICKER_SCALE;
    private static final float POPUP_PADDING = 7.0f * PICKER_SCALE;
    private static final float PALETTE_SIZE = 62.0f * PICKER_SCALE;
    private static final float HUE_GAP = 5.0f * PICKER_SCALE;
    private static final float HUE_WIDTH = 7.0f * PICKER_SCALE;

    private static final int[] HUE_COLORS = {
            Color.HSBtoRGB(0.0f, 1.0f, 1.0f),
            Color.HSBtoRGB(1.0f / 6.0f, 1.0f, 1.0f),
            Color.HSBtoRGB(2.0f / 6.0f, 1.0f, 1.0f),
            Color.HSBtoRGB(3.0f / 6.0f, 1.0f, 1.0f),
            Color.HSBtoRGB(4.0f / 6.0f, 1.0f, 1.0f),
            Color.HSBtoRGB(5.0f / 6.0f, 1.0f, 1.0f),
            Color.HSBtoRGB(1.0f, 1.0f, 1.0f)
    };

    private final SmoothAnimation openAnimation = new SmoothAnimation();
    private final ColorSetting setting;
    private float hue;
    private float saturation;
    private float brightness;
    private float alphaValue;
    private boolean hueInitialized;
    private boolean open;
    private int dragMode;
    private float swatchX;
    private float swatchY;
    private float swatchSize;
    private float popupX;
    private float popupY;
    private float popupWidth;
    private float popupHeight;
    private float paletteX;
    private float paletteY;
    private float paletteSize;
    private float hueX;
    private float hueY;
    private float hueWidth;
    private float hueHeight;

    public ColorOption(ColorSetting setting) {
        super(setting.getName());
        this.setting = setting;
        applyColor(setting.getValue());
        openAnimation.set(0.0);
    }

    @Override
    protected void renderControl(GuiGraphics graphics, float x, float y, float width, float height, float scale, float alpha) {
        if (dragMode == 0) {
            applyColor(setting.getValue());
        }

        swatchSize = 15.6f * scale;
        swatchX = x + width - swatchSize;
        swatchY = y + (height - swatchSize) * 0.5f;
        int current = currentRgb();

        // Color settings use the same row layout as the rest of the settings:
        // text on the left and a simple circular color button on the right.
        Render2D.rect(swatchX, swatchY, swatchSize, swatchSize, swatchSize * 0.5f,
                color(red(current), green(current), blue(current), Math.round(alphaValue * 255.0f), alpha));
        Render2D.outline(swatchX, swatchY, swatchSize, swatchSize, swatchSize * 0.5f, 0.65f * scale,
                color(235, 235, 246, 150, alpha));

        float labelSize = 9.9f * scale;
        float labelY = y + (height - labelSize) * 0.5f - 0.90f * scale;
        float available = Math.max(0.0f, swatchX - 8.0f * scale - x);
        renderPlainLabel(x, labelY, labelSize, available);
    }

    @Override
    public void renderOverlay(GuiGraphics graphics) {
        openAnimation.update();
        float progress = openAnimation.get();
        if (progress <= 0.01f || !popupAnchorVisible()) {
            return;
        }

        float popupAlpha = alpha * progress;
        popupWidth = POPUP_WIDTH * scale;
        popupHeight = POPUP_HEIGHT * scale;
        updatePopupBounds();
        paletteX = popupX + POPUP_PADDING * scale;
        paletteY = popupY + POPUP_PADDING * scale;
        paletteSize = PALETTE_SIZE * scale;
        hueX = paletteX + paletteSize + HUE_GAP * scale;
        hueY = paletteY;
        hueWidth = HUE_WIDTH * scale;
        hueHeight = paletteSize;

        float radius = 9.0f * scale;
        float defaultPopupAlpha = defaultThemeAlpha(popupAlpha);
        if (defaultPopupAlpha > 0.001f) {
            Render2D.blur(popupX, popupY, popupWidth, popupHeight, radius, 1, 1.0f, color(255, 255, 255, 255, defaultPopupAlpha));
            Render2D.glass(popupX, popupY, popupWidth, popupHeight, radius, color(255, 255, 255, 82, defaultPopupAlpha), 0.64f * defaultPopupAlpha, 42.0f, color(255, 255, 255, 154, defaultPopupAlpha), 0.16f * defaultPopupAlpha, true, 0.22f, 0.003f, 1.0f, 0.0f);
            Render2D.glassOutline(popupX, popupY, popupWidth, popupHeight, radius, 0.9f * scale, color(255, 255, 255, 170, defaultPopupAlpha), 1.0f, 35.0f, color(162, 162, 168, 122, defaultPopupAlpha), 0.42f * defaultPopupAlpha, false, 0.45f, 0.0015f, 1.0f, 0.0f);
        }
        ThemeRender.clickGuiGlass(popupX, popupY, popupWidth, popupHeight, 6.0f * scale, 7.0f, popupAlpha);
        drawPalette(popupAlpha);
        drawHueSlider(popupAlpha);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) {
            return false;
        }

        double mouseX = event.x();
        double mouseY = event.y();
        if (isPopupVisible() && popupAnchorVisible()) {
            popupWidth = POPUP_WIDTH * scale;
            popupHeight = POPUP_HEIGHT * scale;
            updatePopupBounds();
            paletteX = popupX + POPUP_PADDING * scale;
            paletteY = popupY + POPUP_PADDING * scale;
            paletteSize = PALETTE_SIZE * scale;
            hueX = paletteX + paletteSize + HUE_GAP * scale;
            hueY = paletteY;
            hueWidth = HUE_WIDTH * scale;
            hueHeight = paletteSize;
            if (hovered(mouseX, mouseY, paletteX, paletteY, paletteSize, paletteSize)) {
                dragMode = 1;
                updatePalette(mouseX, mouseY);
                return true;
            }
            if (hovered(mouseX, mouseY, hueX, hueY, hueWidth, hueHeight)) {
                dragMode = 2;
                updateHue(mouseY);
                return true;
            }
        }

        if (hovered(mouseX, mouseY, swatchX - 2.5f * scale, swatchY - 2.5f * scale, swatchSize + 5.0f * scale, swatchSize + 5.0f * scale)) {
            setOpen(!open);
            return true;
        }

        if (open) {
            setOpen(false);
        }
        return false;
    }

    @Override
    public boolean wantsMousePriority(MouseButtonEvent event) {
        return popupAnchorVisible() && isPopupVisible() && hovered(event.x(), event.y(), popupX, popupY, popupWidth, popupHeight);
    }

    @Override
    public boolean hasOpenPopup() {
        return isPopupVisible();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragMode == 0) {
            return false;
        }
        dragMode = 0;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragMode == 0) {
            return false;
        }
        if (dragMode == 1) {
            updatePalette(event.x(), event.y());
        } else if (dragMode == 2) {
            updateHue(event.y());
        }
        return true;
    }

    @Override
    public void closePopup() {
        if (open) {
            setOpen(false);
        }
    }

    private void updatePopupBounds() {
        // Open beside the settings panel. Prefer the right side; if there is no room,
        // mirror the picker to the left. The popup remains vertically aligned to its row.
        float margin = 4.0f * scale;
        float gap = 9.1f * scale;
        float screenWidth = popupScreenWidth();
        float screenHeight = popupScreenHeight();
        float rightX = x + width + gap;
        float leftX = x - popupWidth - gap;
        float maxX = Math.max(margin, screenWidth - popupWidth - margin);
        if (rightX + popupWidth <= screenWidth - margin) {
            popupX = rightX;
        } else if (leftX >= margin) {
            popupX = leftX;
        } else {
            popupX = clamp(rightX, margin, maxX);
        }
        float centeredY = y + height * 0.5f - popupHeight * 0.5f;
        float maxY = Math.max(margin, screenHeight - popupHeight - margin);
        popupY = clamp(centeredY, margin, maxY);
    }

    private void drawPalette(float popupAlpha) {
        int hueColor = Color.HSBtoRGB(hue, 1.0f, 1.0f);
        int topLeft = color(255, 255, 255, 255, popupAlpha);
        int topRight = color(red(hueColor), green(hueColor), blue(hueColor), 255, popupAlpha);
        int bottom = color(0, 0, 0, 255, popupAlpha);
        Render2D.rect(paletteX, paletteY, paletteSize, paletteSize, 4.0f * scale, topLeft, topRight, bottom, bottom);
        float selectorX = paletteX + saturation * paletteSize;
        float selectorY = paletteY + (1.0f - brightness) * paletteSize;
        Render2D.outline(selectorX - 2.5f * scale, selectorY - 2.5f * scale, 5.0f * scale, 5.0f * scale, 1.5f * scale, 1.0f * scale, color(255, 255, 255, 245, popupAlpha));
        Render2D.rect(selectorX - 1.0f * scale, selectorY - 1.0f * scale, 2.0f * scale, 2.0f * scale, 0.5f * scale, color(20, 24, 32, 180, popupAlpha));
    }

    private void drawHueSlider(float popupAlpha) {
        float segmentHeight = hueHeight / (HUE_COLORS.length - 1);
        for (int i = 0; i < HUE_COLORS.length - 1; i++) {
            int top = HUE_COLORS[i];
            int bottom = HUE_COLORS[i + 1];
            float overlap = 0.8f * scale;
            float segmentY = hueY + i * segmentHeight - (i == 0 ? 0.0f : overlap);
            float segmentBottom = hueY + (i + 1) * segmentHeight + (i == HUE_COLORS.length - 2 ? 0.0f : overlap);
            float radius = 1.5f * scale;
            Render2D.rect(hueX, segmentY, hueWidth, segmentBottom - segmentY,
                    i == 0 ? radius : 0.0f,
                    i == 0 ? radius : 0.0f,
                    i == HUE_COLORS.length - 2 ? radius : 0.0f,
                    i == HUE_COLORS.length - 2 ? radius : 0.0f,
                    color(red(top), green(top), blue(top), 255, popupAlpha),
                    color(red(top), green(top), blue(top), 255, popupAlpha),
                    color(red(bottom), green(bottom), blue(bottom), 255, popupAlpha),
                    color(red(bottom), green(bottom), blue(bottom), 255, popupAlpha));
        }
        float selectorY = hueY + hue * hueHeight;
        Render2D.rect(hueX - 1.0f * scale, selectorY - 1.0f * scale, hueWidth + 2.0f * scale, 2.0f * scale, 0.0f * scale, color(255, 255, 255, 255, popupAlpha));
    }

    private void updatePalette(double mouseX, double mouseY) {
        saturation = clamp((float) ((mouseX - paletteX) / paletteSize), 0.0f, 1.0f);
        brightness = 1.0f - clamp((float) ((mouseY - paletteY) / paletteSize), 0.0f, 1.0f);
        float alphaZoneTop = paletteY + paletteSize - 12.0f * scale;
        alphaValue = mouseY >= alphaZoneTop ? clamp((float) ((mouseX - paletteX) / paletteSize), 0.0f, 1.0f) : 1.0f;
        syncSetting();
    }

    private void updateHue(double mouseY) {
        hue = clamp((float) ((mouseY - hueY) / hueHeight), 0.0f, 1.0f);
        hueInitialized = true;
        syncSetting();
    }

    private void applyColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        if (!hueInitialized || hsb[1] > 0.001f) {
            this.hue = hsb[0];
            this.hueInitialized = true;
        }
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alphaValue = color.getAlpha() / 255.0f;
    }

    private int currentRgb() {
        return Color.HSBtoRGB(hue, saturation, brightness);
    }

    private void syncSetting() {
        int color = currentRgb();
        setting.setValue(new Color(red(color), green(color), blue(color), Math.round(alphaValue * 255.0f)));
    }

    private boolean isPopupVisible() {
        return open || openAnimation.get() > 0.01f || openAnimation.isAlive();
    }

    private void setOpen(boolean open) {
        if (this.open == open) {
            return;
        }
        this.open = open;
        openAnimation.run(open ? 1.0 : 0.0, 0.16, Easings.CUBIC_OUT);
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

    @Override
    protected int color(int red, int green, int blue, int alpha, float alphaMultiplier) {
        return ColorUtil.rgba(red, green, blue, (int) (alpha * alphaMultiplier));
    }
}
