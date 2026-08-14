package universalmod.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.utils.serverhelper.ServerHelperItemInfo;

import java.util.List;

@Mixin(ItemStack.class)
public abstract class ItemStackServerHelperMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"), require = 0)
    private void universalmod$appendServerHelperTooltip(Item.TooltipContext tooltipContext, Player player, TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
        ServerHelperItemInfo.appendTooltip((ItemStack) (Object) this, cir.getReturnValue());
    }

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true, require = 0)
    private void universalmod$appendCompassCooldownToName(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(ServerHelperItemInfo.appendCompassCooldownToName((ItemStack) (Object) this, cir.getReturnValue()));
    }
}
