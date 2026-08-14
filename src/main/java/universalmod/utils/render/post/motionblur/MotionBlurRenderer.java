package universalmod.utils.render.post.motionblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import universalmod.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public final class MotionBlurRenderer {
    private static final int UNIFORM_BYTES = 16;

    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath(
            "universalmod",
            "pipeline/post/motion_blur"
    );
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath(
            "universalmod",
            "post/motionblur/motion_blur"
    );
    private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath(
            "universalmod",
            "post/motionblur/motion_blur"
    );

    private static RenderPipeline pipeline;
    private static GpuBuffer uniformBuffer;
    private static GpuBuffer dummyVertexBuffer;

    private static GpuTexture currentTexture;
    private static GpuTextureView currentTextureView;
    private static GpuTexture previousTexture;
    private static GpuTextureView previousTextureView;

    private static int textureWidth = -1;
    private static int textureHeight = -1;
    private static ClientLevel historyLevel;
    private static boolean historyValid;
    private static boolean disabledAfterError;

    private MotionBlurRenderer() {
    }

    public static void apply(float blendFactor) {
        if (disabledAfterError || !Float.isFinite(blendFactor)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ClientLevel level = minecraft.level;
        if (minecraft.player == null || level == null || mainTarget == null
                || mainTarget.getColorTexture() == null
                || mainTarget.getColorTextureView() == null) {
            resetHistory();
            return;
        }

        int width = mainTarget.width;
        int height = mainTarget.height;
        if (width <= 0 || height <= 0) {
            resetHistory();
            return;
        }

        float clampedBlend = Math.clamp(blendFactor, 0.0F, 0.99F);
        if (clampedBlend <= 0.0F) {
            resetHistory();
            return;
        }

        if (historyLevel != level) {
            historyLevel = level;
            historyValid = false;
        }

        try {
            ensureResources(width, height);
            if (!isReady()) {
                return;
            }

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            encoder.copyTextureToTexture(
                    mainTarget.getColorTexture(),
                    currentTexture,
                    0,
                    0,
                    0,
                    0,
                    0,
                    width,
                    height
            );

            if (!historyValid) {
                encoder.copyTextureToTexture(
                        mainTarget.getColorTexture(),
                        previousTexture,
                        0,
                        0,
                        0,
                        0,
                        0,
                        width,
                        height
                );
                historyValid = true;
            }

            writeUniform(encoder, clampedBlend);

            try (RenderPass renderPass = encoder.createRenderPass(
                    () -> "universalmod:motion_blur_composite",
                    mainTarget.getColorTextureView(),
                    OptionalInt.empty()
            )) {
                renderPass.setPipeline(pipeline);
                renderPass.setVertexBuffer(0, dummyVertexBuffer);
                renderPass.setUniform("MotionBlurData", uniformBuffer.slice(0, UNIFORM_BYTES));
                renderPass.bindTexture("DiffuseSampler", currentTextureView, RenderSampler.linear());
                renderPass.bindTexture("PrevSampler", previousTextureView, RenderSampler.linear());
                renderPass.draw(0, 6);
            }

            encoder.copyTextureToTexture(
                    mainTarget.getColorTexture(),
                    previousTexture,
                    0,
                    0,
                    0,
                    0,
                    0,
                    width,
                    height
            );
        } catch (Throwable throwable) {
            disabledAfterError = true;
            System.err.println("[UniversalMod] Motion Blur renderer disabled after a rendering error:");
            throwable.printStackTrace(System.err);
            close();
        }
    }

    public static void resetHistory() {
        historyValid = false;
        historyLevel = null;
    }

    public static void recover() {
        disabledAfterError = false;
        resetHistory();
    }

    public static boolean isDisabledAfterError() {
        return disabledAfterError;
    }

    public static void close() {
        historyValid = false;
        historyLevel = null;
        textureWidth = -1;
        textureHeight = -1;

        if (currentTextureView != null) {
            currentTextureView.close();
            currentTextureView = null;
        }
        if (previousTextureView != null) {
            previousTextureView.close();
            previousTextureView = null;
        }
        if (currentTexture != null) {
            currentTexture.close();
            currentTexture = null;
        }
        if (previousTexture != null) {
            previousTexture.close();
            previousTexture = null;
        }
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
    }

    private static void ensureResources(int width, int height) {
        if (pipeline == null) {
            pipeline = RenderPipelines.register(
                    RenderPipeline.builder()
                            .withLocation(PIPELINE_ID)
                            .withVertexShader(VERTEX_SHADER)
                            .withFragmentShader(FRAGMENT_SHADER)
                            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                            .withUniform("MotionBlurData", UniformType.UNIFORM_BUFFER)
                            .withSampler("DiffuseSampler")
                            .withSampler("PrevSampler")

                            .withBlend(BlendFunction.TRANSLUCENT)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .build()
            );
        }

        if (uniformBuffer == null || uniformBuffer.isClosed() || uniformBuffer.size() < UNIFORM_BYTES) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:motion_blur_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
        }

        if (dummyVertexBuffer == null || dummyVertexBuffer.isClosed()) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.calloc(4);
                dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "universalmod:motion_blur_dummy_vertex",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                        data
                );
            }
        }

        if (currentTexture != null && previousTexture != null
                && textureWidth == width && textureHeight == height) {
            return;
        }

        closeTextures();

        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING;
        currentTexture = RenderSystem.getDevice().createTexture(
                () -> "universalmod:motion_blur_current",
                usage,
                TextureFormat.RGBA8,
                width,
                height,
                1,
                1
        );
        previousTexture = RenderSystem.getDevice().createTexture(
                () -> "universalmod:motion_blur_previous",
                usage,
                TextureFormat.RGBA8,
                width,
                height,
                1,
                1
        );
        currentTextureView = RenderSystem.getDevice().createTextureView(currentTexture);
        previousTextureView = RenderSystem.getDevice().createTextureView(previousTexture);
        textureWidth = width;
        textureHeight = height;
        historyValid = false;
    }

    private static void writeUniform(CommandEncoder encoder, float blendFactor) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(UNIFORM_BYTES);
            data.putFloat(0, blendFactor);
            data.position(0);
            encoder.writeToBuffer(uniformBuffer.slice(0, UNIFORM_BYTES), data);
        }
    }

    private static boolean isReady() {
        return pipeline != null
                && uniformBuffer != null
                && !uniformBuffer.isClosed()
                && dummyVertexBuffer != null
                && !dummyVertexBuffer.isClosed()
                && currentTexture != null
                && previousTexture != null
                && currentTextureView != null
                && previousTextureView != null;
    }

    private static void closeTextures() {
        if (currentTextureView != null) {
            currentTextureView.close();
            currentTextureView = null;
        }
        if (previousTextureView != null) {
            previousTextureView.close();
            previousTextureView = null;
        }
        if (currentTexture != null) {
            currentTexture.close();
            currentTexture = null;
        }
        if (previousTexture != null) {
            previousTexture.close();
            previousTexture = null;
        }
        textureWidth = -1;
        textureHeight = -1;
        historyValid = false;
    }
}
