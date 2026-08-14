package universalmod.utils.render.ui.blur;

import net.minecraft.client.gui.GuiGraphics;
import universalmod.utils.render.color.ColorUtil;

public record BuiltBlur(
        float x,
        float y,
        float width,
        float height,
        float radiusTopLeft,
        float radiusTopRight,
        float radiusBottomRight,
        float radiusBottomLeft,
        float smoothness,
        float blurRadius,
        int colorTopLeft,
        int colorTopRight,
        int colorBottomRight,
        int colorBottomLeft,
        BlurAlgorithm algorithm,
        float verticalSplit,
        int splitColor
) {
    private static final int DEFAULT_COLOR = ColorUtil.WHITE;
    public static final float NO_VERTICAL_SPLIT = -1.0f;

    public BuiltBlur {
        if (algorithm == null) {
            algorithm = BlurAlgorithm.KAWASE;
        }
    }

    public BuiltBlur(
            float x,
            float y,
            float width,
            float height,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft,
            float smoothness,
            float blurRadius,
            int colorTopLeft,
            int colorTopRight,
            int colorBottomRight,
            int colorBottomLeft
    ) {
        this(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                BlurAlgorithm.KAWASE,
                NO_VERTICAL_SPLIT,
                DEFAULT_COLOR
        );
    }

    public BuiltBlur(float x, float y, float width, float height, float radius, float smoothness, float blurRadius) {
        this(x, y, width, height, radius, radius, radius, radius, smoothness, blurRadius, DEFAULT_COLOR);
    }

    public BuiltBlur(
            float x,
            float y,
            float width,
            float height,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft,
            float smoothness,
            float blurRadius,
            int color
    ) {
        this(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                color,
                color,
                color,
                color
        );
    }

    public void render(GuiGraphics graphics) {
        BlurFramebuffer.getInstance().draw(graphics, this);
    }

    public BuiltBlur withColor(int color) {
        return withColors(color, color, color, color);
    }

    public BuiltBlur withColors(int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
        return new BuiltBlur(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                algorithm,
                verticalSplit,
                splitColor
        );
    }

    public BuiltBlur withAlgorithm(BlurAlgorithm algorithm) {
        return new BuiltBlur(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                algorithm,
                verticalSplit,
                splitColor
        );
    }

    public BuiltBlur withVerticalColorSplit(float splitY, int topColor, int bodyColor) {
        return new BuiltBlur(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                bodyColor,
                bodyColor,
                bodyColor,
                bodyColor,
                algorithm,
                splitY,
                topColor
        );
    }

    public BuiltBlur withoutVerticalColorSplit() {
        return new BuiltBlur(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                algorithm,
                NO_VERTICAL_SPLIT,
                splitColor
        );
    }

    public int color() {
        return colorTopLeft;
    }

    public boolean visible() {
        int colors = colorTopLeft | colorTopRight | colorBottomRight | colorBottomLeft;
        if (verticalSplit >= 0.0f) {
            colors |= splitColor;
        }
        return width > 0.0f && height > 0.0f && (colors >>> 24) != 0 && blurRadius > 0.0f;
    }
}
