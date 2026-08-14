package universalmod.utils.render.ui.emotionwheel;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
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

public final class EmotionWheelArcRenderer implements AutoCloseable {
    private static final int MAX_ARCS = 64;
    private static final int PARAMS_PER_ARC = 11;
    private static final int FLOATS_PER_PARAM = 4;
    private static final int UNIFORM_BYTES = MAX_ARCS * PARAMS_PER_ARC * FLOATS_PER_PARAM * Float.BYTES;
    private static volatile EmotionWheelArcRenderer instance;

    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/emotion_wheel_arc"))
            .withVertexShader(id("ui/emotionwheel/arc_quad"))
            .withFragmentShader(id("ui/emotionwheel/arc"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("EmotionWheelArcParams", UniformType.UNIFORM_BUFFER)
            .build();

    private final List<BuiltEmotionWheelArc> prepared = new ArrayList<>(8);
    private GuiGraphics graphics;
    private GpuBuffer buffer;
    private boolean dirty = true;

    private EmotionWheelArcRenderer() {
    }

    public static void closeInstance() {
        EmotionWheelArcRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public static EmotionWheelArcRenderer getInstance() {
        EmotionWheelArcRenderer local = instance;
        if (local == null) {
            synchronized (EmotionWheelArcRenderer.class) {
                local = instance;
                if (local == null) {
                    instance = local = new EmotionWheelArcRenderer();
                }
            }
        }
        return local;
    }

    public void beginFrame(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public void flush() {
        graphics = null;
    }

    public void beginGuiFrame() {
        prepared.clear();
        dirty = false;
    }

    public void enqueue(BuiltEmotionWheelArc arc) {
        if (graphics == null || arc == null || !arc.visible()) {
            return;
        }
        BuiltEmotionWheelArc normalized = normalize(arc);
        Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
        ((GuiGraphicsExtractorAccessor) graphics).universalmod$getGuiRenderState()
                .submitGuiElement(new EmotionWheelArcRenderState(pose, normalized, ScissorUtil.current()));
    }

    int reserve(BuiltEmotionWheelArc arc) {
        int index = prepared.size();
        if (index >= MAX_ARCS) {
            return -1;
        }
        prepared.add(arc);
        dirty = true;
        return index;
    }

    public boolean isPipeline(RenderPipeline pipeline) {
        return pipeline == PIPELINE;
    }

    public void bindParams(RenderPass pass) {
        if (pass == null || prepared.isEmpty()) {
            return;
        }
        GpuBuffer current = ensureBuffer();
        if (current != null) {
            pass.setUniform("EmotionWheelArcParams", current);
        }
    }

    public void prepareBuffers() {
        if (prepared.isEmpty() || !dirty) {
            return;
        }
        GpuBuffer current = ensureWritableBuffer();
        if (current == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildUniformData(stack);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(current.slice(0, data.remaining()), data);
            dirty = false;
        } catch (RuntimeException ignored) {
            dirty = true;
        }
    }

    private GpuBuffer ensureBuffer() {
        if (!dirty && buffer != null) {
            return buffer;
        }
        prepareBuffers();
        if (!dirty && buffer != null) {
            return buffer;
        }
        closeBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildUniformData(stack);
            buffer = RenderSystem.getDevice().createBuffer(() -> "UNIVERSALMOD_emotion_wheel_arc_params", GpuBuffer.USAGE_UNIFORM, data);
            dirty = false;
            return buffer;
        }
    }

    private GpuBuffer ensureWritableBuffer() {
        if (buffer != null && !buffer.isClosed() && buffer.size() >= UNIFORM_BYTES) {
            return buffer;
        }
        closeBuffer();
        try {
            buffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_emotion_wheel_arc_params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            return buffer;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ByteBuffer buildUniformData(MemoryStack stack) {
        int usedBytes = Math.max(1, prepared.size()) * PARAMS_PER_ARC * FLOATS_PER_PARAM * Float.BYTES;
        ByteBuffer data = stack.calloc(usedBytes);
        for (int i = 0; i < prepared.size(); i++) {
            BuiltEmotionWheelArc arc = prepared.get(i);
            int offset = i * PARAMS_PER_ARC * FLOATS_PER_PARAM * Float.BYTES;
            data.putFloat(offset, arc.size());
            data.putFloat(offset + 4, arc.thickness());
            data.putFloat(offset + 8, arc.degree());
            data.putFloat(offset + 12, arc.rotation());
            data.putFloat(offset + 16, arc.blurRadius());
            int[] colors = arc.colors();
            for (int colorIndex = 0; colorIndex < 9; colorIndex++) {
                putColor(data, offset + (colorIndex + 2) * 16, colors[colorIndex]);
            }
        }
        data.position(0);
        return data;
    }

    private static BuiltEmotionWheelArc normalize(BuiltEmotionWheelArc arc) {
        float size = Math.max(0.0F, arc.size());
        return new BuiltEmotionWheelArc(
                arc.x(),
                arc.y(),
                size,
                Math.max(0.0F, Math.min(arc.thickness(), size)),
                Math.max(0.0F, Math.min(arc.degree(), 360.0F)),
                arc.rotation(),
                Math.max(0.0F, Math.min(arc.blurRadius(), size * 0.25F)),
                arc.colors()
        );
    }

    private static void putColor(ByteBuffer data, int offset, int color) {
        data.putFloat(offset, ((color >>> 16) & 0xFF) / 255.0F);
        data.putFloat(offset + 4, ((color >>> 8) & 0xFF) / 255.0F);
        data.putFloat(offset + 8, (color & 0xFF) / 255.0F);
        data.putFloat(offset + 12, ((color >>> 24) & 0xFF) / 255.0F);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    @Override
    public void close() {
        prepared.clear();
        graphics = null;
        closeBuffer();
    }

    private void closeBuffer() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }
}
