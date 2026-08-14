package universalmod.utils.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import universalmod.screens.pvp.PvpLeaveConfirmScreen;

import java.util.Arrays;
import java.util.List;

public final class SaveKtManager {
    public static final List<String> PVP_TYPES = Arrays.asList("режим боя", "пвп", "pvp");
    public static final List<String> COMMANDS_FOR_LEAVE = Arrays.asList("hub", "lobby", "рги", "дщиин");

    public static boolean openScreen;
    public static boolean lastAttack;
    public static boolean pvp;
    public static boolean bypassDisconnectHook;

    private SaveKtManager() {
    }

    public static void disc() {
        reset();
        Minecraft client = Minecraft.getInstance();
        bypassDisconnectHook = true;

        try {
            if (client.isLocalServer()) {
                client.disconnectWithSavingScreen();
                return;
            } else {
                client.disconnectWithProgressScreen();
            }

            client.setScreen(new TitleScreen());
        } finally {
            bypassDisconnectHook = false;
        }
    }

    public static int getPing() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.getConnection() != null) {
            PlayerInfo entry = client.getConnection().getPlayerInfo(client.player.getUUID());
            int ping = entry != null ? entry.getLatency() : -1;
            return Math.max(ping, 150);
        }
        return 150;
    }

    public static void reset() {
        lastAttack = false;
        openScreen = false;
    }

    public static void sendConfirmScreenIfNeeded(String command) {
        Minecraft client = Minecraft.getInstance();
        if (pvp) {
            client.setScreen(new PvpLeaveConfirmScreen(command));
            openScreen = true;
        } else {
            if (command == null) {
                disc();
            } else {
                ClientPacketListener connection = client.getConnection();
                if (connection instanceof ClientPlayNetworkHandlerHelper helper) {
                    client.execute(() -> helper.sendFinalCommand(command));
                }
            }
        }
    }

    public static void checkLeaveCommand(String text) {
        if (!openScreen) {
            sendConfirmScreenIfNeeded(text);
        }
    }
}
