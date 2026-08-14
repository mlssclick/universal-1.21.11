package universalmod.screens.clickgui.impl.options;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import universalmod.api.settings.impl.ButtonSetting;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

public class ButtonOption extends ClickGuiOption {
    private static final float TEXT_SIZE = 9.9f;

    private final String action;
    private final SmoothAnimation scaleAnimation = new SmoothAnimation();
    private final ButtonSetting setting;
    private float controlX;
    private float controlY;
    private float controlWidth;
    private float controlHeight;

    public ButtonOption(ButtonSetting setting, String action) {
        super(setting.getName());
        this.setting = setting;
        this.action = action;
        scaleAnimation.set(1.0);
    }

    @Override
    public float getHeight() {
        return 28.0f;
    }

    @Override
    protected void renderControl(GuiGraphics graphics, float x, float y, float width, float height, float scale, float alpha) {
        // Final on-screen width is 1.5x the previous button even though the settings UI
        // itself is now scaled to 70%. 58.5 * 1.5 / 0.70 = 125.36 logical px.
        controlWidth = Math.min(width, 125.36f * scale);
        controlHeight = 19.5f * scale;
        controlX = x + (width - controlWidth) * 0.5f;
        controlY = y + (height - controlHeight) * 0.5f;

        scaleAnimation.update();
        float visualScale = scaleAnimation.get();
        float renderWidth = controlWidth * visualScale;
        float renderHeight = controlHeight * visualScale;
        float renderX = controlX + (controlWidth - renderWidth) * 0.5f;
        float renderY = controlY + (controlHeight - renderHeight) * 0.5f;

        renderGlassControl(renderX, renderY, renderWidth, renderHeight, 5.0f * scale * visualScale, alpha);

        float textSize = TEXT_SIZE * scale * visualScale;
        String fitted = fitText(action, textSize, Math.max(1.0f, renderWidth - 10.0f * scale));
        float actionWidth = Render2D.textWidth(FontType.BOLD, fitted, textSize);
        float actionX = renderX + (renderWidth - actionWidth) * 0.5f;
        float actionY = renderY + (renderHeight - textSize) * 0.5f - 0.90f * scale * visualScale;
        Render2D.text(FontType.BOLD, fitted, actionX, actionY, textSize,
                color(255, 255, 255, 248, alpha));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) {
            return false;
        }
        if (hovered(event.x(), event.y(), controlX, controlY, controlWidth, controlHeight)) {
            scaleAnimation.set(0.90);
            scaleAnimation.run(1.0, 0.22, Easings.CUBIC_OUT);
            setting.press();
            return true;
        }
        return false;
    }
}
