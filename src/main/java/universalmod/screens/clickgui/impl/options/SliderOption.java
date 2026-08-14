package universalmod.screens.clickgui.impl.options;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.screens.clickgui.impl.ClickGuiController;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

public class SliderOption extends ClickGuiOption {
    private static final float OPTION_SCALE = 0.70f;
    private static final float TITLE_TEXT_SIZE = 6.5f / OPTION_SCALE;
    private static final float VALUE_TEXT_SIZE = 6.25f / OPTION_SCALE;
    private static final float VALUE_PADDING_X = 3.0f / OPTION_SCALE;
    private static final float VALUE_BOX_HEIGHT = 8.25f / OPTION_SCALE;
    private static final float VALUE_BOX_RADIUS = 2.0f / OPTION_SCALE;
    private static final float TRACK_TOP = 13.0f / OPTION_SCALE;
    private static final float TRACK_HEIGHT = 3.0f / OPTION_SCALE;
    private static final float TRACK_RADIUS = 0.75f / OPTION_SCALE;
    private static final float KNOB_SIZE = 6.0f / OPTION_SCALE;
    private static final float KNOB_RADIUS = 2.0f / OPTION_SCALE;
    private static final float SLIDER_HIT_TOP = 6.5f / OPTION_SCALE;
    private static final float SLIDER_HIT_HEIGHT = 11.0f / OPTION_SCALE;
    private static final float SOURCE_HEIGHT = 18.0f / OPTION_SCALE;
    private static final float BOTTOM_SPACING_COMPENSATION = 1.9f / OPTION_SCALE;

    private final NumberSetting setting;
    private final String suffix;
    private float value;
    private float min;
    private float max;
    private boolean dragging;
    private float dragProgress;
    private float sliderX;
    private float sliderY;
    private float sliderWidth;
    private float sliderHeight;
    private float sliderHitY;
    private float sliderHitHeight;
    private float visualProgress;
    private long lastRenderNanos;

    public SliderOption(NumberSetting setting, String suffix) {
        super(setting.getName());
        this.setting = setting;
        this.value = setting.getFloat();
        this.min = (float) setting.getMin();
        this.max = (float) setting.getMax();
        this.suffix = suffix == null ? "" : suffix;
        this.dragProgress = progress();
        visualProgress = this.dragProgress;
        lastRenderNanos = System.nanoTime();
    }

    @Override
    public float getHeight() {
        return SOURCE_HEIGHT + BOTTOM_SPACING_COMPENSATION;
    }

