package universalmod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.api.module.impl.utils.CustomDonate;
import universalmod.api.module.impl.utils.TotemCounter;
import universalmod.utils.player.PingNametagHelper;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void universalmod$appendTotemCounter(CallbackInfoReturnable<Component> cir) {
        Player self = (Player) (Object) this;
        Component customDonate = CustomDonate.replaceDonate(self, cir.getReturnValue());
        Component modified = TotemCounter.appendCounter(self, customDonate);
        cir.setReturnValue(PingNametagHelper.appendPing(self, modified));
    }
}
