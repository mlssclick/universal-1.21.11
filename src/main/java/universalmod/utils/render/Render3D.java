package universalmod.utils.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import universalmod.IMinecraft;
import universalmod.api.events.impl.WorldRenderEvent;
import universalmod.mixin.accessor.MixinRenderPipelines;
import universalmod.utils.render.color.ColorUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Render3D implements IMinecraft {
    private static final double MAX_SAFE_COORD = 30_000_000.0;
    private static final Map<VoxelShape, List<AABB>> SHAPE_BOXES = new HashMap<>();

    public static final List<Line> LINE_DEPTH = new ArrayList<>();
    public static final List<Line> LINE = new ArrayList<>();
    public static final List<Line> LINE_OVERLAY = new ArrayList<>();
    public static final List<Quad> QUAD_DEPTH = new ArrayList<>();
    public static final List<Quad> QUAD = new ArrayList<>();

    public static final Matrix4f lastProjMat = new Matrix4f();
    public static final Matrix4f lastModMat = new Matrix4f();
    public static final Matrix4f lastWorldSpaceMatrix = new Matrix4f();

    public static PoseStack.Pose lastWorldSpaceEntry = new PoseStack().last();
    public static float lastTickDelta = 1.0f;
    public static Vec3 lastCameraPos = Vec3.ZERO;
    public static Quaternionf lastCameraRotation = new Quaternionf();
    private static final Vector3f LINE_NORMAL = new Vector3f();

    private static final BlendFunction STANDARD_BLEND = new BlendFunction(
            SourceFactor.SRC_ALPHA,
            DestFactor.ONE_MINUS_SRC_ALPHA,
            SourceFactor.ONE,
            DestFactor.ZERO
    );
    private static final RenderPipeline.Snippet UNIVERSALMOD_LINES_SNIPPET = RenderPipeline.builder(MixinRenderPipelines.universalmod$getLinesSnippet())
            .withBlend(STANDARD_BLEND)
            .withDepthWrite(false)
            .withCull(false)
            .buildSnippet();

    private static final RenderType UNIVERSALMOD_LINES_OVERLAY = RenderLayerFactory.create(
            "rendertype/universalmod_lines_overlay",
            256,
            RenderPipeline.builder(UNIVERSALMOD_LINES_SNIPPET)
                    .withLocation("pipelines/universalmod_lines_overlay")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    private static final RenderType UNIVERSALMOD_LINES_NO_DEPTH = RenderLayerFactory.create(
            "rendertype/universalmod_lines_no_depth",
            256,
            RenderPipeline.builder(UNIVERSALMOD_LINES_SNIPPET)
                    .withLocation("pipelines/universalmod_lines_no_depth")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    private static final RenderType UNIVERSALMOD_FILLED_BOX_NO_DEPTH = RenderLayerFactory.create(
            "rendertype/universalmod_filled_box_no_depth",
            256,
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation("pipelines/universalmod_filled_box_no_depth")
                    .withBlend(STANDARD_BLEND)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private Render3D() {
    }

    public static void setLastWorldSpaceEntry(PoseStack.Pose entry) {
        if (entry != null) {
            lastWorldSpaceEntry = entry;
        }
    }

    public static void setLastTickDelta(float tickDelta) {
        lastTickDelta = Float.isFinite(tickDelta) ? tickDelta : 1.0f;
    }

    public static void setLastCameraPos(Vec3 cameraPos) {
        if (cameraPos != null && isFinite(cameraPos)) {
            lastCameraPos = cameraPos;
        }
    }

    public static void setLastCameraRotation(Quaternionf rotation) {
        if (rotation != null) {
            lastCameraRotation = rotation;
        }
    }

    public static void onWorldRender(WorldRenderEvent e) {
        if (mc.level == null || mc.player == null) {
            clearQueues();
            return;
        }

        PoseStack matrices = e.getStack();
        MultiBufferSource.BufferSource immediate = mc.renderBuffers().bufferSource();

        Vec3 cameraPos = lastCameraPos;
        if (!isFinite(cameraPos)) {
            clearQueues();
            return;
        }

        renderQuads(matrices, immediate, cameraPos);
        renderLines(matrices, immediate, cameraPos);

        immediate.endBatch();
    }

    public static void render(WorldRenderEvent e) {
        onWorldRender(e);
    }

    private static void clearQueues() {
        LINE_DEPTH.clear();
        LINE.clear();
        LINE_OVERLAY.clear();
        QUAD_DEPTH.clear();
        QUAD.clear();
    }

    private static void renderLines(PoseStack matrices, MultiBufferSource.BufferSource immediate, Vec3 cameraPos) {
        if (LINE.isEmpty() && LINE_DEPTH.isEmpty() && LINE_OVERLAY.isEmpty()) return;

        try {
            renderLineBatch(matrices, immediate, cameraPos, LINE_DEPTH, RenderTypes.lines());
            renderLineBatch(matrices, immediate, cameraPos, LINE, UNIVERSALMOD_LINES_NO_DEPTH);
            renderLineBatch(matrices, immediate, cameraPos, LINE_OVERLAY, UNIVERSALMOD_LINES_OVERLAY);
        } finally {
            LINE.clear();
            LINE_DEPTH.clear();
            LINE_OVERLAY.clear();
        }
    }

    private static void renderLineBatch(PoseStack matrices, MultiBufferSource.BufferSource immediate, Vec3 cameraPos, List<Line> lines, RenderType layer) {
        if (lines.isEmpty()) {
            return;
        }

        VertexConsumer buffer = immediate.getBuffer(layer);
        for (Line line : lines) {
            drawLineVertex(matrices, buffer, line, cameraPos);
        }
        immediate.endBatch(layer);
    }

    private static void drawLineVertex(PoseStack matrices, VertexConsumer buffer, Line line, Vec3 cameraPos) {
        if (line == null || !isFinite(cameraPos) || !isFinite(line.start) || !isFinite(line.end)) {
            return;
        }
        PoseStack.Pose entry = line.entry != null ? line.entry : matrices.last();
        Vector3f normal = lineNormal(line.start, line.end);
        float width = sanitizeLineWidth(line.width);

        float x1 = (float) (line.start.x - cameraPos.x);
        float y1 = (float) (line.start.y - cameraPos.y);
        float z1 = (float) (line.start.z - cameraPos.z);

        float x2 = (float) (line.end.x - cameraPos.x);
        float y2 = (float) (line.end.y - cameraPos.y);
        float z2 = (float) (line.end.z - cameraPos.z);

        buffer.addVertex(entry, x1, y1, z1)
                .setColor(line.colorStart)
                .setNormal(entry, normal)
                .setLineWidth(width);
        buffer.addVertex(entry, x2, y2, z2)
                .setColor(line.colorEnd)
                .setNormal(entry, normal)
                .setLineWidth(width);
    }

    private static void renderQuads(PoseStack matrices, MultiBufferSource.BufferSource immediate, Vec3 cameraPos) {
        if (QUAD.isEmpty() && QUAD_DEPTH.isEmpty()) return;

        if (!QUAD_DEPTH.isEmpty()) {
            RenderType layer = RenderTypes.debugFilledBox();
            VertexConsumer buffer = immediate.getBuffer(layer);
            for (Quad quad : QUAD_DEPTH) {
                drawQuadVertex(matrices, buffer, quad, cameraPos);
            }
            immediate.endBatch(layer);
        }

        if (!QUAD.isEmpty()) {
            VertexConsumer buffer = immediate.getBuffer(UNIVERSALMOD_FILLED_BOX_NO_DEPTH);
            for (Quad quad : QUAD) {
                drawQuadVertex(matrices, buffer, quad, cameraPos);
            }
            immediate.endBatch(UNIVERSALMOD_FILLED_BOX_NO_DEPTH);
        }

        QUAD.clear();
        QUAD_DEPTH.clear();
    }

    private static void drawQuadVertex(PoseStack matrices, VertexConsumer buffer, Quad quad, Vec3 cameraPos) {
        if (quad == null || !isFinite(cameraPos) || !isFinite(quad.x) || !isFinite(quad.y) || !isFinite(quad.w) || !isFinite(quad.z)) {
            return;
        }
        PoseStack.Pose entry = quad.entry != null ? quad.entry : matrices.last();

        float x1 = (float) (quad.x.x - cameraPos.x);
        float y1 = (float) (quad.x.y - cameraPos.y);
        float z1 = (float) (quad.x.z - cameraPos.z);

        float x2 = (float) (quad.y.x - cameraPos.x);
        float y2 = (float) (quad.y.y - cameraPos.y);
        float z2 = (float) (quad.y.z - cameraPos.z);

        float x3 = (float) (quad.w.x - cameraPos.x);
        float y3 = (float) (quad.w.y - cameraPos.y);
        float z3 = (float) (quad.w.z - cameraPos.z);

        float x4 = (float) (quad.z.x - cameraPos.x);
        float y4 = (float) (quad.z.y - cameraPos.y);
        float z4 = (float) (quad.z.z - cameraPos.z);

        buffer.addVertex(entry, x1, y1, z1).setColor(quad.color);
        buffer.addVertex(entry, x2, y2, z2).setColor(quad.color);
        buffer.addVertex(entry, x3, y3, z3).setColor(quad.color);
        buffer.addVertex(entry, x4, y4, z4).setColor(quad.color);
    }

    public static void drawLineGradient(Vec3 start, Vec3 end, int colorStart, int colorEnd, float width, boolean depth) {
        if (!isFinite(start) || !isFinite(end)) {
            return;
        }
        Line line = new Line(null, start, end, colorStart, colorEnd, sanitizeLineWidth(width));
        if (depth) LINE_DEPTH.add(line);
        else LINE.add(line);
    }

    private static Vector3f lineNormal(Vec3 start, Vec3 end) {
        float x = (float) (start.x - end.x);
        float y = (float) (start.y - end.y);
        float z = (float) (start.z - end.z);
        float length = Mth.sqrt(x * x + y * y + z * z);
        if (length < 0.0001F) {
            return LINE_NORMAL.set(0.0F, 1.0F, 0.0F);
        }
        return LINE_NORMAL.set(x / length, y / length, z / length);
    }

    public static void drawShape(BlockPos blockPos, VoxelShape voxelShape, int color, float width) {
        drawShape(blockPos, voxelShape, color, width, true, false);
    }

    public static void drawShape(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        List<AABB> boxes = SHAPE_BOXES.computeIfAbsent(voxelShape, VoxelShape::toAabbs);
        boxes.forEach(box -> {
            AABB offsetBox = box.move(blockPos);
            drawBox(offsetBox, color, width, true, fill, depth);
        });
    }

    public static void drawBox(AABB box, int color, float width) {
        drawBox(box, color, width, true, true, false);
    }

    public static void drawBox(AABB box, int color, float width, boolean line, boolean fill, boolean depth) {
        drawBox(null, box, color, width, line, fill, depth);
    }

    public static void drawBox(PoseStack.Pose entry, AABB box, int color, float width, boolean line, boolean fill, boolean depth) {
        if (!isFinite(box)) {
            return;
        }
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;

        if (fill) {
            int fillColor = ColorUtil.multAlpha(color, 0.3f);
            drawQuad(entry, new Vec3(x1, y1, z1), new Vec3(x2, y1, z1), new Vec3(x2, y1, z2), new Vec3(x1, y1, z2), fillColor, depth);
            drawQuad(entry, new Vec3(x1, y1, z1), new Vec3(x1, y2, z1), new Vec3(x2, y2, z1), new Vec3(x2, y1, z1), fillColor, depth);
            drawQuad(entry, new Vec3(x2, y1, z1), new Vec3(x2, y2, z1), new Vec3(x2, y2, z2), new Vec3(x2, y1, z2), fillColor, depth);
            drawQuad(entry, new Vec3(x1, y1, z2), new Vec3(x2, y1, z2), new Vec3(x2, y2, z2), new Vec3(x1, y2, z2), fillColor, depth);
            drawQuad(entry, new Vec3(x1, y1, z1), new Vec3(x1, y1, z2), new Vec3(x1, y2, z2), new Vec3(x1, y2, z1), fillColor, depth);
            drawQuad(entry, new Vec3(x1, y2, z1), new Vec3(x1, y2, z2), new Vec3(x2, y2, z2), new Vec3(x2, y2, z1), fillColor, depth);
        }

        if (line) {
            drawLine(entry, x1, y1, z1, x2, y1, z1, color, width, depth);
            drawLine(entry, x2, y1, z1, x2, y1, z2, color, width, depth);
            drawLine(entry, x2, y1, z2, x1, y1, z2, color, width, depth);
            drawLine(entry, x1, y1, z2, x1, y1, z1, color, width, depth);
            drawLine(entry, x1, y1, z2, x1, y2, z2, color, width, depth);
            drawLine(entry, x1, y1, z1, x1, y2, z1, color, width, depth);
            drawLine(entry, x2, y1, z2, x2, y2, z2, color, width, depth);
            drawLine(entry, x2, y1, z1, x2, y2, z1, color, width, depth);
            drawLine(entry, x1, y2, z1, x2, y2, z1, color, width, depth);
            drawLine(entry, x2, y2, z1, x2, y2, z2, color, width, depth);
            drawLine(entry, x2, y2, z2, x1, y2, z2, color, width, depth);
            drawLine(entry, x1, y2, z2, x1, y2, z1, color, width, depth);
        }
    }

    public static void drawLine(PoseStack.Pose entry, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, float width, boolean depth) {
        drawLine(entry, new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ), color, color, width, depth);
    }

    public static void drawLine(Vec3 start, Vec3 end, int color, float width, boolean depth) {
        drawLine(null, start, end, color, color, width, depth);
    }

    public static void drawLine(PoseStack.Pose entry, Vec3 start, Vec3 end, int colorStart, int colorEnd, float width, boolean depth) {
        if (!isFinite(start) || !isFinite(end)) {
            return;
        }
        Line line = new Line(entry, start, end, colorStart, colorEnd, sanitizeLineWidth(width));
        if (depth) LINE_DEPTH.add(line);
        else LINE.add(line);
    }

    public static void drawLineOverlay(Vec3 start, Vec3 end, int color, float width) {
        drawLineOverlay(null, start, end, color, color, width);
    }

    public static void drawLineOverlay(PoseStack.Pose entry, Vec3 start, Vec3 end, int colorStart, int colorEnd, float width) {
        if (!isFinite(start) || !isFinite(end)) {
            return;
        }
        LINE_OVERLAY.add(new Line(entry, start, end, colorStart, colorEnd, sanitizeLineWidth(width)));
    }

    public static void drawQuad(Vec3 x, Vec3 y, Vec3 w, Vec3 z, int color, boolean depth) {
        drawQuad(null, x, y, w, z, color, depth);
    }

    public static void drawQuad(PoseStack.Pose entry, Vec3 x, Vec3 y, Vec3 w, Vec3 z, int color, boolean depth) {
        if (!isFinite(x) || !isFinite(y) || !isFinite(w) || !isFinite(z)) {
            return;
        }
        Quad quad = new Quad(entry, x, y, w, z, color);
        if (depth) QUAD_DEPTH.add(quad);
        else QUAD.add(quad);
    }

    private static float sanitizeLineWidth(float width) {
        if (!Float.isFinite(width)) {
            return 1.0f;
        }
        return Math.clamp(width, 0.1f, 16.0f);
    }

    private static boolean isFinite(Vec3 vec) {
        return vec != null && isFinite(vec.x) && isFinite(vec.y) && isFinite(vec.z);
    }

    private static boolean isFinite(AABB box) {
        return box != null
                && isFinite(box.minX) && isFinite(box.minY) && isFinite(box.minZ)
                && isFinite(box.maxX) && isFinite(box.maxY) && isFinite(box.maxZ);
    }

    private static boolean isFinite(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_SAFE_COORD;
    }

    public record Line(PoseStack.Pose entry, Vec3 start, Vec3 end, int colorStart, int colorEnd, float width) {
    }

    public record Quad(PoseStack.Pose entry, Vec3 x, Vec3 y, Vec3 w, Vec3 z, int color) {
    }

}
