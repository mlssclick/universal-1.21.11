package universalmod.utils.lang;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class LanguageManager {
    private static final String RESOURCE_ROOT = "assets/universalmod/lang/";
    private static final Map<LanguageCode, Map<String, String>> CACHE = new EnumMap<>(LanguageCode.class);
    private static volatile LanguageCode current = LanguageCode.RU_RU;

    private LanguageManager() {
    }

    public static void setCurrent(LanguageCode languageCode) {
        current = languageCode == null ? LanguageCode.RU_RU : languageCode;
    }

    public static void setCurrent(String mode) {
        setCurrent(LanguageCode.fromMode(mode));
    }

    public static LanguageCode current() {
        return current;
    }

    public static String translate(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        String localized = translations(current).get(key);
        if (localized != null) {
            return localized;
        }

        localized = translations(LanguageCode.EN_US).get(key);
        return localized != null ? localized : key;
    }

    public static String translateFormat(String key, Object... args) {
        String pattern = translate(key);
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (Exception ignored) {
            return pattern;
        }
    }

    private static Map<String, String> translations(LanguageCode languageCode) {
        return CACHE.computeIfAbsent(languageCode, LanguageManager::loadTranslations);
    }

    private static Map<String, String> loadTranslations(LanguageCode languageCode) {
        String resource = RESOURCE_ROOT + languageCode.fileName() + ".json";
        try (InputStream stream = LanguageManager.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                return Collections.emptyMap();
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    result.put(entry.getKey(), value.getAsString());
                }
            }
            return result;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
