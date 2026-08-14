package universalmod.utils.render.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import org.joml.Quaternionf;
import universalmod.api.events.impl.WorldRenderEvent;
import universalmod.utils.render.Render3D;
import universalmod.utils.render.WorldVertex;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.pipeline.ClientPipelines;

import java.util.HashSet;
import java.util.Set;

public final class HealthIndicatorOverlay {
    private static final Identifier HEART_EMPTY = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/container.png");
    private static final Identifier HEART_RED_FULL = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/full.png");
    private static final Identifier HEART_RED_HALF = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/half.png");
    private static final Identifier HEART_YELLOW_FULL = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/absorbing_full.png");
    private static final Identifier HEART_YELLOW_HALF = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/absorbing_half.png");
    private static final float PIXEL_SIZE = 0.025F;
    private static final float HEART_SIZE = 9.0F;
    private static final int MAX_HEARTS_PER_ROW = 10;
    private static final int WHITE = ColorUtil.rgba(255, 255, 255, 255);

    private final Set<RenderType> usedRenderTypes = new HashSet<>();

    public void render(WorldRenderEvent event, Minecraft minecraft) {
        if (event == null || minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.gameRenderer == null) {
            return;
        }

        PoseStack stack = event.getStack();
        MultiBufferSource.BufferSource provider = minecraft.renderBuffers().bufferSource();
        Vec3 cameraPos = resolveCameraPos(minecraft);
        Quaternionf cameraRotation = resolveCameraRotation(minecraft);
        usedRenderTypes.clear();

        for (Player player : minecraft.level.players()) {
            if (!shouldRenderPlayer(minecraft, player)) {
                continue;
            }
            renderHearts(player, stack, provider, cameraPos, cameraRotation, event.getPartialTicks(), minecraft);
        }

        for (RenderType renderType : usedRenderTypes) {
            provider.endBatch(renderType);
        }
        usedRenderTypes.clear();
    }

    private boolean shouldRenderPlayer(Minecraft minecraft, Player player) {
        if (player == null || player.isRemoved() || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        if (player == minecraft.player && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            return false;
        }
        if (player.isInvisible()) {
            return isVisibleByRaytrace(minecraft, player);
        }
        return true;
    }

    private boolean isVisibleByRaytrace(Minecraft minecraft, Player player) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null || player == null) {
            return false;
        }
        BlockHitResult hit = minecraft.level.clip(new ClipContext(
                minecraft.player.getEyePosition(1.0F),
                player.getEyePosition(1.0F),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                minecraft.player
        ));
        return hit == null || hit.getType() != HitResult.Type.BLOCK;
    }

    private void renderHearts(Player player, PoseStack stack, MultiBufferSource.BufferSource provider, Vec3 cameraPos, Quaternionf cameraRotation, float tickDelta, Minecraft minecraft) {
        int healthRed = Mth.ceil(player.getHealth());
        int maxHealth = Mth.ceil(player.getMaxHealth());
        int healthYellow = Mth.ceil(player.getAbsorptionAmount());
        int heartsRed = Mth.ceil((float) healthRed / 2.0F);
        boolean lastRedHalf = (healthRed & 1) == 1;
        int heartsNormal = Mth.ceil((float) maxHealth / 2.0F);
        int heartsYellow = Mth.ceil((float) healthYellow / 2.0F);
        boolean lastYellowHalf = (healthYellow & 1) == 1;
        int heartsTotal = heartsNormal + heartsYellow;
        if (heartsTotal <= 0) {
            return;
        }

        int rowsTotal = (heartsTotal + MAX_HEARTS_PER_ROW - 1) / MAX_HEARTS_PER_ROW;
        int rowOffset = Math.max(10 - (rowsTotal - 2), 3);
        int pixelsTotal = MAX_HEARTS_PER_ROW * 8 + 1;
        float maxX = (float) pixelsTotal / 2.0F;

        Vec3 position = player.getPosition(tickDelta);
        double distanceSq = cameraPos.distanceToSqr(position);
        double yOffset = player.getBbHeight() + 0.65F;
        if (distanceSq <= 4096.0D) {
            yOffset += 0.25875D;
            if (distanceSq < 100.0D && hasBelowNameScoreboard(minecraft)) {
                yOffset += 0.25875D;
            }
        }

        stack.pushPose();
        stack.translate(position.x - cameraPos.x, position.y + yOffset - cameraPos.y, position.z - cameraPos.z);
        stack.mulPose(cameraRotation);
        stack.mulPose(Axis.YP.rotationDegrees(180.0F));

        for (int heart = 0; heart < heartsTotal; heart++) {
            int row = heart / MAX_HEARTS_PER_ROW;
            int col = heart % MAX_HEARTS_PER_ROW;
            float x = maxX - (float) (col * 8);
            float y = (float) (row * rowOffset);
            float z = (float) row * 0.00025F;

            drawHeart(stack, provider, x, y, z, HEART_EMPTY);
            Identifier filled = filledHeart(heart, heartsRed, lastRedHalf, heartsNormal, heartsTotal, lastYellowHalf);
            if (filled != null) {
                drawHeart(stack, provider, x, y, z + 0.0001F, filled);
            }
        }

        stack.popPose();
    }

    private Identifier filledHeart(int heart, int heartsRed, boolean lastRedHalf, int heartsNormal, int heartsTotal, boolean lastYellowHalf) {
        if (heart < heartsRed) {
            return heart == heartsRed - 1 && lastRedHalf ? HEART_RED_HALF : HEART_RED_FULL;
        }
        if (heart < heartsNormal) {
            return null;
        }
        return heart == heartsTotal - 1 && lastYellowHalf ? HEART_YELLOW_HALF : HEART_YELLOW_FULL;
    }

    private void drawHeart(PoseStack stack, MultiBufferSource.BufferSource provider, float x, float y, float z, Identifier texture) {
        RenderType renderType = ClientPipelines.WORLD_HEALTH_ICONS.apply(texture);
        usedRenderTypes.add(renderType);
        VertexConsumer consumer = provider.getBuffer(renderType);
        PoseStack.Pose pose = stack.last();
        float x0 = x * PIXEL_SIZE;
        float x1 = (x - HEART_SIZE) * PIXEL_SIZE;
        float y0 = y * PIXEL_SIZE;
        float y1 = (y - HEART_SIZE) * PIXEL_SIZE;

        WorldVertex.textured(consumer, pose, x0, y1, z, 0.0F, 1.0F, WHITE);
        WorldVertex.textured(consumer, pose, x1, y1, z, 1.0F, 1.0F, WHITE);
        WorldVertex.textured(consumer, pose, x1, y0, z, 1.0F, 0.0F, WHITE);
        WorldVertex.textured(consumer, pose, x0, y0, z, 0.0F, 0.0F, WHITE);
    }

    private boolean hasBelowNameScoreboard(Minecraft minecraft) {
        return minecraft.level != null
                && minecraft.level.getScoreboard() != null
                && minecraft.level.getScoreboard().getDisplayObjective(DisplaySlot.BELOW_NAME) != null;
    }

    private Vec3 resolveCameraPos(Minecraft minecraft) {
        Vec3 cameraPos = Render3D.lastCameraPos;
        if (cameraPos != null) {
            return cameraPos;
        }
        return minecraft.gameRenderer.getMainCamera().position();
    }

    private Quaternionf resolveCameraRotation(Minecraft minecraft) {
        Quaternionf rotation = Render3D.lastCameraRotation;
        if (rotation != null) {
            return new Quaternionf(rotation);
        }
        return new Quaternionf(minecraft.gameRenderer.getMainCamera().rotation());
    }
}
