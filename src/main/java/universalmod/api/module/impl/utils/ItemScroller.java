package universalmod.api.module.impl.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.HandledScreenEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.utils.inventory.InventoryTask;

public final class ItemScroller extends Module {
    private final NumberSetting scrollDelay = register(new NumberSetting(
            "Scroll Delay",
            "Delay between item scroll clicks.",
            50.0,
            0.0,
            100.0,
            1.0
    ));
    private long lastActionTime = System.currentTimeMillis();

    public ItemScroller() {
        super("ItemScroller", "Item Scroller", ModuleCategory.UTILS);
    }

    @SubscribeEvent
    private void onHandledScreen(HandledScreenEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) {
            return;
        }

        Slot hoverSlot = event.getSlotHover();
        if (hoverSlot == null || !hoverSlot.hasItem()) {
            return;
        }

        long window = client.getWindow().handle();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) != GLFW.GLFW_PRESS
                || !elapsed(scrollDelay.getValue().intValue())) {
            return;
        }

        int slotId = InventoryTask.getMenuSlotId(hoverSlot);
        if (slotId != -1) {
            client.gameMode.handleInventoryMouseClick(
                    client.player.containerMenu.containerId,
                    slotId,
                    0,
                    ClickType.QUICK_MOVE,
                    client.player
            );
            resetTimer();
        }
    }

    private boolean elapsed(long delayMs) {
        return System.currentTimeMillis() - delayMs >= lastActionTime;
    }

    private void resetTimer() {
        lastActionTime = System.currentTimeMillis();
    }
}
