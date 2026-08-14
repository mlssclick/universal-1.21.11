package universalmod.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.access.EmotionEntityCarrier;
import universalmod.api.module.impl.render.InvisibleTags;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"), require = 0)
    private void updateRenderStateHook(LivingEntity entity, S state, float tickDelta, CallbackInfo ci) {
        ((EmotionEntityCarrier) state).universalmod$setEmotionEntity(entity);
        if (entity instanceof Player player) {
            InvisibleTags.applyVanillaInvisibleTag(player, state, tickDelta);
        }
    }
}
