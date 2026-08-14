package universalmod.utils.render.ui.emotionwheel;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

final class EmotionWheelArcRenderState implements GuiElementRenderState {
    private final BuiltEmotionWheelArc arc;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissor;
    private final ScreenRectangle bounds;

    EmotionWheelArcRenderState(Matrix3x2f pose, BuiltEmotionWheelArc arc, ScreenRectangle scissor) {
        this.arc = arc;
        this.pose = pose;
        this.scissor = scissor;
        float blur = arc.blurRadius();
        ScreenRectangle transformed = new ScreenRectangle(
                Math.round(arc.x() - blur),
                Math.round(arc.y() - blur),
                Math.round(arc.size() + blur * 2.0F),
                Math.round(arc.size() + blur * 2.0F)
        ).transformMaxBounds(pose);
        this.bounds = scissor == null ? transformed : scissor.intersection(transformed);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        int batchIndex = EmotionWheelArcRenderer.getInstance().reserve(arc);
        if (batchIndex < 0) {
            return;
        }
        float blur = arc.blurRadius();
        float x0 = arc.x() - blur;
        float y0 = arc.y() - blur;
        float x1 = arc.x() + arc.size() + blur;
        float y1 = arc.y() + arc.size() + blur;
        vertex(consumer, x0, y0, 0, 0, batchIndex);
        vertex(consumer, x0, y1, 0, 255, batchIndex);
        vertex(consumer, x1, y1, 255, 255, batchIndex);
        vertex(consumer, x1, y0, 255, 0, batchIndex);
    }

    private void vertex(VertexConsumer consumer, float x, float y, int coordX, int coordY, int batchIndex) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(coordX, coordY, 255, 255)
                .setLineWidth(batchIndex + 1.0F);
    }

    @Override
    public RenderPipeline pipeline() {
        return EmotionWheelArcRenderer.PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle scissorArea() {
        return scissor;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
