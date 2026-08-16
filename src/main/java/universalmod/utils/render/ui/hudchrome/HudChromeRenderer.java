package universalmod.utils.render.ui.hudchrome;

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

/** Dedicated GLSL skin used by every regular HUD card. */
public final class HudChromeRenderer implements AutoCloseable {
    private static final int MAX = 512;
    private static final int PARAMS_PER_PANEL = 3;
    private static final int UNIFORM_BYTES = MAX * PARAMS_PER_PANEL * 4 * Float.BYTES;
    private static volatile HudChromeRenderer instance;

    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/hud_chrome"))
            .withVertexShader(id("ui/hud_chrome/hud_chrome"))
            .withFragmentShader(id("ui/hud_chrome/hud_chrome"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("HudChromeParams", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final List<BuiltHudChrome> prepared = new ArrayList<>(96);
    private GuiGraphics activeGraphics;
    private GpuBuffer paramsBuffer;
    private boolean paramsDirty = true;

    private HudChromeRenderer() {
    }

    public static HudChromeRenderer getInstance() {
        HudChromeRenderer local = instance;
        if (local == null) {
            synchronized (HudChromeRenderer.class) {
                local = instance;
                if (local == null) {
                    instance = local = new HudChromeRenderer();
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        HudChromeRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginFrame(GuiGraphics graphics) {
        activeGraphics = graphics;
    }

    public void flush() {
        activeGraphics = null;
    }

    public void beginGuiFrame() {
        prepared.clear();
        paramsDirty = false;
    }

    public void draw(GuiGraphics graphics, BuiltHudChrome panel) {
        beginFrame(graphics);
        enqueue(panel);
        flush();
    }

    public void enqueue(BuiltHudChrome panel) {
        if (activeGraphics == null || panel == null || !panel.visible()) {
            return;
        }
        float maxRadius = Math.max(0.0f, Math.min(panel.width(), panel.height()) * 0.5f);
        BuiltHudChrome normalized = new BuiltHudChrome(
                panel.x(), panel.y(), panel.width(), panel.height(),
                clamp(panel.radiusTopLeft(), 0.0f, maxRadius),
                clamp(panel.radiusTopRight(), 0.0f, maxRadius),
                clamp(panel.radiusBottomRight(), 0.0f, maxRadius),
                clamp(panel.radiusBottomLeft(), 0.0f, maxRadius),
                clamp(panel.alpha(), 0.0f, 1.0f), Math.max(0.45f, panel.smoothness()),
                clamp(panel.darkness(), 0.0f, 1.0f)
        );
        Matrix3x2f pose = Render2DCoordinateSpace.pose(activeGraphics);
        ((GuiGraphicsExtractorAccessor) activeGraphics).universalmod$getGuiRenderState()
                .submitGuiElement(new HudChromeRenderState(pose, normalized, ScissorUtil.current()));
    }

    int reserve(BuiltHudChrome panel) {
        if (prepared.size() >= MAX) {
            return -1;
        }
        int index = prepared.size();
        prepared.add(panel);
        paramsDirty = true;
        return index;
    }

    public boolean isPipeline(RenderPipeline pipeline) {
        return pipeline == PIPELINE;
    }

    public void prepareBuffers() {
        if (prepared.isEmpty() || !paramsDirty) {
            return;
        }
        GpuBuffer buffer = ensureWritableBuffer();
        if (buffer == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildData(stack);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, data.remaining()), data);
            paramsDirty = false;
        } catch (RuntimeException ignored) {
            paramsDirty = true;
        }
    }

    public void bindParams(RenderPass pass) {
        if (pass == null || prepared.isEmpty()) {
            return;
        }
        GpuBuffer buffer = ensureBuffer();
        if (buffer != null) {
            pass.setUniform("HudChromeParams", buffer);
        }
    }

    private GpuBuffer ensureBuffer() {
        if (!paramsDirty && paramsBuffer != null && !paramsBuffer.isClosed()) {
            return paramsBuffer;
        }
        prepareBuffers();
        if (!paramsDirty && paramsBuffer != null && !paramsBuffer.isClosed()) {
            return paramsBuffer;
        }
        closeBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildData(stack);
            paramsBuffer = RenderSystem.getDevice().createBuffer(() -> "UNIVERSALMOD_hud_chrome_params", GpuBuffer.USAGE_UNIFORM, data);
            paramsDirty = false;
            return paramsBuffer;
        }
    }

    private GpuBuffer ensureWritableBuffer() {
        if (paramsBuffer != null && !paramsBuffer.isClosed() && paramsBuffer.size() >= UNIFORM_BYTES) {
            return paramsBuffer;
        }
        closeBuffer();
        try {
            paramsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_hud_chrome_params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            return paramsBuffer;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ByteBuffer buildData(MemoryStack stack) {
        ByteBuffer data = stack.calloc(Math.max(1, prepared.size()) * PARAMS_PER_PANEL * 4 * Float.BYTES);
        for (int i = 0; i < prepared.size(); i++) {
            BuiltHudChrome panel = prepared.get(i);
            int offset = i * PARAMS_PER_PANEL * 4 * Float.BYTES;
            data.putFloat(offset, panel.radiusTopLeft());
            data.putFloat(offset + 4, panel.radiusTopRight());
            data.putFloat(offset + 8, panel.radiusBottomRight());
            data.putFloat(offset + 12, panel.radiusBottomLeft());
            data.putFloat(offset + 16, panel.width());
            data.putFloat(offset + 20, panel.height());
            data.putFloat(offset + 24, panel.alpha());
            data.putFloat(offset + 28, panel.smoothness());
            data.putFloat(offset + 32, panel.darkness());
        }
        data.position(0);
        return data;
    }

    private void closeBuffer() {
        if (paramsBuffer != null) {
            paramsBuffer.close();
            paramsBuffer = null;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    @Override
    public void close() {
        prepared.clear();
        activeGraphics = null;
        closeBuffer();
    }
}
