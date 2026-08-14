package universalmod.utils.cooldown;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import universalmod.utils.network.ServerHelper;
import net.minecraft.resources.Identifier;

public final class HolyWorldHealingCooldown {
    public static final int DURATION_SECONDS = 10;

    private static final long DURATION_NANOS = DURATION_SECONDS * 1_000_000_000L;
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private static long expiresAtNanos;
    private static String serverAddress = "";
    private static ItemStack displayStack = ItemStack.EMPTY;
    private static boolean actualCooldownObserved;

    private HolyWorldHealingCooldown() {
    }

    public static void recordThrown(Player player, ItemStack stack) {
        if (!isLocalPlayer(player) || !isThrowableHealingPotion(stack)) {
            return;
        }
        start(stack);
    }

    public static void recordDrunk(Player player, ItemStack stack) {
        if (!isLocalPlayer(player) || !isDrinkableHealingPotion(stack)) {
            return;
        }
        start(stack);
    }

    public static Snapshot snapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        String currentAddress = normalizedServerAddress();
        if (minecraft.player == null || minecraft.level == null || !isHolyWorldAddress(currentAddress)) {
            reset();
            return null;
        }

        if (!serverAddress.isEmpty() && !serverAddress.equals(currentAddress)) {
            reset();
            return null;
        }

        if (displayStack.isEmpty()) {
            reset();
            return null;
        }

        Player player = minecraft.player;
        if (player.getCooldowns().isOnCooldown(displayStack)) {
            actualCooldownObserved = true;
            Identifier group = player.getCooldowns().getCooldownGroup(displayStack);
            int totalTicks = CooldownStateStorage.getDuration(group);
            float progress = Math.clamp(player.getCooldowns().getCooldownPercent(displayStack, 0.0F), 0.0F, 1.0F);
            int fallbackTotal = DURATION_SECONDS * 20;
            int remainingTicks = Math.max(1, Math.round(progress * (totalTicks > 0 ? totalTicks : fallbackTotal)));
            expiresAtNanos = System.nanoTime() + remainingTicks * NANOS_PER_TICK;
            return new Snapshot(
                    displayStack.copy(),
                    displayStack.getHoverName().getString(),
                    remainingTicks,
                    formatSeconds(Math.max(1, (remainingTicks + 19) / 20)),
                    true
            );
        }

        if (actualCooldownObserved) {
            reset();
            return null;
        }

        long remainingNanos = expiresAtNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            reset();
            return null;
        }

        int remainingTicks = safeCeilToInt(remainingNanos, NANOS_PER_TICK);
        int remainingSeconds = safeCeilToInt(remainingNanos, NANOS_PER_SECOND);
        return new Snapshot(
                displayStack.copy(),
                displayStack.getHoverName().getString(),
                remainingTicks,
                formatSeconds(remainingSeconds),
                false
        );
    }

    public static void reset() {
        expiresAtNanos = 0L;
        serverAddress = "";
        displayStack = ItemStack.EMPTY;
        actualCooldownObserved = false;
    }

    public static boolean isHealingPotion(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.has(DataComponents.POTION_CONTENTS)) {
            return false;
        }

        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }

        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect != null && MobEffects.INSTANT_HEALTH.equals(effect.getEffect())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDrinkableHealingPotion(ItemStack stack) {
        return stack != null && stack.getItem() == Items.POTION && isHealingPotion(stack);
    }

    private static boolean isThrowableHealingPotion(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        return (stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION)
                && isHealingPotion(stack);
    }

    private static boolean isLocalPlayer(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        return player != null && player == minecraft.player;
    }

    private static void start(ItemStack stack) {
        String currentAddress = normalizedServerAddress();
        if (!isHolyWorldAddress(currentAddress)) {
            reset();
            return;
        }

        ItemStack copy = stack.copy();
        copy.setCount(1);
        displayStack = copy;
        serverAddress = currentAddress;
        actualCooldownObserved = false;
        expiresAtNanos = System.nanoTime() + DURATION_NANOS;
    }

    private static String normalizedServerAddress() {
        return ServerHelper.serverAddress().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isHolyWorldAddress(String address) {
        return address != null && address.contains("holyworld");
    }

    private static int safeCeilToInt(long value, long divisor) {
        long result = (value + divisor - 1L) / divisor;
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, result));
    }

    private static String formatSeconds(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = Math.min(99, safeSeconds / 60);
        int seconds = safeSeconds % 60;
        return twoDigits(minutes) + ":" + twoDigits(seconds);
    }

    private static String twoDigits(int value) {
        int safe = Math.max(0, Math.min(99, value));
        return Character.toString((char) ('0' + safe / 10))
                + (char) ('0' + safe % 10);
    }

    public record Snapshot(
            ItemStack stack,
            String displayName,
            int remainingTicks,
            String remainingText,
            boolean synchronizedWithItemCooldown
    ) {
    }
}
