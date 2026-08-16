package universalmod.utils.render.ui.hudchrome;

import net.minecraft.client.gui.GuiGraphics;

public record BuiltHudChrome(
        float x, float y, float width, float height,
        float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
        float alpha, float smoothness, float darkness
) {
    public BuiltHudChrome(float x, float y, float width, float height, float radius, float alpha) {
        this(x, y, width, height, radius, radius, radius, radius, alpha, 0.85f, 0.92f);
    }

    public BuiltHudChrome(
            float x, float y, float width, float height, float radius,
            float alpha, float smoothness, float darkness
    ) {
        this(x, y, width, height, radius, radius, radius, radius, alpha, smoothness, darkness);
    }

    public boolean visible() {
        return width > 0.0f && height > 0.0f && alpha > 0.001f;
    }

    public void render(GuiGraphics graphics) {
        HudChromeRenderer.getInstance().draw(graphics, this);
    }
}
