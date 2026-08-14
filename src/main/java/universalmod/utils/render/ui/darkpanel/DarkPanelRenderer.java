package universalmod.utils.render.ui.darkpanel;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;
import universalmod.utils.render.color.ColorUtil;
import universalmod.mixin.accessor.GuiGraphicsExtractorAccessor;
import universalmod.utils.render.ScissorUtil;
import universalmod.utils.render.ui.Render2DCoordinateSpace;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class DarkPanelRenderer implements AutoCloseable {
    private static final int MAX = 512;
    private static final int PARAMS_PER_PANEL = 4;
    private static final int UNIFORM_BYTES = MAX * PARAMS_PER_PANEL * 4 * Float.BYTES;
    private static volatile DarkPanelRenderer instance;

    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/dark_panel"))
            .withVertexShader(id("ui/dark_panel/dark_panel"))
            .withFragmentShader(id("ui/dark_panel/dark_panel"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("DarkPanelParamsArray", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final List<BuiltDarkPanel> prepared = new ArrayList<>(128);
    private GuiGraphics activeGraphics;
    private GpuBuffer paramsBuffer;
    private boolean paramsDirty = true;

    private DarkPanelRenderer() {}

    public static DarkPanelRenderer getInstance() {
        DarkPanelRenderer local = instance;
        if (local == null) {
            synchronized (DarkPanelRenderer.class) {
                local = instance;
                if (local == null) instance = local = new DarkPanelRenderer();
            }
        }
        return local;
    }

    public static void closeInstance() {
        DarkPanelRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginFrame(GuiGraphics graphics) { activeGraphics = graphics; }
    public void flush() { activeGraphics = null; }
    public void beginGuiFrame() { prepared.clear(); paramsDirty = false; }
    public boolean isPipeline(RenderPipeline pipeline) { return pipeline == PIPELINE; }
    public void draw(GuiGraphics graphics, BuiltDarkPanel panel) { beginFrame(graphics); enqueue(panel); flush(); }
    public void enqueue(BuiltDarkPanel panel) { submit(activeGraphics, panel); }

    int reserve(BuiltDarkPanel panel) {
        if (prepared.size() >= MAX) return -1;
        int index = prepared.size();
        prepared.add(panel);
        paramsDirty = true;
        return index;
    }

    private void submit(GuiGraphics graphics, BuiltDarkPanel panel) {
        if (graphics == null || panel == null || !panel.visible()) return;
        float maxRadius = Math.max(0.0f, Math.min(panel.width(), panel.height()) * 0.5f);
        BuiltDarkPanel normalized = new BuiltDarkPanel(
                panel.x(), panel.y(), panel.width(), panel.height(),
                clamp(panel.radiusTopLeft(), 0.0f, maxRadius),
                clamp(panel.radiusTopRight(), 0.0f, maxRadius),
                clamp(panel.radiusBottomRight(), 0.0f, maxRadius),
                clamp(panel.radiusBottomLeft(), 0.0f, maxRadius),
                clamp(panel.alpha(), 0.0f, 1.0f), Math.max(0.5f, panel.smoothness()),
                clamp(panel.gradientStrength(), 0.0f, 1.0f), panel.shadow(), panel.baseColor()
        );
        Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
        ((GuiGraphicsExtractorAccessor) graphics).universalmod$getGuiRenderState()
                .submitGuiElement(new DarkPanelRenderState(pose, normalized, ScissorUtil.current()));
    }

    public void bindParams(RenderPass pass) {
        if (pass == null || prepared.isEmpty()) return;
        GpuBuffer buffer = ensureParamsBuffer();
        if (buffer != null) pass.setUniform("DarkPanelParamsArray", buffer);
    }

    public void prepareBuffers() {
        if (prepared.isEmpty() || !paramsDirty) return;
        GpuBuffer buffer = ensureWritableParamsBuffer();
        if (buffer == null) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildData(stack);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, data.remaining()), data);
            paramsDirty = false;
        } catch (RuntimeException ignored) { paramsDirty = true; }
    }

    private GpuBuffer ensureParamsBuffer() {
        if (!paramsDirty && paramsBuffer != null) return paramsBuffer;
        prepareBuffers();
        if (!paramsDirty && paramsBuffer != null) return paramsBuffer;
        closeBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildData(stack);
            paramsBuffer = RenderSystem.getDevice().createBuffer(() -> "UNIVERSALMOD_dark_panel_params", GpuBuffer.USAGE_UNIFORM, data);
            paramsDirty = false;
            return paramsBuffer;
        }
    }

    private GpuBuffer ensureWritableParamsBuffer() {
        if (paramsBuffer != null && !paramsBuffer.isClosed() && paramsBuffer.size() >= UNIFORM_BYTES) return paramsBuffer;
        closeBuffer();
        try {
            paramsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_dark_panel_params", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_BYTES);
            return paramsBuffer;
        } catch (RuntimeException ignored) { return null; }
    }

    private ByteBuffer buildData(MemoryStack stack) {
        int bytes = Math.max(1, prepared.size()) * PARAMS_PER_PANEL * 4 * Float.BYTES;
        ByteBuffer data = stack.calloc(bytes);
        for (int i = 0; i < prepared.size(); i++) {
            BuiltDarkPanel p = prepared.get(i);
            int o = i * PARAMS_PER_PANEL * 4 * Float.BYTES;
            data.putFloat(o, p.radiusTopLeft());
            data.putFloat(o + 4, p.radiusTopRight());
            data.putFloat(o + 8, p.radiusBottomRight());
            data.putFloat(o + 12, p.radiusBottomLeft());
            data.putFloat(o + 16, p.width());
            data.putFloat(o + 20, p.height());
            data.putFloat(o + 24, p.alpha());
            data.putFloat(o + 28, p.smoothness());
            data.putFloat(o + 32, p.gradientStrength());
            data.putFloat(o + 36, p.shadow() ? 1.0f : 0.0f);
            data.putFloat(o + 40, 0.0f);
            data.putFloat(o + 44, 0.0f);
            data.putFloat(o + 48, ColorUtil.getRed(p.baseColor()) / 255.0f);
            data.putFloat(o + 52, ColorUtil.getGreen(p.baseColor()) / 255.0f);
            data.putFloat(o + 56, ColorUtil.getBlue(p.baseColor()) / 255.0f);
            data.putFloat(o + 60, 1.0f);
        }
        data.position(0);
        return data;
    }

    private void closeBuffer() { if (paramsBuffer != null) { paramsBuffer.close(); paramsBuffer = null; } }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("universalmod", path); }

    @Override public void close() { prepared.clear(); activeGraphics = null; closeBuffer(); }
}
