package universalmod.utils.render.ui.darkpanel;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

final class DarkPanelRenderState implements GuiElementRenderState {
    private final BuiltDarkPanel panel;
    private final Matrix3x2f pose;
    private final ScreenRectangle scissorArea;
    private final ScreenRectangle bounds;

    DarkPanelRenderState(Matrix3x2f pose, BuiltDarkPanel panel, ScreenRectangle scissorArea) {
        this.panel = panel;
        this.pose = pose;
        this.scissorArea = scissorArea;
        float shadowPad = panel.shadow() ? 2.0f : 0.0f;
        ScreenRectangle transformed = new ScreenRectangle(
                Math.round(panel.x() - shadowPad), Math.round(panel.y() - shadowPad),
                Math.round(panel.width() + shadowPad * 2.0f), Math.round(panel.height() + shadowPad * 2.0f)
        ).transformMaxBounds(pose);
        this.bounds = scissorArea == null ? transformed : scissorArea.intersection(transformed);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        int batchIndex = DarkPanelRenderer.getInstance().reserve(panel);
        if (batchIndex < 0) return;
        float shadowPad = panel.shadow() ? 2.0f : 0.0f;
        float x0 = panel.x() - shadowPad;
        float y0 = panel.y() - shadowPad;
        float x1 = panel.x() + panel.width() + shadowPad;
        float y1 = panel.y() + panel.height() + shadowPad;
        vertex(consumer, x0, y0, batchIndex);
        vertex(consumer, x0, y1, batchIndex);
        vertex(consumer, x1, y1, batchIndex);
        vertex(consumer, x1, y0, batchIndex);
    }

    private void vertex(VertexConsumer consumer, float x, float y, int batchIndex) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(0xFFFFFFFF)
                .setLineWidth((float) (batchIndex + 1));
    }

    @Override public RenderPipeline pipeline() { return DarkPanelRenderer.PIPELINE; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
    @Override public ScreenRectangle scissorArea() { return scissorArea; }
    @Override public ScreenRectangle bounds() { return bounds; }
}
