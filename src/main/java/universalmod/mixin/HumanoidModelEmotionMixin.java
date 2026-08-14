package universalmod.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.access.EmotionEntityCarrier;
import universalmod.api.module.impl.render.emotions.EmotionModelAnimator;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelEmotionMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"), require = 0)
    private void universalmod$applyEmotion(HumanoidRenderState state, CallbackInfo ci) {
        LivingEntity entity = ((EmotionEntityCarrier) state).universalmod$getEmotionEntity();
        if (entity instanceof Player player) {
            EmotionModelAnimator.apply((HumanoidModel<?>) (Object) this, state, player);
        }
    }
}
