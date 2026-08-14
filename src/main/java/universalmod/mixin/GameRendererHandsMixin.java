package universalmod.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.ViewModel;
import universalmod.utils.render.hand.HandsRenderer;

@Mixin(GameRenderer.class)
public abstract class GameRendererHandsMixin {
    @Shadow
    @Final
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Inject(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"
            ),
            require = 0
    )
    private void universalmod$captureHandsScene(float partialTicks, boolean renderBlockOutline, Matrix4f projectionMatrix, CallbackInfo ci) {
        ViewModel viewModel = ViewModel.getInstance();
        if (viewModel != null) {
            viewModel.captureEditorProjection(projectionMatrix);
        }
        HandsRenderer.captureBeforeHands();
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;renderScreenEffect(ZFLnet/minecraft/client/renderer/SubmitNodeCollector;)V"
            ),
            require = 0
    )
    private void universalmod$renderShaderHandsBeforeScreenEffects(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!HandsRenderer.hasCapturedHands()) {
            return;
        }

        featureRenderDispatcher.renderAllFeatures();
        renderBuffers.bufferSource().endBatch();
        HandsRenderer.captureAfterHands();
    }
}
