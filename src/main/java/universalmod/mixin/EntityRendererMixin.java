package universalmod.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.InvisibleTags;
import universalmod.utils.player.PingNametagHelper;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<S extends EntityRenderState> {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"), require = 0)
    private void universalmod$updatePlayerNameTag(Entity entity, S state, float tickDelta, CallbackInfo ci) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            InvisibleTags.applyVanillaInvisibleTag(player, state, tickDelta);
            state.nameTag = PingNametagHelper.appendPing(player, state.nameTag);
        }
    }
}
