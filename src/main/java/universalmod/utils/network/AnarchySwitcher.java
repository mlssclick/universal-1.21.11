package universalmod.utils.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import universalmod.api.drag.impl.CurrentEventsPanel;

import java.util.List;
import java.util.Locale;

/** Performs the two-menu light-anarchy transition used by the server selector. */
public final class AnarchySwitcher {
    private static final String TYPE_KEY = "advancedserverselecter:server-type";
    private static final String SERVER_KEY = "advancedserverselecter:server";
    private static final int TIMEOUT_TICKS = 200;
    private static final List<String> RANGE_KEYS = List.of("Solo", "Duo", "Trio", "Clan");
    private static final AnarchySwitcher INSTANCE = new AnarchySwitcher();

    private State state = State.IDLE;
    private String categoryKey;
    private String serverKey;
    private ClientLevel previousLevel;
    private int elapsedTicks;

    private AnarchySwitcher() {
    }

    public static AnarchySwitcher getInstance() {
        return INSTANCE;
    }

    public boolean isBusy() {
        return state != State.IDLE;
    }

    public void start(int number) {
        Minecraft client = Minecraft.getInstance();
        if (isBusy()) {
            return;
        }

        String resolvedCategory = categoryFor(number);
        if (resolvedCategory == null) {
            return;
        }
        if (client.player == null || client.getConnection() == null) {
            return;
        }

        categoryKey = resolvedCategory;
        serverKey = number == 1 ? "lanarchy" : "lanarchy" + number;
        previousLevel = client.level;
        elapsedTicks = 0;
        state = State.WAITING_HUB_WORLD;
        client.getConnection().sendCommand("hub");
    }

    public void handleServerMessage(String text) {
        if (state != State.WAITING_HUB_WORLD || text == null
                || !text.toLowerCase(Locale.ROOT).contains("уже подключен")) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            client.getConnection().sendCommand("lite");
            state = State.WAITING_TYPE_MENU;
            elapsedTicks = 0;
        }
    }

    public void tick(Minecraft client) {
        if (state == State.IDLE || client == null) {
            return;
        }
        if (client.player == null || client.getConnection() == null) {
            if (state != State.WAITING_HUB_WORLD) {
                reset();
            }
            return;
        }

        elapsedTicks++;
        if (elapsedTicks > TIMEOUT_TICKS) {
            reset();
            return;
        }

        switch (state) {
            case WAITING_HUB_WORLD -> waitForHub(client);
            case WAITING_TYPE_MENU -> scanMenu(client, TYPE_KEY, categoryKey, true);
            case WAITING_SERVER_MENU -> scanMenu(client, SERVER_KEY, serverKey, false);
            case IDLE -> {
            }
        }
    }

    private void waitForHub(Minecraft client) {
        if (client.level != null && client.level != previousLevel && client.getConnection() != null) {
            client.getConnection().sendCommand("lite");
            state = State.WAITING_TYPE_MENU;
            elapsedTicks = 0;
        }
    }

    private void scanMenu(Minecraft client, String nbtKey, String expectedValue, boolean firstMenu) {
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)
                || client.gameMode == null || client.player == null) {
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        int containerSlots = Math.max(0, menu.slots.size() - 36);
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !expectedValue.equals(readPublicBukkitValue(stack, nbtKey))) {
                continue;
            }

            client.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0,
                    ClickType.PICKUP, client.player);
            state = firstMenu ? State.WAITING_SERVER_MENU : State.IDLE;
            elapsedTicks = 0;
            if (state == State.IDLE) {
                previousLevel = null;
            }
            return;
        }
    }

    private static String readPublicBukkitValue(ItemStack stack, String key) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return null;
        }
        CompoundTag root = customData.copyTag();
        if (!root.contains("PublicBukkitValues")) {
            return null;
        }
        CompoundTag values = root.getCompoundOrEmpty("PublicBukkitValues");
        return values.getString(key).orElse(null);
    }

    private static String categoryFor(int number) {
        CurrentEventsPanel events = CurrentEventsPanel.getInstance();
        for (String rangeKey : RANGE_KEYS) {
            if (events.matchesAnarchyRange(rangeKey, number)) {
                return switch (rangeKey) {
                    case "Solo" -> "solo";
                    case "Duo" -> "duo";
                    case "Trio" -> "trio";
                    case "Clan" -> "clans";
                    default -> null;
                };
            }
        }
        return null;
    }

    private void reset() {
        state = State.IDLE;
        elapsedTicks = 0;
        previousLevel = null;
        categoryKey = null;
        serverKey = null;
    }

    private enum State {
        IDLE,
        WAITING_HUB_WORLD,
        WAITING_TYPE_MENU,
        WAITING_SERVER_MENU
    }
}
