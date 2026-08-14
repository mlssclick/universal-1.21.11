package universalmod.utils.render.ui.emotionwheel;

import java.util.Arrays;

public record BuiltEmotionWheelArc(
        float x,
        float y,
        float size,
        float thickness,
        float degree,
        float rotation,
        float blurRadius,
        int[] colors
) {
    public BuiltEmotionWheelArc {
        blurRadius = Math.max(0.0F, blurRadius);
        colors = normalizeColors(colors);
    }

    public BuiltEmotionWheelArc(float x, float y, float size, float thickness, float degree, float rotation, float blurRadius, int color) {
        this(x, y, size, thickness, degree, rotation, blurRadius, new int[]{color});
    }

    public boolean visible() {
        if (size <= 0.0F || thickness <= 0.0F || degree <= 0.0F) {
            return false;
        }
        for (int color : colors) {
            if ((color >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    private static int[] normalizeColors(int[] colors) {
        if (colors == null || colors.length == 0) {
            return new int[9];
        }
        if (colors.length == 1) {
            int[] result = new int[9];
            Arrays.fill(result, colors[0]);
            return result;
        }
        int[] result = new int[9];
        for (int i = 0; i < result.length; i++) {
            result[i] = i < colors.length ? colors[i] : colors[colors.length - 1];
        }
        return result;
    }
}
