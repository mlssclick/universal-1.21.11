package universalmod.utils.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class ServerHelper {
    private static final Minecraft MC = Minecraft.getInstance();

    private ServerHelper() {
    }

    public static boolean isReallyWorld() {
        if (MC.getConnection() == null || MC.getConnection().getServerData() == null) {
            return false;
        }
        return safeLower(MC.getConnection().getServerData().ip).contains("reallyworld");
    }

    public static boolean isSingleplayer() {
        return MC.isLocalServer();
    }

    public static String serverAddress() {
        if (MC.getConnection() == null || MC.getConnection().getServerData() == null) {
            return "";
        }
        return safeTrim(MC.getConnection().getServerData().ip);
    }

    public static String serverBrand() {
        if (MC.getConnection() == null || MC.getConnection().getServerData() == null) {
            return "";
        }

        Object serverData = MC.getConnection().getServerData();
        for (String methodName : new String[]{"getGameVersion", "gameVersion", "getBrand", "brand"}) {
            try {
                Method method = serverData.getClass().getMethod(methodName);
                Object value = method.invoke(serverData);
                if (value != null) {
                    return value.toString().trim();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        for (String fieldName : new String[]{"gameVersion", "brand"}) {
            try {
                Field field = serverData.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Object value = field.get(serverData);
                if (value != null) {
                    return value.toString().trim();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return "";
    }

    public static String pingText(PlayerInfo playerInfo) {
        int ping = ping(playerInfo);
        return ping < 0 ? "?ms" : ping + "ms";
    }

    public static int ping(PlayerInfo playerInfo) {
        if (playerInfo == null) {
            return -1;
        }

        try {
            return Math.max(-1, playerInfo.getLatency());
        } catch (Throwable ignored) {
        }

        for (String methodName : new String[]{"getLatency", "latency", "getPing", "ping"}) {
            try {
                Method method = playerInfo.getClass().getMethod(methodName);
                Object value = method.invoke(playerInfo);
                if (value instanceof Number number) {
                    return Math.max(-1, number.intValue());
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        for (String fieldName : new String[]{"latency", "ping"}) {
            try {
                Field field = playerInfo.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(playerInfo);
                if (value instanceof Number number) {
                    return Math.max(-1, number.intValue());
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return -1;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
