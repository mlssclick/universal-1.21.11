package universalmod.utils.serverhelper;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import universalmod.api.module.impl.misc.ServerHelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ServerHelperDetectionTracker {
    private static final long DISPLAY_DURATION_MS = 15_000L;
    private static final long DEBOUNCE_MS = 250L;
    private static final long PENDING_CONFIRM_MS = 2_000L;
    private static final double TELEPORT_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final int TELEPORT_GRACE_TICKS = 20;
    private static final ItemStack STUN_COOLDOWN_STACK = new ItemStack(Items.NETHER_STAR);
    private static final List<TrackedZone> ACTIVE_ZONES = new ArrayList<>();
    private static final Map<TrapType, Long> LAST_TRIGGER_AT = new EnumMap<>(TrapType.class);

    private static boolean initialized;
    private static int lastStunCount = -1;
    private static Vec3 lastPlayerPos;
    private static ResourceKey<Level> lastWorldKey;
    private static int inventoryUsePauseTicks;
    private static Vec3 pendingStunPosition;
    private static long pendingStunExpiresAt;
    private static boolean lastStunCoolingDown;

    private ServerHelperDetectionTracker() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            pruneExpired();
            detectInventoryStunUse(client);
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            recordUse(player, player.getItemInHand(hand), entityPos(player));
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            Vec3 position = hitResult == null ? entityPos(player) : hitResult.getLocation();
            recordUse(player, player.getItemInHand(hand), position);
            return InteractionResult.PASS;
        });
    }

    public static void reset() {
        ACTIVE_ZONES.clear();
        LAST_TRIGGER_AT.clear();
        lastStunCount = -1;
        lastPlayerPos = null;
        lastWorldKey = null;
        inventoryUsePauseTicks = 0;
        pendingStunPosition = null;
        pendingStunExpiresAt = 0L;
        lastStunCoolingDown = false;
    }

    public static void recordFinishedUse(Player player) {
        if (player == null) {
            return;
        }
        recordUse(player, player.getUseItem(), entityPos(player));
    }

    public static AABB createPreviewBox(Vec3 position, TrapType type) {
        if (position == null || type == null) {
            return null;
        }
        return createBox(position, type);
    }

    public static TrapType detectType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() == Items.POPPED_CHORUS_FRUIT) {
            return TrapType.TRAPKA;
        }
        if (stack.getItem() == Items.PRISMARINE_SHARD) {
            return TrapType.EXPLOSION_TRAP;
        }
        if (stack.getItem() == Items.NETHER_STAR) {
            return TrapType.STUN;
        }
        return null;
    }

    public static List<TrackedZone> getActiveZones() {
        pruneExpired();
        return new ArrayList<>(ACTIVE_ZONES);
    }

    private static void recordUse(Player player, ItemStack stack, Vec3 position) {
        if (player == null || stack == null || stack.isEmpty() || position == null) {
            return;
        }

        TrapType type = detectType(stack);
        if (type == null || isOnCooldown(player, stack) || type != TrapType.STUN || !ServerHelper.isStunMemoryActive()) {
            return;
        }

        long now = System.currentTimeMillis();
        pendingStunPosition = position;
        pendingStunExpiresAt = now + PENDING_CONFIRM_MS;
    }

    private static boolean isOnCooldown(Player player, ItemStack stack) {
        return player != null && stack != null && !stack.isEmpty() && player.getCooldowns().isOnCooldown(stack);
    }

    private static AABB createBox(Vec3 position, TrapType type) {
        double baseY = Math.floor(position.y);
        return new AABB(
                position.x - type.radius,
                baseY,
                position.z - type.radius,
                position.x + type.radius,
                baseY + type.height,
                position.z + type.radius
        );
    }

    private static void pruneExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<TrackedZone> iterator = ACTIVE_ZONES.iterator(); iterator.hasNext(); ) {
            if (iterator.next().expiresAt <= now) {
                iterator.remove();
            }
        }
    }

    private static void detectInventoryStunUse(Minecraft client) {
        if (client == null || client.player == null) {
            lastStunCount = -1;
            lastStunCoolingDown = false;
            pendingStunPosition = null;
            pendingStunExpiresAt = 0L;
            return;
        }

        updateInventoryUsePause(client.player);

        long now = System.currentTimeMillis();
        int currentStunCount = countStuns(client.player);
        int usedStuns = lastStunCount - currentStunCount;
        boolean stunCoolingDown = client.player.getCooldowns().isOnCooldown(STUN_COOLDOWN_STACK);
        boolean stunCooldownStarted = !lastStunCoolingDown && stunCoolingDown;

        if (inventoryUsePauseTicks <= 0 && lastStunCount >= 0 && usedStuns == 1) {
            recordStunUse(client.player, pendingOrCurrentPosition(client.player, now));
        } else if (inventoryUsePauseTicks <= 0 && hasPendingStunUse(now) && stunCooldownStarted) {
            recordStunUse(client.player, pendingStunPosition);
        }

        if (now > pendingStunExpiresAt) {
            pendingStunPosition = null;
            pendingStunExpiresAt = 0L;
        }

        lastStunCount = currentStunCount;
        lastStunCoolingDown = stunCoolingDown;
    }

    private static void updateInventoryUsePause(Player player) {
        ResourceKey<Level> currentWorldKey = player.level() == null ? null : player.level().dimension();
        Vec3 currentPos = entityPos(player);

        if (lastWorldKey != null && currentWorldKey != null && !lastWorldKey.equals(currentWorldKey)) {
            inventoryUsePauseTicks = TELEPORT_GRACE_TICKS;
            lastStunCount = -1;
        } else if (lastPlayerPos != null && currentPos.distanceToSqr(lastPlayerPos) > TELEPORT_DISTANCE_SQUARED) {
            inventoryUsePauseTicks = TELEPORT_GRACE_TICKS;
            lastStunCount = -1;
        } else if (inventoryUsePauseTicks > 0) {
            inventoryUsePauseTicks--;
        }

        lastWorldKey = currentWorldKey;
        lastPlayerPos = currentPos;
    }

    private static int countStuns(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack != null && stack.getItem() == Items.NETHER_STAR) {
                count += stack.getCount();
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand != null && offhand.getItem() == Items.NETHER_STAR) {
            count += offhand.getCount();
        }
        return count;
    }

    private static void recordStunUse(Player player, Vec3 position) {
        if (player == null || position == null || !ServerHelper.isStunMemoryActive()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastTrigger = LAST_TRIGGER_AT.get(TrapType.STUN);
        if (lastTrigger != null && now - lastTrigger < DEBOUNCE_MS) {
            return;
        }

        LAST_TRIGGER_AT.put(TrapType.STUN, now);
        ACTIVE_ZONES.add(new TrackedZone(TrapType.STUN, createBox(position, TrapType.STUN), now + DISPLAY_DURATION_MS));
        pendingStunPosition = null;
        pendingStunExpiresAt = 0L;
    }

    private static boolean hasPendingStunUse(long now) {
        return pendingStunPosition != null && now <= pendingStunExpiresAt;
    }

    private static Vec3 pendingOrCurrentPosition(Player player, long now) {
        return hasPendingStunUse(now) ? pendingStunPosition : entityPos(player);
    }

    private static Vec3 entityPos(Player player) {
        return player.getPosition(1.0F);
    }

    public enum TrapType {
        TRAPKA(1.0D, 3.0D),
        EXPLOSION_TRAP(3.0D, 3.0D),
        STUN(15.0D, 15.0D);

        private final double radius;
        private final double height;

        TrapType(double radius, double height) {
            this.radius = radius;
            this.height = height;
        }
    }

    public static final class TrackedZone {
        private final TrapType type;
        private final AABB box;
        private final long expiresAt;

        private TrackedZone(TrapType type, AABB box, long expiresAt) {
            this.type = type;
            this.box = box;
            this.expiresAt = expiresAt;
        }

        public TrapType getType() {
            return type;
        }

        public AABB getBox() {
            return box;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
