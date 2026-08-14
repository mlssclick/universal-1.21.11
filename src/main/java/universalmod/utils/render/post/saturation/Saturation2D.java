package universalmod.utils.render.post.saturation;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import universalmod.utils.render.RenderSampler;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

public final class Saturation2D {
    private static final int UNIFORM_BYTES = 16;

    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath(
            "universalmod", "pipeline/post/saturation"
    );
    private static final Identifier VERTEX_SHADER = Identifier.fromNamespaceAndPath(
            "universalmod", "post/saturation/saturation"
    );
    private static final Identifier FRAGMENT_SHADER = Identifier.fromNamespaceAndPath(
            "universalmod", "post/saturation/saturation"
    );

    private static RenderPipeline pipeline;
    private static ByteBuffer dataBuffer;
    private static GpuBuffer dummyVertexBuffer;
    private static GpuBuffer uniformBuffer;
    private static GpuTexture tempTexture;
    private static GpuTextureView tempTextureView;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static boolean disabledAfterError;

    private Saturation2D() {
    }

    public static void applyWithCopy(float saturation) {
        if (disabledAfterError || !Float.isFinite(saturation)) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getMainRenderTarget() == null) {
            return;
        }

        float clampedSaturation = Math.clamp(saturation, 0.0F, 2.0F);
        if (Math.abs(clampedSaturation - 1.0F) <= 0.0005F) {
            return;
        }

        int width = client.getMainRenderTarget().width;
        int height = client.getMainRenderTarget().height;
        if (width <= 0 || height <= 0
                || client.getMainRenderTarget().getColorTexture() == null
                || client.getMainRenderTarget().getColorTextureView() == null) {
            return;
        }

        try {
            ensureResources(width, height);
            if (!isReady()) {
                return;
            }

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(
                    client.getMainRenderTarget().getColorTexture(),
                    tempTexture,
                    0,
                    0,
                    0,
                    0,
                    0,
                    width,
                    height
            );
            apply(client.getMainRenderTarget().getColorTextureView(), tempTextureView, clampedSaturation);
        } catch (Throwable throwable) {
            disabledAfterError = true;
            closeGpuResources();
            System.err.println("[UniversalMod] Saturation renderer disabled after a rendering error:");
            throwable.printStackTrace(System.err);
        }
    }

    public static void reloadResources() {
        closeGpuResources();
        disabledAfterError = false;
    }

    public static void recoverAfterReload() {
        disabledAfterError = false;
    }

    private static void ensureResources(int width, int height) {
        if (pipeline == null) {
            pipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                            .withLocation(PIPELINE_ID)
                            .withVertexShader(VERTEX_SHADER)
                            .withFragmentShader(FRAGMENT_SHADER)
                            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                            .withUniform("SaturationData", UniformType.UNIFORM_BUFFER)
                            .withSampler("Sampler0")
                            .withBlend(BlendFunction.TRANSLUCENT)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(false)
                            .build()
            );
        }

        if (dataBuffer == null) {
            dataBuffer = MemoryUtil.memAlloc(UNIFORM_BYTES);
        }

        if (dummyVertexBuffer == null || dummyVertexBuffer.isClosed()) {
            ByteBuffer dummyData = MemoryUtil.memAlloc(4);
            try {
                dummyData.putInt(0);
                dummyData.flip();
                dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "universalmod:saturation_dummy_vertex",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                        dummyData
                );
            } finally {
                MemoryUtil.memFree(dummyData);
            }
        }

        if (uniformBuffer == null || uniformBuffer.isClosed() || uniformBuffer.size() < UNIFORM_BYTES) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:saturation_uniform",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_BYTES
            );
        }

        if (tempTexture != null && tempTextureView != null
                && width == lastWidth && height == lastHeight) {
            return;
        }

        closeTemporaryTexture();
        tempTexture = RenderSystem.getDevice().createTexture(
                () -> "universalmod:saturation_temp",
                GpuTexture.USAGE_COPY_DST
                        | GpuTexture.USAGE_TEXTURE_BINDING
                        | GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8,
                width,
                height,
                1,
                1
        );
        tempTextureView = RenderSystem.getDevice().createTextureView(tempTexture);
        lastWidth = width;
        lastHeight = height;
    }

    private static void apply(GpuTextureView targetView, GpuTextureView sourceView, float saturation) {
        dataBuffer.clear();
        dataBuffer.putFloat(saturation);
        dataBuffer.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(uniformBuffer.slice(0, UNIFORM_BYTES), dataBuffer);

        try (RenderPass renderPass = encoder.createRenderPass(
                () -> "universalmod:saturation_pass",
                targetView,
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(pipeline);
            renderPass.setVertexBuffer(0, dummyVertexBuffer);
            renderPass.bindTexture("Sampler0", sourceView, RenderSampler.linear());
            renderPass.setUniform("SaturationData", uniformBuffer.slice(0, UNIFORM_BYTES));
            renderPass.draw(0, 6);
        }
    }

    private static boolean isReady() {
        return pipeline != null
                && dataBuffer != null
                && dummyVertexBuffer != null
                && !dummyVertexBuffer.isClosed()
                && uniformBuffer != null
                && !uniformBuffer.isClosed()
                && tempTexture != null
                && tempTextureView != null;
    }

    private static void closeGpuResources() {
        closeTemporaryTexture();

        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        if (dummyVertexBuffer != null) {
            dummyVertexBuffer.close();
            dummyVertexBuffer = null;
        }
        if (dataBuffer != null) {
            MemoryUtil.memFree(dataBuffer);
            dataBuffer = null;
        }
    }

    private static void closeTemporaryTexture() {
        if (tempTextureView != null) {
            tempTextureView.close();
            tempTextureView = null;
        }
        if (tempTexture != null) {
            tempTexture.close();
            tempTexture = null;
        }
        lastWidth = -1;
        lastHeight = -1;
    }
}
