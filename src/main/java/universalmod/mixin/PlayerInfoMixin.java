package universalmod.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.api.module.impl.utils.CustomDonate;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void universalmod$customDonateTab(CallbackInfoReturnable<Component> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        Component current = cir.getReturnValue();
        Component replaced = CustomDonate.replaceDonateTab(self, current);
        if (replaced != current) {
            cir.setReturnValue(replaced);
        }
    }
}
