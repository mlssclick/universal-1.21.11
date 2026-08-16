package universalmod.utils.world;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Tracks TNT from the moment it is visible and continues its fuse using a monotonic clock. */
public final class TntTimerTracker {
    private static final int MAX_DISPLAY = 5;
    private static final long TICK_MILLIS = 50L;
    private static final Map<UUID, TrackedTnt> TRACKED = new LinkedHashMap<>();
    private static final List<Entry> VIEW = new ArrayList<>();
    private static long lastEntityScanTick = Long.MIN_VALUE;

    private TntTimerTracker() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }

        long nowNanos = System.nanoTime();
        long gameTick = minecraft.level.getGameTime();
        if (gameTick != lastEntityScanTick) {
            lastEntityScanTick = gameTick;
            scanVisibleTnt(minecraft, nowNanos);
        }
        rebuildView(minecraft, nowNanos);
    }

    public static List<Entry> getEntries() {
        return List.copyOf(VIEW);
    }

    public static void reset() {
        TRACKED.clear();
        VIEW.clear();
        lastEntityScanTick = Long.MIN_VALUE;
    }

    private static void scanVisibleTnt(Minecraft minecraft, long nowNanos) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof PrimedTnt tnt) || !hasVisibleRay(minecraft, tnt)) {
                continue;
            }

            TRACKED.compute(tnt.getUUID(), (id, tracked) -> {
                if (tracked == null) {
                    return new TrackedTnt(
                            id,
                            tnt.getName().getString(),
                            tnt.position().x,
                            tnt.position().y,
                            tnt.position().z,
                            tnt.getFuse(),
                            nowNanos
                    );
                }
                tracked.refresh(
                        tnt.getName().getString(),
                        tnt.position().x,
                        tnt.position().y,
                        tnt.position().z,
                        tnt.getFuse(),
                        nowNanos
                );
                return tracked;
            });
        }
    }

    private static boolean hasVisibleRay(Minecraft minecraft, PrimedTnt tnt) {
        Vec3 eye = minecraft.player.getEyePosition();
        AABB bounds = tnt.getBoundingBox().deflate(0.01D);
        double centerX = (bounds.minX + bounds.maxX) * 0.5D;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        double topY = bounds.maxY;
        return reaches(minecraft, eye, new Vec3(centerX, (bounds.minY + bounds.maxY) * 0.5D, centerZ))
                || reaches(minecraft, eye, new Vec3(centerX, topY, centerZ))
                || reaches(minecraft, eye, new Vec3(bounds.minX, topY, bounds.minZ))
                || reaches(minecraft, eye, new Vec3(bounds.minX, topY, bounds.maxZ))
                || reaches(minecraft, eye, new Vec3(bounds.maxX, topY, bounds.minZ))
                || reaches(minecraft, eye, new Vec3(bounds.maxX, topY, bounds.maxZ));
    }

    private static boolean reaches(Minecraft minecraft, Vec3 start, Vec3 end) {
        HitResult hit = minecraft.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static void rebuildView(Minecraft minecraft, long nowNanos) {
        VIEW.clear();
        Iterator<Map.Entry<UUID, TrackedTnt>> iterator = TRACKED.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedTnt tracked = iterator.next().getValue();
            long remainingMillis = tracked.remainingMillis(nowNanos);
            if (remainingMillis <= 0L) {
                iterator.remove();
                continue;
            }

            int remainingFuse = (int) Math.max(1L, (remainingMillis + TICK_MILLIS - 1L) / TICK_MILLIS);
            double distance = minecraft.player.distanceToSqr(tracked.x, tracked.y, tracked.z);
            VIEW.add(new Entry(tracked.id, tracked.name, remainingFuse, remainingMillis, Math.sqrt(distance)));
        }

        if (VIEW.size() > 1) {
            VIEW.sort(Comparator.comparingDouble(Entry::distance));
        }
        if (VIEW.size() > MAX_DISPLAY) {
            VIEW.subList(MAX_DISPLAY, VIEW.size()).clear();
        }
    }

    private static final class TrackedTnt {
        private final UUID id;
        private String name;
        private double x;
        private double y;
        private double z;
        private long remainingMillisAtSample;
        private long sampleNanos;

        private TrackedTnt(UUID id, String name, double x, double y, double z, int fuseTicks, long nowNanos) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.remainingMillisAtSample = Math.max(0L, fuseTicks) * TICK_MILLIS;
            this.sampleNanos = nowNanos;
        }

        private void refresh(String name, double x, double y, double z, int fuseTicks, long nowNanos) {
            long observedMillis = Math.max(0L, fuseTicks) * TICK_MILLIS;
            long runningMillis = remainingMillis(nowNanos);
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.remainingMillisAtSample = Math.min(runningMillis, observedMillis);
            this.sampleNanos = nowNanos;
        }

        private long remainingMillis(long nowNanos) {
            long elapsedMillis = Math.max(0L, (nowNanos - sampleNanos) / 1_000_000L);
            return Math.max(0L, remainingMillisAtSample - elapsedMillis);
        }
    }

    public record Entry(UUID id, String name, int remainingFuse, long remainingMillis, double distance) {
        public String label() {
            long seconds = remainingMillis / 1000L;
            long millis = remainingMillis % 1000L;
            return name + " | "
                    + String.format(Locale.US, "%.1f", distance) + "m | "
                    + String.format(Locale.US, "%d.%03d", seconds, millis);
        }
    }
}
