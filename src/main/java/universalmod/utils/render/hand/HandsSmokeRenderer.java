package universalmod.utils.render.hand;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import universalmod.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class HandsSmokeRenderer {
    private static final Identifier TRAIL_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/effects/hands_flame_trail");
    private static final Identifier BLUR_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/effects/hands_flame_kawase_blur");
    private static final Identifier COMPOSITE_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/effects/hands_flame_composite");
    private static final Identifier FULLSCREEN_VERTEX_SHADER = Identifier.fromNamespaceAndPath("universalmod", "effects/hands_flame/fullscreen");
    private static final Identifier TRAIL_FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("universalmod", "effects/hands_flame/trail");
    private static final Identifier BLUR_FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("universalmod", "effects/hands_flame/kawase_blur");
    private static final Identifier COMPOSITE_FRAGMENT_SHADER = Identifier.fromNamespaceAndPath("universalmod", "effects/hands_flame/composite");
    private static final int UNIFORM_BYTES = 80;

    private static boolean flameEnabled;
    private static float flameStrength = 0.85f;
    private static float flameRiseSpeed;
    private static float flameWobble = 0.65f;
    private static float flameLength = 0.95f;
    private static float flameBrightness = 0.9f;
    private static int flameColorMode = 1;
    private static int flameColor = 0xE6FFFFFF;

    private static RenderPipeline trailPipeline;
    private static RenderPipeline blurPipeline;
    private static RenderPipeline compositePipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;
    private static ByteBuffer dataBuffer;
    private static GpuTexture beforeTexture;
    private static GpuTexture sceneTexture;
    private static GpuTexture trailTextureA;
    private static GpuTexture trailTextureB;
    private static GpuTextureView beforeTextureView;
    private static GpuTextureView sceneTextureView;
    private static GpuTextureView trailTextureViewA;
    private static GpuTextureView trailTextureViewB;
    private static boolean useTrailAAsHistory = true;
    private static boolean capturedBeforeHands;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static boolean disabledAfterError;

    private HandsSmokeRenderer() {
    }

    public static void captureBeforeHandRender() {
        capturedBeforeHands = false;
        if (!shouldRenderFlame()) {
            return;
        }

        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (!isUsable(target)) {
            return;
        }

        if (!ensureReady(target.width, target.height)) {
            return;
        }

        try {
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .copyTextureToTexture(target.getColorTexture(), beforeTexture, 0, 0, 0, 0, 0, target.width, target.height);
            capturedBeforeHands = true;
        } catch (Throwable throwable) {
            disableAfterError(throwable);
        }
    }

    public static void renderCapturedHandsFlame(GpuTextureView depthMaskView) {
        if (!capturedBeforeHands) {
            return;
        }
        capturedBeforeHands = false;
        if (!shouldRenderFlame() || depthMaskView == null) {
            return;
        }

        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (!isUsable(target) || !ensureReady(target.width, target.height)) {
            return;
        }

        try {
            writeUniforms(target.width, target.height, 1.0f);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);
            renderTrail(encoder, target, depthMaskView);
            renderKawaseBlur(encoder, nextTrailView(), historyTrailView(), 0.75f);
            renderKawaseBlur(encoder, historyTrailView(), nextTrailView(), 1.55f);
            renderKawaseBlur(encoder, nextTrailView(), historyTrailView(), 2.45f);
            renderKawaseBlur(encoder, historyTrailView(), nextTrailView(), 3.35f);
            swapTrailHistory();
            encoder.copyTextureToTexture(target.getColorTexture(), sceneTexture, 0, 0, 0, 0, 0, target.width, target.height);
            renderComposite(encoder, target, depthMaskView);
        } catch (Throwable throwable) {
            disableAfterError(throwable);
        }
    }

    public static void resetTrail() {
        capturedBeforeHands = false;
        if (trailTextureA == null || trailTextureB == null) {
            return;
        }

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorTexture(trailTextureA, 0);
            encoder.clearColorTexture(trailTextureB, 0);
        } catch (Throwable throwable) {
            disableAfterError(throwable);
        }
    }

    public static void shutdown() {
        closeTextures();
        if (uniformBuffer != null) {
            uniformBuffer.close();
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
        }
        uniformBuffer = null;
        dummyVertexBuffer = null;
        dataBuffer = null;
        trailPipeline = null;
        blurPipeline = null;
        compositePipeline = null;
        capturedBeforeHands = false;
    }

    public static void reloadResources() {
        shutdown();
        disabledAfterError = false;
    }

    public static void setFlameEnabled(boolean enabled) {
        flameEnabled = enabled;
        if (!enabled) {
            resetTrail();
        }
    }

    public static void configure(float strength, float riseSpeed, float wobble, float length, float brightness, int colorMode, int color) {
        flameStrength = clamp(strength, 0.0f, 2.0f);
        flameRiseSpeed = clamp(riseSpeed, 0.0f, 2.0f);
        flameWobble = clamp(wobble, 0.0f, 2.0f);
        flameLength = clamp(length, 0.1f, 2.5f);
        flameBrightness = clamp(brightness, 0.0f, 2.0f);
        flameColorMode = Math.max(0, Math.min(2, colorMode));
        flameColor = color;
    }

    public static boolean shouldRenderFlame() {
        if (!flameEnabled || disabledAfterError) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null && minecraft.level != null;
    }

    private static boolean ensureReady(int width, int height) {
        if (trailPipeline == null || blurPipeline == null || compositePipeline == null || uniformBuffer == null || dummyVertexBuffer == null || dataBuffer == null) {
            initPipelines();
        }
        ensureTextures(width, height);
        return trailPipeline != null
                && blurPipeline != null
                && compositePipeline != null
                && uniformBuffer != null
                && dummyVertexBuffer != null
                && dataBuffer != null
                && beforeTexture != null
                && sceneTexture != null
                && trailTextureA != null
                && trailTextureB != null
                && beforeTextureView != null
                && sceneTextureView != null
                && trailTextureViewA != null
                && trailTextureViewB != null;
    }

    private static void initPipelines() {
        try {
            trailPipeline = RenderPipeline.builder()
                    .withLocation(TRAIL_PIPELINE_ID)
                    .withVertexShader(FULLSCREEN_VERTEX_SHADER)
                    .withFragmentShader(TRAIL_FRAGMENT_SHADER)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withUniform("HandsFlameData", UniformType.UNIFORM_BUFFER)
                    .withSampler("BeforeSampler")
                    .withSampler("AfterSampler")
                    .withSampler("PrevTrailSampler")
                    .withSampler("DepthSampler")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
            blurPipeline = RenderPipeline.builder()
                    .withLocation(BLUR_PIPELINE_ID)
                    .withVertexShader(FULLSCREEN_VERTEX_SHADER)
                    .withFragmentShader(BLUR_FRAGMENT_SHADER)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withUniform("HandsFlameData", UniformType.UNIFORM_BUFFER)
                    .withSampler("InputSampler")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
            compositePipeline = RenderPipeline.builder()
                    .withLocation(COMPOSITE_PIPELINE_ID)
                    .withVertexShader(FULLSCREEN_VERTEX_SHADER)
                    .withFragmentShader(COMPOSITE_FRAGMENT_SHADER)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                    .withUniform("HandsFlameData", UniformType.UNIFORM_BUFFER)
                    .withSampler("SceneSampler")
                    .withSampler("BeforeSampler")
                    .withSampler("TrailSampler")
                    .withSampler("DepthSampler")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();

            dataBuffer = MemoryUtil.memAlloc(UNIFORM_BYTES);
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:hands_flame_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
            ByteBuffer dummyData = MemoryUtil.memAlloc(4);
            dummyData.putInt(0);
            dummyData.flip();
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:hands_flame_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    dummyData
            );
            MemoryUtil.memFree(dummyData);
        } catch (Throwable throwable) {
            disableAfterError(throwable);
        }
    }

    private static void ensureTextures(int width, int height) {
        if (beforeTexture != null && width == lastWidth && height == lastHeight) {
            return;
        }

        closeTextures();
        int trailWidth = Math.max(1, width / 2);
        int trailHeight = Math.max(1, height / 2);
        beforeTexture = createTexture("universalmod:hands_flame_before", width, height, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING);
        sceneTexture = createTexture("universalmod:hands_flame_scene", width, height, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING);
        trailTextureA = createTexture("universalmod:hands_flame_trail_a", trailWidth, trailHeight, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT);
        trailTextureB = createTexture("universalmod:hands_flame_trail_b", trailWidth, trailHeight, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT);
        beforeTextureView = RenderSystem.getDevice().createTextureView(beforeTexture);
        sceneTextureView = RenderSystem.getDevice().createTextureView(sceneTexture);
        trailTextureViewA = RenderSystem.getDevice().createTextureView(trailTextureA);
        trailTextureViewB = RenderSystem.getDevice().createTextureView(trailTextureB);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(trailTextureA, 0);
        encoder.clearColorTexture(trailTextureB, 0);
        useTrailAAsHistory = true;
        lastWidth = width;
        lastHeight = height;
    }

    private static GpuTexture createTexture(String label, int width, int height, int usage) {
        return RenderSystem.getDevice().createTexture(
                () -> label,
                usage,
                TextureFormat.RGBA8,
                width,
                height,
                1,
                1
        );
    }

    private static void writeUniforms(int width, int height, float kawaseOffset) {
        int color = flameColor;
        float alpha = ((color >>> 24) & 0xFF) / 255.0f;
        dataBuffer.clear();
        dataBuffer.putFloat(((color >> 16) & 0xFF) / 255.0f);
        dataBuffer.putFloat(((color >> 8) & 0xFF) / 255.0f);
        dataBuffer.putFloat((color & 0xFF) / 255.0f);
        dataBuffer.putFloat(alpha);
        dataBuffer.putFloat(flameStrength);
        dataBuffer.putFloat(flameRiseSpeed);
        dataBuffer.putFloat(clamp(flameWobble * 1.2f, 0.0f, 2.4f));
        dataBuffer.putFloat(flameLength);
        dataBuffer.putFloat(flameBrightness);
        dataBuffer.putFloat(flameTime() * 1.2f);
        dataBuffer.putFloat(flameColorMode);
        dataBuffer.putFloat(alpha);
        dataBuffer.putFloat(width);
        dataBuffer.putFloat(height);
        dataBuffer.putFloat(1.0f / Math.max(width, 1));
        dataBuffer.putFloat(1.0f / Math.max(height, 1));
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(0.0f);
        dataBuffer.putFloat(kawaseOffset);
        dataBuffer.flip();
    }

    private static void renderTrail(CommandEncoder encoder, RenderTarget target, GpuTextureView depthMaskView) {
        try (var renderPass = encoder.createRenderPass(
                () -> "universalmod:hands_flame_trail",
                nextTrailView(),
                OptionalInt.empty(),
                null,
                OptionalDouble.empty()
        )) {
            renderPass.setPipeline(trailPipeline);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("BeforeSampler", beforeTextureView, RenderSampler.linear());
            renderPass.bindTexture("AfterSampler", target.getColorTextureView(), RenderSampler.linear());
            renderPass.bindTexture("PrevTrailSampler", historyTrailView(), RenderSampler.linear());
            renderPass.bindTexture("DepthSampler", depthMaskView, RenderSampler.nearest());
            renderPass.setUniform("HandsFlameData", uniformBuffer.slice());
            renderPass.draw(0, 6);
        }
    }

    private static void renderKawaseBlur(CommandEncoder encoder, GpuTextureView inputView, GpuTextureView outputView, float offset) {
        writeUniforms(lastWidth, lastHeight, offset);
        encoder.writeToBuffer(uniformBuffer.slice(0, dataBuffer.remaining()), dataBuffer);
        try (var renderPass = encoder.createRenderPass(
                () -> "universalmod:hands_flame_kawase_blur",
                outputView,
                OptionalInt.empty(),
                null,
                OptionalDouble.empty()
        )) {
            renderPass.setPipeline(blurPipeline);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("InputSampler", inputView, RenderSampler.linear());
            renderPass.setUniform("HandsFlameData", uniformBuffer.slice());
            renderPass.draw(0, 6);
        }
    }

    private static void renderComposite(CommandEncoder encoder, RenderTarget target, GpuTextureView depthMaskView) {
        try (var renderPass = encoder.createRenderPass(
                () -> "universalmod:hands_flame_composite",
                target.getColorTextureView(),
                OptionalInt.empty(),
                null,
                OptionalDouble.empty()
        )) {
            renderPass.setPipeline(compositePipeline);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("SceneSampler", sceneTextureView, RenderSampler.linear());
            renderPass.bindTexture("BeforeSampler", beforeTextureView, RenderSampler.linear());
            renderPass.bindTexture("TrailSampler", historyTrailView(), RenderSampler.linear());
            renderPass.bindTexture("DepthSampler", depthMaskView, RenderSampler.nearest());
            renderPass.setUniform("HandsFlameData", uniformBuffer.slice());
            renderPass.draw(0, 6);
        }
    }

    private static GpuTextureView historyTrailView() {
        return useTrailAAsHistory ? trailTextureViewA : trailTextureViewB;
    }

    private static GpuTextureView nextTrailView() {
        return useTrailAAsHistory ? trailTextureViewB : trailTextureViewA;
    }

    private static void swapTrailHistory() {
        useTrailAAsHistory = !useTrailAAsHistory;
    }

    private static boolean isUsable(RenderTarget target) {
        return target != null
                && target.getColorTexture() != null
                && target.getColorTextureView() != null
                && target.width > 0
                && target.height > 0;
    }

    private static float flameTime() {
        return (System.nanoTime() % 180_000_000_000L) / 1_000_000_000.0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void disableAfterError(Throwable throwable) {
        disabledAfterError = true;
        throwable.printStackTrace();
        shutdown();
    }

    private static void closeTextures() {
        if (beforeTextureView != null) {
            beforeTextureView.close();
        }
        if (sceneTextureView != null) {
            sceneTextureView.close();
        }
        if (trailTextureViewA != null) {
            trailTextureViewA.close();
        }
        if (trailTextureViewB != null) {
            trailTextureViewB.close();
        }
        if (beforeTexture != null) {
            beforeTexture.close();
        }
        if (sceneTexture != null) {
            sceneTexture.close();
        }
        if (trailTextureA != null) {
            trailTextureA.close();
        }
        if (trailTextureB != null) {
            trailTextureB.close();
        }
        beforeTextureView = null;
        sceneTextureView = null;
        trailTextureViewA = null;
        trailTextureViewB = null;
        beforeTexture = null;
        sceneTexture = null;
        trailTextureA = null;
        trailTextureB = null;
        lastWidth = -1;
        lastHeight = -1;
    }
}
