package universalmod.api.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.WorldRenderEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.utils.render.Render3D;
import universalmod.utils.render.world.BlockOverlayShaderRenderer;
import universalmod.utils.render.world.BlockOverlayShaderRenderer.ShaderMode;

import java.awt.Color;

public final class BlockOverlay extends Module {
    private static final String SHADER_NONE = "None";
    private static final String SHADER_NEBULA = "Nebula";
    private static final String SHADER_COBWEB = "Cobweb";
    private static final String SHADER_PLASMA = "Plasma";
    private static final String SHADER_STARFIELD = "Starfield";
    private static final double SHAPE_EPSILON = 0.001D;

    private static BlockOverlay instance;

    private final BooleanSetting outline = register(new BooleanSetting("Outline", "Draw block outline.", true));
    private final NumberSetting lineWidth = register(new NumberSetting("Line Width", "Outline line width.", 2.5D, 0.5D, 8.0D, 0.1D));
    private final ColorSetting outlineColor = register(new ColorSetting("Outline Color", "Outline color.", new Color(0, 0, 0, 255)));

    private final BooleanSetting fill = register(new BooleanSetting("Fill", "Draw block fill.", true));
    private final ModeSetting fillShader = register(new ModeSetting("Shader", "Fill shader.", SHADER_NONE, SHADER_NONE, SHADER_NEBULA, SHADER_COBWEB, SHADER_PLASMA, SHADER_STARFIELD));
    private final ColorSetting fillColor = register(new ColorSetting("Fill Color", "Fill color.", new Color(0, 0, 0, 102)));
    private final NumberSetting fillOpacity = register(new NumberSetting("Fill Opacity", "Non-shader fill opacity.", 40.0D, 0.0D, 100.0D, 1.0D));
    private final BooleanSetting fillChroma = register(new BooleanSetting("Fill Chroma", "Animate fill color.", false));
    private final NumberSetting fillSaturation = register(new NumberSetting("Fill Saturation", "Fill chroma saturation.", 1.0D, 0.0D, 1.0D, 0.01D));
    private final NumberSetting fillBrightness = register(new NumberSetting("Fill Brightness", "Fill chroma brightness.", 1.0D, 0.0D, 1.0D, 0.01D));
    private final NumberSetting fillSpeed = register(new NumberSetting("Fill Speed", "Fill chroma speed.", 0.25D, 0.0D, 1.0D, 0.01D));
    private final NumberSetting shaderTransparency = register(new NumberSetting("Shader Transparency", "Shader fill transparency.", 1.0D, 0.0D, 1.0D, 0.01D));
    private final ColorSetting nebulaColor = register(new ColorSetting("Nebula Color", "Nebula shader tint.", new Color(0, 255, 171, 255)));
    private final NumberSetting nebulaSpeed = register(new NumberSetting("Nebula Speed", "Nebula shader speed.", 1.5D, 0.0D, 4.0D, 0.01D));
    private final ColorSetting cobwebColor = register(new ColorSetting("Cobweb Color", "Cobweb shader tint.", new Color(234, 234, 240, 255)));
    private final NumberSetting cobwebSpeed = register(new NumberSetting("Cobweb Speed", "Cobweb shader speed.", 1.5D, 0.0D, 4.0D, 0.01D));
    private final ColorSetting plasmaColor = register(new ColorSetting("Plasma Color", "Plasma shader tint.", new Color(255, 96, 210, 255)));
    private final NumberSetting plasmaSpeed = register(new NumberSetting("Plasma Speed", "Plasma shader speed.", 1.5D, 0.0D, 4.0D, 0.01D));
    private final ColorSetting starfieldColor = register(new ColorSetting("Starfield Color", "Starfield shader tint.", new Color(132, 198, 255, 255)));
    private final NumberSetting starfieldSpeed = register(new NumberSetting("Starfield Speed", "Starfield shader speed.", 0.6D, 0.0D, 4.0D, 0.01D));

    private final BooleanSetting smoothMovement = register(new BooleanSetting("Smooth Movement", "Smooth block overlay movement.", true));
    private final NumberSetting smoothSpeed = register(new NumberSetting("Smooth Speed", "Smooth movement speed.", 14.0D, 1.0D, 30.0D, 0.5D));

    private final BlockOverlayShaderRenderer shaderRenderer = new BlockOverlayShaderRenderer();
    private Vec3 animatedTargetPos;
    private Vec3 animationStartPos;
    private Vec3 animationEndPos;
    private float animationProgress = 1.0F;
    private long lastFrameNanos = System.nanoTime();

