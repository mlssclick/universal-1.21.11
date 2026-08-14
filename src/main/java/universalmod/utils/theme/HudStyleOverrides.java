package universalmod.utils.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import universalmod.api.config.ConfigManager;
import universalmod.api.module.ModuleManager;
import universalmod.api.module.impl.misc.CustomTheme;
import universalmod.manager.Manager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HudStyleOverrides {
    public static final String PRESET_DEFAULT = "Default";
    public static final String PRESET_CUSTOM = "Custom";
    public static final String DESIGN_DEFAULT = "Default";
    public static final String DESIGN_LIQUID_GLASS = "Liquid Glass";
    public static final String DESIGN_DARK = "Dark";
    public static final String STYLE_WITHOUT_NAME = "Without Name";
    public static final String STYLE_SPLIT = "Split";
    public static final String STYLE_MERGE = "Merge";
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

    public String getPreset(String elementId) {
        Entry entry = entry(elementId);
        return entry == null ? PRESET_DEFAULT : normalizePreset(entry.preset);
    }

    public String getDesign(String elementId) {
        Entry entry = entry(elementId);
        return entry == null ? DESIGN_DEFAULT : normalizeDesign(entry.design, elementId);
    }

    public String getStyle(String elementId) {
        Entry entry = entry(elementId);
        return entry == null ? STYLE_SPLIT : normalizeStyle(entry.style, elementId);
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
        Entry entry = entry(elementId);
        if (entry != null) {
            entry.sizePercent = normalizeSizePercent(entry.sizePercent);
            pruneIfDefault(normalize(elementId), entry);
        }
        save();
    }

    public void setPreset(String elementId, String preset) {
        ensureLoaded();
        Entry entry = editableEntry(elementId);
        entry.preset = normalizePreset(preset);
        if (PRESET_DEFAULT.equals(entry.preset)) {
            pruneIfDefault(normalize(elementId), entry);
        }
        save();
    }

    public void setDesign(String elementId, String design) {
        ensureLoaded();
        Entry entry = editableEntry(elementId);
        entry.design = normalizeDesign(design, elementId);
        save();
    }

    public void setStyle(String elementId, String style) {
        ensureLoaded();
        Entry entry = editableEntry(elementId);
        entry.style = normalizeStyle(style, elementId);
        save();
    }

    private void setSizePercentInternal(String elementId, float sizePercent, boolean persist) {
        ensureLoaded();
        Entry entry = editableEntry(elementId);
        entry.sizePercent = normalizeSizePercent(sizePercent);
        if (persist) {
            pruneIfDefault(normalize(elementId), entry);
            save();
        }
    }

    public String resolveHudDesign(String elementId) {
        CustomTheme theme = theme();
        String global = theme == null || !theme.isEnabled() ? DESIGN_DEFAULT : theme.getHudDesign();
        if (elementId == null || elementId.isBlank()) {
            return global;
        }
        Entry entry = entry(elementId);
        if (entry == null || !PRESET_CUSTOM.equals(normalizePreset(entry.preset))) {
            return normalizedResolvedDesign(global, elementId);
        }
        return normalizeDesign(entry.design, elementId);
    }

    public String resolveHudStyle(String elementId) {
        CustomTheme theme = theme();
        String global = theme == null || !theme.isEnabled() ? STYLE_SPLIT : theme.getHudStyle();
        if (elementId == null || elementId.isBlank()) {
            return normalizeStyle(global, elementId);
        }
        Entry entry = entry(elementId);
        if (entry == null || !PRESET_CUSTOM.equals(normalizePreset(entry.preset))) {
            return normalizeStyle(global, elementId);
        }
        return normalizeStyle(entry.style, elementId);
    }

    public boolean hasCustomPreset(String elementId) {
        return PRESET_CUSTOM.equals(getPreset(elementId));
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
                entry.preset = normalizePreset(entry.preset);
                entry.design = normalizeDesign(entry.design, key);
                entry.style = normalizeStyle(entry.style, key);
                entry.sizePercent = normalizeSizePercent(entry.sizePercent);
                entries.put(normalize(key), entry);
            }
        } catch (Exception exception) {
            System.err.println("Failed to load HUD style overrides: " + exception.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(configFile().getParent());
            try (Writer writer = Files.newBufferedWriter(configFile(), StandardCharsets.UTF_8)) {
                JsonObject root = new JsonObject();
                root.addProperty("version", 1);
                JsonObject object = new JsonObject();
                for (Map.Entry<String, Entry> entry : entries.entrySet()) {
                    object.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
                }
                root.add("entries", object);
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            System.err.println("Failed to save HUD style overrides: " + exception.getMessage());
        }
    }

    private Entry entry(String elementId) {
        ensureLoaded();
        return entries.get(normalize(elementId));
    }

    private Entry editableEntry(String elementId) {
        String key = normalize(elementId);
        return entries.computeIfAbsent(key, ignored -> new Entry());
    }

    private void pruneIfDefault(String key, Entry entry) {
        if (entry == null) {
            return;
        }
        if (PRESET_DEFAULT.equals(normalizePreset(entry.preset))
                && DESIGN_DEFAULT.equals(normalizeDesign(entry.design, key))
                && STYLE_SPLIT.equals(normalizeStyle(entry.style, key))
                && Math.abs(normalizeSizePercent(entry.sizePercent) - SIZE_DEFAULT_PERCENT) < 0.001F) {
            entries.remove(key);
        }
    }

    private static String normalizedResolvedDesign(String design, String elementId) {
        return normalizeDesign(design, elementId);
    }

    private static float normalizeSizePercent(float value) {

        if (!Float.isFinite(value) || value <= 0.0F) {
            return SIZE_DEFAULT_PERCENT;
        }
        return Math.max(SIZE_MIN_PERCENT, Math.min(SIZE_MAX_PERCENT, Math.round(value)));
    }

    private static String normalizePreset(String preset) {
        if (PRESET_CUSTOM.equalsIgnoreCase(String.valueOf(preset))) {
            return PRESET_CUSTOM;
        }
        return PRESET_DEFAULT;
    }

    private static String normalizeStyle(String style, String elementId) {
        if (STYLE_WITHOUT_NAME.equalsIgnoreCase(String.valueOf(style))) {
            return isEventElement(elementId) ? STYLE_MERGE : STYLE_WITHOUT_NAME;
        }
        if (STYLE_MERGE.equalsIgnoreCase(String.valueOf(style))) {
            return STYLE_MERGE;
        }
        return STYLE_SPLIT;
    }

    private static String normalizeDesign(String design, String elementId) {
        if (DESIGN_LIQUID_GLASS.equalsIgnoreCase(String.valueOf(design))) {
            return DESIGN_LIQUID_GLASS;
        }
        if (DESIGN_DARK.equalsIgnoreCase(String.valueOf(design)) && !isDarkBlockedElement(elementId)) {
            return DESIGN_DARK;
        }
        return DESIGN_DEFAULT;
    }

    private static boolean isEventElement(String elementId) {
        String key = normalize(elementId);
        return key.equals("event") || key.equals("hud.event");
    }

    private static boolean isDarkBlockedElement(String elementId) {
        return false;
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase();
    }

    private static Path configFile() {
        return ConfigManager.systemDirectory().resolve(FILE_NAME);
    }

    private static CustomTheme theme() {
        ModuleManager modules = Manager.getModules();
        if (modules == null) {
            return null;
        }
        return modules.getByType(CustomTheme.class).orElse(null);
    }

    private static final class Entry {
        private String preset = PRESET_DEFAULT;
        private String design = DESIGN_DEFAULT;
        private String style = STYLE_SPLIT;
        private float sizePercent = SIZE_DEFAULT_PERCENT;
    }
}
