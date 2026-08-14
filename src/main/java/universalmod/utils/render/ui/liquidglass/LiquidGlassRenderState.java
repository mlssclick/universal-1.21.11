package universalmod.utils.render.ui.liquidglass;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

final class LiquidGlassRenderState implements GuiElementRenderState {
    private final BuiltLiquidGlass glass;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private final ScreenRectangle bounds;

    LiquidGlassRenderState(Matrix3x2f pose, BuiltLiquidGlass glass, ScreenRectangle scissorArea) {
        this.glass = glass;
        this.pose = pose;
        this.scissorArea = scissorArea;
        ScreenRectangle transformedBounds = new ScreenRectangle(
                Math.round(glass.x()), Math.round(glass.y()), Math.round(glass.width()), Math.round(glass.height())
        ).transformMaxBounds(pose);
        this.bounds = scissorArea == null ? transformedBounds : scissorArea.intersection(transformedBounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        int batchIndex = LiquidGlassRenderer.getInstance().reserve(glass);
        if (batchIndex < 0) return;
        float x0 = glass.x();
        float y0 = glass.y();
        float x1 = x0 + glass.width();
        float y1 = y0 + glass.height();
        vertex(consumer, x0, y0, batchIndex);
        vertex(consumer, x0, y1, batchIndex);
        vertex(consumer, x1, y1, batchIndex);
        vertex(consumer, x1, y0, batchIndex);
    }

    private void vertex(VertexConsumer consumer, float x, float y, int batchIndex) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(glass.color())
                .setLineWidth((float) (batchIndex + 1));
    }

    @Override public RenderPipeline pipeline() { return LiquidGlassRenderer.LIQUID_GLASS_PIPELINE; }
    @Override public TextureSetup textureSetup() { return LiquidGlassFramebuffer.getInstance().textureSetup(glass.blurChannel()); }
    @Override public ScreenRectangle scissorArea() { return scissorArea; }
    @Override public ScreenRectangle bounds() { return bounds; }
}