    public BlockOverlay() {
        super("Block Overlay", "Draws a custom outline and fill for the block under your crosshair.", ModuleCategory.RENDER);
        instance = this;
        configureVisibility();
    }

    public static boolean shouldSuppressVanillaOutline(Minecraft client) {
        return instance != null && instance.isEnabled()
                && (instance.outline.getValue() || instance.fill.getValue())
                && instance.resolveTarget(client) != null;
    }

    @Override
    protected void onDisable() {
        resetSmoothingState();
        BlockOverlayShaderRenderer.close();
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        if (!outline.getValue() && !fill.getValue()) {
            return;
        }

        TargetBlock target = resolveTarget(mc);
        RenderTarget renderTarget = resolveRenderTarget(target);
        if (renderTarget == null) {
            return;
        }

        boolean renderThroughWalls = false;
        if (fill.getValue()) {
            ShaderMode shaderMode = ShaderMode.fromSetting(fillShader.getValue());
            if (shaderMode == ShaderMode.NONE) {
                int fillRgba = resolveColor(fillColor.getValue(), fillChroma.getValue(), fillSaturation.getValue(), fillBrightness.getValue(), fillSpeed.getValue());
                fillRgba = applyAlpha(fillRgba, fillOpacity.getFloat());
                drawFilledShape(renderTarget.origin(), renderTarget.shape(), fillRgba, !renderThroughWalls);
            } else {
                shaderRenderer.renderFill(
                        mc,
                        event.getStack(),
                        Render3D.lastCameraPos,
                        renderTarget.origin(),
                        renderTarget.shape(),
                        shaderMode,
                        resolveShaderColor(shaderMode),
                        shaderTransparency.getFloat(),
                        resolveShaderSpeed(shaderMode),
                        renderThroughWalls
                );
            }
        }

        if (outline.getValue()) {
            int outlineRgba = outlineColor.getValue().getRGB();
            drawOutlineShape(renderTarget.origin(), renderTarget.shape(), outlineRgba, lineWidth.getFloat(), !renderThroughWalls);
        }
    }

    private void configureVisibility() {
        lineWidth.visibleWhen(outline::getValue);
        outlineColor.visibleWhen(outline::getValue);

        fillShader.visibleWhen(fill::getValue);
        fillColor.visibleWhen(() -> fill.getValue() && isShaderNoneSelected());
        fillOpacity.visibleWhen(() -> fill.getValue() && isShaderNoneSelected());
        fillChroma.visibleWhen(() -> fill.getValue() && isShaderNoneSelected());
        fillSaturation.visibleWhen(() -> fill.getValue() && fillChroma.getValue() && isShaderNoneSelected());
        fillBrightness.visibleWhen(() -> fill.getValue() && fillChroma.getValue() && isShaderNoneSelected());
        fillSpeed.visibleWhen(() -> fill.getValue() && fillChroma.getValue() && isShaderNoneSelected());
        shaderTransparency.visibleWhen(() -> fill.getValue() && !isShaderNoneSelected());
        nebulaColor.visibleWhen(() -> fill.getValue() && SHADER_NEBULA.equals(fillShader.getValue()));
        nebulaSpeed.visibleWhen(() -> fill.getValue() && SHADER_NEBULA.equals(fillShader.getValue()));
        cobwebColor.visibleWhen(() -> fill.getValue() && SHADER_COBWEB.equals(fillShader.getValue()));
        cobwebSpeed.visibleWhen(() -> fill.getValue() && SHADER_COBWEB.equals(fillShader.getValue()));
        plasmaColor.visibleWhen(() -> fill.getValue() && SHADER_PLASMA.equals(fillShader.getValue()));
        plasmaSpeed.visibleWhen(() -> fill.getValue() && SHADER_PLASMA.equals(fillShader.getValue()));
        starfieldColor.visibleWhen(() -> fill.getValue() && SHADER_STARFIELD.equals(fillShader.getValue()));
        starfieldSpeed.visibleWhen(() -> fill.getValue() && SHADER_STARFIELD.equals(fillShader.getValue()));
        smoothSpeed.visibleWhen(smoothMovement::getValue);
    }

