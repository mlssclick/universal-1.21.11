package universalmod.utils.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import universalmod.api.config.ConfigManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent per-element HUD size and Music Player view settings. */
public final class HudStyleOverrides {
    public static final String MUSIC_PLAYER_VIEW_1 = "1";
    public static final String MUSIC_PLAYER_VIEW_2 = "2";
    public static final float SIZE_MIN_PERCENT = 50.0F;
    public static final float SIZE_MAX_PERCENT = 150.0F;
    public static final float SIZE_DEFAULT_PERCENT = 100.0F;

    private static final String FILE_NAME = "hud_style_overrides" + ConfigManager.CONFIG_EXTENSION;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final HudStyleOverrides INSTANCE = new HudStyleOverrides();

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean loaded;

    private HudStyleOverrides() {
    }

    public static HudStyleOverrides getInstance() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public float getSizePercent(String elementId) {
        Entry entry = entry(elementId);
        return entry == null ? SIZE_DEFAULT_PERCENT : normalizeSizePercent(entry.sizePercent);
    }

    public void setSizePercent(String elementId, float sizePercent) {
        setSizePercentInternal(elementId, sizePercent, true);
    }

    public void previewSizePercent(String elementId, float sizePercent) {
        setSizePercentInternal(elementId, sizePercent, false);
    }

    public void commitSizePercent(String elementId) {
        ensureLoaded();
        String key = normalize(elementId);
        Entry entry = entries.get(key);
        if (entry != null) {
            entry.sizePercent = normalizeSizePercent(entry.sizePercent);
            pruneIfDefault(key, entry);
        }
        save();
    }

    public String getMusicPlayerView(String elementId) {
        Entry entry = entry(elementId);
        return entry == null ? MUSIC_PLAYER_VIEW_2 : normalizeMusicPlayerView(entry.musicPlayerView);
    }

    public void setMusicPlayerView(String elementId, String view) {
        ensureLoaded();
        String key = normalize(elementId);
        Entry entry = editableEntry(key);
        entry.musicPlayerView = normalizeMusicPlayerView(view);
        pruneIfDefault(key, entry);
        save();
    }

    private void setSizePercentInternal(String elementId, float sizePercent, boolean persist) {
        ensureLoaded();
        String key = normalize(elementId);
        Entry entry = editableEntry(key);
        entry.sizePercent = normalizeSizePercent(sizePercent);
        if (persist) {
            pruneIfDefault(key, entry);
            save();
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = configFile();
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root == null || !root.has("entries") || !root.get("entries").isJsonObject()) {
                return;
            }
            JsonObject object = root.getAsJsonObject("entries");
            for (String key : object.keySet()) {
                Entry entry = GSON.fromJson(object.get(key), Entry.class);
                if (entry == null) {
                    continue;
                }
                entry.sizePercent = normalizeSizePercent(entry.sizePercent);
                entry.musicPlayerView = normalizeMusicPlayerView(entry.musicPlayerView);
                entries.put(normalize(key), entry);
            }
        } catch (Exception exception) {
            System.err.println("Failed to load HUD settings: " + exception.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(configFile().getParent());
            try (Writer writer = Files.newBufferedWriter(configFile(), StandardCharsets.UTF_8)) {
                JsonObject root = new JsonObject();
                root.addProperty("version", 2);
                JsonObject object = new JsonObject();
                for (Map.Entry<String, Entry> entry : entries.entrySet()) {
                    object.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
                }
                root.add("entries", object);
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            System.err.println("Failed to save HUD settings: " + exception.getMessage());
        }
    }

    private Entry entry(String elementId) {
        ensureLoaded();
        return entries.get(normalize(elementId));
    }

    private Entry editableEntry(String key) {
        return entries.computeIfAbsent(key, ignored -> new Entry());
    }

    private void pruneIfDefault(String key, Entry entry) {
        if (entry != null
                && Math.abs(normalizeSizePercent(entry.sizePercent) - SIZE_DEFAULT_PERCENT) < 0.001F
                && MUSIC_PLAYER_VIEW_2.equals(normalizeMusicPlayerView(entry.musicPlayerView))) {
            entries.remove(key);
        }
    }

    private static float normalizeSizePercent(float value) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            return SIZE_DEFAULT_PERCENT;
        }
        return Math.max(SIZE_MIN_PERCENT, Math.min(SIZE_MAX_PERCENT, Math.round(value)));
    }

    private static String normalizeMusicPlayerView(String value) {
        return MUSIC_PLAYER_VIEW_1.equals(String.valueOf(value)) ? MUSIC_PLAYER_VIEW_1 : MUSIC_PLAYER_VIEW_2;
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase();
    }

    private static Path configFile() {
        return ConfigManager.systemDirectory().resolve(FILE_NAME);
    }

    private static final class Entry {
        private String musicPlayerView = MUSIC_PLAYER_VIEW_2;
        private float sizePercent = SIZE_DEFAULT_PERCENT;
    }
}
