package universalmod.utils.render.ui.hudchrome;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

final class HudChromeRenderState implements GuiElementRenderState {
    private final BuiltHudChrome panel;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private final ScreenRectangle bounds;

    HudChromeRenderState(Matrix3x2f pose, BuiltHudChrome panel, ScreenRectangle scissorArea) {
        this.panel = panel;
        this.pose = pose;
        this.scissorArea = scissorArea;
        ScreenRectangle transformed = new ScreenRectangle(
                Math.round(panel.x()), Math.round(panel.y()),
                Math.round(panel.width()), Math.round(panel.height())
        ).transformMaxBounds(pose);
        this.bounds = scissorArea == null ? transformed : scissorArea.intersection(transformed);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        int batchIndex = HudChromeRenderer.getInstance().reserve(panel);
        if (batchIndex < 0) {
            return;
        }
        vertex(consumer, panel.x(), panel.y(), batchIndex);
        vertex(consumer, panel.x(), panel.y() + panel.height(), batchIndex);
        vertex(consumer, panel.x() + panel.width(), panel.y() + panel.height(), batchIndex);
        vertex(consumer, panel.x() + panel.width(), panel.y(), batchIndex);
    }

    private void vertex(VertexConsumer consumer, float x, float y, int batchIndex) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(0xFFFFFFFF)
                .setLineWidth(batchIndex + 1.0f);
    }

    @Override public RenderPipeline pipeline() { return HudChromeRenderer.PIPELINE; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    @Override public ScreenRectangle scissorArea() { return scissorArea; }
    @Override public ScreenRectangle bounds() { return bounds; }
}
