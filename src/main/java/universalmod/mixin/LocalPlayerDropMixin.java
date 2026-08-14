package universalmod.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.api.module.impl.utils.LockSlot;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerDropMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$cancelLockedSlotDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        if (LockSlot.shouldCancelCurrentSlotDrop()) {
            cir.setReturnValue(false);
        }
    }
}
