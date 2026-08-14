package universalmod.utils.render.ui.liquidglass;

public record BuiltSquircle(
        float x, float y, float width, float height,
        float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
        float squirt, int color, float z
) {
    public boolean visible() { return width > 0.0f && height > 0.0f && ((color >>> 24) & 255) > 0; }
}
