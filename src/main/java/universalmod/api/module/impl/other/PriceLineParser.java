package universalmod.api.module.impl.other;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PriceLineParser {
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "[0-9](?:[0-9\\s\\u00A0\\u202F.,'_])*"
    );

    private static final Pattern BID_ACTION_PATTERN = Pattern.compile(
            "(?iu)ставку\\s+в\\s+([0-9](?:[0-9\\s\\u00A0\\u202F.,'_])*)"
    );

    private static final Pattern LEGACY_FORMATTING_PATTERN = Pattern.compile(
            "(?i)\\u00A7[0-9A-FK-ORX]"
    );

    private static final Pattern EXISTING_SUFFIX_PATTERN = Pattern.compile(
            ".*\\([0-9]+[.,][0-9]{2}\\s+\\|.\\|\\).*"
    );

    private PriceLineParser() {
    }

    public static boolean isSupportedPriceLine(String rawText) {
        String clean = stripLegacyFormatting(rawText);
        if (BID_ACTION_PATTERN.matcher(clean).find()) {
            return true;
        }

        int colonIndex = findColon(clean);
        if (colonIndex < 0) {
            return false;
        }

        String label = normalizeLabel(clean.substring(0, colonIndex));
        return label.contains("текущая цена")
                || label.contains("цена шага")
                || label.contains("начальная цена")
                || label.contains("цена выкупа")
                || label.contains("цена за 1 ед")
                || label.contains("цена за ед")
                || label.contains("цена за 1 шт")
                || label.contains("цена за шт")
                || label.contains("сумма ставки")
                || label.equals("цена")
                || label.endsWith(" цена");
    }

    public static Double extractPrice(String rawText) {
        String clean = stripLegacyFormatting(rawText);
        Matcher bidMatcher = BID_ACTION_PATTERN.matcher(clean);
        if (bidMatcher.find()) {
            return parsePriceToken(bidMatcher.group(1));
        }

        int colonIndex = findColon(clean);
        if (colonIndex < 0 || colonIndex + 1 >= clean.length()) {
            return null;
        }

        Matcher matcher = NUMBER_PATTERN.matcher(clean.substring(colonIndex + 1));
        return matcher.find() ? parsePriceToken(matcher.group()) : null;
    }

    public static boolean hasCoinSuffix(String rawText) {
        return rawText.contains("|❘|") || EXISTING_SUFFIX_PATTERN.matcher(rawText).matches();
    }

    private static Double parsePriceToken(String token) {
        String digitsOnly = token.replaceAll("[^0-9]", "");
        if (digitsOnly.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(digitsOnly);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeLabel(String value) {
        return stripLegacyFormatting(value)
                .toLowerCase(Locale.ROOT)
                .replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String stripLegacyFormatting(String value) {
        return LEGACY_FORMATTING_PATTERN.matcher(value).replaceAll("");
    }

    private static int findColon(String value) {
        int asciiColon = value.indexOf(':');
        int fullWidthColon = value.indexOf('：');
        if (asciiColon < 0) {
            return fullWidthColon;
        }
        if (fullWidthColon < 0) {
            return asciiColon;
        }
        return Math.min(asciiColon, fullWidthColon);
    }
}
