package universalmod.utils.inventory;

import net.minecraft.world.inventory.Slot;
import universalmod.IMinecraft;

import java.util.List;
import java.util.stream.Stream;

public final class InventoryTask implements IMinecraft {
    private InventoryTask() {
    }

    public static Stream<Slot> slots() {
        if (mc.player == null) {
            return Stream.empty();
        }
        return mc.player.containerMenu.slots.stream();
    }

    public static int getMenuSlotId(Slot slot) {
        if (slot == null || mc.player == null || mc.player.containerMenu == null) {
            return -1;
        }

        List<Slot> slots = mc.player.containerMenu.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == slot) {
                return i;
            }
        }

        return slot.index;
    }

}
