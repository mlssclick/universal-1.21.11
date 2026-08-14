package universalmod.screens.clickgui.impl.options;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.api.settings.bind.KeyBind;
import universalmod.api.settings.impl.BindSetting;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

public class BindOption extends ClickGuiOption {
    private static final float TEXT_SIZE = 9.9f;

    private final BindSetting setting;
    private String key;
    private boolean listening;
    private float boxX;
    private float boxY;
    private float boxWidth;
    private float boxHeight;

    public BindOption(BindSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.key = setting.getValue().getDisplayName();
    }

    @Override
    protected void renderControl(GuiGraphics graphics, float x, float y, float width, float height, float scale, float alpha) {
        if (!listening) {
            key = setting.getValue().getDisplayName();
        }
        String text = listening ? "..." : key;
        float textSize = TEXT_SIZE * scale;
        float textWidth = Render2D.textWidth(FontType.BOLD, text, textSize);
        boxWidth = Math.max(30.0f * scale, Math.min(50.0f * scale, textWidth + 11.0f * scale));
        boxHeight = 16.0f * scale;
        boxX = x + width - boxWidth;
        boxY = y + (height - boxHeight) * 0.5f;

        Render2D.rect(boxX, boxY, boxWidth, boxHeight, 4.5f * scale,
                color(31, 34, 50, listening ? 244 : 220, alpha));
        Render2D.outline(boxX, boxY, boxWidth, boxHeight, 4.5f * scale, 0.35f * scale,
                color(151, 137, 216, listening ? 62 : 34, alpha));

        String fitted = fitText(text, textSize, boxWidth - 8.0f * scale);
        float fittedWidth = Render2D.textWidth(FontType.BOLD, fitted, textSize);
        float keyTextX = boxX + (boxWidth - fittedWidth) * 0.5f;
        float keyTextY = boxY + (boxHeight - textSize) * 0.5f - 0.90f * scale;
        Render2D.text(FontType.BOLD, fitted, keyTextX, keyTextY, textSize,
                color(235, 235, 248, 245, alpha));

        float labelSize = TEXT_SIZE * scale;
        float labelY = y + (height - labelSize) * 0.5f - 0.90f * scale;
        float available = Math.max(0.0f, boxX - 8.0f * scale - x);
        renderPlainLabel(x, labelY, labelSize, available);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (listening) {
            KeyBind bind = KeyBind.mouse(event.button());
            key = bind.getDisplayName();
            setting.setValue(bind);
            listening = false;
            return true;
        }
        if (event.button() == 0 && hovered(event.x(), event.y(), boxX, boxY, boxWidth, boxHeight)) {
            listening = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!listening) {
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            setting.setValue(KeyBind.NONE);
        } else {
            setting.setValue(KeyBind.keyboard(event.key()));
        }
        key = setting.getValue().getDisplayName();
        listening = false;
        return true;
    }
}
