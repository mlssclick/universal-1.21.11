package universalmod.api.module.impl.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.api.settings.impl.StringSetting;
import universalmod.utils.lang.LanguageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CustomDonate extends Module {
    private static CustomDonate instance;
    private static final String SCOREBOARD_GROUP_LABEL = "\u0413\u0440\u0443\u043f\u043f\u0430:";
    private static final String SCOREBOARD_TITLE_LABEL = "\u0422\u0438\u0442\u0443\u043b:";
    private static final String SCOREBOARD_GROUP_KEY = "\u0433\u0440\u0443\u043f\u043f\u0430";
    private static final String SCOREBOARD_TITLE_KEY = "\u0442\u0438\u0442\u0443\u043b";
    private static final int SCOREBOARD_TIME_COLOR = 0xFF989898;
    private static final int TAB_ORDER_NONE = 0;
    private static final int TAB_ORDER_GRIEFER = 100;
    private static final int TAB_ORDER_MUSTANG = 200;
    private static final int TAB_ORDER_GHAST = 300;
    private static final int TAB_ORDER_WITHER = 400;
    private static final int TAB_ORDER_KRAKEN = 500;
    private static final int TAB_ORDER_DRAGON = 600;
    private static final int TAB_ORDER_STINGER = 700;
    private static final int TAB_ORDER_ETERNITY = 800;
    private static final int TAB_ORDER_CUSTOM = 900;
    private static final int TAB_ORDER_STAZHER = 1000;
    private static final int TAB_ORDER_JUNIOR_STAFF = 1100;
    private static final int TAB_ORDER_STAFF = 1200;
    private static final int TAB_ORDER_JUNIOR_SPECTATOR = 1300;
    private static final int TAB_ORDER_LEAD_STAFF = 1400;
    private static final int TAB_ORDER_SPECTATOR = 1500;
    private static final int TAB_ORDER_SENIOR_STAFF = 1600;
    private static final String RANK_STAZHER = "\u0441\u0442\u0430\u0436\u0435\u0440";
    private static final String RANK_JUNIOR_STAFF = "\u043c\u043b.\u0441\u043e\u0442\u0440\u0443\u0434\u043d\u0438\u043a";
    private static final String RANK_STAFF = "\u0441\u043e\u0442\u0440\u0443\u0434\u043d\u0438\u043a";
    private static final String RANK_JUNIOR_SPECTATOR = "\u043c\u043b.\u0441\u043f\u0435\u043a\u0442\u0430\u0442\u043e\u0440";
    private static final String RANK_LEAD_STAFF = "\u0432\u0435\u0434.\u0441\u043e\u0442\u0440\u0443\u0434\u043d\u0438\u043a";
    private static final String RANK_SPECTATOR = "\u0441\u043f\u0435\u043a\u0442\u0430\u0442\u043e\u0440";
    private static final String RANK_SENIOR_STAFF = "\u0441\u0442.\u0441\u043e\u0442\u0440\u0443\u0434\u043d\u0438\u043a";
    private static final String RANK_GRIEFER = "griefer";
    private static final String RANK_MUSTANG = "mustang";
    private static final String RANK_GHAST = "ghast";
    private static final String RANK_WITHER = "wither";
    private static final String RANK_KRAKEN = "kraken";
    private static final String RANK_DRAGON = "dragon";
    private static final String RANK_STINGER = "stinger";
    private static final String RANK_ETERNITY = "eternity";

    private final StringSetting prefix = register(new StringSetting("Prefix", "Text inserted before your nickname.", "", 512));
    private final StringSetting suffix = register(new StringSetting("Suffix", "Text inserted after your nickname.", "", 512));
    private final BooleanSetting scoreboardTime = register(new BooleanSetting("Время", "Shows a fake group age in the scoreboard.", false));
    private final NumberSetting scoreboardDays = register(new NumberSetting("Дни", "Days shown after the fake group in the scoreboard.", 29.0, 1.0, 180.0, 1.0));

    public CustomDonate() {
        super("Custom Donate", "Replaces server prefix and suffix around your nickname.", ModuleCategory.UTILS);
        scoreboardDays.visibleWhen(scoreboardTime::getValue);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static boolean appliesTo(Player player) {
        if (!isActive() || player == null) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.getUser() == null) {
            return false;
        }
        return player.getUUID().equals(client.player.getUUID())
                || same(player.getScoreboardName(), client.getUser().getName())
                || same(player.getName().getString(), client.getUser().getName());
    }

    public static Component replaceDonate(Player player, Component fallback) {
        if (!appliesTo(player)) {
            return fallback;
        }
        return buildDonateComponent(cleanPlayerName(player));
    }

    public static Component replaceDonateTab(Player player, Component fallback) {
        if (!appliesTo(player)) {
            return fallback;
        }
        return replaceTabName(cleanPlayerName(player), fallback);
    }

    public static Component replaceDonateTab(PlayerInfo info, Component fallback) {
        if (!isActive() || info == null || info.getProfile() == null || !isLocalTabEntry(info, null)) {
            return fallback;
        }
        return replaceTabName(info.getProfile().name(), fallback);
    }

    private static Component replaceTabName(String name, Component fallback) {
        String cleanName = clean(name);
        if (cleanName.isBlank()) {
            return fallback;
        }
        if (fallback == null) {
            return buildDonateComponent(cleanName);
        }

        String plain = fallback.getString();
        int nameStart = indexOfIgnoreCase(plain, cleanName, 0);
        if (nameStart < 0) {
            return buildDonateComponent(cleanName);
        }

        int decorationEnd = leadingTabDecorationEnd(plain, nameStart);
        MutableComponent result = Component.empty();
        result.append(sliceComponent(fallback, 0, decorationEnd));
        result.append(buildDonateComponent(cleanName));
        return result;
    }

    public static String replaceDonateText(Player player, String fallback) {
        if (!appliesTo(player)) {
            return fallback;
        }
        return instance.buildText(cleanPlayerName(player));
    }

    public static String prefixText() {
        return isActive() ? instance.resolvePrefixText() : "";
    }

    public static String suffixText() {
        return isActive() ? normalizedDonateText(instance.suffix.getValue()) : "";
    }

    public static boolean hasPrefixText() {
        return isActive() && !stripFormatting(prefixText()).isBlank();
    }

    public static boolean hasSuffixText() {
        return isActive() && !stripFormatting(suffixText()).isBlank();
    }

    public static boolean scoreboardTimeEnabled() {
        return isActive() && instance.scoreboardTime.getValue();
    }

    public static int tabPriority(PlayerInfo info, Player player) {
        if (info == null || info.getProfile() == null) {
            return TAB_ORDER_NONE;
        }
        if (isLocalTabEntry(info, player) && isActive()) {
            int customOverride = resolveKnownRank(normalizeRankText(prefixText()));
            if (customOverride != TAB_ORDER_NONE) {
                return customOverride;
            }
            return stripFormatting(prefixText()).isBlank() ? TAB_ORDER_NONE : TAB_ORDER_CUSTOM;
        }
        return resolveVisibleTabRank(info, player);
    }

    public static Component buildDonateComponent(String name) {
        if (!isActive()) {
            return Component.literal(name == null ? "" : name);
        }
        return instance.buildComponent(name == null ? "" : name);
    }

    public static Component replaceChatMessage(Component message) {
        if (!isActive() || message == null) {
            return message;
        }
        String plain = message.getString();
        LocalNameMatch match = findLocalName(plain);
        if (match == null) {
            return message;
        }
        int nameStart = match.index();
        int nameEnd = nameStart + match.name().length();
        int replaceStart = chatPrefixStart(plain, nameStart);
        int replaceEnd = chatSuffixEnd(plain, nameEnd);

        MutableComponent result = Component.empty();
        result.append(sliceComponent(message, 0, replaceStart));
        result.append(buildDonateComponent(match.name()));
        result.append(sliceComponent(message, replaceEnd, plain.length()));
        return result;
    }

    public static Component replaceScoreboardLine(Component line) {
        if (!isActive() || line == null) {
            return line;
        }
        if (hasPrefixText() && isScoreboardGroupLine(line)) {
            return replaceScoreboardValue(line, SCOREBOARD_GROUP_LABEL, prefixText(), true);
        }
        if (hasSuffixText() && isScoreboardTitleLine(line)) {
            return replaceScoreboardValue(line, SCOREBOARD_TITLE_LABEL, suffixText(), false);
        }
        return replaceLocalPlayerName(line);
    }

    public static boolean isScoreboardGroupLine(Component line) {
        return containsLabel(line, SCOREBOARD_GROUP_LABEL);
    }

    public static boolean isScoreboardTitleLine(Component line) {
        return containsLabel(line, SCOREBOARD_TITLE_LABEL);
    }

    private Component buildComponent(String name) {
        MutableComponent result = Component.empty();
        String prefixText = resolvePrefixText();
        String suffixText = normalizedDonateText(suffix.getValue());
        if (!stripFormatting(prefixText).isBlank()) {
            result.append(parseFormatted(prefixText));
            result.append(Component.literal(" "));
        }
        result.append(Component.literal(name));
        if (!stripFormatting(suffixText).isBlank()) {
            result.append(Component.literal(" "));
            result.append(parseFormatted(suffixText));
        }
        return result;
    }

    private String buildText(String name) {
        return join(stripFormatting(prefixText()), name, stripFormatting(suffixText()));
    }

    private String resolvePrefixText() {
        return effectivePrefixText(prefix.getValue());
    }

    private static String cleanPlayerName(Player player) {
        if (player == null) {
            return "Player";
        }
        String name = clean(player.getName().getString());
        if (name.isBlank()) {
            name = clean(player.getScoreboardName());
        }
        return name.isBlank() ? "Player" : name;
    }

    private static String join(String prefix, String name, String suffix) {
        StringBuilder builder = new StringBuilder();
        String cleanPrefix = normalizedDonateText(prefix);
        String cleanSuffix = normalizedDonateText(suffix);
        if (!cleanPrefix.isBlank()) {
            builder.append(cleanPrefix);
            builder.append(' ');
        }
        builder.append(name);
        if (!cleanSuffix.isBlank()) {
            builder.append(' ');
            builder.append(cleanSuffix);
        }
        return builder.toString();
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        boolean skipFormatting = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skipFormatting) {
                skipFormatting = false;
                continue;
            }
            if (c == '\u00a7') {
                skipFormatting = true;
                continue;
            }
            if (!Character.isISOControl(c)) {
                builder.append(c);
            }
        }
        return builder.toString().trim();
    }

    private static String normalizedDonateText(String text) {
        return text == null ? "" : text.strip();
    }

    private static String effectivePrefixText(String text) {
        return normalizedDonateText(text);
    }

    private static Component replaceLocalPlayerName(Component line) {
        LocalNameMatch match = findLocalName(line.getString());
        if (match == null) {
            return line;
        }
        return buildDonateComponent(match.name());
    }

    private static LocalNameMatch findLocalName(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        if (client.player != null) {
            addNameCandidate(candidates, client.player.getScoreboardName());
            addNameCandidate(candidates, client.player.getName().getString());
        }
        if (client.getUser() != null) {
            addNameCandidate(candidates, client.getUser().getName());
        }
        LocalNameMatch best = null;
        for (String candidate : candidates) {
            int index = indexOfIgnoreCase(text, candidate, 0);
            if (index < 0) {
                continue;
            }
            if (best == null || index < best.index() || index == best.index() && candidate.length() > best.name().length()) {
                best = new LocalNameMatch(candidate, index);
            }
        }
        return best;
    }

    private static void addNameCandidate(List<String> candidates, String value) {
        String name = clean(value);
        if (name.isBlank()) {
            return;
        }
        for (String candidate : candidates) {
            if (same(candidate, name)) {
                return;
            }
        }
        candidates.add(name);
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT));
    }

    private static int leadingTabDecorationEnd(String text, int maxEnd) {
        if (text == null || text.isBlank() || maxEnd <= 0) {
            return 0;
        }
        int end = 0;
        while (end < maxEnd) {
            char c = text.charAt(end);
            if (Character.isLetterOrDigit(c)) {
                break;
            }
            end++;
        }
        return end;
    }

    private static int resolveVisibleTabRank(PlayerInfo info, Player player) {
        for (String sample : collectRankSamples(info, player)) {
            int rank = resolveKnownRank(normalizeRankText(sample));
            if (rank != TAB_ORDER_NONE) {
                return rank;
            }
        }
        return TAB_ORDER_NONE;
    }

    private static List<String> collectRankSamples(PlayerInfo info, Player player) {
        List<String> samples = new ArrayList<>();
        if (info.getTabListDisplayName() != null) {
            samples.add(info.getTabListDisplayName().getString());
        }
        PlayerTeam team = info.getTeam();
        if (team != null) {
            if (team.getPlayerPrefix() != null) {
                samples.add(team.getPlayerPrefix().getString());
            }
            if (team.getDisplayName() != null) {
                samples.add(team.getDisplayName().getString());
            }
            samples.add(team.getName());
        }
        if (player != null) {
            samples.add(player.getDisplayName().getString());
            samples.add(player.getName().getString());
            samples.add(player.getScoreboardName());
        }
        return samples;
    }

    private static boolean isLocalTabEntry(PlayerInfo info, Player player) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getUser() == null) {
            return false;
        }
        if (player != null && appliesTo(player)) {
            return true;
        }
        return same(info.getProfile().name(), client.getUser().getName());
    }

    private static String normalizeRankText(String text) {
        String plain = stripFormatting(clean(text)).toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
        StringBuilder normalized = new StringBuilder(plain.length());
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            normalized.append(c);
        }
        return normalized.toString();
    }

    private static int resolveStaffRank(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return TAB_ORDER_NONE;
        }
        if (normalizedText.contains(RANK_SENIOR_STAFF)) {
            return TAB_ORDER_SENIOR_STAFF;
        }
        if (normalizedText.contains(RANK_LEAD_STAFF)) {
            return TAB_ORDER_LEAD_STAFF;
        }
        if (normalizedText.contains(RANK_JUNIOR_SPECTATOR)) {
            return TAB_ORDER_JUNIOR_SPECTATOR;
        }
        if (normalizedText.contains(RANK_JUNIOR_STAFF)) {
            return TAB_ORDER_JUNIOR_STAFF;
        }
        if (normalizedText.contains(RANK_SPECTATOR)) {
            return TAB_ORDER_SPECTATOR;
        }
        if (normalizedText.contains(RANK_STAFF)) {
            return TAB_ORDER_STAFF;
        }
        if (normalizedText.contains(RANK_STAZHER)) {
            return TAB_ORDER_STAZHER;
        }
        return TAB_ORDER_NONE;
    }

    private static int resolveKnownRank(String normalizedText) {
        int staffRank = resolveStaffRank(normalizedText);
        if (staffRank != TAB_ORDER_NONE) {
            return staffRank;
        }
        if (normalizedText == null || normalizedText.isBlank()) {
            return TAB_ORDER_NONE;
        }
        if (normalizedText.contains(RANK_ETERNITY)) {
            return TAB_ORDER_ETERNITY;
        }
        if (normalizedText.contains(RANK_STINGER)) {
            return TAB_ORDER_STINGER;
        }
        if (normalizedText.contains(RANK_DRAGON)) {
            return TAB_ORDER_DRAGON;
        }
        if (normalizedText.contains(RANK_KRAKEN)) {
            return TAB_ORDER_KRAKEN;
        }
        if (normalizedText.contains(RANK_WITHER)) {
            return TAB_ORDER_WITHER;
        }
        if (normalizedText.contains(RANK_GHAST)) {
            return TAB_ORDER_GHAST;
        }
        if (normalizedText.contains(RANK_MUSTANG)) {
            return TAB_ORDER_MUSTANG;
        }
        if (normalizedText.contains(RANK_GRIEFER)) {
            return TAB_ORDER_GRIEFER;
        }
        return TAB_ORDER_NONE;
    }

    private static Component replaceScoreboardValue(Component line, String label, String value, boolean groupLine) {
        String plain = line.getString();
        int labelStart = indexOfIgnoreCase(plain, label, 0);
        MutableComponent result = Component.empty();
        if (labelStart >= 0) {
            result.append(sliceComponent(line, 0, labelStart + label.length()));
            result.append(Component.literal(" "));
            result.append(parseFormatted(value));
            appendScoreboardTime(result, groupLine);
            return result;
        }
        int prefixEnd = Math.min(plain.length(), Math.max(1, firstLetterIndex(plain)));
        result.append(sliceComponent(line, 0, prefixEnd));
        result.append(Component.literal(label).setStyle(styleAt(line, prefixEnd)));
        result.append(Component.literal(" "));
        result.append(parseFormatted(value));
        appendScoreboardTime(result, groupLine);
        return result;
    }

    private static void appendScoreboardTime(MutableComponent result, boolean groupLine) {
        if (!groupLine || !scoreboardTimeEnabled()) {
            return;
        }
        int days = Math.max(1, Math.min(180, (int) Math.round(instance.scoreboardDays.getValue())));
        result.append(Component.literal(" ").setStyle(Style.EMPTY));
        result.append(Component.literal(LanguageManager.translateFormat("scoreboard.days.short", days)).setStyle(Style.EMPTY.withColor(SCOREBOARD_TIME_COLOR)));
    }

    private static boolean containsLabel(Component line, String label) {
        if (line == null) {
            return false;
        }
        String normalized = scoreboardKey(line.getString());
        return label.equals(SCOREBOARD_GROUP_LABEL) ? normalized.contains(SCOREBOARD_GROUP_KEY) : normalized.contains(SCOREBOARD_TITLE_KEY);
    }

    private static String scoreboardKey(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        boolean skipFormatting = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (skipFormatting) {
                skipFormatting = false;
                continue;
            }
            if (c == '\u00a7') {
                skipFormatting = true;
                continue;
            }
            if (Character.isLetter(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private static int firstLetterIndex(String text) {
        if (text == null) {
            return 0;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                return i;
            }
        }
        return 0;
    }

    private static int chatPrefixStart(String text, int nameStart) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, nameStart - 1)) + 1;
        int pipe = text.lastIndexOf('|', Math.max(0, nameStart - 1));
        if (pipe >= lineStart && nameStart - pipe <= 96) {
            int start = pipe + 1;
            while (start < nameStart && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            return start;
        }
        int bracketStart = text.lastIndexOf('[', Math.max(0, nameStart - 1));
        if (bracketStart >= lineStart) {
            int bracketEndBeforeName = text.indexOf(']', bracketStart);
            if (bracketEndBeforeName >= 0 && bracketEndBeforeName < nameStart) {
                return bracketStart;
            }
        }
        int decoratedStart = lastDecoratedPrefixStart(text, lineStart, nameStart);
        if (decoratedStart >= 0) {
            return decoratedStart;
        }
        return nameStart;
    }

    private static int chatSuffixEnd(String text, int nameEnd) {
        int cursor = nameEnd;
        int length = text.length();
        while (cursor < length && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        cursor = skipOwnChatSuffix(text, cursor);
        while (cursor < length && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        if (cursor < length && text.charAt(cursor) == '[') {
            int close = text.indexOf(']', cursor + 1);
            int colon = text.indexOf(':', cursor + 1);
            if (close >= 0 && (colon < 0 || close < colon)) {
                cursor = close + 1;
            }
            while (cursor < length && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
            return cursor;
        }
        return skipServerChatSuffix(text, cursor);
    }

    private static int skipOwnChatSuffix(String text, int start) {
        String suffix = stripFormatting(suffixText()).strip();
        if (suffix.isBlank()) {
            return start;
        }
        int suffixStart = indexOfIgnoreCase(text, suffix, start);
        if (suffixStart != start) {
            return start;
        }
        return suffixStart + suffix.length();
    }

    private static int skipServerChatSuffix(String text, int start) {
        int cursor = start;
        int length = text.length();
        int firstTokenStart = cursor;
        while (cursor < length && !Character.isWhitespace(text.charAt(cursor)) && !isChatMessageSeparator(text.charAt(cursor))) {
            cursor++;
        }
        if (cursor == firstTokenStart) {
            return start;
        }
        while (cursor < length && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static boolean isChatMessageSeparator(char c) {
        return c == ':' || c == '\u00bb' || c == '>' || c == '\u203a';
    }

    private static int lastDecoratedPrefixStart(String text, int lineStart, int nameStart) {
        int best = -1;
        char[] openers = {'[', '<', '\u00ab', '\u300a', '\u27e8', '\u3010', '('};
        for (char opener : openers) {
            int index = text.lastIndexOf(opener, Math.max(0, nameStart - 1));
            if (index >= lineStart && index > best) {
                best = index;
            }
        }
        return best;
    }

    private static int indexOfIgnoreCase(String text, String needle, int fromIndex) {
        if (text == null || needle == null || needle.isEmpty()) {
            return -1;
        }
        int max = text.length() - needle.length();
        for (int i = Math.max(0, fromIndex); i <= max; i++) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private static Component sliceComponent(Component component, int start, int end) {
        if (component == null || end <= start) {
            return Component.empty();
        }
        List<StyledText> segments = flatten(component);
        MutableComponent result = Component.empty();
        int index = 0;
        for (StyledText segment : segments) {
            int segmentStart = index;
            int segmentEnd = index + segment.text().length();
            if (segmentEnd <= start) {
                index = segmentEnd;
                continue;
            }
            if (segmentStart >= end) {
                break;
            }
            int localStart = Math.max(0, start - segmentStart);
            int localEnd = Math.min(segment.text().length(), end - segmentStart);
            if (localStart < localEnd) {
                result.append(Component.literal(segment.text().substring(localStart, localEnd)).setStyle(segment.style()));
            }
            index = segmentEnd;
        }
        return result;
    }

    private static Style styleAt(Component component, int targetIndex) {
        int index = 0;
        for (StyledText segment : flatten(component)) {
            int end = index + segment.text().length();
            if (targetIndex >= index && targetIndex < end) {
                return segment.style();
            }
            index = end;
        }
        return component == null ? Style.EMPTY : component.getStyle();
    }

    private static List<StyledText> flatten(Component component) {
        List<StyledText> segments = new ArrayList<>();
        if (component == null) {
            return segments;
        }
        component.visit((style, text) -> {
            if (text != null && !text.isEmpty()) {
                segments.add(new StyledText(text, style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (segments.isEmpty() && !component.getString().isEmpty()) {
            segments.add(new StyledText(component.getString(), component.getStyle()));
        }
        return segments;
    }

    public static MutableComponent parseFormatted(String raw) {
        MutableComponent result = Component.empty();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        Style style = Style.EMPTY;
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            LegacyCode code = readLegacyCode(raw, i);
            if (code != null) {
                appendSegment(result, segment, style);
                style = code.color() == null ? applyCode(style, code.code()) : style.withColor(code.color());
                i = code.endIndex();
                continue;
            }
            if (c == '\u00a7' && i + 1 < raw.length()) {
                appendSegment(result, segment, style);
                style = applyCode(style, Character.toLowerCase(raw.charAt(i + 1)));
                i++;
                continue;
            }
            segment.append(c);
        }
        appendSegment(result, segment, style);
        return result;
    }

    public static String stripFormatting(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            LegacyCode code = readLegacyCode(raw, i);
            if (code != null) {
                i = code.endIndex();
                continue;
            }
            if (raw.charAt(i) == '\u00a7' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            builder.append(raw.charAt(i));
        }
        return builder.toString();
    }

    private static void appendSegment(MutableComponent target, StringBuilder segment, Style style) {
        if (segment.isEmpty()) {
            return;
        }
        target.append(Component.literal(segment.toString()).setStyle(style));
        segment.setLength(0);
    }

    private static Style applyCode(Style style, char code) {
        Integer color = colorFor(code);
        if (color != null) {
            return style.withColor(color);
        }
        return switch (Character.toLowerCase(code)) {
            case 'k' -> style.withObfuscated(true);
            case 'l' -> style.withBold(true);
            case 'm' -> style.withStrikethrough(true);
            case 'n' -> style.withUnderlined(true);
            case 'o' -> style.withItalic(true);
            case 'r' -> Style.EMPTY;
            default -> style;
        };
    }

    private static LegacyCode readLegacyCode(String text, int index) {
        if (text == null || index < 0 || index + 1 >= text.length() || text.charAt(index) != '&') {
            return null;
        }
        char code = Character.toLowerCase(text.charAt(index + 1));
        if (code == 'x' && index + 13 < text.length()) {
            StringBuilder hex = new StringBuilder(6);
            for (int cursor = index + 2; cursor <= index + 12; cursor += 2) {
                if (text.charAt(cursor) != '&' || !isHex(text.charAt(cursor + 1))) {
                    return new LegacyCode(null, code, index + 1);
                }
                hex.append(text.charAt(cursor + 1));
            }
            try {
                return new LegacyCode(0xFF000000 | Integer.parseInt(hex.toString(), 16), '\u0000', index + 13);
            } catch (NumberFormatException ignored) {
                return new LegacyCode(null, code, index + 1);
            }
        }
        if (code == '#' && index + 7 < text.length()) {
            String hex = text.substring(index + 2, index + 8);
            if (hex.chars().allMatch(value -> isHex((char) value))) {
                try {
                    return new LegacyCode(0xFF000000 | Integer.parseInt(hex, 16), '\u0000', index + 7);
                } catch (NumberFormatException ignored) {
                    return new LegacyCode(null, code, index + 1);
                }
            }
        }
        if (colorFor(code) != null || "klmnor".indexOf(code) >= 0) {
            return new LegacyCode(null, code, index + 1);
        }
        return null;
    }

    private static Integer colorFor(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> 0xFF000000;
            case '1' -> 0xFF0000AA;
            case '2' -> 0xFF00AA00;
            case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000;
            case '5' -> 0xFFAA00AA;
            case '6' -> 0xFFFFAA00;
            case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555;
            case '9' -> 0xFF5555FF;
            case 'a' -> 0xFF55FF55;
            case 'b' -> 0xFF55FFFF;
            case 'c' -> 0xFFFF5555;
            case 'd' -> 0xFFFF55FF;
            case 'e' -> 0xFFFFFF55;
            case 'f' -> 0xFFFFFFFF;
            default -> null;
        };
    }

    private static boolean isHex(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    private record LegacyCode(Integer color, char code, int endIndex) {
    }

    private record StyledText(String text, Style style) {
    }

    private record LocalNameMatch(String name, int index) {
    }
}

