package universalmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.TwoDItems;
import universalmod.mixin.accessor.EntityRendererAccessor;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererTwoDItemsMixin {
    @Shadow
    @Final
    private RandomSource random;

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void universalmod$render2DItems(ItemEntityRenderState state, PoseStack matrices, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (!TwoDItems.isFeatureEnabled() || state.item.isEmpty()) {
            return;
        }

        if (!TwoDItems.shouldAffect3DModels() && state.item.usesBlockLight()) {
            return;
        }

        ((EntityRendererAccessor) this).universalmod$setShadowRadius(TwoDItems.shouldCastShadows() ? 0.15F : 0.0F);

        matrices.pushPose();
        try {
            AABB box = state.item.getModelBoundingBox();
            float lift = -(float) box.minY + 0.0625F;
            float bounce = Mth.sin(state.ageInTicks / 10.0F + state.bobOffset) * 0.1F + 0.1F;
            matrices.translate(0.0F, bounce + lift, 0.0F);
            matrices.mulPose(cameraRenderState.orientation);
            ItemEntityRenderer.submitMultipleFromCount(matrices, submitNodeCollector, state.lightCoords, state, this.random, box);
        } finally {
            matrices.popPose();
        }

        ci.cancel();
    }
}
