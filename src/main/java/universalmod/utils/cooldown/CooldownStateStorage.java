package universalmod.utils.cooldown;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownStateStorage {
    private static final Map<Identifier, Integer> DURATIONS = new ConcurrentHashMap<>();

    private CooldownStateStorage() {
    }

    public static void setDuration(Identifier group, int duration) {
        if (group == null || duration <= 0) {
            return;
        }
        DURATIONS.put(group, duration);
    }

    public static void remove(Identifier group) {
        if (group != null) {
            DURATIONS.remove(group);
        }
    }

    public static int getDuration(Identifier group) {
        Integer duration = DURATIONS.get(group);
        return duration == null ? 0 : duration;
    }
}
