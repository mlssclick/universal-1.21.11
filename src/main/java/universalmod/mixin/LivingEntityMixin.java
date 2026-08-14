package universalmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.api.events.impl.SwingDurationEvent;
import universalmod.manager.Manager;
import universalmod.utils.cooldown.HolyWorldHealingCooldown;
import universalmod.utils.serverhelper.ServerHelperDetectionTracker;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "updatingUsingItem", at = @At("HEAD"), require = 0)
    private void universalmod$serverHelperUsingItemTick(CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            ServerHelperDetectionTracker.recordFinishedUse(player);
        }
    }

    @Inject(method = "completeUsingItem", at = @At("HEAD"), require = 0)
    private void universalmod$startHolyWorldDrunkHealingCooldown(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player) {
            ItemStack stack = player.getUseItem();
            HolyWorldHealingCooldown.recordDrunk(player, stack);
        }
    }

    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$swingDuration(CallbackInfoReturnable<Integer> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || (Object) this != player) {
            return;
        }

        SwingDurationEvent event = Manager.postEvent(new SwingDurationEvent());
        if (!event.isCancelled()) {
            return;
        }

        float animation = event.getAnimation();
        if (MobEffectUtil.hasDigSpeed(player)) {
            animation *= 6 - (1 + MobEffectUtil.getDigSpeedAmplification(player));
        } else {
            animation *= player.hasEffect(MobEffects.MINING_FATIGUE)
                    ? 6 + (1 + player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) * 2
                    : 6;
        }
        cir.setReturnValue(Math.max(1, (int) animation));
    }
}
