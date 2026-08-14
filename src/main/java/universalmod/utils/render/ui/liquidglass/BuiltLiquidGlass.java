package universalmod.utils.render.ui.liquidglass;

import net.minecraft.client.gui.GuiGraphics;

public record BuiltLiquidGlass(
        float x,
        float y,
        float width,
        float height,
        float radiusTopLeft,
        float radiusTopRight,
        float radiusBottomRight,
        float radiusBottomLeft,
        int color,
        float globalAlpha,
        float fresnelPower,
        int fresnelColor,
        float baseAlpha,
        boolean fresnelInvert,
        float fresnelMix,
        float distortStrength,
        float squirt,
        float blurStrength,
        LiquidGlassBlurChannel blurChannel,
        float z
) {
    public boolean visible() {
        return width > 0.0f && height > 0.0f && globalAlpha > 0.001f;
    }

    public void render(GuiGraphics graphics) {
        LiquidGlassRenderer.getInstance().draw(graphics, this);
    }
}
