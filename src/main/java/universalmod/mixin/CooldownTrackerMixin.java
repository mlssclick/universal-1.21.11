package universalmod.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.utils.cooldown.CooldownStateStorage;

@Mixin(ItemCooldowns.class)
public abstract class CooldownTrackerMixin {
    @Shadow
    public abstract Identifier getCooldownGroup(ItemStack stack);

    @Inject(method = "addCooldown(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("TAIL"), require = 0)
    private void universalmod$storeStackCooldownDuration(ItemStack stack, int ticks, CallbackInfo ci) {
        CooldownStateStorage.setDuration(getCooldownGroup(stack), ticks);
    }

    @Inject(method = "addCooldown(Lnet/minecraft/resources/Identifier;I)V", at = @At("TAIL"), require = 0)
    private void universalmod$storeGroupCooldownDuration(Identifier group, int ticks, CallbackInfo ci) {
        CooldownStateStorage.setDuration(group, ticks);
    }

    @Inject(method = "removeCooldown", at = @At("TAIL"), require = 0)
    private void universalmod$removeCooldownDuration(Identifier group, CallbackInfo ci) {
        CooldownStateStorage.remove(group);
    }
}
