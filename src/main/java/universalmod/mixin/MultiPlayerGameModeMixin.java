package universalmod.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.api.events.impl.AttackEntityEvent;
import universalmod.api.events.impl.ClickSlotEvent;
import universalmod.manager.Manager;
import universalmod.utils.cooldown.HolyWorldHealingCooldown;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Unique
    private ItemStack universalmod$holyWorldHealingUse = ItemStack.EMPTY;

    @Inject(method = "attack", at = @At("HEAD"))
    private void universalmod$attackHook(Player player, Entity target, CallbackInfo info) {
        if (player != null && target != null) {
            Manager.postEvent(new AttackEntityEvent(target));
        }
    }

    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void universalmod$clickSlotHook(int syncId, int slotId, int button, ClickType actionType, Player player, CallbackInfo info) {
        ClickSlotEvent event = Manager.postEvent(new ClickSlotEvent(syncId, slotId, button, actionType));
        if (event.isCancelled()) {
            info.cancel();
        }
    }

    @Inject(method = "useItem", at = @At("HEAD"), require = 0)
    private void universalmod$captureHolyWorldHealingPotion(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        ItemStack stack = player == null || hand == null ? ItemStack.EMPTY : player.getItemInHand(hand);
        universalmod$holyWorldHealingUse = HolyWorldHealingCooldown.isHealingPotion(stack)
                ? stack.copy()
                : ItemStack.EMPTY;
    }

    @Inject(method = "useItem", at = @At("RETURN"), require = 0)
    private void universalmod$startHolyWorldThrownHealingCooldown(
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        ItemStack captured = universalmod$holyWorldHealingUse;
        universalmod$holyWorldHealingUse = ItemStack.EMPTY;

        InteractionResult result = cir.getReturnValue();
        if (!captured.isEmpty() && result != null && result.consumesAction()) {
            HolyWorldHealingCooldown.recordThrown(player, captured);
        }
    }
}
