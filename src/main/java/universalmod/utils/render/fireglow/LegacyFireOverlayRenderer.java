package universalmod.utils.render.fireglow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

public final class LegacyFireOverlayRenderer {
    private static final Identifier BLOCK_ATLAS = Identifier.withDefaultNamespace("blocks");
    private static final Identifier FIRE_0 = Identifier.withDefaultNamespace("block/fire_0");
    private static final Identifier FIRE_1 = Identifier.withDefaultNamespace("block/fire_1");
    private static final float LAYER_DROP = 0.45F;
    private static final float WIDTH_SHRINK = 0.9F;
    private static final float DEPTH_STEP = -0.03F;
    private static final int FULL_BRIGHT = 0x00F000F0;

    private LegacyFireOverlayRenderer() {
    }

    public static void submit(PoseStack matrices, SubmitNodeCollector queue, EntityRenderState renderState, Quaternionf rotation) {
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(BLOCK_ATLAS);
        TextureAtlasSprite fire0 = atlas.getSprite(FIRE_0);
        TextureAtlasSprite fire1 = atlas.getSprite(FIRE_1);

        float scale = renderState.boundingBoxWidth * 1.4F;
        if (scale <= 0.0F) {
            scale = 1.0F;
        }

        matrices.pushPose();
        matrices.scale(scale, scale, scale);
        matrices.mulPose(rotation);

        float height = renderState.boundingBoxHeight / scale;
        matrices.translate(0.0F, 0.0F, 0.3F - ((int) height) * 0.02F);

        int outlineColor = FireGlowConfig.hasCustomColor()
                ? 0xFF000000 | (FireGlowConfig.getRed() << 16) | (FireGlowConfig.getGreen() << 8) | FireGlowConfig.getBlue()
                : renderState.outlineColor;

        FireGlowFramebuffer.markMaskWritten();
        queue.submitCustomGeometry(matrices, FireGlowRenderLayer.fireMask(), (entry, vertices) ->
                renderFireStack(entry, vertices, fire0, fire1, height, outlineColor, true)
        );
        queue.submitCustomGeometry(matrices, FireGlowRenderLayer.fireBlocks(), (entry, vertices) ->
                renderFireStack(entry, vertices, fire0, fire1, height, -1, false)
        );
        matrices.popPose();
    }

    private static void renderFireStack(PoseStack.Pose entry, VertexConsumer vertices, TextureAtlasSprite fire0, TextureAtlasSprite fire1, float remainingHeight, int color, boolean mask) {
        float halfWidth = 0.5F;
        float y = 0.0F;
        float depth = 0.0F;
        int layerIndex = 0;

        while (remainingHeight > 0.0F) {
            TextureAtlasSprite sprite = (layerIndex & 1) == 0 ? fire0 : fire1;
            float minU = sprite.getU0();
            float maxU = sprite.getU1();
            float minV = sprite.getV0();
            float maxV = sprite.getV1();
            if (((layerIndex / 2) & 1) == 0) {
                float swap = maxU;
                maxU = minU;
                minU = swap;
            }

            vertex(entry, vertices, -halfWidth, y, depth, maxU, maxV, color, mask);
            vertex(entry, vertices, halfWidth, y, depth, minU, maxV, color, mask);
            vertex(entry, vertices, halfWidth, 1.4F + y, depth, minU, minV, color, mask);
            vertex(entry, vertices, -halfWidth, 1.4F + y, depth, maxU, minV, color, mask);

            remainingHeight -= LAYER_DROP;
            y += LAYER_DROP;
            halfWidth *= WIDTH_SHRINK;
            depth += DEPTH_STEP;
            layerIndex++;
        }
    }

    private static void vertex(PoseStack.Pose entry, VertexConsumer vertices, float x, float y, float z, float u, float v, int color, boolean mask) {
        vertices.addVertex(entry, x, y, z)
                .setUv(u, v)
                .setColor(color);
        if (!mask) {
            vertices.setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(entry, 0.0F, 1.0F, 0.0F);
        }
    }
}
