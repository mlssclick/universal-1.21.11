package universalmod.utils.render.ui.blur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
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
import universalmod.mixin.accessor.GuiGraphicsExtractorAccessor;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2DCoordinateSpace;
import universalmod.utils.render.ScissorUtil;
import universalmod.utils.render.post.fogblur.FogBlurRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;
import universalmod.screens.clickgui.ClickGui;
import universalmod.screens.clickgui.impl.ClickGuiWorldAnimation;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class BlurFramebuffer implements AutoCloseable {
    private static final int MAX_BLUR_RECTS = 1024;
    private static final int MAX_BLUR_ITERATIONS = 4;
    private static final int PARAMS_PER_RECT = 3;
    private static final int FLOATS_PER_PARAM = 4;
    private static final int UNIFORM_BYTES = MAX_BLUR_RECTS * PARAMS_PER_RECT * FLOATS_PER_PARAM * Float.BYTES;
    private static final int REGION_UNIFORM_BYTES = 4 * Float.BYTES;
    private static final int KAWASE_UNIFORM_BYTES = 12 * Float.BYTES;
    private static final int SCOREBOARD_CROP_UNIFORM_BYTES = 4 * Float.BYTES;
    private static final int SCOREBOARD_SAMPLER_INFO_BYTES = 4 * Float.BYTES;
    private static final int SCOREBOARD_BLUR_CONFIG_BYTES = 4 * Float.BYTES;
    private static final float MAX_BLUR_RENDER_SCALE = 0.5f;
    private static final float MEDIUM_BLUR_RENDER_SCALE = 0.44f;
    private static final float MIN_BLUR_RENDER_SCALE = 0.4f;

    private static volatile BlurFramebuffer instance;
    private static float skyFallbackRed = 0.55f;
    private static float skyFallbackGreen = 0.65f;
    private static float skyFallbackBlue = 0.78f;

    public static final RenderPipeline BATCHED_BLUR_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/batched_blur"))
            .withVertexShader(id("ui/batched_blur/batched_blur"))
            .withFragmentShader(id("ui/batched_blur/batched_blur"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("DynamicTransforms", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("BlurParamsArray", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("BlurRegion", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline KAWASE_DOWN_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/kawase_down"))
            .withVertexShader(id("ui/kawase/down"))
            .withFragmentShader(id("ui/kawase/down"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("KawaseParams", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline KAWASE_UP_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/kawase_up"))
            .withVertexShader(id("ui/kawase/down"))
            .withFragmentShader(id("ui/kawase/up"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("Sampler0")
            .withUniform("KawaseParams", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline SCOREBOARD_CROP_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/scoreboard_blur_crop"))
            .withVertexShader(id("ui/scoreboard_blur/scoreboard_blur"))
            .withFragmentShader(id("ui/scoreboard_blur/crop"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("InSampler")
            .withUniform("CropConfig", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline SCOREBOARD_SEPARABLE_PIPELINE = RenderPipeline.builder()
            .withLocation(id("pipeline/scoreboard_separable_blur"))
            .withVertexShader(id("ui/scoreboard_blur/scoreboard_blur"))
            .withFragmentShader(id("ui/scoreboard_blur/scoreboard_blur"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withSampler("InSampler")
            .withUniform("SamplerInfo", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("BlurConfig", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .build();

    private final List<PendingBlur> pendingBlurs = new ArrayList<>(32);
    private final List<BuiltBlur> preparedBlurs = new ArrayList<>(32);
    private GuiGraphics activeGraphics;
    private TextureTarget blurTarget;
    private TextureTarget scoreboardSourceTarget;
    private TextureTarget scoreboardHorizontalTarget;
    private TextureTarget scoreboardBlurTarget;
    private final TextureTarget[] downTargets = new TextureTarget[MAX_BLUR_ITERATIONS];
    private final TextureTarget[] upTargets = new TextureTarget[MAX_BLUR_ITERATIONS];
    private GpuBuffer fullscreenQuadBuffer;
    private GpuBuffer paramsBuffer;
    private GpuBuffer regionBuffer;
    private GpuBuffer kawaseParamsBuffer;
    private GpuBuffer scoreboardCropConfigBuffer;
    private GpuBuffer scoreboardSamplerInfoBuffer;
    private GpuBuffer scoreboardBlurConfigBuffer;
    private TextureSetup blurTextureSetup = TextureSetup.noTexture();
    private TextureSetup scoreboardBlurTextureSetup = TextureSetup.noTexture();
    private boolean captureRequested;
    private boolean kawaseCaptureRequested;
    private boolean scoreboardCaptureRequested;
    private boolean paramsDirty = true;
    private boolean regionDirty = true;
    private int regionX;
    private int regionY;
    private int regionWidth;
    private int regionHeight;
    private int preparedIterations;
    private float maxBlurRadius;
    private float maxKawaseBlurRadius;
    private float maxScoreboardBlurRadius;
    private BlurFramebuffer() {
    }

    public static BlurFramebuffer getInstance() {
        BlurFramebuffer local = instance;
        if (local == null) {
            synchronized (BlurFramebuffer.class) {
                local = instance;
                if (local == null) {
                    local = new BlurFramebuffer();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        BlurFramebuffer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public static void setSkyFallbackColor(float red, float green, float blue) {
        if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue)) {
            return;
        }

        skyFallbackRed = clamp(red, 0.0f, 1.0f);
        skyFallbackGreen = clamp(green, 0.0f, 1.0f);
        skyFallbackBlue = clamp(blue, 0.0f, 1.0f);
    }

    public void beginFrame(GuiGraphics graphics) {
        activeGraphics = graphics;
    }

    public void draw(GuiGraphics graphics, BuiltBlur blur) {
        beginFrame(graphics);
        enqueue(blur);
        flush();
    }

    public void enqueue(BuiltBlur blur) {
        submit(activeGraphics, blur);
    }

    public void flush() {
        activeGraphics = null;
    }

    public void beginGuiFrame() {
        preparedBlurs.clear();
        captureRequested = false;
        kawaseCaptureRequested = false;
        scoreboardCaptureRequested = false;
        paramsDirty = false;
        regionDirty = false;
        regionWidth = 0;
        regionHeight = 0;
        preparedIterations = 0;
        maxBlurRadius = 0.0f;
        maxKawaseBlurRadius = 0.0f;
        maxScoreboardBlurRadius = 0.0f;
        blurTextureSetup = TextureSetup.noTexture();
        scoreboardBlurTextureSetup = TextureSetup.noTexture();
    }

    public void preparePending() {
        if (pendingBlurs.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            pendingBlurs.clear();
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null || mainTarget.getColorTexture() == null || !computeBlurRegion(minecraft, mainTarget)) {
            pendingBlurs.clear();
            return;
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        if (kawaseCaptureRequested) {
            preparedIterations = computeIterations(maxKawaseBlurRadius);
            ensureBlurTargets(
                    regionWidth,
                    regionHeight,
                    computeRenderScale(maxKawaseBlurRadius, regionWidth, regionHeight),
                    preparedIterations
            );
            if (blurTarget != null && blurTarget.getColorTextureView() != null) {
                blurTextureSetup = TextureSetup.singleTexture(blurTarget.getColorTextureView(), sampler);
            } else {
                kawaseCaptureRequested = false;
            }
        }

        if (scoreboardCaptureRequested) {
            ensureScoreboardBlurTargets(regionWidth, regionHeight);
            if (scoreboardBlurTarget != null && scoreboardBlurTarget.getColorTextureView() != null) {
                scoreboardBlurTextureSetup = TextureSetup.singleTexture(scoreboardBlurTarget.getColorTextureView(), sampler);
            } else {
                scoreboardCaptureRequested = false;
            }
        }

        captureRequested = kawaseCaptureRequested || scoreboardCaptureRequested;
        regionDirty = true;
        uploadRegionBuffer();
        pendingBlurs.clear();
    }

    public TextureSetup textureSetup(BlurAlgorithm algorithm) {
        return algorithm == BlurAlgorithm.SCOREBOARD_SEPARABLE
                ? scoreboardBlurTextureSetup
                : blurTextureSetup;
    }

    public TextureSetup textureSetup() {
        return blurTextureSetup;
    }

    public void requestCapture(GuiGraphics graphics, BuiltBlur blur) {
        if (graphics == null || blur == null || !blur.visible()) {
            return;
        }

        try {
            BuiltBlur normalized = normalize(blur);
            Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
            pendingBlurs.add(new PendingBlur(normalized, pose, ScissorUtil.current()));
        } catch (RuntimeException ignored) {
        }
    }

    public void prepareGuiDraw() {
        if (!captureRequested || regionWidth <= 0 || regionHeight <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null || mainTarget.getColorTextureView() == null) {
            return;
        }

        GpuTextureView guiSource = shouldUseMainTargetSource(minecraft)
                ? null
                : FogBlurRenderer.getGuiSourceTextureView(mainTarget.width, mainTarget.height);
        if (kawaseCaptureRequested) {
            renderBlurChain(mainTarget, guiSource);
        }
        if (scoreboardCaptureRequested) {
            renderScoreboardSeparableBlur(mainTarget, guiSource);
        }
    }

    private boolean shouldUseMainTargetSource(Minecraft minecraft) {
        return minecraft.screen instanceof ClickGui || ClickGuiWorldAnimation.isActive();
    }

    public boolean isBlurPipeline(RenderPipeline pipeline) {
        return pipeline == BATCHED_BLUR_PIPELINE;
    }

    public void bindBlurParams(RenderPass renderPass) {
        if (renderPass == null || preparedBlurs.isEmpty()) {
            return;
        }

        GpuBuffer buffer = ensureParamsBuffer();
        if (buffer != null) {
            renderPass.setUniform("BlurParamsArray", buffer);
        }
        GpuBuffer region = ensureRegionBuffer();
        if (region != null) {
            renderPass.setUniform("BlurRegion", region);
        }
    }

    public void bindBlurRegion(RenderPass renderPass) {
        if (renderPass == null) {
            return;
        }

        GpuBuffer region = ensureRegionBuffer();
        if (region != null) {
            renderPass.setUniform("BlurRegion", region);
        }
    }

    int reserve(BuiltBlur blur) {
        int index = preparedBlurs.size();
        if (index == MAX_BLUR_RECTS) {
            return -1;
        }

        preparedBlurs.add(blur);
        paramsDirty = true;
        maxBlurRadius = Math.max(maxBlurRadius, blur.blurRadius());
        return index;
    }

    private void submit(GuiGraphics graphics, BuiltBlur blur) {
        if (blur == null || !blur.visible()) {
            return;
        }

        if (graphics == null) {
            return;
        }

        try {
            BuiltBlur normalized = normalize(blur);
            Matrix3x2f pose = Render2DCoordinateSpace.pose(graphics);
            ScreenRectangle scissorArea = ScissorUtil.current();
            pendingBlurs.add(new PendingBlur(normalized, pose, scissorArea));
            ((GuiGraphicsExtractorAccessor) graphics).universalmod$getGuiRenderState().submitGuiElement(new BlurRenderState(pose, normalized, scissorArea));
        } catch (RuntimeException ignored) {
        }
    }

    private GpuBuffer ensureParamsBuffer() {
        if (paramsDirty) {
            prepareBuffers();
        }

        if (!paramsDirty && paramsBuffer != null) {
            return paramsBuffer;
        }

        if (paramsDirty) {
            closeParamsBuffer();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer uniformData = buildUniformData(stack, preparedBlurs);
                paramsBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "UNIVERSALMOD_blur_params",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        uniformData
                );
            }
            paramsDirty = false;
            return paramsBuffer;
        }

        if (paramsBuffer == null || paramsBuffer.isClosed() || paramsBuffer.size() < UNIFORM_BYTES) {
            closeParamsBuffer();
            paramsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_blur_params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
        }

        return paramsBuffer;
    }

    public void prepareBuffers() {
        if (preparedBlurs.isEmpty() || !paramsDirty) {
            return;
        }

        GpuBuffer buffer = ensureWritableParamsBuffer();
        if (buffer == null) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = buildUniformData(stack, preparedBlurs);
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(buffer.slice(0, uniformData.remaining()), uniformData);
            paramsDirty = false;
        } catch (RuntimeException ignored) {
            paramsDirty = true;
        }
    }

    private GpuBuffer ensureWritableParamsBuffer() {
        if (paramsBuffer != null && !paramsBuffer.isClosed() && paramsBuffer.size() >= UNIFORM_BYTES) {
            return paramsBuffer;
        }

        closeParamsBuffer();

        try {
            paramsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_blur_params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            return paramsBuffer;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ByteBuffer buildUniformData(MemoryStack stack, List<BuiltBlur> batch) {
        int usedBytes = Math.max(1, batch.size()) * PARAMS_PER_RECT * FLOATS_PER_PARAM * Float.BYTES;
        ByteBuffer data = stack.calloc(usedBytes);

        for (int i = 0; i < batch.size(); i++) {
            BuiltBlur blur = batch.get(i);
            int offset = i * PARAMS_PER_RECT * FLOATS_PER_PARAM * Float.BYTES;

            data.putFloat(offset, blur.radiusTopLeft());
            data.putFloat(offset + 4, blur.radiusTopRight());
            data.putFloat(offset + 8, blur.radiusBottomRight());
            data.putFloat(offset + 12, blur.radiusBottomLeft());
            data.putFloat(offset + 16, blur.width());
            data.putFloat(offset + 20, blur.height());
            data.putFloat(offset + 24, blur.smoothness());
            data.putFloat(offset + 28, blur.verticalSplit());

            putColor(data, offset + 32, blur.splitColor());
        }

        data.position(0);
        return data;
    }

    private static void putColor(ByteBuffer data, int offset, int color) {
        data.putFloat(offset, ((color >>> 16) & 0xFF) / 255.0f);
        data.putFloat(offset + 4, ((color >>> 8) & 0xFF) / 255.0f);
        data.putFloat(offset + 8, (color & 0xFF) / 255.0f);
        data.putFloat(offset + 12, ((color >>> 24) & 0xFF) / 255.0f);
    }

    private void ensureScoreboardBlurTargets(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        scoreboardSourceTarget = ensureTarget(
                scoreboardSourceTarget,
                "UNIVERSALMOD_scoreboard_blur_source",
                width,
                height
        );
        scoreboardHorizontalTarget = ensureTarget(
                scoreboardHorizontalTarget,
                "UNIVERSALMOD_scoreboard_blur_horizontal",
                width,
                height
        );
        scoreboardBlurTarget = ensureTarget(
                scoreboardBlurTarget,
                "UNIVERSALMOD_scoreboard_blur_result",
                width,
                height
        );
    }

    private void ensureBlurTargets(int width, int height, float renderScale, int iterations) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int resultWidth = Math.max(Math.round(width * renderScale), 1);
        int resultHeight = Math.max(Math.round(height * renderScale), 1);

        if (blurTarget == null) {
            blurTarget = new TextureTarget("UNIVERSALMOD_blur_result", resultWidth, resultHeight, false);
        } else if (blurTarget.width != resultWidth || blurTarget.height != resultHeight) {
            blurTarget.resize(resultWidth, resultHeight);
        }

        int targetWidth = resultWidth;
        int targetHeight = resultHeight;
        for (int i = 0; i < iterations; i++) {
            downTargets[i] = ensureTarget(downTargets[i], "UNIVERSALMOD_blur_down_" + i, targetWidth, targetHeight);
            if (i < iterations - 1) {
                upTargets[i] = ensureTarget(upTargets[i], "UNIVERSALMOD_blur_up_" + i, targetWidth, targetHeight);
            }
            targetWidth = Math.max(targetWidth / 2, 1);
            targetHeight = Math.max(targetHeight / 2, 1);
        }
    }

    private TextureTarget ensureTarget(TextureTarget target, String name, int width, int height) {
        if (target == null) {
            return new TextureTarget(name, width, height, false);
        }

        if (target.width != width || target.height != height) {
            target.resize(width, height);
        }

        return target;
    }

    private boolean computeBlurRegion(Minecraft minecraft, RenderTarget mainTarget) {
        Window window = minecraft.getWindow();
        int guiWidth = Math.max(window.getGuiScaledWidth(), 1);
        int guiHeight = Math.max(window.getGuiScaledHeight(), 1);
        float scaleX = (float) mainTarget.width / (float) guiWidth;
        float scaleY = (float) mainTarget.height / (float) guiHeight;

        float minX = guiWidth;
        float minY = guiHeight;
        float maxX = 0.0f;
        float maxY = 0.0f;
        maxBlurRadius = 0.0f;
        maxKawaseBlurRadius = 0.0f;
        maxScoreboardBlurRadius = 0.0f;
        kawaseCaptureRequested = false;
        scoreboardCaptureRequested = false;
        boolean hasBounds = false;

        for (PendingBlur pendingBlur : pendingBlurs) {
            BuiltBlur blur = pendingBlur.blur();
            float padding = Render2DCoordinateSpace.toGui(Math.max(16.0f, blur.blurRadius() * 2.0f));
            ScreenRectangle bounds = new ScreenRectangle(
                    Math.round(blur.x()),
                    Math.round(blur.y()),
                    Math.round(blur.width()),
                    Math.round(blur.height())
            ).transformMaxBounds(pendingBlur.pose());
            if (pendingBlur.scissorArea() != null) {
                bounds = pendingBlur.scissorArea().intersection(bounds);
                if (bounds == null) {
                    continue;
                }
            }

            minX = Math.min(minX, bounds.left() - padding);
            minY = Math.min(minY, bounds.top() - padding);
            maxX = Math.max(maxX, bounds.right() + padding);
            maxY = Math.max(maxY, bounds.bottom() + padding);
            maxBlurRadius = Math.max(maxBlurRadius, blur.blurRadius());
            if (blur.algorithm() == BlurAlgorithm.SCOREBOARD_SEPARABLE) {
                scoreboardCaptureRequested = true;
                maxScoreboardBlurRadius = Math.max(maxScoreboardBlurRadius, blur.blurRadius());
            } else {
                kawaseCaptureRequested = true;
                maxKawaseBlurRadius = Math.max(maxKawaseBlurRadius, blur.blurRadius());
            }
            hasBounds = true;
        }
        if (!hasBounds) {
            return false;
        }

        minX = clamp(minX, 0.0f, guiWidth);
        minY = clamp(minY, 0.0f, guiHeight);
        maxX = clamp(maxX, minX + 1.0f, guiWidth);
        maxY = clamp(maxY, minY + 1.0f, guiHeight);

        int x0 = clampInt((int) Math.floor(minX * scaleX), 0, mainTarget.width - 1);
        int x1 = clampInt((int) Math.ceil(maxX * scaleX), x0 + 1, mainTarget.width);
        int y0 = clampInt((int) Math.floor((guiHeight - maxY) * scaleY), 0, mainTarget.height - 1);
        int y1 = clampInt((int) Math.ceil((guiHeight - minY) * scaleY), y0 + 1, mainTarget.height);

        regionX = x0;
        regionY = y0;
        regionWidth = Math.max(x1 - x0, 1);
        regionHeight = Math.max(y1 - y0, 1);
        return regionWidth > 0 && regionHeight > 0;
    }

    private void renderBlurChain(RenderTarget mainTarget, GpuTextureView sourceOverride) {
        if (blurTarget == null) {
            return;
        }

        int iterations = preparedIterations > 0 ? preparedIterations : computeIterations(maxKawaseBlurRadius);
        float offsetScale = clamp(maxKawaseBlurRadius / 18.0f, 0.85f, 2.0f);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuTextureView source = sourceOverride != null ? sourceOverride : mainTarget.getColorTextureView();
        int sourceWidth = mainTarget.width;
        int sourceHeight = mainTarget.height;
        float sourceX = (float) regionX / Math.max(mainTarget.width, 1);
        float sourceY = (float) regionY / Math.max(mainTarget.height, 1);
        float sourceW = (float) regionWidth / Math.max(mainTarget.width, 1);
        float sourceH = (float) regionHeight / Math.max(mainTarget.height, 1);

        for (int i = 0; i < iterations; i++) {
            TextureTarget target = downTargets[i];
            renderKawasePass(KAWASE_DOWN_PIPELINE, source, sourceWidth, sourceHeight, target, offsetScale, sourceX, sourceY, sourceW, sourceH, sampler);
            source = target.getColorTextureView();
            sourceWidth = target.width;
            sourceHeight = target.height;
            sourceX = 0.0f;
            sourceY = 0.0f;
            sourceW = 1.0f;
            sourceH = 1.0f;
        }

        for (int i = iterations - 2; i >= 0; i--) {
            TextureTarget target = upTargets[i];
            renderKawasePass(KAWASE_UP_PIPELINE, source, sourceWidth, sourceHeight, target, offsetScale, 0.0f, 0.0f, 1.0f, 1.0f, sampler);
            source = target.getColorTextureView();
            sourceWidth = target.width;
            sourceHeight = target.height;
        }

        renderKawasePass(KAWASE_UP_PIPELINE, source, sourceWidth, sourceHeight, blurTarget, offsetScale, 0.0f, 0.0f, 1.0f, 1.0f, sampler);
    }

    private void renderScoreboardSeparableBlur(RenderTarget mainTarget, GpuTextureView sourceOverride) {
        if (scoreboardSourceTarget == null
                || scoreboardHorizontalTarget == null
                || scoreboardBlurTarget == null) {
            return;
        }

        GpuTextureView source = sourceOverride != null ? sourceOverride : mainTarget.getColorTextureView();
        if (source == null) {
            return;
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        float sourceX = (float) regionX / Math.max(mainTarget.width, 1);
        float sourceY = (float) regionY / Math.max(mainTarget.height, 1);
        float sourceW = (float) regionWidth / Math.max(mainTarget.width, 1);
        float sourceH = (float) regionHeight / Math.max(mainTarget.height, 1);

        renderScoreboardCropPass(
                source,
                scoreboardSourceTarget,
                sourceX,
                sourceY,
                sourceW,
                sourceH,
                sampler
        );

        float radius = clamp(maxScoreboardBlurRadius, 1.0f, 32.0f);
        renderScoreboardDirectionalPass(
                scoreboardSourceTarget.getColorTextureView(),
                scoreboardSourceTarget.width,
                scoreboardSourceTarget.height,
                scoreboardHorizontalTarget,
                1.0f,
                0.0f,
                radius,
                sampler
        );
        renderScoreboardDirectionalPass(
                scoreboardHorizontalTarget.getColorTextureView(),
                scoreboardHorizontalTarget.width,
                scoreboardHorizontalTarget.height,
                scoreboardBlurTarget,
                0.0f,
                1.0f,
                radius,
                sampler
        );
    }

    private void renderScoreboardCropPass(
            GpuTextureView source,
            TextureTarget target,
            float sourceX,
            float sourceY,
            float sourceW,
            float sourceH,
            GpuSampler sampler
    ) {
        if (source == null || target == null || target.getColorTextureView() == null) {
            return;
        }

        GpuBuffer fullscreenQuad = ensureFullscreenQuadBuffer();
        GpuBuffer configBuffer = ensureScoreboardCropConfigBuffer();
        if (fullscreenQuad == null || configBuffer == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(6);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer config = stack.calloc(SCOREBOARD_CROP_UNIFORM_BYTES);
            config.putFloat(0, sourceX);
            config.putFloat(4, sourceY);
            config.putFloat(8, sourceW);
            config.putFloat(12, sourceH);
            config.position(0);
            encoder.writeToBuffer(configBuffer.slice(0, SCOREBOARD_CROP_UNIFORM_BYTES), config);

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "UNIVERSALMOD_scoreboard_blur_crop",
                    target.getColorTextureView(),
                    OptionalInt.of(ColorUtil.TRANSPARENT),
                    null,
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(SCOREBOARD_CROP_PIPELINE);
                pass.bindTexture("InSampler", source, sampler);
                pass.setUniform("CropConfig", configBuffer);
                pass.setVertexBuffer(0, fullscreenQuad);
                pass.setIndexBuffer(indices, indexBuffer.type());
                pass.drawIndexed(0, 0, 6, 1);
            }
        }
    }

    private void renderScoreboardDirectionalPass(
            GpuTextureView source,
            int sourceWidth,
            int sourceHeight,
            TextureTarget target,
            float directionX,
            float directionY,
            float radius,
            GpuSampler sampler
    ) {
        if (source == null || target == null || target.getColorTextureView() == null) {
            return;
        }

        GpuBuffer fullscreenQuad = ensureFullscreenQuadBuffer();
        GpuBuffer samplerInfoBuffer = ensureScoreboardSamplerInfoBuffer();
        GpuBuffer blurConfigBuffer = ensureScoreboardBlurConfigBuffer();
        if (fullscreenQuad == null || samplerInfoBuffer == null || blurConfigBuffer == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(6);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer samplerInfo = stack.calloc(SCOREBOARD_SAMPLER_INFO_BYTES);
            samplerInfo.putFloat(0, target.width);
            samplerInfo.putFloat(4, target.height);
            samplerInfo.putFloat(8, Math.max(sourceWidth, 1));
            samplerInfo.putFloat(12, Math.max(sourceHeight, 1));
            samplerInfo.position(0);

            ByteBuffer blurConfig = stack.calloc(SCOREBOARD_BLUR_CONFIG_BYTES);
            blurConfig.putFloat(0, directionX);
            blurConfig.putFloat(4, directionY);
            blurConfig.putFloat(8, radius);
            blurConfig.putFloat(12, 0.0f);
            blurConfig.position(0);

            encoder.writeToBuffer(
                    samplerInfoBuffer.slice(0, SCOREBOARD_SAMPLER_INFO_BYTES),
                    samplerInfo
            );
            encoder.writeToBuffer(
                    blurConfigBuffer.slice(0, SCOREBOARD_BLUR_CONFIG_BYTES),
                    blurConfig
            );

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "UNIVERSALMOD_scoreboard_directional_blur",
                    target.getColorTextureView(),
                    OptionalInt.of(ColorUtil.TRANSPARENT),
                    null,
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(SCOREBOARD_SEPARABLE_PIPELINE);
                pass.bindTexture("InSampler", source, sampler);
                pass.setUniform("SamplerInfo", samplerInfoBuffer);
                pass.setUniform("BlurConfig", blurConfigBuffer);
                pass.setVertexBuffer(0, fullscreenQuad);
                pass.setIndexBuffer(indices, indexBuffer.type());
                pass.drawIndexed(0, 0, 6, 1);
            }
        }
    }

    private int computeIterations(float blurRadius) {
        if (blurRadius >= 52.0f) {
            return 4;
        }
        if (blurRadius >= 36.0f) {
            return 3;
        }
        return 2;
    }

    private float computeRenderScale(float blurRadius, int width, int height) {
        int area = Math.max(width, 1) * Math.max(height, 1);
        if (blurRadius >= 30.0f || area >= 220_000) {
            return MIN_BLUR_RENDER_SCALE;
        }
        if (blurRadius >= 18.0f || area >= 120_000) {
            return MEDIUM_BLUR_RENDER_SCALE;
        }
        return MAX_BLUR_RENDER_SCALE;
    }

    private void renderKawasePass(
            RenderPipeline pipeline,
            GpuTextureView source,
            int sourceWidth,
            int sourceHeight,
            TextureTarget target,
            float offsetScale,
            float sourceX,
            float sourceY,
            float sourceW,
            float sourceH,
            GpuSampler sampler
    ) {
        if (source == null || target == null) {
            return;
        }

        GpuBuffer fullscreenQuad = ensureFullscreenQuadBuffer();
        if (fullscreenQuad == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(6);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uniformData = stack.calloc(KAWASE_UNIFORM_BYTES);
            uniformData.putFloat(0, sourceX);
            uniformData.putFloat(4, sourceY);
            uniformData.putFloat(8, sourceW);
            uniformData.putFloat(12, sourceH);
            uniformData.putFloat(16, offsetScale / Math.max(sourceWidth, 1));
            uniformData.putFloat(20, offsetScale / Math.max(sourceHeight, 1));
            uniformData.putFloat(32, skyFallbackRed);
            uniformData.putFloat(36, skyFallbackGreen);
            uniformData.putFloat(40, skyFallbackBlue);
            uniformData.putFloat(44, 1.0f);
            uniformData.position(0);

            GpuBuffer uniformBuffer = ensureKawaseParamsBuffer();
            if (uniformBuffer == null) {
                return;
            }

            encoder.writeToBuffer(uniformBuffer.slice(0, KAWASE_UNIFORM_BYTES), uniformData);

            try (RenderPass renderPass = encoder.createRenderPass(
                         () -> "UNIVERSALMOD_kawase_blur",
                         target.getColorTextureView(),
                         OptionalInt.of(ColorUtil.TRANSPARENT),
                         null,
                         OptionalDouble.empty()
                 )) {
                renderPass.setPipeline(pipeline);
                renderPass.bindTexture("Sampler0", source, sampler);
                renderPass.setUniform("KawaseParams", uniformBuffer);
                renderPass.setVertexBuffer(0, fullscreenQuad);
                renderPass.setIndexBuffer(indices, indexBuffer.type());
                renderPass.drawIndexed(0, 0, 6, 1);
            }
        }
    }

    private GpuBuffer ensureRegionBuffer() {
        if (regionDirty) {
            closeRegionBuffer();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = buildRegionData(stack);
                regionBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "UNIVERSALMOD_blur_region",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        data
                );
            }
            regionDirty = false;
            return regionBuffer;
        }

        if (regionBuffer == null || regionBuffer.isClosed() || regionBuffer.size() < REGION_UNIFORM_BYTES) {
            closeRegionBuffer();
            regionBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_blur_region",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    REGION_UNIFORM_BYTES
            );
        }

        return regionBuffer;
    }

    private void uploadRegionBuffer() {
        GpuBuffer buffer = ensureWritableRegionBuffer();
        if (buffer == null) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = buildRegionData(stack);
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(buffer.slice(0, REGION_UNIFORM_BYTES), data);
            regionDirty = false;
        } catch (RuntimeException ignored) {
        }
    }

    private GpuBuffer ensureWritableRegionBuffer() {
        if (regionBuffer != null && !regionBuffer.isClosed() && regionBuffer.size() >= REGION_UNIFORM_BYTES) {
            return regionBuffer;
        }

        closeRegionBuffer();

        try {
            regionBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_blur_region",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    REGION_UNIFORM_BYTES
            );
            return regionBuffer;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private ByteBuffer buildRegionData(MemoryStack stack) {
        ByteBuffer data = stack.calloc(REGION_UNIFORM_BYTES);
        data.putFloat(0, regionX);
        data.putFloat(4, regionY);
        data.putFloat(8, regionWidth);
        data.putFloat(12, regionHeight);
        data.position(0);
        return data;
    }

    private GpuBuffer ensureKawaseParamsBuffer() {
        if (kawaseParamsBuffer == null || kawaseParamsBuffer.isClosed() || kawaseParamsBuffer.size() < KAWASE_UNIFORM_BYTES) {
            closeKawaseParamsBuffer();
            kawaseParamsBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_kawase_params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    KAWASE_UNIFORM_BYTES
            );
        }

        return kawaseParamsBuffer;
    }

    private GpuBuffer ensureScoreboardCropConfigBuffer() {
        scoreboardCropConfigBuffer = ensureUniformBuffer(
                scoreboardCropConfigBuffer,
                "UNIVERSALMOD_scoreboard_crop_config",
                SCOREBOARD_CROP_UNIFORM_BYTES
        );
        return scoreboardCropConfigBuffer;
    }

    private GpuBuffer ensureScoreboardSamplerInfoBuffer() {
        scoreboardSamplerInfoBuffer = ensureUniformBuffer(
                scoreboardSamplerInfoBuffer,
                "UNIVERSALMOD_scoreboard_sampler_info",
                SCOREBOARD_SAMPLER_INFO_BYTES
        );
        return scoreboardSamplerInfoBuffer;
    }

    private GpuBuffer ensureScoreboardBlurConfigBuffer() {
        scoreboardBlurConfigBuffer = ensureUniformBuffer(
                scoreboardBlurConfigBuffer,
                "UNIVERSALMOD_scoreboard_blur_config",
                SCOREBOARD_BLUR_CONFIG_BYTES
        );
        return scoreboardBlurConfigBuffer;
    }

    private static GpuBuffer ensureUniformBuffer(GpuBuffer buffer, String name, int size) {
        if (buffer != null && !buffer.isClosed() && buffer.size() >= size) {
            return buffer;
        }
        if (buffer != null) {
            buffer.close();
        }
        try {
            return RenderSystem.getDevice().createBuffer(
                    () -> name,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    size
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private GpuBuffer ensureFullscreenQuadBuffer() {
        if (fullscreenQuadBuffer != null) {
            return fullscreenQuadBuffer;
        }

        try (ByteBufferBuilder allocator = new ByteBufferBuilder(4 * DefaultVertexFormat.POSITION.getVertexSize());
             MeshData meshData = buildFullscreenQuad(allocator)) {
            fullscreenQuadBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "UNIVERSALMOD_blur_fullscreen_quad",
                    GpuBuffer.USAGE_VERTEX,
                    meshData.vertexBuffer()
            );
            return fullscreenQuadBuffer;
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

    private BuiltBlur normalize(BuiltBlur blur) {
        float maxRadius = Math.max(0.0f, Math.min(blur.width(), blur.height()) * 0.5f);
        float radiusTopLeft = clamp(blur.radiusTopLeft(), 0.0f, maxRadius);
        float radiusTopRight = clamp(blur.radiusTopRight(), 0.0f, maxRadius);
        float radiusBottomRight = clamp(blur.radiusBottomRight(), 0.0f, maxRadius);
        float radiusBottomLeft = clamp(blur.radiusBottomLeft(), 0.0f, maxRadius);
        float smoothness = Math.max(0.0f, blur.smoothness());
        float blurRadius = Math.max(0.0f, blur.blurRadius());
        float verticalSplit = blur.verticalSplit();
        if (!Float.isFinite(verticalSplit) || verticalSplit < 0.0f) {
            verticalSplit = BuiltBlur.NO_VERTICAL_SPLIT;
        } else {
            verticalSplit = clamp(verticalSplit, 0.0f, Math.max(blur.height(), 0.0f));
        }

        if (radiusTopLeft == blur.radiusTopLeft()
                && radiusTopRight == blur.radiusTopRight()
                && radiusBottomRight == blur.radiusBottomRight()
                && radiusBottomLeft == blur.radiusBottomLeft()
                && smoothness == blur.smoothness()
                && blurRadius == blur.blurRadius()
                && verticalSplit == blur.verticalSplit()) {
            return blur;
        }

        return new BuiltBlur(
                blur.x(),
                blur.y(),
                blur.width(),
                blur.height(),
                radiusTopLeft,
                radiusTopRight,
                radiusBottomRight,
                radiusBottomLeft,
                smoothness,
                blurRadius,
                blur.colorTopLeft(),
                blur.colorTopRight(),
                blur.colorBottomRight(),
                blur.colorBottomLeft(),
                blur.algorithm(),
                verticalSplit,
                blur.splitColor()
        );
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void closeParamsBuffer() {
        if (paramsBuffer != null) {
            paramsBuffer.close();
            paramsBuffer = null;
        }
    }

    private void closeRegionBuffer() {
        if (regionBuffer != null) {
            regionBuffer.close();
            regionBuffer = null;
        }
    }

    private void closeKawaseParamsBuffer() {
        if (kawaseParamsBuffer != null) {
            kawaseParamsBuffer.close();
            kawaseParamsBuffer = null;
        }
    }

    private void closeScoreboardBuffers() {
        if (scoreboardCropConfigBuffer != null) {
            scoreboardCropConfigBuffer.close();
            scoreboardCropConfigBuffer = null;
        }
        if (scoreboardSamplerInfoBuffer != null) {
            scoreboardSamplerInfoBuffer.close();
            scoreboardSamplerInfoBuffer = null;
        }
        if (scoreboardBlurConfigBuffer != null) {
            scoreboardBlurConfigBuffer.close();
            scoreboardBlurConfigBuffer = null;
        }
    }

    private static void destroyTarget(TextureTarget target) {
        if (target != null) {
            target.destroyBuffers();
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("universalmod", path);
    }

    @Override
    public void close() {
        pendingBlurs.clear();
        preparedBlurs.clear();
        activeGraphics = null;
        captureRequested = false;
        kawaseCaptureRequested = false;
        scoreboardCaptureRequested = false;
        blurTextureSetup = TextureSetup.noTexture();
        scoreboardBlurTextureSetup = TextureSetup.noTexture();
        closeParamsBuffer();
        closeRegionBuffer();
        closeKawaseParamsBuffer();
        closeScoreboardBuffers();
        if (blurTarget != null) {
            blurTarget.destroyBuffers();
            blurTarget = null;
        }
        destroyTarget(scoreboardSourceTarget);
        destroyTarget(scoreboardHorizontalTarget);
        destroyTarget(scoreboardBlurTarget);
        scoreboardSourceTarget = null;
        scoreboardHorizontalTarget = null;
        scoreboardBlurTarget = null;
        for (int i = 0; i < MAX_BLUR_ITERATIONS; i++) {
            if (downTargets[i] != null) {
                downTargets[i].destroyBuffers();
                downTargets[i] = null;
            }
            if (upTargets[i] != null) {
                upTargets[i].destroyBuffers();
                upTargets[i] = null;
            }
        }
        if (fullscreenQuadBuffer != null) {
            fullscreenQuadBuffer.close();
            fullscreenQuadBuffer = null;
        }
    }

    private record PendingBlur(BuiltBlur blur, Matrix3x2f pose, ScreenRectangle scissorArea) {
    }
}