    @Override
    protected void renderControl(GuiGraphics graphics, float x, float y, float width, float height, float scale, float alpha) {
        value = setting.getFloat();
        min = (float) setting.getMin();
        max = (float) setting.getMax();

        sliderX = x;
        sliderWidth = width;
        sliderY = y + TRACK_TOP * scale;
        sliderHeight = TRACK_HEIGHT * scale;
        sliderHitY = y + SLIDER_HIT_TOP * scale;
        sliderHitHeight = SLIDER_HIT_HEIGHT * scale;

        if (dragging) {
            // MouseDragged events can arrive less often than render frames. Reading the live mouse
            // coordinate here removes that event-rate hitch completely. The knob/fill follows the
            // cursor 1:1 while dragging; animation remains for non-dragging value transitions.
            updateValue(ClickGuiController.mouseX());
        }

        float targetProgress = dragging ? dragProgress : progress();
        // Smooth every rendered frame instead of restarting a time-based animation every time
        // the mouse changes position. This keeps the HUD-like glide without the old input lag.
        long nowNanos = System.nanoTime();
        float deltaSeconds = lastRenderNanos == 0L
                ? 1.0f / 60.0f
                : Math.min(0.05f, Math.max(0.0f, (nowNanos - lastRenderNanos) / 1_000_000_000.0f));
        lastRenderNanos = nowNanos;
        // MathUtil.c(..., 1.0) from the reference works in tick-delta units. Converting that
        // response to seconds gives the same roughly 20/s exponential interpolation here.
        float response = 20.0f;
        float smoothing = 1.0f - (float) Math.exp(-response * deltaSeconds);
        visualProgress += (targetProgress - visualProgress) * smoothing;
        if (Math.abs(targetProgress - visualProgress) < 0.0005f) {
            visualProgress = targetProgress;
        }
        float animatedProgress = clamp(visualProgress, 0.0f, 1.0f);
        if (!dragging) {
            dragProgress = targetProgress;
        }

        float displayedValue = min + (max - min) * animatedProgress;

        String valueText = formatValue(displayedValue);
        float valueTextSize = VALUE_TEXT_SIZE * scale;
        float valueTextWidth = Render2D.textWidth(FontType.SEMIBOLD, valueText, valueTextSize);
        float valueBoxWidth = valueTextWidth + VALUE_PADDING_X * 2.0f * scale;
        float valueBoxHeight = VALUE_BOX_HEIGHT * scale;
        float valueBoxX = x + width - valueBoxWidth;
        float valueBoxY = y;
        float titleSize = TITLE_TEXT_SIZE * scale;
        float titleY = y + (0.5f / OPTION_SCALE) * scale;
        float titleWidth = Math.max(1.0f, valueBoxX - x - (4.0f / OPTION_SCALE) * scale);

        renderScrollingText(graphics, "slider:title", displayName(), x, titleY, titleSize,
                color(236, 237, 247, 250, alpha),
                x, y, titleWidth, valueBoxHeight,
                x, y, titleWidth, valueBoxHeight);

        Render2D.rect(valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight, VALUE_BOX_RADIUS * scale,
                color(137, 117, 199, 8, alpha));
        Render2D.outline(valueBoxX, valueBoxY, valueBoxWidth, valueBoxHeight, VALUE_BOX_RADIUS * scale,
                (0.5f / OPTION_SCALE) * scale, color(157, 143, 217, 62, alpha));
        float valueTextX = valueBoxX + (valueBoxWidth - valueTextWidth) * 0.5f;
        float valueTextY = valueBoxY + (valueBoxHeight - valueTextSize) * 0.5f
                - (0.5f / OPTION_SCALE) * scale;
        Render2D.text(FontType.SEMIBOLD, valueText, valueTextX, valueTextY, valueTextSize,
                color(240, 240, 250, 255, alpha));

        Render2D.rect(sliderX, sliderY, sliderWidth, sliderHeight, TRACK_RADIUS * scale,
                color(50, 52, 60, 89, alpha));
        float filledWidth = sliderWidth * animatedProgress;
        if (filledWidth > 0.01f) {
            Render2D.rect(sliderX, sliderY, filledWidth, sliderHeight, TRACK_RADIUS * scale,
                    color(137, 117, 199, 255, alpha));
        }

        float knobSize = KNOB_SIZE * scale;
        float knobX = sliderX + (sliderWidth - knobSize) * animatedProgress;
        float knobY = sliderY - (1.5f / OPTION_SCALE) * scale;
        Render2D.rect(knobX, knobY, knobSize, knobSize, KNOB_RADIUS * scale,
                color(255, 255, 255, 255, alpha));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 2 && hovered(event.x(), event.y(), x, y, width, SOURCE_HEIGHT * scale)) {
            setting.reset();
            value = setting.getFloat();
            dragProgress = progress();
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        if (hovered(event.x(), event.y(), sliderX, sliderHitY, sliderWidth, sliderHitHeight)) {
            dragging = true;
            updateValue(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) {
            return false;
        }
        updateValue(event.x());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (scrollY == 0.0 || !hovered(mouseX, mouseY, x, y, width, SOURCE_HEIGHT * scale)) {
            return false;
        }
        double step = Math.max(1.0E-6, setting.getStep());
        setting.setValue(setting.getValue() + Math.signum(scrollY) * step);
        value = setting.getFloat();
        dragProgress = progress();
        return true;
    }

    private void updateValue(double mouseX) {
        dragProgress = clamp((float) ((mouseX - sliderX) / Math.max(1.0f, sliderWidth)), 0.0f, 1.0f);
        double raw = min + (max - min) * dragProgress;
        double step = Math.max(1.0E-6, setting.getStep());
        double stepped = Math.round(raw / step) * step;
        setting.setValue(stepped);
        value = setting.getFloat();
    }

    private float progress() {
        if (max == min) {
            return 0.0f;
        }
        return clamp((value - min) / (max - min), 0.0f, 1.0f);
    }

    private String formatValue(float displayValue) {
        double step = Math.max(1.0E-6, setting.getStep());
        String valueText = step % 1.0 == 0.0
                ? String.valueOf(Math.round(displayValue))
                : String.valueOf(Math.round(displayValue * 100.0f) / 100.0f);
        return valueText + suffix;
    }
}
