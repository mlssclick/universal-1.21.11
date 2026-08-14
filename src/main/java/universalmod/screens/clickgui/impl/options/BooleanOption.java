package universalmod.screens.clickgui.impl.options;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

public class BooleanOption extends ClickGuiOption {
    private static final float TEXT_SIZE = 9.9f;

    private final SmoothAnimation toggleAnimation = new SmoothAnimation();
    private final BooleanSetting setting;
    private boolean enabled;
    private float boxX;
    private float boxY;
    private float boxSize;

    public BooleanOption(BooleanSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.enabled = setting.getValue();
        toggleAnimation.set(enabled ? 1.0 : 0.0);
    }

    @Override
    protected void renderControl(GuiGraphics graphics, float x, float y, float width, float height, float scale, float alpha) {
        enabled = setting.getValue();
        toggleAnimation.run(enabled ? 1.0 : 0.0, 0.16, Easings.CUBIC_OUT, true);
        toggleAnimation.update();
        float progress = clamp(toggleAnimation.get(), 0.0f, 1.0f);

        boxSize = 15.6f * scale;
        boxX = x + width - boxSize;
        boxY = y + (height - boxSize) * 0.5f;

        int background = color(
                Math.round(38.0f + 119.0f * progress),
                Math.round(40.0f + 91.0f * progress),
                Math.round(53.0f + 157.0f * progress),
                Math.round(205.0f + 38.0f * progress),
                alpha
        );
        Render2D.rect(boxX, boxY, boxSize, boxSize, 1.32f * scale, background);
        Render2D.outline(boxX, boxY, boxSize, boxSize, 1.32f * scale, 0.60f * scale,
                color(190, 178, 230, Math.round(70.0f + 80.0f * progress), alpha));

        if (progress > 0.01f) {
            int checkAlpha = Math.round(255.0f * progress);
            String check = "j";
            // Scale follows the toggle animation, so the check grows/shrinks smoothly.
            float checkScale = 0.68f + 0.32f * progress;
            float checkSize = 12.77f * scale * checkScale;
            float checkWidth = Render2D.textWidth(FontType.ICONNEW, check, checkSize);
            float checkX = boxX + (boxSize - checkWidth) * 0.5f;
            float checkY = boxY + (boxSize - checkSize) * 0.5f - 0.18f * scale;
            Render2D.text(FontType.ICONNEW, check, checkX, checkY, checkSize,
                    color(255, 255, 255, checkAlpha, alpha));
        }

        float labelSize = TEXT_SIZE * scale;
        float labelY = y + (height - labelSize) * 0.5f - 0.90f * scale;
        float available = Math.max(0.0f, boxX - 8.0f * scale - x);
        renderPlainLabel(x, labelY, labelSize, available);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) {
            return false;
        }
        if (hovered(event.x(), event.y(), boxX - 2.5f * scale, boxY - 2.5f * scale, boxSize + 5.0f * scale, boxSize + 5.0f * scale)) {
            enabled = !enabled;
            setting.setValue(enabled);
            toggleAnimation.run(enabled ? 1.0 : 0.0, 0.16, Easings.CUBIC_OUT);
            return true;
        }
        return false;
    }
}
