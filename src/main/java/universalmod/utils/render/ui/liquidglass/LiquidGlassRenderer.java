package universalmod.utils.render.ui.liquidglass;

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
import universalmod.mixin.accessor.GuiGraphicsExtractorAccessor;
import universalmod.utils.render.ScissorUtil;
import universalmod.utils.render.ui.Render2DCoordinateSpace;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class LiquidGlassRenderer implements AutoCloseable {
    private static final int MAX_GLASSES = 512;
    private static final int PARAMS_PER_GLASS = 5;
    private static final int FLOATS_PER_PARAM = 4;
    private static final int UNIFORM_BYTES = MAX_GLASSES * PARAMS_PER_GLASS * FLOATS_PER_PARAM * Float.BYTES;
    private static volatile LiquidGlassRenderer instance;

    public static final RenderPipeline LIQUID_GLASS_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/liquidglass"))
            .withVertexShader(id("ui/liquidglass/liquidglass"))
            .withFragmentShader(id("ui/liquidglass/liquidglass"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("LiquidGlassParamsArray", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("LiquidGlassFrame", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final List<BuiltLiquidGlass> prepared = new ArrayList<>(64);
    private GuiGraphics activeGraphics;
    private GpuBuffer paramsBuffer;
    private boolean paramsDirty = true;

    private LiquidGlassRenderer() {}

    public static LiquidGlassRenderer getInstance() {
        LiquidGlassRenderer local = instance;
        if (local == null) {
            synchronized (LiquidGlassRenderer.class) {
                local = instance;
                if (local == null) instance = local = new LiquidGlassRenderer();
            }
        }
        return local;
    }

    public static void closeInstance() {
        LiquidGlassRenderer local = instance;
        if (local != null) { local.close(); instance = null; }
    }

    public void beginFrame(GuiGraphics graphics) { activeGraphics = graphics; }
    public void draw(GuiGraphics graphics, BuiltLiquidGlass glass) { beginFrame(graphics); enqueue(glass); flush(); }
    public void enqueue(BuiltLiquidGlass glass) { submit(activeGraphics, glass); }
    public void flush() { activeGraphics = null; }
    public void beginGuiFrame() { prepared.clear(); paramsDirty = false; }
    public boolean isLiquidGlassPipeline(RenderPipeline pipeline) { return pipeline == LIQUID_GLASS_PIPELINE; }

    public void bindParams(RenderPass pass) {
        if (pass == null || prepared.isEmpty()) return;
        GpuBuffer buffer = ensureParamsBuffer();
        if (buffer != null) pass.setUniform("LiquidGlassParamsArray", buffer);
        LiquidGlassFramebuffer.getInstance().bindFrameInfo(pass);
    }

    public void prepareBuffers() {
        if (prepared.isEmpty() || !paramsDirty) return;
        GpuBuffer buffer = ensureWritableParamsBuffer();
        if (buffer == null) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildUniformData(stack, prepared);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, data.remaining()), data);
            paramsDirty = false;
        } catch (RuntimeException ignored) { paramsDirty = true; }
    }

    int reserve(BuiltLiquidGlass glass) {
        int index = prepared.size();
        if (index >= MAX_GLASSES) return -1;
        prepared.add(glass);
        paramsDirty = true;
        return index;
    }

    private void submit(GuiGraphics graphics, BuiltLiquidGlass glass) {
        if (graphics == null || glass == null || !glass.visible()) return;
        try {
            BuiltLiquidGlass normalized = normalize(glass);
            LiquidGlassFramebuffer.getInstance().requestCapture(normalized.blurChannel(), normalized.blurStrength());
            Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
            ((GuiGraphicsExtractorAccessor) graphics).universalmod$getGuiRenderState()
                    .submitGuiElement(new LiquidGlassRenderState(pose, normalized, ScissorUtil.current()));
        } catch (RuntimeException ignored) {}
    }

    private ByteBuffer buildUniformData(MemoryStack stack, List<BuiltLiquidGlass> batch) {
        int used = Math.max(1, batch.size()) * PARAMS_PER_GLASS * FLOATS_PER_PARAM * Float.BYTES;
        ByteBuffer data = stack.calloc(used);
        for (int i = 0; i < batch.size(); i++) {
            BuiltLiquidGlass g = batch.get(i);
            int o = i * PARAMS_PER_GLASS * FLOATS_PER_PARAM * Float.BYTES;
            
            data.putFloat(o, g.radiusTopLeft());
            data.putFloat(o + 4, g.radiusBottomLeft());
            data.putFloat(o + 8, g.radiusTopRight());
            data.putFloat(o + 12, g.radiusBottomRight());
            data.putFloat(o + 16, g.width());
            data.putFloat(o + 20, g.height());
            data.putFloat(o + 24, 0.5f); 
            data.putFloat(o + 28, g.squirt()); 
            data.putFloat(o + 32, g.globalAlpha());
            data.putFloat(o + 36, g.fresnelPower());
            data.putFloat(o + 40, g.baseAlpha());
            data.putFloat(o + 44, g.fresnelMix());
            putColor(data, o + 48, g.fresnelColor());
            data.putFloat(o + 64, g.fresnelInvert() ? 1.0f : 0.0f);
            data.putFloat(o + 68, g.distortStrength());
            data.putFloat(o + 72, g.z());
            data.putFloat(o + 76, 0.0f);
        }
        data.position(0);
        return data;
    }

    private BuiltLiquidGlass normalize(BuiltLiquidGlass g) {

        return new BuiltLiquidGlass(
                g.x(), g.y(), g.width(), g.height(),
                Math.max(0.0f, g.radiusTopLeft()), Math.max(0.0f, g.radiusTopRight()),
                Math.max(0.0f, g.radiusBottomRight()), Math.max(0.0f, g.radiusBottomLeft()),
                g.color(), clamp(g.globalAlpha(), 0, 1), Math.max(0.0f, g.fresnelPower()), g.fresnelColor(),
                clamp(g.baseAlpha(), 0, 1), g.fresnelInvert(), clamp(g.fresnelMix(), 0, 1),
                g.distortStrength(), Math.max(0.001f, g.squirt()), clamp(g.blurStrength(), 0, 8),
                g.blurChannel() == null ? LiquidGlassBlurChannel.THEME : g.blurChannel(), g.z()
        );
    }

    private GpuBuffer ensureParamsBuffer() {
        if (!paramsDirty && paramsBuffer != null) return paramsBuffer;
        prepareBuffers();
        if (!paramsDirty && paramsBuffer != null) return paramsBuffer;
        closeBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildUniformData(stack, prepared);
            paramsBuffer = RenderSystem.getDevice().createBuffer(() -> "UNIVERSALMOD_liquid_glass_params", GpuBuffer.USAGE_UNIFORM, data);
            paramsDirty = false;
            return paramsBuffer;
        }
    }

    private GpuBuffer ensureWritableParamsBuffer() {
        if (paramsBuffer != null && !paramsBuffer.isClosed() && paramsBuffer.size() >= UNIFORM_BYTES) return paramsBuffer;
        closeBuffer();
        try {
            paramsBuffer = RenderSystem.getDevice().createBuffer(() -> "UNIVERSALMOD_liquid_glass_params", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_BYTES);
            return paramsBuffer;
        } catch (RuntimeException ignored) { return null; }
    }

    private static void putColor(ByteBuffer data, int o, int color) {
        data.putFloat(o, ((color >>> 16) & 255) / 255.0f);
        data.putFloat(o + 4, ((color >>> 8) & 255) / 255.0f);
        data.putFloat(o + 8, (color & 255) / 255.0f);
        data.putFloat(o + 12, ((color >>> 24) & 255) / 255.0f);
    }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private void closeBuffer() { if (paramsBuffer != null) { paramsBuffer.close(); paramsBuffer = null; } }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("universalmod", path); }
    @Override public void close() { prepared.clear(); activeGraphics = null; closeBuffer(); }
}
