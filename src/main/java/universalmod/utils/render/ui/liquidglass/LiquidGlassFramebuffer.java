package universalmod.utils.render.ui.liquidglass;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import universalmod.screens.clickgui.ClickGui;
import universalmod.screens.clickgui.impl.ClickGuiWorldAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.post.fogblur.FogBlurRenderer;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class LiquidGlassFramebuffer implements AutoCloseable {
    private static final int KAWASE_BYTES = 16;
    private static final int FRAME_BYTES = 16;
    private static volatile LiquidGlassFramebuffer instance;

    public static final RenderPipeline KAWASE_DOWN = RenderPipeline.builder()
            .withLocation(id("pipeline/liquidglass_kawase_down"))
            .withVertexShader(id("ui/liquidglass/kawase"))
            .withFragmentShader(id("ui/liquidglass/kawase_down"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("KawaseParams", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline KAWASE_UP = RenderPipeline.builder()
            .withLocation(id("pipeline/liquidglass_kawase_up"))
            .withVertexShader(id("ui/liquidglass/kawase"))
            .withFragmentShader(id("ui/liquidglass/kawase_up"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("KawaseParams", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final Map<LiquidGlassBlurChannel, BlurSlot> slots = new EnumMap<>(LiquidGlassBlurChannel.class);
    private GpuBuffer fullscreenQuad;
    private GpuBuffer kawaseParams;
    private GpuBuffer frameInfo;
    private int frameWidth = 1;
    private int frameHeight = 1;
    private Object lastLevelIdentity;
    private RenderTarget lastMainTarget;

    private LiquidGlassFramebuffer() {
        for (LiquidGlassBlurChannel channel : LiquidGlassBlurChannel.values()) {
            slots.put(channel, new BlurSlot(channel));
        }
    }

    public static LiquidGlassFramebuffer getInstance() {
        LiquidGlassFramebuffer local = instance;
        if (local == null) {
            synchronized (LiquidGlassFramebuffer.class) {
                local = instance;
                if (local == null) instance = local = new LiquidGlassFramebuffer();
            }
        }
        return local;
    }

    public static void closeInstance() {
        LiquidGlassFramebuffer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void requestCapture(LiquidGlassBlurChannel channel, float blurStrength) {
        BlurSlot slot = slot(channel);
        slot.pendingBlur = clamp(blurStrength, 0.0f, 8.0f);
    }

    public void beginGuiFrame() {
        for (BlurSlot slot : slots.values()) {
            slot.captureRequested = false;
            slot.capturedThisFrame = false;
            slot.targetsResized = false;
            slot.textureSetup = TextureSetup.noTexture();
        }
    }

    public void preparePending() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            clearPendingRequests();
            return;
        }

        RenderTarget main = mc.getMainRenderTarget();
        if (main == null || main.getColorTextureView() == null || main.width <= 0 || main.height <= 0) {
            clearPendingRequests();
            return;
        }

        Object levelIdentity = mc.level;
        if (lastMainTarget != main || lastLevelIdentity != levelIdentity) {
            invalidateBackdropCaches();
            lastMainTarget = main;
            lastLevelIdentity = levelIdentity;
        }

        frameWidth = Math.max(main.width, 1);
        frameHeight = Math.max(main.height, 1);
        uploadFrameInfo();

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        for (BlurSlot slot : slots.values()) {
            if (slot.pendingBlur < 0.0f) {
                continue;
            }

            float requestedBlur = slot.pendingBlur;
            slot.pendingBlur = -1.0f;

            boolean blurChanged = Float.isNaN(slot.lastPreparedBlur)
                    || Math.abs(slot.lastPreparedBlur - requestedBlur) > 0.0001f;
            slot.lastPreparedBlur = requestedBlur;
            slot.blur = requestedBlur;
            computeLiquidSettings(slot, requestedBlur);

            int targetWidth = Math.max(Math.round(frameWidth * slot.effectiveDownscale), 1);
            int targetHeight = Math.max(Math.round(frameHeight * slot.effectiveDownscale), 1);
            slot.targetsResized = targetNeedsResize(slot.cache, targetWidth, targetHeight)
                    || targetNeedsResize(slot.buffer, targetWidth, targetHeight);

            String suffix = slot.channel.name().toLowerCase();
            slot.cache = ensureTarget(slot.cache, "UNIVERSALMOD_liquidglass_cache_" + suffix, targetWidth, targetHeight);
            slot.buffer = ensureTarget(slot.buffer, "UNIVERSALMOD_liquidglass_buffer_" + suffix, targetWidth, targetHeight);
            slot.textureSetup = slot.buffer != null && slot.buffer.getColorTextureView() != null
                    ? TextureSetup.singleTexture(slot.buffer.getColorTextureView(), sampler)
                    : TextureSetup.noTexture();
            slot.captureRequested = slot.buffer != null && slot.cache != null;

            if (blurChanged) {
                slot.lastCaptureTime = 0L;
            }
        }
    }

    public void prepareGuiDraw() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null || main.getColorTextureView() == null) return;

        GpuTextureView source = mc.screen instanceof ClickGui || ClickGuiWorldAnimation.isActive()
                ? main.getColorTextureView()
                : FogBlurRenderer.getGuiSourceTextureView(main.width, main.height);
        if (source == null) source = main.getColorTextureView();

        long now = System.currentTimeMillis();
        for (BlurSlot slot : slots.values()) {
            prepareGuiDraw(slot, source, main, now);
        }
    }

    private void prepareGuiDraw(BlurSlot slot, GpuTextureView source, RenderTarget main, long now) {
        if (!slot.captureRequested || slot.capturedThisFrame || slot.cache == null || slot.buffer == null) {
            return;
        }

        boolean resized = slot.targetsResized || frameWidth != main.width || frameHeight != main.height;
        if (!resized && slot.lastCaptureTime != 0L && now - slot.lastCaptureTime < 25L) {
            slot.capturedThisFrame = true;
            return;
        }

        renderLiquidChain(slot, source, main.width, main.height);
        slot.lastCaptureTime = now;
        slot.capturedThisFrame = true;
        slot.targetsResized = false;
    }

    public TextureSetup textureSetup(LiquidGlassBlurChannel channel) {
        return slot(channel).textureSetup;
    }

    public void bindFrameInfo(RenderPass pass) {
        if (pass == null) return;
        GpuBuffer info = ensureFrameInfo();
        if (info != null) pass.setUniform("LiquidGlassFrame", info);
    }

    private void computeLiquidSettings(BlurSlot slot, float blurOffset) {
        float shifted = blurOffset - 0.5f;
        if (shifted >= 0.0f) {
            slot.effectiveOffset = Math.max(0.005f, shifted);
            slot.effectiveDownscale = 0.5f;
            slot.steps = shifted > 5.0f ? 7 : (shifted > 3.0f ? 5 : 3);
        } else {
            float blend = clamp(blurOffset / 0.5f, 0.0f, 1.0f);
            slot.effectiveDownscale = 1.0f - 0.5f * blend;
            slot.effectiveOffset = 0.005f;
            slot.steps = 3;
        }
    }

    private void renderLiquidChain(BlurSlot slot, GpuTextureView mainSource, int mainWidth, int mainHeight) {
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        renderPass(KAWASE_DOWN, mainSource, mainWidth, mainHeight, slot.cache, sampler, slot.effectiveOffset);
        TextureTarget[] pingPong = { slot.cache, slot.buffer };

        for (int i = 1; i < slot.steps; i++) {
            int step = i & 1;
            int previous = step ^ 1;
            TextureTarget source = pingPong[previous];
            TextureTarget target = pingPong[step];
            renderPass(KAWASE_DOWN, source.getColorTextureView(), source.width, source.height, target, sampler, slot.effectiveOffset);
        }

        for (int i = 0; i < slot.steps; i++) {
            int step = i & 1;
            int next = step ^ 1;
            TextureTarget source = pingPong[step];
            TextureTarget target = pingPong[next];
            renderPass(KAWASE_UP, source.getColorTextureView(), source.width, source.height, target, sampler, slot.effectiveOffset);
        }
    }

    private void renderPass(
            RenderPipeline pipeline,
            GpuTextureView source,
            int sourceWidth,
            int sourceHeight,
            TextureTarget target,
            GpuSampler sampler,
            float effectiveOffset
    ) {
        if (source == null || target == null || target.getColorTextureView() == null) return;
        GpuBuffer quad = ensureFullscreenQuad();
        GpuBuffer uniform = ensureKawaseParams();
        if (quad == null || uniform == null) return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(KAWASE_BYTES);
            data.putFloat(0, 1.0f / Math.max(sourceWidth, 1));
            data.putFloat(4, 1.0f / Math.max(sourceHeight, 1));
            data.putFloat(8, effectiveOffset);
            data.putFloat(12, 0.0f);
            data.position(0);
            encoder.writeToBuffer(uniform.slice(0, KAWASE_BYTES), data);

            RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            GpuBuffer indices = indexBuffer.getBuffer(6);
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "UNIVERSALMOD_liquidglass_blur",
                    target.getColorTextureView(), OptionalInt.of(ColorUtil.TRANSPARENT), null, OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                pass.bindTexture("Sampler0", source, sampler);
                pass.setUniform("KawaseParams", uniform);
                pass.setVertexBuffer(0, quad);
                pass.setIndexBuffer(indices, indexBuffer.type());
                pass.drawIndexed(0, 0, 6, 1);
            }
        }
    }

    private void uploadFrameInfo() {
        GpuBuffer info = ensureFrameInfo();
        if (info == null) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(FRAME_BYTES);
            data.putFloat(0, frameWidth);
            data.putFloat(4, frameHeight);
            data.putFloat(8, 1.0f / Math.max(frameWidth, 1));
            data.putFloat(12, 1.0f / Math.max(frameHeight, 1));
            data.position(0);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(info.slice(0, FRAME_BYTES), data);
        }
    }

    private static boolean targetNeedsResize(TextureTarget target, int width, int height) {
        return target == null || target.width != width || target.height != height;
    }

    private TextureTarget ensureTarget(TextureTarget target, String name, int width, int height) {
        if (target == null) return new TextureTarget(name, width, height, false);
        if (target.width != width || target.height != height) target.resize(width, height);
        return target;
    }

    private GpuBuffer ensureFullscreenQuad() {
        if (fullscreenQuad != null && !fullscreenQuad.isClosed()) return fullscreenQuad;
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(4 * DefaultVertexFormat.POSITION.getVertexSize());
             MeshData mesh = buildFullscreenQuad(allocator)) {
            fullscreenQuad = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_liquidglass_fullscreen_quad", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            return fullscreenQuad;
        }
    }

    private MeshData buildFullscreenQuad(ByteBufferBuilder allocator) {
        BufferBuilder builder = new BufferBuilder(allocator, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(-1.0f, -1.0f, 0.0f);
        builder.addVertex(-1.0f, 1.0f, 0.0f);
        builder.addVertex(1.0f, 1.0f, 0.0f);
        builder.addVertex(1.0f, -1.0f, 0.0f);
        return builder.buildOrThrow();
    }

    private GpuBuffer ensureKawaseParams() {
        if (kawaseParams != null && !kawaseParams.isClosed() && kawaseParams.size() >= KAWASE_BYTES) return kawaseParams;
        if (kawaseParams != null) kawaseParams.close();
        try {
            kawaseParams = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_liquidglass_kawase_params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, KAWASE_BYTES);
            return kawaseParams;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private GpuBuffer ensureFrameInfo() {
        if (frameInfo != null && !frameInfo.isClosed() && frameInfo.size() >= FRAME_BYTES) return frameInfo;
        if (frameInfo != null) frameInfo.close();
        try {
            frameInfo = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_liquidglass_frame",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, FRAME_BYTES);
            return frameInfo;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private BlurSlot slot(LiquidGlassBlurChannel channel) {
        LiquidGlassBlurChannel safe = channel == null ? LiquidGlassBlurChannel.THEME : channel;
        return slots.get(safe);
    }

    private void clearPendingRequests() {
        for (BlurSlot slot : slots.values()) {
            slot.pendingBlur = -1.0f;
            slot.captureRequested = false;
            slot.textureSetup = TextureSetup.noTexture();
        }
    }

    private void invalidateBackdropCaches() {
        for (BlurSlot slot : slots.values()) {
            destroyTargets(slot);
            slot.textureSetup = TextureSetup.noTexture();
            slot.lastCaptureTime = 0L;
            slot.lastPreparedBlur = Float.NaN;
            slot.capturedThisFrame = false;
            slot.targetsResized = true;
        }
    }

    private static void destroyTargets(BlurSlot slot) {
        if (slot.cache != null) {
            slot.cache.destroyBuffers();
            slot.cache = null;
        }
        if (slot.buffer != null) {
            slot.buffer.destroyBuffers();
            slot.buffer = null;
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    @Override
    public void close() {
        for (BlurSlot slot : slots.values()) {
            slot.textureSetup = TextureSetup.noTexture();
            slot.pendingBlur = -1.0f;
            slot.captureRequested = false;
            destroyTargets(slot);
        }
        if (fullscreenQuad != null) {
            fullscreenQuad.close();
            fullscreenQuad = null;
        }
        if (kawaseParams != null) {
            kawaseParams.close();
            kawaseParams = null;
        }
        if (frameInfo != null) {
            frameInfo.close();
            frameInfo = null;
        }
        lastMainTarget = null;
        lastLevelIdentity = null;
    }

    private static final class BlurSlot {
        final LiquidGlassBlurChannel channel;
        TextureTarget cache;
        TextureTarget buffer;
        TextureSetup textureSetup = TextureSetup.noTexture();
        float pendingBlur = -1.0f;
        float blur = 0.5f;
        float lastPreparedBlur = Float.NaN;
        float effectiveOffset = 0.005f;
        float effectiveDownscale = 0.5f;
        int steps = 3;
        boolean captureRequested;
        boolean capturedThisFrame;
        boolean targetsResized;
        long lastCaptureTime;

        BlurSlot(LiquidGlassBlurChannel channel) {
            this.channel = channel;
        }
    }
}
