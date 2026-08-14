package universalmod.screens.clickgui.impl.options;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.api.settings.impl.StringSetting;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

public class TextOption extends ClickGuiOption {
    private static final String LOWERCASE_CENTER_SAMPLE = "acegmnopqrsuvwxyz";

    private final SmoothAnimation scrollAnimation = new SmoothAnimation();
    private final SmoothAnimation outlineAnimation = new SmoothAnimation();
    private final StringSetting setting;
    private String value;
    private boolean editing;
    private boolean selectedAll;
    private int cursor;
    private String cachedCursorValue;
    private int cachedCursor = -1;
    private float cachedCursorTextSize;
    private float cachedCursorWidth;
    private float controlX;
    private float controlY;
    private float controlWidth;
    private float controlHeight;

    public TextOption(StringSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.value = setting.getValue();
        this.cursor = value.length();
        scrollAnimation.set(0.0);
        outlineAnimation.set(0.0);
    }

    @Override
    protected void renderControl(GuiGraphics graphics, float x, float y, float width, float height, float scale, float alpha) {
        if (!editing) {
            value = setting.getValue();
            cursor = Math.min(cursor, value.length());
        }
        controlWidth = 54.0f * scale;
        controlHeight = 16.0f * scale;
        controlX = x + width - controlWidth;
        controlY = y + (height - controlHeight) * 0.5f;
        float textSize = 9.9f * scale;
        float textX = controlX + 5.0f * scale;
        Render2D.TextVisualBounds centerBounds = Render2D.textVisualBounds(FontType.SEMIBOLD, LOWERCASE_CENTER_SAMPLE, textSize);
        float textY = centerBounds.empty()
                ? controlY + (controlHeight - textSize) * 0.5f
                : controlY + controlHeight * 0.5f - centerBounds.centerY();
        textY += 0.5f * scale;
        float caretHeight = 10.0f * scale;
        float caretY = controlY + (controlHeight - caretHeight) * 0.5f;
        float available = controlWidth - 10.0f * scale;
        float valueWidth = textWidth(value, textSize);
        float targetScroll = valueWidth > available ? available - valueWidth - 1.0f * scale : 0.0f;
        scrollAnimation.run(targetScroll, 0.20, Easings.CUBIC_OUT, true);
        scrollAnimation.update();
        outlineAnimation.run(editing ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT, true);
        outlineAnimation.update();

        renderGlassControl(controlX, controlY, controlWidth, controlHeight, 4.0f * scale, alpha);
        if (outlineAnimation.get() > 0.01f) {
            Render2D.outline(controlX, controlY, controlWidth, controlHeight, 4.0f * scale, 1.0f * scale,
                    color(155, 162, 176, Math.round(165.0f * outlineAnimation.get()), alpha));
        }

        if (editing) {
            Render2D.pushScissor(graphics, controlX + 4.0f * scale, controlY, available + 2.0f * scale, controlHeight);
            Render2D.TextVisualBounds valueBounds = Render2D.textVisualBounds(FontType.SEMIBOLD, value, textSize);
            float renderedTextX = textX + scrollAnimation.get();
            if (selectedAll && !value.isEmpty()) {
                Render2D.rect(renderedTextX + valueBounds.minX() - 1.5f * scale, caretY,
                        Math.max(valueBounds.width(), valueWidth) + 3.0f * scale, caretHeight, 1.0f * scale,
                        color(155, 162, 176, 85, alpha));
            }
            Render2D.text(FontType.SEMIBOLD, value, renderedTextX, textY, textSize,
                    color(255, 255, 255, 235, alpha));
            if (!selectedAll && (System.currentTimeMillis() / 420L) % 2L == 0L) {
                float cursorX = renderedTextX + cursorWidth(valueBounds, textSize);
                Render2D.rect(cursorX, caretY, 1.0f * scale,
                        caretHeight, 0.0f, color(255, 255, 255, 235, alpha));
            }
            Render2D.popScissor(graphics);
        } else {
            String fitted = fitText(value, textSize, available);
            Render2D.text(FontType.SEMIBOLD, fitted, textX, textY, textSize,
                    color(255, 255, 255, 235, alpha));
        }

        float labelSize = 9.9f * scale;
        float labelY = y + (height - labelSize) * 0.5f - 0.90f * scale;
        float labelWidth = Math.max(0.0f, controlX - 8.0f * scale - x);
        renderPlainLabel(x, labelY, labelSize, labelWidth);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) {
            return false;
        }
        if (hovered(event.x(), event.y(), controlX, controlY, controlWidth, controlHeight)) {
            editing = true;
            selectedAll = false;
            cursor = value.length();
            return true;
        }
        editing = false;
        selectedAll = false;
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!editing) {
            return false;
        }

        int key = event.key();
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectedAll = true;
            cursor = value.length();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            setClipboard(value);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            insertText(getClipboard());
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (selectedAll) {
                value = "";
                cursor = 0;
                selectedAll = false;
            } else if (cursor > 0) {
                value = value.substring(0, cursor - 1) + value.substring(cursor);
                cursor--;
            }
            syncSetting();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (selectedAll) {
                value = "";
                cursor = 0;
                selectedAll = false;
            } else if (cursor < value.length()) {
                value = value.substring(0, cursor) + value.substring(cursor + 1);
            }
            syncSetting();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            cursor = Math.max(0, cursor - 1);
            selectedAll = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            cursor = Math.min(value.length(), cursor + 1);
            selectedAll = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            cursor = 0;
            selectedAll = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            cursor = value.length();
            selectedAll = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            editing = false;
            selectedAll = false;
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!editing || !event.isAllowedChatCharacter()) {
            return false;
        }
        insertText(event.codepointAsString());
        return true;
    }

    private void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String cleaned = text.replace("\r", "").replace("\n", " ");
        if (selectedAll) {
            value = cleaned;
            cursor = value.length();
            selectedAll = false;
            syncSetting();
            return;
        }
        value = value.substring(0, cursor) + cleaned + value.substring(cursor);
        cursor += cleaned.length();
        syncSetting();
    }

    private String getClipboard() {
        Minecraft client = Minecraft.getInstance();
        String clipboard = GLFW.glfwGetClipboardString(client.getWindow().handle());
        return clipboard == null ? "" : clipboard;
    }

    private void setClipboard(String text) {
        Minecraft client = Minecraft.getInstance();
        GLFW.glfwSetClipboardString(client.getWindow().handle(), text);
    }

    private void syncSetting() {
        setting.setValue(value);
        value = setting.getValue();
        cursor = Math.min(cursor, value.length());
        invalidateCursorWidth();
    }

    private float cursorWidth(Render2D.TextVisualBounds valueBounds, float textSize) {
        int safeCursor = Math.min(cursor, value.length());
        if (safeCursor == 0 && !value.isEmpty() && !valueBounds.empty()) {
            return valueBounds.minX();
        }
        if (cachedCursorValue != value || cachedCursor != safeCursor || cachedCursorTextSize != textSize) {
            cachedCursorValue = value;
            cachedCursor = safeCursor;
            cachedCursorTextSize = textSize;
            cachedCursorWidth = caretOffset(value.substring(0, safeCursor), textSize);
        }
        return cachedCursorWidth;
    }

    private float caretOffset(String text, float textSize) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        if (Character.isWhitespace(text.charAt(text.length() - 1))) {
            return textWidth(text, textSize);
        }
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(FontType.SEMIBOLD, text, textSize);
        return bounds.empty() ? textWidth(text, textSize) : bounds.maxX();
    }

    private void invalidateCursorWidth() {
        cachedCursorValue = null;
        cachedCursor = -1;
        cachedCursorTextSize = 0.0f;
        cachedCursorWidth = 0.0f;
    }
}