    private RenderTarget resolveRenderTarget(TargetBlock target) {
        long nowNanos = System.nanoTime();
        float deltaSeconds = Mth.clamp((nowNanos - lastFrameNanos) / 1_000_000_000.0F, 0.0F, 0.1F);
        lastFrameNanos = nowNanos;

        if (target == null) {
            resetSmoothingState();
            return null;
        }

        Vec3 targetPos = Vec3.atLowerCornerOf(target.pos());
        if (!smoothMovement.getValue()) {
            animatedTargetPos = targetPos;
            animationStartPos = targetPos;
            animationEndPos = targetPos;
            animationProgress = 1.0F;
            return new RenderTarget(targetPos, target.shape());
        }

        if (animatedTargetPos == null || animationEndPos == null || animatedTargetPos.distanceToSqr(targetPos) > 64.0D) {
            animatedTargetPos = targetPos;
            animationStartPos = targetPos;
            animationEndPos = targetPos;
            animationProgress = 1.0F;
            return new RenderTarget(targetPos, target.shape());
        }

        if (animationEndPos.distanceToSqr(targetPos) > 1.0E-6D) {
            animationStartPos = animatedTargetPos;
            animationEndPos = targetPos;
            animationProgress = 0.0F;
        }

        if (animationProgress < 1.0F) {
            float progressStep = deltaSeconds * smoothSpeed.getFloat() * 0.45F;
            animationProgress = Mth.clamp(animationProgress + progressStep, 0.0F, 1.0F);
        }

        float eased = easeOutCubic(animationProgress);
        animatedTargetPos = new Vec3(
                Mth.lerp(eased, animationStartPos.x, animationEndPos.x),
                Mth.lerp(eased, animationStartPos.y, animationEndPos.y),
                Mth.lerp(eased, animationStartPos.z, animationEndPos.z)
        );

        if (animationProgress >= 1.0F || animatedTargetPos.distanceToSqr(targetPos) <= 1.0E-4D) {
            animatedTargetPos = targetPos;
            animationStartPos = targetPos;
            animationEndPos = targetPos;
            animationProgress = 1.0F;
        }

        return new RenderTarget(animatedTargetPos, target.shape());
    }

    private TargetBlock resolveTarget(Minecraft client) {
        if (!canRender(client)) {
            return null;
        }

        BlockHitResult blockHit = null;
        HitResult hitResult = client.hitResult;
        if (hitResult instanceof BlockHitResult hit && hitResult.getType() == HitResult.Type.BLOCK) {
            blockHit = hit;
        }

        if (blockHit == null || client.level == null || client.player == null) {
            return null;
        }

        return createTarget(blockHit, CollisionContext.of(client.player));
    }

