package universalmod.api.module.impl.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.ClickSlotEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.MultiModeSetting;
import universalmod.utils.chat.ChatMessage;

public final class LockSlot extends Module {
    private static final String[] SLOT_MODES = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static LockSlot instance;

    private final MultiModeSetting slots = register(new MultiModeSetting(
            "Slots",
            "Hotbar slots protected from item dropping.",
            SLOT_MODES
    ));

    public LockSlot() {
        super("LockSlot", "Blocks dropping items from selected hotbar slots.", ModuleCategory.UTILS);
        instance = this;
    }

    public static boolean shouldCancelCurrentSlotDrop() {
        LockSlot module = instance;
        Minecraft client = Minecraft.getInstance();
        if (module == null || !module.isEnabled() || client.player == null || client.screen != null) {
            return false;
        }

        int selectedSlot = client.player.getInventory().getSelectedSlot();
        ItemStack stack = client.player.getInventory().getSelectedItem();
        if (stack == null || stack.isEmpty() || !module.isHotbarSlotLocked(selectedSlot)) {
            return false;
        }

        module.sendLockedMessage(selectedSlot);
        return true;
    }

    @SubscribeEvent
    private void onClickSlot(ClickSlotEvent event) {
        if (!isEnabled() || event.getActionType() != ClickType.THROW) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.containerMenu == null) {
            return;
        }

        int hotbarSlot = getHotbarSlotFromClick(client, event.getSlotId());
        if (hotbarSlot >= 0 && isHotbarSlotLocked(hotbarSlot)) {
            event.cancel();
            sendLockedMessage(hotbarSlot);
        }
    }

    private boolean isHotbarSlotLocked(int slot) {
        return slot >= 0 && slot < SLOT_MODES.length && slots.isSelected(SLOT_MODES[slot]);
    }

    private int getHotbarSlotFromClick(Minecraft client, int slotId) {
        if (client.player == null || slotId < 0 || slotId >= client.player.containerMenu.slots.size()) {
            return -1;
        }

        Slot slot = client.player.containerMenu.getSlot(slotId);
        if (slot == null || slot.container != client.player.getInventory()) {
            return -1;
        }

        int inventoryIndex = slot.getContainerSlot();
        return inventoryIndex >= 0 && inventoryIndex <= 8 ? inventoryIndex : -1;
    }

    private void sendLockedMessage(int slot) {
        ChatMessage.brandmessage(Component.literal("Выброс предмета из слота " + (slot + 1) + " заблокирован").withStyle(ChatFormatting.RED));
    }
}
