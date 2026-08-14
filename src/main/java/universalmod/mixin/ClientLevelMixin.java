package universalmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.Ambience;
import universalmod.api.module.impl.render.NoRender;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Shadow
    @Final
    private ClientLevel.ClientLevelData clientLevelData;

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$tickTime(CallbackInfo ci) {
        Ambience ambience = Ambience.getInstance();
        boolean ambienceActive = ambience != null && ambience.isEnabled();
        if (!ambienceActive) {
            return;
        }
        ClientLevel level = (ClientLevel) (Object) this;
        long time = ambience.getInternalTime();
        this.clientLevelData.setDayTime(time);
        if (ambienceActive) {
            ambience.syncWeather(level, clientLevelData);
        }
        ci.cancel();
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$addParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        if (universalmod$shouldCancelParticle(parameters)) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$addParticle(ParticleOptions parameters, boolean force, boolean canSpawnOnMinimal, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo ci) {
        if (universalmod$shouldCancelParticle(parameters)) {
            ci.cancel();
        }
    }

    @Inject(method = "addDestroyBlockEffect(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$addDestroyBlockEffect(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (NoRender.isActive("Block Particles")) {
            ci.cancel();
        }
    }

    private static boolean universalmod$shouldCancelParticle(ParticleOptions parameters) {
        if (parameters == null) {
            return false;
        }
        if (NoRender.isActive("Particles")) {
            return true;
        }
        if (!NoRender.isActive("Hit Particles")) {
            return false;
        }
        return parameters.getType() == ParticleTypes.DAMAGE_INDICATOR
                || parameters.getType() == ParticleTypes.CRIT
                || parameters.getType() == ParticleTypes.ENCHANTED_HIT
                || parameters.getType() == ParticleTypes.SWEEP_ATTACK;
    }
}