    private TargetBlock createTarget(BlockHitResult hit, CollisionContext shapeContext) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape = state.getShape(mc.level, pos, shapeContext);
        if (!shape.isEmpty()) {
            return new TargetBlock(pos, shape);
        }
        return null;
    }

    private boolean canRender(Minecraft client) {
        if (!isEnabled() || client == null || client.level == null || client.player == null) {
            return false;
        }
        if (client.options.hideGui) {
            return false;
        }

        MultiPlayerGameMode gameMode = client.gameMode;
        if (gameMode == null) {
            return false;
        }
        GameType mode = gameMode.getPlayerMode();
        return mode != GameType.ADVENTURE && mode != GameType.SPECTATOR;
    }

    private void drawFilledShape(Vec3 origin, VoxelShape shape, int color, boolean depth) {
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> drawFilledBox(
                origin.x + minX - SHAPE_EPSILON,
                origin.y + minY - SHAPE_EPSILON,
                origin.z + minZ - SHAPE_EPSILON,
                origin.x + maxX + SHAPE_EPSILON,
                origin.y + maxY + SHAPE_EPSILON,
                origin.z + maxZ + SHAPE_EPSILON,
                color,
                depth
        ));
    }

    private void drawFilledBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, boolean depth) {
        Render3D.drawQuad(new Vec3(minX, minY, maxZ), new Vec3(maxX, minY, maxZ), new Vec3(maxX, maxY, maxZ), new Vec3(minX, maxY, maxZ), color, depth);
        Render3D.drawQuad(new Vec3(maxX, minY, minZ), new Vec3(minX, minY, minZ), new Vec3(minX, maxY, minZ), new Vec3(maxX, maxY, minZ), color, depth);
        Render3D.drawQuad(new Vec3(minX, minY, minZ), new Vec3(minX, minY, maxZ), new Vec3(minX, maxY, maxZ), new Vec3(minX, maxY, minZ), color, depth);
        Render3D.drawQuad(new Vec3(maxX, minY, maxZ), new Vec3(maxX, minY, minZ), new Vec3(maxX, maxY, minZ), new Vec3(maxX, maxY, maxZ), color, depth);
        Render3D.drawQuad(new Vec3(minX, maxY, maxZ), new Vec3(maxX, maxY, maxZ), new Vec3(maxX, maxY, minZ), new Vec3(minX, maxY, minZ), color, depth);
        Render3D.drawQuad(new Vec3(minX, minY, minZ), new Vec3(maxX, minY, minZ), new Vec3(maxX, minY, maxZ), new Vec3(minX, minY, maxZ), color, depth);
    }

    private void drawOutlineShape(Vec3 origin, VoxelShape shape, int color, float width, boolean depth) {
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            AABB box = new AABB(
                    origin.x + minX - SHAPE_EPSILON,
                    origin.y + minY - SHAPE_EPSILON,
                    origin.z + minZ - SHAPE_EPSILON,
                    origin.x + maxX + SHAPE_EPSILON,
                    origin.y + maxY + SHAPE_EPSILON,
                    origin.z + maxZ + SHAPE_EPSILON
            );
            drawBoxOutline(box, color, width, depth);
        });
    }

    private void drawBoxOutline(AABB box, int color, float width, boolean depth) {
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;

        Render3D.drawLine(new Vec3(x1, y1, z1), new Vec3(x2, y1, z1), color, width, depth);
        Render3D.drawLine(new Vec3(x2, y1, z1), new Vec3(x2, y1, z2), color, width, depth);
        Render3D.drawLine(new Vec3(x2, y1, z2), new Vec3(x1, y1, z2), color, width, depth);
        Render3D.drawLine(new Vec3(x1, y1, z2), new Vec3(x1, y1, z1), color, width, depth);
        Render3D.drawLine(new Vec3(x1, y1, z2), new Vec3(x1, y2, z2), color, width, depth);
        Render3D.drawLine(new Vec3(x1, y1, z1), new Vec3(x1, y2, z1), color, width, depth);
        Render3D.drawLine(new Vec3(x2, y1, z2), new Vec3(x2, y2, z2), color, width, depth);
        Render3D.drawLine(new Vec3(x2, y1, z1), new Vec3(x2, y2, z1), color, width, depth);
        Render3D.drawLine(new Vec3(x1, y2, z1), new Vec3(x2, y2, z1), color, width, depth);
        Render3D.drawLine(new Vec3(x2, y2, z1), new Vec3(x2, y2, z2), color, width, depth);
        Render3D.drawLine(new Vec3(x2, y2, z2), new Vec3(x1, y2, z2), color, width, depth);
        Render3D.drawLine(new Vec3(x1, y2, z2), new Vec3(x1, y2, z1), color, width, depth);
    }

    private static int resolveColor(Color baseColor, boolean chroma, double saturation, double brightness, double speed) {
        if (!chroma) {
            return baseColor.getRGB();
        }
        float hue = (float) ((System.currentTimeMillis() * Math.max(0.0D, speed) * 0.02D) % 360.0D);
        int rgb = Color.HSBtoRGB(
                hue / 360.0F,
                Mth.clamp((float) saturation, 0.0F, 1.0F),
                Mth.clamp((float) brightness, 0.0F, 1.0F)
        );
        int alpha = baseColor.getAlpha();
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static int applyAlpha(int color, float opacityPercent) {
        int alpha = Math.round(Mth.clamp(opacityPercent, 0.0F, 100.0F) * 2.55F);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private boolean isShaderNoneSelected() {
        return SHADER_NONE.equals(fillShader.getValue());
    }

    private Color resolveShaderColor(ShaderMode mode) {
        return switch (mode) {
            case NEBULA -> nebulaColor.getValue();
            case COBWEB -> cobwebColor.getValue();
            case PLASMA -> plasmaColor.getValue();
            case STARFIELD -> starfieldColor.getValue();
            case NONE -> fillColor.getValue();
        };
    }

    private float resolveShaderSpeed(ShaderMode mode) {
        return switch (mode) {
            case NEBULA -> nebulaSpeed.getFloat();
            case COBWEB -> cobwebSpeed.getFloat();
            case PLASMA -> plasmaSpeed.getFloat();
            case STARFIELD -> starfieldSpeed.getFloat();
            case NONE -> fillSpeed.getFloat();
        };
    }

    private void resetSmoothingState() {
        animatedTargetPos = null;
        animationStartPos = null;
        animationEndPos = null;
        animationProgress = 1.0F;
        lastFrameNanos = System.nanoTime();
    }

    private static float easeOutCubic(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    private record TargetBlock(BlockPos pos, VoxelShape shape) {
    }

    private record RenderTarget(Vec3 origin, VoxelShape shape) {
    }
}
