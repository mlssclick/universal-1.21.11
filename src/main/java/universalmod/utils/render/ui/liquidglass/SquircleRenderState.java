package universalmod.utils.render.ui.liquidglass;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

final class SquircleRenderState implements GuiElementRenderState {
    private final BuiltSquircle shape;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissor;
    private final ScreenRectangle bounds;

    SquircleRenderState(Matrix3x2f pose, BuiltSquircle shape, ScreenRectangle scissor) {
        this.shape = shape; this.pose = pose; this.scissor = scissor;
        
        float pad = 0.75f;
        ScreenRectangle b = new ScreenRectangle(
                Math.round(shape.x() - pad * 0.5f),
                Math.round(shape.y() - pad * 0.5f),
                Math.round(shape.width() + pad),
                Math.round(shape.height() + pad)
        ).transformMaxBounds(pose);
        this.bounds = scissor == null ? b : scissor.intersection(b);
    }

    @Override public void buildVertices(VertexConsumer c) {
        int i = SquircleRenderer.getInstance().reserve(shape);
        if (i < 0) return;
        float smoothness = 0.5f;
        float horizontalPadding = -smoothness / 2.0f + smoothness * 2.0f;
        float verticalPadding = smoothness / 2.0f + smoothness;
        float x0 = shape.x() - horizontalPadding / 2.0f;
        float y0 = shape.y() - verticalPadding / 2.0f;
        float x1 = x0 + shape.width() + horizontalPadding;
        float y1 = y0 + shape.height() + verticalPadding;
        vertex(c,x0,y0,i); vertex(c,x0,y1,i); vertex(c,x1,y1,i); vertex(c,x1,y0,i);
    }
    private void vertex(VertexConsumer c,float x,float y,int i){ c.addVertexWith2DPose(pose,x,y).setColor(shape.color()).setLineWidth(i+1.0f); }
    @Override public RenderPipeline pipeline(){ return SquircleRenderer.PIPELINE; }
    @Override public TextureSetup textureSetup(){ return TextureSetup.noTexture(); }
    @Override public ScreenRectangle scissorArea(){ return scissor; }
    @Override public ScreenRectangle bounds(){ return bounds; }
}
