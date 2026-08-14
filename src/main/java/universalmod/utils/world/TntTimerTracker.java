package universalmod.utils.world;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TntTimerTracker {
    private static final int MAX_DISPLAY = 20;
    private static final Map<UUID, TrackedTnt> TRACKED = new LinkedHashMap<>();
    private static final List<Entry> VIEW = new ArrayList<>();

    private TntTimerTracker() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }

        long now = minecraft.level.getGameTime();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt tnt && minecraft.player.hasLineOfSight(tnt)) {
                TRACKED.put(tnt.getUUID(), new TrackedTnt(
                        tnt.getUUID(),
                        tnt.getName().getString(),
                        tnt.position().x,
                        tnt.position().y,
                        tnt.position().z,
                        tnt.getFuse(),
                        now
                ));
            }
        }

        VIEW.clear();
        Iterator<Map.Entry<UUID, TrackedTnt>> iterator = TRACKED.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedTnt tracked = iterator.next().getValue();
            int remainingFuse = tracked.remainingFuse(now);
            if (remainingFuse <= 0) {
                iterator.remove();
                continue;
            }
            tracked.currentFuse = remainingFuse;
            double distance = minecraft.player.distanceToSqr(tracked.x, tracked.y, tracked.z);
            VIEW.add(new Entry(tracked.id, tracked.name, remainingFuse, Math.sqrt(distance)));
        }

        if (VIEW.size() > 1) {
            VIEW.sort(Comparator.comparingDouble(Entry::distance));
        }
        if (VIEW.size() > MAX_DISPLAY) {
            VIEW.subList(MAX_DISPLAY, VIEW.size()).clear();
        }
    }

    public static List<Entry> getEntries() {
        return List.copyOf(VIEW);
    }

    public static void reset() {
        TRACKED.clear();
        VIEW.clear();
    }

    private static final class TrackedTnt {
        private final UUID id;
        private final String name;
        private final double x;
        private final double y;
        private final double z;
        private final int initialFuse;
        private final long seenAt;
        private int currentFuse;

        private TrackedTnt(UUID id, String name, double x, double y, double z, int initialFuse, long seenAt) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.initialFuse = initialFuse;
            this.currentFuse = initialFuse;
            this.seenAt = seenAt;
        }

        private int remainingFuse(long now) {
            return initialFuse - (int) Math.max(0L, now - seenAt);
        }
    }

    public record Entry(UUID id, String name, int remainingFuse, double distance) {
        public String label() {
            long remainingMillis = Math.max(0L, remainingFuse) * 50L;
            long seconds = remainingMillis / 1000L;
            long millis = remainingMillis % 1000L;
            return name + " | "
                    + String.format(Locale.US, "%.1f", distance) + "m | "
                    + String.format(Locale.US, "%d.%03d", seconds, Math.max(0L, Math.min(999L, millis)));
        }
    }
}
