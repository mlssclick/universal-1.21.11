package universalmod.utils.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TotemCounterTracker {
    private final Map<UUID, Integer> pops = new HashMap<>();

    public void recordPop(Player player) {
        if (player == null) {
            return;
        }
        pops.merge(player.getUUID(), 1, Integer::sum);
    }

    public int getPops(Player player) {
        if (player == null) {
            return 0;
        }
        return pops.getOrDefault(player.getUUID(), 0);
    }

    public boolean hasPops(Player player) {
        return player != null && pops.containsKey(player.getUUID());
    }

    public void remove(Player player) {
        if (player != null) {
            pops.remove(player.getUUID());
        }
    }

    public void clearInvalid(Minecraft client) {
        if (client == null || client.level == null) {
            pops.clear();
            return;
        }

        Set<UUID> alivePlayers = new HashSet<>();
        for (Player player : client.level.players()) {
            if (player != null && player.isAlive() && !player.isRemoved()) {
                alivePlayers.add(player.getUUID());
            }
        }

        Iterator<UUID> iterator = pops.keySet().iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (!alivePlayers.contains(uuid)) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        pops.clear();
    }
}
