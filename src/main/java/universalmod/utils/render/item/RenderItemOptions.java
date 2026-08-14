package universalmod.utils.render.item;

public record RenderItemOptions(
        float alpha,
        int color,
        boolean showCount,
        boolean showDurability
) {
    private static final RenderItemOptions DEFAULTS = new RenderItemOptions(
            1.0f,
            0xFFFFFFFF,
            true,
            true
    );
    private static final RenderItemOptions COUNT_NO_DURABILITY = new RenderItemOptions(
            1.0f,
            0xFFFFFFFF,
            true,
            false
    );
    private static final RenderItemOptions NO_DECORATIONS = new RenderItemOptions(
            1.0f,
            0xFFFFFFFF,
            false,
            false
    );

    public RenderItemOptions {
        alpha = clamp(alpha, 0.0f, 1.0f);
        color = normalizeColor(color);
    }

    public static RenderItemOptions defaults() {
        return DEFAULTS;
    }

    public static RenderItemOptions decorated(float alpha) {
        return alpha >= 0.999f ? DEFAULTS : new RenderItemOptions(alpha, 0xFFFFFFFF, true, true);
    }

    public static RenderItemOptions countNoDurability(float alpha) {
        return alpha >= 0.999f ? COUNT_NO_DURABILITY : new RenderItemOptions(alpha, 0xFFFFFFFF, true, false);
    }

    public static RenderItemOptions noDecorations(float alpha) {
        return alpha >= 0.999f ? NO_DECORATIONS : new RenderItemOptions(alpha, 0xFFFFFFFF, false, false);
    }

    private static int normalizeColor(int color) {
        if ((color & 0xFF000000) == 0 && (color & 0x00FFFFFF) != 0) {
            return color | 0xFF000000;
        }
        return color;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
