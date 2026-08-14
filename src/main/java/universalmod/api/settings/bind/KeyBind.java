package universalmod.api.settings.bind;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Objects;

public final class KeyBind {
    public static final KeyBind NONE = new KeyBind(InputType.NONE, -1);

    private final InputType type;
    private final int code;

    private KeyBind(InputType type, int code) {
        this.type = type == null ? InputType.NONE : type;
        this.code = code;
    }

    public static KeyBind keyboard(int key) {
        return isValidKeyboardCode(key) ? new KeyBind(InputType.KEYBOARD, key) : NONE;
    }

    public static KeyBind mouse(int button) {
        return isValidMouseCode(button) ? new KeyBind(InputType.MOUSE, button) : NONE;
    }

    public static KeyBind of(InputType type, int code) {
        return switch (type == null ? InputType.NONE : type) {
            case KEYBOARD -> keyboard(code);
            case MOUSE -> mouse(code);
            case NONE -> NONE;
        };
    }

    public InputType getType() {
        return type;
    }

    public int getCode() {
        return code;
    }

    public boolean isBound() {
        return switch (type) {
            case KEYBOARD -> isValidKeyboardCode(code);
            case MOUSE -> isValidMouseCode(code);
            case NONE -> false;
        };
    }

    public boolean isDown(long windowHandle) {
        if (!isBound() || windowHandle == 0L) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null
                || minecraft.getWindow() == null
                || minecraft.getWindow().handle() == 0L
                || minecraft.getWindow().handle() != windowHandle
                || minecraft.screen != null) {
            return false;
        }

        return switch (type) {
            case KEYBOARD ->
                    GLFW.glfwGetKey(windowHandle, code) == GLFW.GLFW_PRESS;

            case MOUSE ->
                    GLFW.glfwGetMouseButton(windowHandle, code) == GLFW.GLFW_PRESS;

            case NONE -> false;
        };
    }

    public String getDisplayName() {
        if (!isBound()) {
            return "None";
        }

        if (type == InputType.MOUSE) {
            return switch (code) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "MOUSE 1";
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "MOUSE 2";
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MOUSE 3";
                default -> "MOUSE " + (code + 1);
            };
        }

        return keyboardName(code);
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("type", type.name().toLowerCase(Locale.ROOT));
        object.addProperty("code", code);
        object.addProperty("name", getDisplayName());
        return object;
    }

    public static KeyBind fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return NONE;
        }

        JsonObject object = element.getAsJsonObject();

        String typeName = object.has("type")
                ? object.get("type").getAsString()
                : "none";

        int code = object.has("code")
                ? object.get("code").getAsInt()
                : -1;

        InputType type;

        try {
            type = InputType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            type = InputType.NONE;
        }

        return of(type, code);
    }

    private static String keyboardName(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return String.valueOf(
                    (char) ('A' + key - GLFW.GLFW_KEY_A)
            );
        }

        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return String.valueOf(
                    (char) ('0' + key - GLFW.GLFW_KEY_0)
            );
        }

        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }

        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return "NUM " + (key - GLFW.GLFW_KEY_KP_0);
        }

        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_APOSTROPHE -> "'";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_SLASH -> "/";
            case GLFW.GLFW_KEY_SEMICOLON -> ";";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_BACKSLASH -> "\\";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "`";

            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_INSERT -> "INSERT";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";

            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_UP -> "UP";

            case GLFW.GLFW_KEY_PAGE_UP -> "PAGE UP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE DOWN";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";

            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS LOCK";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "SCROLL LOCK";
            case GLFW.GLFW_KEY_NUM_LOCK -> "NUM LOCK";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "PRINT SCREEN";
            case GLFW.GLFW_KEY_PAUSE -> "PAUSE";

            case GLFW.GLFW_KEY_KP_DECIMAL -> "NUM .";
            case GLFW.GLFW_KEY_KP_DIVIDE -> "NUM /";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "NUM *";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "NUM -";
            case GLFW.GLFW_KEY_KP_ADD -> "NUM +";
            case GLFW.GLFW_KEY_KP_ENTER -> "NUM ENTER";
            case GLFW.GLFW_KEY_KP_EQUAL -> "NUM =";

            case GLFW.GLFW_KEY_LEFT_SHIFT -> "L SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "L CTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "L ALT";
            case GLFW.GLFW_KEY_LEFT_SUPER -> "L WIN";

            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "R SHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "R CTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "R ALT";
            case GLFW.GLFW_KEY_RIGHT_SUPER -> "R WIN";

            case GLFW.GLFW_KEY_MENU -> "MENU";

            default -> "KEY " + key;
        };
    }

    private static boolean isValidKeyboardCode(int key) {
        return key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_LAST;
    }

    private static boolean isValidMouseCode(int button) {
        return button >= GLFW.GLFW_MOUSE_BUTTON_1 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof KeyBind keyBind)) {
            return false;
        }

        return code == keyBind.code && type == keyBind.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, code);
    }
}
