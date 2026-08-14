package universalmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.events.impl.HandAnimationEvent;
import universalmod.api.events.impl.HandOffsetEvent;
import universalmod.api.module.impl.render.ViewModel;
import universalmod.manager.Manager;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private PoseStack universalmod$customSwingMatrices;

    @Unique
    private InteractionHand universalmod$customSwingHand;

    @Unique
    private float universalmod$customSwingProgress;

    @Unique
    private float universalmod$currentItemScale = 1.0F;

    @Unique
    private InteractionHand universalmod$currentRenderHand;

    @Unique
    private int universalmod$itemScaleDepth;

    @Unique
    private int universalmod$mapScaleDepth;

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER), require = 0)
    private void universalmod$handOffset(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack matrices, SubmitNodeCollector nodeCollector, int light, CallbackInfo ci) {
        this.universalmod$currentRenderHand = hand;
        HandOffsetEvent event = Manager.postEvent(new HandOffsetEvent(matrices, stack, hand));
        this.universalmod$currentItemScale = universalmod$sanitizeItemScale(event.getScale());
    }

    @Inject(method = "renderItem", at = @At("HEAD"), require = 0)
    private void universalmod$beforeRenderItem(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack matrices,
            SubmitNodeCollector nodeCollector,
            int light,
            CallbackInfo ci
    ) {
        if (universalmod$pushItemScale(matrices, stack)) {
            this.universalmod$itemScaleDepth++;
        }

        ViewModel viewModel = ViewModel.getInstance();
        if (viewModel != null && this.universalmod$currentRenderHand != null) {
            viewModel.captureRenderedHandPose(this.universalmod$currentRenderHand, new org.joml.Matrix4f(matrices.last().pose()));
        }
    }

    @Inject(method = "renderItem", at = @At("RETURN"), require = 0)
    private void universalmod$afterRenderItem(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack matrices,
            SubmitNodeCollector nodeCollector,
            int light,
            CallbackInfo ci
    ) {
        if (this.universalmod$itemScaleDepth > 0) {
            this.universalmod$itemScaleDepth--;
            matrices.popPose();
        }
    }

    @Inject(method = "renderMap", at = @At("HEAD"), require = 0)
    private void universalmod$beforeRenderMap(
            PoseStack matrices,
            SubmitNodeCollector nodeCollector,
            int light,
            ItemStack stack,
            CallbackInfo ci
    ) {
        if (universalmod$pushItemScale(matrices, stack)) {
            this.universalmod$mapScaleDepth++;
        }

        ViewModel viewModel = ViewModel.getInstance();
        if (viewModel != null && this.universalmod$currentRenderHand != null) {
            viewModel.captureRenderedHandPose(this.universalmod$currentRenderHand, new org.joml.Matrix4f(matrices.last().pose()));
        }
    }

    @Inject(method = "renderMap", at = @At("RETURN"), require = 0)
    private void universalmod$afterRenderMap(
            PoseStack matrices,
            SubmitNodeCollector nodeCollector,
            int light,
            ItemStack stack,
            CallbackInfo ci
    ) {
        if (this.universalmod$mapScaleDepth > 0) {
            this.universalmod$mapScaleDepth--;
            matrices.popPose();
        }
    }

    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"), require = 0)
    private void universalmod$baseSwingAnimation(ItemInHandRenderer instance, PoseStack matrices, HumanoidArm arm, float equipProgress, Operation<Void> original, @Local(argsOnly = true) AbstractClientPlayer player, @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true, ordinal = 2) float swingProgress) {
        if (player.isUsingItem() && player.getUsedItemHand() == hand) {
            original.call(instance, matrices, arm, equipProgress);
            return;
        }

        HandAnimationEvent event = Manager.postEvent(new HandAnimationEvent(matrices, hand, swingProgress));
        if (event.isCancelled()) {
            universalmod$markCustomSwing(matrices, hand, swingProgress);
            return;
        }

        original.call(instance, matrices, arm, equipProgress);
    }

    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"), require = 0)
    private void universalmod$swingAnimation(ItemInHandRenderer instance, float swingProgress, PoseStack matrices, int armX, HumanoidArm arm, Operation<Void> original, @Local(argsOnly = true) AbstractClientPlayer player, @Local(argsOnly = true) InteractionHand hand) {
        if (player.isUsingItem() && player.getUsedItemHand() == hand) {
            original.call(instance, swingProgress, matrices, armX, arm);
            return;
        }

        if (universalmod$consumeCustomSwing(matrices, hand, swingProgress)) {
            return;
        }

        HandAnimationEvent event = Manager.postEvent(new HandAnimationEvent(matrices, hand, swingProgress));
        if (!event.isCancelled()) {
            original.call(instance, swingProgress, matrices, armX, arm);
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"), require = 0)
    private void universalmod$clearSwingAnimation(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack matrices, SubmitNodeCollector nodeCollector, int light, CallbackInfo ci) {
        universalmod$clearCustomSwing();
        this.universalmod$currentItemScale = 1.0F;
        this.universalmod$currentRenderHand = null;
        this.universalmod$itemScaleDepth = 0;
        this.universalmod$mapScaleDepth = 0;
    }

    @Unique
    private boolean universalmod$pushItemScale(PoseStack matrices, ItemStack stack) {
        float scale = this.universalmod$currentItemScale;
        if (stack == null || stack.isEmpty() || Float.compare(scale, 1.0F) == 0) {
            return false;
        }

        matrices.pushPose();
        matrices.scale(scale, scale, scale);
        return true;
    }

    @Unique
    private static float universalmod$sanitizeItemScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            return 1.0F;
        }
        return scale;
    }

    @Unique
    private void universalmod$markCustomSwing(PoseStack matrices, InteractionHand hand, float swingProgress) {
        this.universalmod$customSwingMatrices = matrices;
        this.universalmod$customSwingHand = hand;
        this.universalmod$customSwingProgress = swingProgress;
    }

    @Unique
    private boolean universalmod$consumeCustomSwing(PoseStack matrices, InteractionHand hand, float swingProgress) {
        boolean matches = this.universalmod$customSwingMatrices == matrices
                && this.universalmod$customSwingHand == hand
                && Float.compare(this.universalmod$customSwingProgress, swingProgress) == 0;
        if (matches) {
            universalmod$clearCustomSwing();
        }
        return matches;
    }

    @Unique
    private void universalmod$clearCustomSwing() {
        this.universalmod$customSwingMatrices = null;
        this.universalmod$customSwingHand = null;
        this.universalmod$customSwingProgress = 0.0F;
    }
}
