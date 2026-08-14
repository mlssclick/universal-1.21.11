package universalmod.utils.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import universalmod.api.module.impl.render.PingNametags;
import universalmod.utils.network.ServerHelper;

import java.util.Collection;

public final class PingNametagHelper {
    private PingNametagHelper() {
    }

    public static Component appendPing(Player player, Component original) {
        if (!PingNametags.isActive() || original == null) {
            return original;
        }
        int ping = resolvePing(player, original, Minecraft.getInstance());
        if (ping < 0) {
            return original;
        }

        String suffix = " (" + ping + "ms)";
        if (original.getString().endsWith(suffix)) {
            return original;
        }

        MutableComponent combined = original.copy();
        combined.append(Component.literal(" "));
        combined.append(Component.literal("(" + ping + "ms)").withStyle(Style.EMPTY.withColor(PingColors.getColor(ping))));
        return combined;
    }

    public static int resolvePing(Player player, Component renderedName, Minecraft minecraft) {
        if (player == null || minecraft == null) {
            return -1;
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return -1;
        }
        PlayerInfo playerInfo = connection.getPlayerInfo(player.getUUID());
        if (playerInfo == null) {
            playerInfo = findMatchingPlayerInfo(connection, player, renderedName);
        }
        return ServerHelper.ping(playerInfo);
    }

    private static PlayerInfo findMatchingPlayerInfo(ClientPacketListener connection, Player player, Component renderedName) {
        if (connection == null || player == null) {
            return null;
        }
        String playerName = player.getGameProfile() == null ? "" : player.getGameProfile().name();
        String rendered = renderedName == null ? "" : renderedName.getString();
        Collection<PlayerInfo> entries = connection.getListedOnlinePlayers();
        for (PlayerInfo entry : entries) {
            if (entry == null || entry.getProfile() == null) {
                continue;
            }
            String entryName = entry.getProfile().name();
            if (playerName != null && playerName.equals(entryName)) {
                return entry;
            }
            Component displayName = entry.getTabListDisplayName();
            if (displayName != null && !rendered.isBlank() && rendered.equals(displayName.getString())) {
                return entry;
            }
        }
        return null;
    }
}
