package universalmod.utils.render.ui.rectangle.rectdefault;

import net.minecraft.client.gui.GuiGraphics;
import universalmod.utils.render.color.ColorUtil;

public record BuiltRectangle(
        float x,
        float y,
        float width,
        float height,
        float radiusTopLeft,
        float radiusTopRight,
        float radiusBottomRight,
        float radiusBottomLeft,
        int colorTopLeft,
        int colorTopRight,
        int colorBottomRight,
        int colorBottomLeft,
        float smoothness,
        float verticalSplit,
        int splitColor
) {
    public static final float DEFAULT_SMOOTHNESS = 1.0f;
    public static final int DEFAULT_COLOR = ColorUtil.WHITE;
    public static final float NO_VERTICAL_SPLIT = -1.0f;

    public BuiltRectangle(
            float x,
            float y,
            float width,
            float height,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft,
            int colorTopLeft,
            int colorTopRight,
            int colorBottomRight,
            int colorBottomLeft,
            float smoothness
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
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                smoothness,
                NO_VERTICAL_SPLIT,
                DEFAULT_COLOR
        );
    }

    public BuiltRectangle(float x, float y, float width, float height, float radius, int color) {
        this(x, y, width, height, radius, radius, radius, radius, color, color, color, color, DEFAULT_SMOOTHNESS);
    }

    public BuiltRectangle(
            float x,
            float y,
            float width,
            float height,
            float radiusTopLeft,
            float radiusTopRight,
            float radiusBottomRight,
            float radiusBottomLeft,
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
                color,
                color,
                color,
                color,
                DEFAULT_SMOOTHNESS
        );
    }

    public BuiltRectangle withSmoothness(float smoothness) {
        return new BuiltRectangle(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                smoothness,
                verticalSplit,
                splitColor
        );
    }

    public BuiltRectangle withColor(int color) {
        return new BuiltRectangle(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                color,
                color,
                color,
                color,
                smoothness,
                verticalSplit,
                splitColor
        );
    }

    public BuiltRectangle withVerticalColorSplit(float splitY, int topColor, int bodyColor) {
        return new BuiltRectangle(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                bodyColor,
                bodyColor,
                bodyColor,
                bodyColor,
                smoothness,
                splitY,
                topColor
        );
    }

    public BuiltRectangle withoutVerticalColorSplit() {
        return new BuiltRectangle(
                x,
                y,
                width,
                height,
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                colorTopLeft,
                colorTopRight,
                colorBottomRight,
                colorBottomLeft,
                smoothness,
                NO_VERTICAL_SPLIT,
                splitColor
        );
    }

    public void render(GuiGraphics graphics) {
        DefaultRectangleRenderer.getInstance().draw(graphics, this);
    }

    public boolean visible() {
        int colors = colorTopLeft | colorTopRight | colorBottomRight | colorBottomLeft;
        if (verticalSplit >= 0.0f) {
            colors |= splitColor;
        }
        return width > 0.0f && height > 0.0f && ((colors >>> 24) != 0);
    }
}
