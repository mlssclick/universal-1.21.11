package universalmod.utils.theme;

public final class HudStyleContext {
    private static final ThreadLocal<String> CURRENT_ELEMENT = new ThreadLocal<>();

    private HudStyleContext() {
    }

    public static void push(String elementId) {
        CURRENT_ELEMENT.set(elementId);
    }

    public static void clear() {
        CURRENT_ELEMENT.remove();
    }

    public static String currentElementId() {
        return CURRENT_ELEMENT.get();
    }
}
