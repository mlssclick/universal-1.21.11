package universalmod.api.module.impl.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.utils.player.TotemCounterTracker;

public final class TotemCounter extends Module {
    private static final TotemCounterTracker TRACKER = new TotemCounterTracker();
    private static TotemCounter instance;

    public TotemCounter() {
        super("Totem Counter", "Shows totem pops near player names.", ModuleCategory.UTILS);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static void recordPop(Player player) {
        if (!isActive() || player == null) {
            return;
        }
        TRACKER.recordPop(player);
    }

    public static int getPops(Player player) {
        return isActive() && player != null ? TRACKER.getPops(player) : 0;
    }

    public static boolean hasPops(Player player) {
        return isActive() && player != null && TRACKER.hasPops(player);
    }

    public static void remove(Player player) {
        TRACKER.remove(player);
    }

    public static void clearAll() {
        TRACKER.clear();
    }

    public static int getPopColor(int pops) {
        return switch (pops) {
            case 1, 2 -> 0xFF55FF55;
            case 3, 4 -> 0xFF00AA00;
            case 5, 6 -> 0xFFFFFF55;
            case 7, 8 -> 0xFFFFAA00;
            default -> 0xFFFF5555;
        };
    }

    public static Component appendCounter(Player player, Component text) {
        if (!hasPops(player) || text == null) {
            return text;
        }

        int pops = TRACKER.getPops(player);
        MutableComponent label = text.copy().append(" ");
        label.append(Component.literal("| ").withStyle(style -> style.withColor(0xFFAAAAAA)));
        label.append(Component.literal("-" + pops).setStyle(Style.EMPTY.withColor(getPopColor(pops))));
        return label;
    }

    @Override
    public void onTick(Minecraft client) {
        TRACKER.clearInvalid(client);
    }

    @Override
    protected void onDisable() {
        TRACKER.clear();
    }
}
