package universalmod.utils.render.hand;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import universalmod.api.module.impl.render.Hands;
import universalmod.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public final class HandsRenderer {
    private static final int MASK_UNIFORM_SIZE = 16;
    private static final int EFFECT_UNIFORM_SIZE = 48;
    private static final int BLUR_UNIFORM_SIZE = 32;
    private static final int OUTLINE_UNIFORM_SIZE = 64;

    private static final Identifier FULLSCREEN_VERTEX = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/fullscreen");
    private static final Identifier MASK_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/mask");
    private static final Identifier WAVE_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/wave");
    private static final Identifier NEBULA_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/nebula");
    private static final Identifier OUTLINE_BLUR_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/outline_blur");
    private static final Identifier OUTLINE_COMPOSITE_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/outline_composite");
    private static final Identifier SMOKE_DEPTH_MASK_SHADER = Identifier.fromNamespaceAndPath("universalmod", "post/shaderhands/smoke_depth_mask");

    private static final Identifier MASK_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/mask");
    private static final Identifier WAVE_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/wave");
    private static final Identifier NEBULA_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/nebula");
    private static final Identifier OUTLINE_BLUR_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/outline_blur");
    private static final Identifier OUTLINE_COMPOSITE_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/outline_composite");
    private static final Identifier SMOKE_DEPTH_MASK_PIPELINE_ID = Identifier.fromNamespaceAndPath("universalmod", "pipeline/post/hands/smoke_depth_mask");

    private static RenderPipeline maskPipeline;
    private static RenderPipeline wavePipeline;
    private static RenderPipeline nebulaPipeline;
    private static RenderPipeline outlineBlurPipeline;
    private static RenderPipeline outlineCompositePipeline;
    private static RenderPipeline smokeDepthMaskPipeline;

    private static GpuBuffer maskUniformBuffer;
    private static GpuBuffer effectUniformBuffer;
    private static GpuBuffer blurUniformBuffer;
    private static GpuBuffer outlineUniformBuffer;
    private static GpuBuffer dummyVertexBuffer;
    private static ByteBuffer dummyVertexData;
    private static TextureTarget beforeTarget;
    private static TextureTarget afterTarget;
    private static TextureTarget maskTarget;
    private static TextureTarget smokeDepthTarget;
    private static TextureTarget blurTargetA;
    private static TextureTarget blurTargetB;
    private static int width = -1;
    private static int height = -1;
    private static boolean capturedBefore;
    
    private static volatile int editorOutlineSide;

    private HandsRenderer() {
    }

    public static void setEditorOutlineSide(int side) {
        editorOutlineSide = side < 0 ? -1 : (side > 0 ? 1 : 0);
    }

    public static void clearEditorOutline() {
        editorOutlineSide = 0;
    }

    private static boolean editorOutlineActive(Hands module) {
        return editorOutlineSide != 0 && (module == null || !module.isEnabled());
    }

    public static void captureBeforeHands() {

        Hands module = Hands.getInstance();
        boolean regularHands = Hands.isActive();
        boolean editorOutline = editorOutlineActive(module);
        if ((!regularHands && !editorOutline) || module == null) {
            capturedBefore = false;
            HandsSmokeRenderer.setFlameEnabled(false);
            return;
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.getColorTexture() == null || main.getDepthTexture() == null || main.width <= 0 || main.height <= 0) {
            capturedBefore = false;
            return;
        }

        init();
        if (!ensureTargets(main.width, main.height)) {
            capturedBefore = false;
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(main.getColorTexture(), beforeTarget.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
        encoder.copyTextureToTexture(main.getDepthTexture(), beforeTarget.getDepthTexture(), 0, 0, 0, 0, 0, main.width, main.height);
        if (regularHands && module.shouldRenderSmoke()) {
            HandsSmokeRenderer.setFlameEnabled(true);
            HandsSmokeRenderer.configure(
                    module.getSmokeStrength(),
                    module.getSmokeRiseSpeed(),
                    module.getSmokeWobble(),
                    module.getSmokeLength(),
                    module.getSmokeBrightness(),
                    1,
                    module.getSmokeResolvedColor()
            );
            HandsSmokeRenderer.captureBeforeHandRender();
        } else {
            HandsSmokeRenderer.setFlameEnabled(false);
        }
        capturedBefore = true;
    }

    public static void captureAfterHands() {

        Hands module = Hands.getInstance();
        boolean regularHands = Hands.isActive();
        boolean editorOutline = editorOutlineActive(module);
        if ((!regularHands && !editorOutline) || !capturedBefore || module == null) {
            capturedBefore = false;
            return;
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.getColorTexture() == null || main.getDepthTexture() == null || main.width <= 0 || main.height <= 0) {
            capturedBefore = false;
            return;
        }

        init();
        if (!ensureTargets(main.width, main.height)) {
            capturedBefore = false;
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(main.getColorTexture(), afterTarget.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
        encoder.copyTextureToTexture(main.getDepthTexture(), afterTarget.getDepthTexture(), 0, 0, 0, 0, 0, main.width, main.height);

        if (regularHands && module.shouldRenderSmoke()) {
            renderSmokeDepthMask();
            HandsSmokeRenderer.setFlameEnabled(true);
            HandsSmokeRenderer.configure(
                    module.getSmokeStrength(),
                    module.getSmokeRiseSpeed(),
                    module.getSmokeWobble(),
                    module.getSmokeLength(),
                    module.getSmokeBrightness(),
                    1,
                    module.getSmokeResolvedColor()
            );
            HandsSmokeRenderer.renderCapturedHandsFlame(smokeDepthTarget.getColorTextureView());
        } else {
            renderMask();
            HandsSmokeRenderer.setFlameEnabled(false);
            if (regularHands && module.shouldRenderShader()) {
                renderShader(main, module);
            }
            if ((regularHands && module.shouldRenderOutline()) || editorOutline) {
                renderOutline(main, module);
            }
        }

        capturedBefore = false;
    }

    public static boolean hasCapturedHands() {
        return capturedBefore;
    }

    public static void reset() {
        destroyTargets();
        closeBuffers();
        maskPipeline = null;
        wavePipeline = null;
        nebulaPipeline = null;
        outlineBlurPipeline = null;
        outlineCompositePipeline = null;
        smokeDepthMaskPipeline = null;
        capturedBefore = false;
        editorOutlineSide = 0;
        HandsSmokeRenderer.reloadResources();
    }

    private static void init() {
        if (maskPipeline == null) {
            maskPipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                            .withLocation(MASK_PIPELINE_ID)
                            .withVertexShader(FULLSCREEN_VERTEX)
                            .withFragmentShader(MASK_SHADER)
                            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                            .withUniform("MaskData", UniformType.UNIFORM_BUFFER)
                            .withSampler("BeforeDepth")
                            .withSampler("AfterDepth")
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .build()
            );
        }
        if (wavePipeline == null) {
            wavePipeline = buildEffectPipeline(WAVE_PIPELINE_ID, WAVE_SHADER);
        }
        if (nebulaPipeline == null) {
            nebulaPipeline = buildEffectPipeline(NEBULA_PIPELINE_ID, NEBULA_SHADER);
        }
        if (outlineBlurPipeline == null) {
            outlineBlurPipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                            .withLocation(OUTLINE_BLUR_PIPELINE_ID)
                            .withVertexShader(FULLSCREEN_VERTEX)
                            .withFragmentShader(OUTLINE_BLUR_SHADER)
                            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                            .withUniform("HandsBlurData", UniformType.UNIFORM_BUFFER)
                            .withSampler("SourceSampler")
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .build()
            );
        }
        if (outlineCompositePipeline == null) {
            outlineCompositePipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                            .withLocation(OUTLINE_COMPOSITE_PIPELINE_ID)
                            .withVertexShader(FULLSCREEN_VERTEX)
                            .withFragmentShader(OUTLINE_COMPOSITE_SHADER)
                            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                            .withUniform("HandsOutlineData", UniformType.UNIFORM_BUFFER)
                            .withSampler("MaskSampler")
                            .withSampler("BlurSampler")
                            .withBlend(BlendFunction.TRANSLUCENT)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .build()
            );
        }
        if (smokeDepthMaskPipeline == null) {
            smokeDepthMaskPipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                            .withLocation(SMOKE_DEPTH_MASK_PIPELINE_ID)
                            .withVertexShader(FULLSCREEN_VERTEX)
                            .withFragmentShader(SMOKE_DEPTH_MASK_SHADER)
                            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                            .withUniform("MaskData", UniformType.UNIFORM_BUFFER)
                            .withSampler("BeforeDepth")
                            .withSampler("AfterDepth")
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .build()
            );
        }
        if (maskUniformBuffer == null || maskUniformBuffer.isClosed() || maskUniformBuffer.size() < MASK_UNIFORM_SIZE) {
            if (maskUniformBuffer != null) {
                maskUniformBuffer.close();
            }
            maskUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "universalmod:hands_mask", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, MASK_UNIFORM_SIZE);
        }
        if (effectUniformBuffer == null || effectUniformBuffer.isClosed() || effectUniformBuffer.size() < EFFECT_UNIFORM_SIZE) {
            if (effectUniformBuffer != null) {
                effectUniformBuffer.close();
            }
            effectUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "universalmod:hands_effect", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, EFFECT_UNIFORM_SIZE);
        }
        if (blurUniformBuffer == null || blurUniformBuffer.isClosed() || blurUniformBuffer.size() < BLUR_UNIFORM_SIZE) {
            if (blurUniformBuffer != null) {
                blurUniformBuffer.close();
            }
            blurUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "universalmod:hands_blur", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BLUR_UNIFORM_SIZE);
        }
        if (outlineUniformBuffer == null || outlineUniformBuffer.isClosed() || outlineUniformBuffer.size() < OUTLINE_UNIFORM_SIZE) {
            if (outlineUniformBuffer != null) {
                outlineUniformBuffer.close();
            }
            outlineUniformBuffer = RenderSystem.getDevice().createBuffer(() -> "universalmod:hands_outline", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, OUTLINE_UNIFORM_SIZE);
        }
        if (dummyVertexBuffer == null || dummyVertexBuffer.isClosed()) {
            if (dummyVertexData == null) {
                dummyVertexData = MemoryUtil.memAlloc(4);
                dummyVertexData.putInt(0);
                dummyVertexData.flip();
            }
            dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:hands_dummy_vertex",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    dummyVertexData
            );
        }
    }

    private static RenderPipeline buildEffectPipeline(Identifier pipelineId, Identifier shader) {
        return RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                        .withLocation(pipelineId)
                        .withVertexShader(FULLSCREEN_VERTEX)
                        .withFragmentShader(shader)
                        .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                        .withUniform("HandsEffectData", UniformType.UNIFORM_BUFFER)
                        .withSampler("MaskSampler")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build()
        );
    }

    private static boolean ensureTargets(int nextWidth, int nextHeight) {
        if (nextWidth == width && nextHeight == height
                && beforeTarget != null && afterTarget != null && maskTarget != null && smokeDepthTarget != null
                && blurTargetA != null && blurTargetB != null) {
            return true;
        }

        destroyTargets();
        beforeTarget = new TextureTarget("UNIVERSALMOD_hands_before", nextWidth, nextHeight, true);
        afterTarget = new TextureTarget("UNIVERSALMOD_hands_after", nextWidth, nextHeight, true);
        maskTarget = new TextureTarget("UNIVERSALMOD_hands_mask", nextWidth, nextHeight, false);
        smokeDepthTarget = new TextureTarget("UNIVERSALMOD_hands_smoke_depth", nextWidth, nextHeight, false);
        blurTargetA = new TextureTarget("UNIVERSALMOD_hands_blur_a", nextWidth, nextHeight, false);
        blurTargetB = new TextureTarget("UNIVERSALMOD_hands_blur_b", nextWidth, nextHeight, false);
        width = nextWidth;
        height = nextHeight;
        return beforeTarget.getColorTextureView() != null
                && afterTarget.getColorTextureView() != null
                && beforeTarget.getDepthTextureView() != null
                && afterTarget.getDepthTextureView() != null
                && maskTarget.getColorTextureView() != null
                && smokeDepthTarget.getColorTextureView() != null
                && blurTargetA.getColorTextureView() != null
                && blurTargetB.getColorTextureView() != null;
    }

    private static void renderMask() {
        if (beforeTarget == null || afterTarget == null || maskTarget == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(MASK_UNIFORM_SIZE);
            data.putFloat(0, 1.0F / Math.max(width, 1));
            data.putFloat(4, 1.0F / Math.max(height, 1));
            Hands module = Hands.getInstance();
            data.putFloat(8, editorOutlineActive(module) ? editorOutlineSide : 0.0F);
            data.putFloat(12, 0.0F);
            data.position(0);
            encoder.writeToBuffer(maskUniformBuffer.slice(0, MASK_UNIFORM_SIZE), data);

            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:hands_mask", maskTarget.getColorTextureView(), OptionalInt.of(0))) {
                pass.setPipeline(maskPipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.bindTexture("BeforeDepth", beforeTarget.getDepthTextureView(), RenderSampler.nearest());
                pass.bindTexture("AfterDepth", afterTarget.getDepthTextureView(), RenderSampler.nearest());
                pass.setUniform("MaskData", maskUniformBuffer);
                pass.draw(0, 6);
            }
        }
    }

    private static void renderSmokeDepthMask() {
        if (beforeTarget == null || afterTarget == null || smokeDepthTarget == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(MASK_UNIFORM_SIZE);
            data.putFloat(0, 1.0F / Math.max(width, 1));
            data.putFloat(4, 1.0F / Math.max(height, 1));
            data.position(0);
            encoder.writeToBuffer(maskUniformBuffer.slice(0, MASK_UNIFORM_SIZE), data);

            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:hands_smoke_depth_mask", smokeDepthTarget.getColorTextureView(), OptionalInt.of(0))) {
                pass.setPipeline(smokeDepthMaskPipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.bindTexture("BeforeDepth", beforeTarget.getDepthTextureView(), RenderSampler.nearest());
                pass.bindTexture("AfterDepth", afterTarget.getDepthTextureView(), RenderSampler.nearest());
                pass.setUniform("MaskData", maskUniformBuffer);
                pass.draw(0, 6);
            }
        }
    }

    private static void renderShader(RenderTarget main, Hands module) {
        if (maskTarget == null || main.getColorTextureView() == null) {
            return;
        }

        RenderPipeline pipeline = module.getShaderMode().equalsIgnoreCase("Nebula") ? nebulaPipeline : wavePipeline;
        if (pipeline == null) {
            return;
        }

        int color = module.getShaderColor();
        float speed = module.getWaveSpeed() / 100.0F;
        float scale = module.getWaveScale() / 100.0F;
        float fill = module.getFill() / 100.0F;
        float alpha = module.getAlpha() / 100.0F;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(EFFECT_UNIFORM_SIZE);
            data.putFloat(0, 1.0F / Math.max(width, 1));
            data.putFloat(4, 1.0F / Math.max(height, 1));
            data.putFloat(8, ((color >> 16) & 0xFF) / 255.0F);
            data.putFloat(12, ((color >> 8) & 0xFF) / 255.0F);
            data.putFloat(16, (color & 0xFF) / 255.0F);
            data.putFloat(20, (System.currentTimeMillis() % 100000L) / 1000.0F);
            data.putFloat(24, speed);
            data.putFloat(28, scale);
            data.putFloat(32, fill);
            data.putFloat(36, alpha);
            data.position(0);
            encoder.writeToBuffer(effectUniformBuffer.slice(0, EFFECT_UNIFORM_SIZE), data);

            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:hands_shader", main.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.bindTexture("MaskSampler", maskTarget.getColorTextureView(), RenderSampler.linear());
                pass.setUniform("HandsEffectData", effectUniformBuffer);
                pass.draw(0, 6);
            }
        }
    }

    private static void renderOutline(RenderTarget main, Hands module) {
        if (maskTarget == null || blurTargetA == null || blurTargetB == null || main.getColorTextureView() == null) {
            return;
        }

        renderBlurPass(maskTarget, blurTargetA, module.getOutlineRadius(), true);
        renderBlurPass(blurTargetA, blurTargetB, module.getOutlineRadius(), false);

        int topColor = module.getOutlineTopColor();
        int bottomColor = module.getOutlineBottomColor();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(OUTLINE_UNIFORM_SIZE);
            data.putFloat(0, 1.0F / Math.max(width, 1));
            data.putFloat(4, 1.0F / Math.max(height, 1));
            data.putFloat(8, module.getOutlineWidth());
            data.putFloat(12, module.getGlowStrength());
            data.putFloat(16, 1.35F);
            data.putFloat(20, module.getOutlineOpacity());
            data.putFloat(24, (System.nanoTime() % 30_000_000_000L) / 1.0E9F);
            data.putFloat(28, module.isOutlineStatic() ? 1.0F : 0.0F);
            data.putFloat(32, ((topColor >> 16) & 0xFF) / 255.0F);
            data.putFloat(36, ((topColor >> 8) & 0xFF) / 255.0F);
            data.putFloat(40, (topColor & 0xFF) / 255.0F);
            data.putFloat(44, ((topColor >> 24) & 0xFF) / 255.0F);
            data.putFloat(48, ((bottomColor >> 16) & 0xFF) / 255.0F);
            data.putFloat(52, ((bottomColor >> 8) & 0xFF) / 255.0F);
            data.putFloat(56, (bottomColor & 0xFF) / 255.0F);
            data.putFloat(60, ((bottomColor >> 24) & 0xFF) / 255.0F);
            data.position(0);
            encoder.writeToBuffer(outlineUniformBuffer.slice(0, OUTLINE_UNIFORM_SIZE), data);

            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:hands_outline", main.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(outlineCompositePipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.bindTexture("MaskSampler", maskTarget.getColorTextureView(), RenderSampler.linear());
                pass.bindTexture("BlurSampler", blurTargetB.getColorTextureView(), RenderSampler.linear());
                pass.setUniform("HandsOutlineData", outlineUniformBuffer);
                pass.draw(0, 6);
            }
        }
    }

    private static void renderBlurPass(TextureTarget source, TextureTarget target, float radius, boolean horizontal) {
        if (source == null || target == null || source.getColorTextureView() == null || target.getColorTextureView() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(BLUR_UNIFORM_SIZE);
            data.putFloat(0, 1.0F / Math.max(width, 1));
            data.putFloat(4, 1.0F / Math.max(height, 1));
            data.putFloat(8, horizontal ? 1.0F : 0.0F);
            data.putFloat(12, horizontal ? 0.0F : 1.0F);
            data.putFloat(16, radius);
            data.position(0);
            encoder.writeToBuffer(blurUniformBuffer.slice(0, BLUR_UNIFORM_SIZE), data);

            try (RenderPass pass = encoder.createRenderPass(() -> "universalmod:hands_outline_blur", target.getColorTextureView(), OptionalInt.of(0))) {
                pass.setPipeline(outlineBlurPipeline);
                pass.setVertexBuffer(0, dummyVertexBuffer);
                pass.bindTexture("SourceSampler", source.getColorTextureView(), RenderSampler.linear());
                pass.setUniform("HandsBlurData", blurUniformBuffer);
                pass.draw(0, 6);
            }
        }
    }

    private static void destroyTargets() {
        if (beforeTarget != null) {
            beforeTarget.destroyBuffers();
            beforeTarget = null;
        }
        if (afterTarget != null) {
            afterTarget.destroyBuffers();
            afterTarget = null;
        }
        if (maskTarget != null) {
            maskTarget.destroyBuffers();
            maskTarget = null;
        }
        if (smokeDepthTarget != null) {
            smokeDepthTarget.destroyBuffers();
            smokeDepthTarget = null;
        }
        if (blurTargetA != null) {
            blurTargetA.destroyBuffers();
            blurTargetA = null;
        }
        if (blurTargetB != null) {
            blurTargetB.destroyBuffers();
            blurTargetB = null;
        }
        width = -1;
        height = -1;
    }

    private static void closeBuffers() {
        if (maskUniformBuffer != null) {
            maskUniformBuffer.close();
            maskUniformBuffer = null;
        }
        if (effectUniformBuffer != null) {
            effectUniformBuffer.close();
            effectUniformBuffer = null;
        }
        if (blurUniformBuffer != null) {
            blurUniformBuffer.close();
            blurUniformBuffer = null;
        }
        if (outlineUniformBuffer != null) {
            outlineUniformBuffer.close();
            outlineUniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dummyVertexData != null) {
            MemoryUtil.memFree(dummyVertexData);
            dummyVertexData = null;
        }
    }
}
