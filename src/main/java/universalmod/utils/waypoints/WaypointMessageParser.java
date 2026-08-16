package universalmod.utils.waypoints;

import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WaypointMessageParser {
    private static final Pattern TREASURE = Pattern.compile("(?iu).*?найдено\\s+сокровище\\s+по\\s+координатам\\s*:\\s*(-?\\d+)\\s*,\\s*(-?\\d+).*");
    private static final Pattern EVENT = Pattern.compile(
            "(?iuU)^\\s*(.+?)\\s+(?:(?:находится|уже)\\s+на(?:\\s+координатах)?|на\\s+координатах)\\s*:?\\s*"
                    + "(-?\\d+)\\s*[,;]?\\s*(-?\\d+)\\s*[,;]?\\s*(-?\\d+)(?:\\s+.*)?$"
    );

    private WaypointMessageParser() {
    }

    public static ParseResult parse(String rawMessage, Minecraft client) {
        String message = clean(rawMessage);
        if (message.isBlank()) {
            return ParseResult.none();
        }

        Matcher treasure = TREASURE.matcher(message);
        if (treasure.matches()) {
            int x = parseInt(treasure.group(1));
            int z = parseInt(treasure.group(2));
            int y = client != null && client.player != null ? client.player.getBlockY() : 64;
            return ParseResult.set(WaypointDefinition.Source.AUTO_TREASURE, "[auto] Сокровище", x, y, z);
        }

        Matcher event = EVENT.matcher(message);
        if (event.matches()) {
            String eventName = normalizeEventName(event.group(1));
            int x = parseInt(event.group(2));
            int y = parseInt(event.group(3));
            int z = parseInt(event.group(4));
            return ParseResult.set(WaypointDefinition.Source.AUTO_EVENT, "[auto] " + eventName, x, y, z);
        }

        return ParseResult.none();
    }

    public static boolean isEventResponse(String rawMessage) {
        String message = clean(rawMessage).toLowerCase(java.util.Locale.ROOT);
        if (message.isBlank()) {
            return false;
        }
        return parse(rawMessage, Minecraft.getInstance()).matched()
                || message.contains("нет доступ")
                || message.contains("недоступ")
                || message.contains("нет актив")
                || message.contains("голосован")
                || message.contains("через")
                || message.contains("команд")
                || message.contains("не существует")
                || message.contains("\u0441\u0435\u0439\u0447\u0430\u0441 \u043e\u0442\u043a\u0440\u044b\u0442\u0430 \u0438 \u0441\u043a\u043e\u0440\u043e \u0437\u0430\u043a\u0440\u043e\u0435\u0442\u0441\u044f")
                || message.contains("\u0441\u0435\u0439\u0447\u0430\u0441 \u043e\u0442\u043a\u0440\u044b\u0442\u043e \u0438 \u0441\u043a\u043e\u0440\u043e \u0437\u0430\u043a\u0440\u043e\u0435\u0442\u0441\u044f")
                || message.contains("unknown command");
    }

    public static String clean(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "";
        }
        return rawMessage
                .replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                .replaceAll("&[0-9a-fk-orA-FK-OR]", "")
                .replaceAll("[\\x00-\\x1F\\x7F]", "")
                .replace('\u00A0', ' ')
                .replace('—', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeEventName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        while (name.startsWith("▶") || name.startsWith("▸") || name.startsWith("➤")
                || name.startsWith("→") || name.startsWith("⚑") || name.startsWith("•")
                || name.startsWith(">") || name.startsWith("-")) {
            name = name.substring(1).trim();
        }
        return name.isBlank() ? "Event" : name;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public record ParseResult(boolean matched, WaypointDefinition.Source source, String name, int x, int y, int z) {
        static ParseResult none() {
            return new ParseResult(false, WaypointDefinition.Source.MANUAL, "", 0, 0, 0);
        }

        static ParseResult set(WaypointDefinition.Source source, String name, int x, int y, int z) {
            return new ParseResult(true, source, name, x, y, z);
        }
    }
}
