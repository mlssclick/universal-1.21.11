package universalmod.utils.theme;

/** Compatibility surface for ClickGUI panels; alternate theme renderers were removed. */
public final class ThemeRender {
    private ThemeRender() {
    }

    public static boolean clickGuiGlass(float x, float y, float width, float height, float rounding, float alpha) {
        return false;
    }

    public static boolean clickGuiGlass(float x, float y, float width, float height,
                                        float rounding, float squirt, float alpha) {
        return false;
    }

    public static boolean hudGlass(float x, float y, float width, float height, float alpha) {
        return false;
    }

    public static boolean hudGlass(float x, float y, float width, float height, float rounding, float alpha) {
        return false;
    }

    public static float defaultAlpha(float alpha) {
        return clamp01(alpha);
    }

    public static float hudDefaultAlpha(float alpha) {
        return clamp01(alpha);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
