package universalmod.utils.render.world;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.OptionalDouble;

public final class BlockOverlayShaderRenderer {
    private static final double SHAPE_EPSILON = 0.002D;
    private static final int UNIFORM_SIZE = 32;
    private static final int ALLOCATOR_SIZE = 786432;
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static RenderPipeline nebulaDepth;
    private static RenderPipeline nebulaNoDepth;
    private static RenderPipeline cobwebDepth;
    private static RenderPipeline cobwebNoDepth;
    private static RenderPipeline plasmaDepth;
    private static RenderPipeline plasmaNoDepth;
    private static RenderPipeline starfieldDepth;
    private static RenderPipeline starfieldNoDepth;
    private static GpuBuffer uniformBuffer;

    public boolean renderFill(Minecraft client,
                              com.mojang.blaze3d.vertex.PoseStack matrices,
                              Vec3 cameraPos,
                              Vec3 origin,
                              VoxelShape shape,
                              ShaderMode mode,
                              Color color,
                              float transparency,
                              float speed,
                              boolean throughWalls) {
        if (client == null || matrices == null || cameraPos == null || origin == null || shape == null || mode == null) {
            return false;
        }
        if (mode == ShaderMode.NONE || shape.isEmpty() || color == null) {
            return false;
        }
        if (transparency <= 0.001F || color.getAlpha() <= 0) {
            return false;
        }

        RenderPipeline pipeline = getPipeline(mode, throughWalls);
        if (pipeline == null || client.getMainRenderTarget() == null
                || client.getMainRenderTarget().getColorTextureView() == null
                || client.getMainRenderTarget().getDepthTextureView() == null) {
            return false;
        }

        try (ByteBufferBuilder allocator = new ByteBufferBuilder(ALLOCATOR_SIZE);
             MeshData mesh = buildMesh(allocator, matrices.last().pose(), cameraPos, origin, shape, color, transparency)) {
            if (mesh == null || mesh.drawState().vertexCount() <= 0 || mesh.drawState().indexCount() <= 0) {
                return false;
            }

            GpuBuffer vertices = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:block_overlay_" + mode.name().toLowerCase(Locale.ROOT),
                    GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer()
            );
            try {
                updateUniform(client, color, transparency, speed);
                draw(client, pipeline, mesh, vertices);
            } finally {
                vertices.close();
            }
        }
        return true;
    }

    public static void close() {
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
        nebulaDepth = null;
        nebulaNoDepth = null;
        cobwebDepth = null;
        cobwebNoDepth = null;
        plasmaDepth = null;
        plasmaNoDepth = null;
        starfieldDepth = null;
        starfieldNoDepth = null;
    }

    private static MeshData buildMesh(ByteBufferBuilder allocator,
                                      Matrix4f positionMatrix,
                                      Vec3 cameraPos,
                                      Vec3 origin,
                                      VoxelShape shape,
                                      Color color,
                                      float transparency) {
        BufferBuilder buffer = new BufferBuilder(allocator, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vec3 relativeOrigin = origin.subtract(cameraPos);
        int alpha = Math.round(Mth.clamp((color.getAlpha() / 255.0F) * transparency, 0.0F, 1.0F) * 255.0F);
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();

        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> renderFilledBox(
                positionMatrix,
                buffer,
                (float) (relativeOrigin.x + minX - SHAPE_EPSILON),
                (float) (relativeOrigin.y + minY - SHAPE_EPSILON),
                (float) (relativeOrigin.z + minZ - SHAPE_EPSILON),
                (float) (relativeOrigin.x + maxX + SHAPE_EPSILON),
                (float) (relativeOrigin.y + maxY + SHAPE_EPSILON),
                (float) (relativeOrigin.z + maxZ + SHAPE_EPSILON),
                red,
                green,
                blue,
                alpha
        ));

        return buffer.buildOrThrow();
    }

    private static void renderFilledBox(Matrix4f matrix,
                                        BufferBuilder buffer,
                                        float minX,
                                        float minY,
                                        float minZ,
                                        float maxX,
                                        float maxY,
                                        float maxZ,
                                        int red,
                                        int green,
                                        int blue,
                                        int alpha) {
        vertex(matrix, buffer, minX, minY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, minY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, maxY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, maxY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, minY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, minY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, maxY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, maxY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, minY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, minY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, maxY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, maxY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, minY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, minY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, maxY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, maxY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, maxY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, maxY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, maxY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, maxY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, minY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, minY, minZ, red, green, blue, alpha);
        vertex(matrix, buffer, maxX, minY, maxZ, red, green, blue, alpha);
        vertex(matrix, buffer, minX, minY, maxZ, red, green, blue, alpha);
    }

    private static void vertex(Matrix4f matrix, BufferBuilder buffer, float x, float y, float z, int red, int green, int blue, int alpha) {
        buffer.addVertex(matrix, x, y, z).setColor(red, green, blue, alpha);
    }

    private static void updateUniform(Minecraft client, Color color, float transparency, float speed) {
        ensureUniformBuffer();
        float time = (float) (System.nanoTime() / 1.0E9D) * speed;
        float width = client.getWindow() == null ? 1.0F : Math.max(1.0F, client.getWindow().getWidth());
        float height = client.getWindow() == null ? 1.0F : Math.max(1.0F, client.getWindow().getHeight());
        float alpha = Mth.clamp((color.getAlpha() / 255.0F) * transparency, 0.0F, 1.0F);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc(UNIFORM_SIZE);
            data.putFloat(0, time);
            data.putFloat(4, width);
            data.putFloat(8, height);
            data.putFloat(12, alpha);
            data.putFloat(16, color.getRed() / 255.0F);
            data.putFloat(20, color.getGreen() / 255.0F);
            data.putFloat(24, color.getBlue() / 255.0F);
            data.putFloat(28, 1.0F);
            encoder.writeToBuffer(uniformBuffer.slice(0, UNIFORM_SIZE), data);
        }
    }

    private static void ensureUniformBuffer() {
        if (uniformBuffer == null || uniformBuffer.isClosed() || uniformBuffer.size() < UNIFORM_SIZE) {
            if (uniformBuffer != null) {
                uniformBuffer.close();
            }
            uniformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "universalmod:block_overlay_uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    UNIFORM_SIZE
            );
        }
    }

    private static void draw(Minecraft client, RenderPipeline pipeline, MeshData mesh, GpuBuffer vertices) {
        MeshData.DrawState drawState = mesh.drawState();
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(drawState.mode());
        GpuBuffer indices = indexBuffer.getBuffer(drawState.indexCount());
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrix(),
                COLOR_MODULATOR,
                MODEL_OFFSET,
                TEXTURE_MATRIX
        );

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "universalmod:block_overlay_shader",
                client.getMainRenderTarget().getColorTextureView(),
                java.util.OptionalInt.empty(),
                client.getMainRenderTarget().getDepthTextureView(),
                OptionalDouble.empty()
        )) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform("NebulaTimeData", uniformBuffer);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexBuffer.type());
            pass.drawIndexed(0, 0, drawState.indexCount(), 1);
        }
    }

    private static RenderPipeline getPipeline(ShaderMode mode, boolean throughWalls) {
        initPipelines();
        return switch (mode) {
            case NEBULA -> throughWalls ? nebulaNoDepth : nebulaDepth;
            case COBWEB -> throughWalls ? cobwebNoDepth : cobwebDepth;
            case PLASMA -> throughWalls ? plasmaNoDepth : plasmaDepth;
            case STARFIELD -> throughWalls ? starfieldNoDepth : starfieldDepth;
            case NONE -> null;
        };
    }

    private static void initPipelines() {
        if (nebulaDepth != null) {
            return;
        }
        nebulaDepth = registerPipeline("block_overlay_nebula_depth", "block_overlay_nebula", DepthTestFunction.LEQUAL_DEPTH_TEST);
        nebulaNoDepth = registerPipeline("block_overlay_nebula_no_depth", "block_overlay_nebula", DepthTestFunction.NO_DEPTH_TEST);
        cobwebDepth = registerPipeline("block_overlay_cobweb_depth", "block_overlay_cobweb", DepthTestFunction.LEQUAL_DEPTH_TEST);
        cobwebNoDepth = registerPipeline("block_overlay_cobweb_no_depth", "block_overlay_cobweb", DepthTestFunction.NO_DEPTH_TEST);
        plasmaDepth = registerPipeline("block_overlay_plasma_depth", "block_overlay_plasma", DepthTestFunction.LEQUAL_DEPTH_TEST);
        plasmaNoDepth = registerPipeline("block_overlay_plasma_no_depth", "block_overlay_plasma", DepthTestFunction.NO_DEPTH_TEST);
        starfieldDepth = registerPipeline("block_overlay_starfield_depth", "block_overlay_starfield", DepthTestFunction.LEQUAL_DEPTH_TEST);
        starfieldNoDepth = registerPipeline("block_overlay_starfield_no_depth", "block_overlay_starfield", DepthTestFunction.NO_DEPTH_TEST);
    }

    private static RenderPipeline registerPipeline(String locationSuffix, String shaderName, DepthTestFunction depthTestFunction) {
        return RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("universalmod", "pipeline/world/" + locationSuffix))
                        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                        .withFragmentShader(Identifier.fromNamespaceAndPath("universalmod", shaderName))
                        .withUniform("NebulaTimeData", UniformType.UNIFORM_BUFFER)
                        .withCull(false)
                        .withDepthTestFunction(depthTestFunction)
                        .withDepthWrite(false)
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .build()
        );
    }

    public enum ShaderMode {
        NONE,
        NEBULA,
        COBWEB,
        PLASMA,
        STARFIELD;

        public static ShaderMode fromSetting(String value) {
            if (value == null) {
                return NONE;
            }
            try {
                return ShaderMode.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return NONE;
            }
        }
    }
}
