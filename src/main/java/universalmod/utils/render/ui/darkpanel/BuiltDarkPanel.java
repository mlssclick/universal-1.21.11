package universalmod.utils.render.ui.darkpanel;

import net.minecraft.client.gui.GuiGraphics;

public record BuiltDarkPanel(
        float x, float y, float width, float height,
        float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
        float alpha, float smoothness, float gradientStrength, boolean shadow, int baseColor
) {
    public BuiltDarkPanel(float x, float y, float width, float height, float radius, float alpha) {
        this(x, y, width, height, radius, radius, radius, radius, alpha, 1.0f, 0.70f, false, 0xFF0D0F12);
    }

    public BuiltDarkPanel(
            float x, float y, float width, float height,
            float radius, float alpha, float gradientStrength, boolean shadow
    ) {
        this(x, y, width, height, radius, radius, radius, radius, alpha, 1.0f, gradientStrength, shadow, 0xFF0D0F12);
    }

    public BuiltDarkPanel(
            float x, float y, float width, float height,
            float radius, float alpha, float gradientStrength, boolean shadow, int baseColor
    ) {
        this(x, y, width, height, radius, radius, radius, radius, alpha, 1.0f, gradientStrength, shadow, baseColor);
    }

    public void render(GuiGraphics graphics) {
        DarkPanelRenderer.getInstance().draw(graphics, this);
    }

    public boolean visible() {
        return width > 0.0f && height > 0.0f && alpha > 0.001f;
    }
}
