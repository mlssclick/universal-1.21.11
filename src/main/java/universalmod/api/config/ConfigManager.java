package universalmod.api.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import universalmod.api.config.exception.ConfigException;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.TickEvent;
import universalmod.api.module.ModuleManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.awt.Desktop;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigManager {
    public static final String ROOT_FOLDER = "universalmod";
    public static final String SYSTEM_FOLDER = "system";
    public static final String CONFIGS_FOLDER = "configs";
    public static final String CONFIG_EXTENSION = ".universalmod";
    public static final String DEFAULT_PROFILE = "default";
    private static final long AUTO_SAVE_DELAY_MS = 1000L;
    private static final String PROFILE_METADATA_KEY = "profile";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path systemDirectory;
    private final Path profilesDirectory;
    private final Path activeProfileFile;
    private final ModuleConfigStorage moduleStorage;
    private final Map<String, CachedProfileInfo> profileInfoCache = new HashMap<>();
    private boolean dirty;
    private boolean applyingProfile;
    private long dirtySince;
    private String activeProfile = DEFAULT_PROFILE;

    public ConfigManager(ModuleManager moduleManager) {
        this.systemDirectory = systemDirectory();
        this.profilesDirectory = systemDirectory.resolve(CONFIGS_FOLDER);
        this.activeProfileFile = systemDirectory.resolve("active_config" + CONFIG_EXTENSION);
        this.moduleStorage = new ModuleConfigStorage(moduleManager);
    }

    public static Path rootDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(ROOT_FOLDER);
    }

    public static Path systemDirectory() {
        return rootDirectory().resolve(SYSTEM_FOLDER);
    }

    public static Path legacyDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("universalmod");
    }

    public void init() {
        try {
            Files.createDirectories(systemDirectory);
            Files.createDirectories(profilesDirectory);
        } catch (IOException exception) {
            throw new ConfigException("Failed to create config directories under: " + rootDirectory(), exception);
        }
    }

    public void loadAll() {
        ensureDefaultProfile();
        activeProfile = readActiveProfile();
        if (!Files.isRegularFile(profileFile(activeProfile))) {
            activeProfile = DEFAULT_PROFILE;
            writeActiveProfile();
        }
        Path file = resolveModulesFileForRead();
        if (!Files.exists(file)) {
            saveModules();
        } else {
            applyProfile(readModules(file));
        }
        dirty = false;
    }

    public void saveAll() {
        saveModules();
        dirty = false;
    }

    public void markDirty() {
        if (applyingProfile) {
            return;
        }
        // Debounce disk writes: every new change restarts the quiet-period timer.
        // This prevents synchronous full-config saves from firing in the middle of
        // continuous UI interactions such as dragging a slider.
        dirty = true;
        dirtySince = System.currentTimeMillis();
    }

    public List<String> profiles() {
        ensureDefaultProfile();
        try {
            if (!Files.exists(profilesDirectory)) {
                return List.of(DEFAULT_PROFILE);
            }
            List<String> result = new ArrayList<>();
            try (var stream = Files.list(profilesDirectory)) {
                stream.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(CONFIG_EXTENSION))
                        .map(name -> name.substring(0, name.length() - CONFIG_EXTENSION.length()))
                        .map(ConfigManager::sanitizeProfileName)
                        .filter(name -> !name.isBlank())
                        .distinct()
                        .sorted(Comparator.comparing(name -> DEFAULT_PROFILE.equals(name) ? "" : name.toLowerCase(Locale.ROOT)))
                        .forEach(result::add);
            }
            if (!result.contains(DEFAULT_PROFILE)) {
                result.add(0, DEFAULT_PROFILE);
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            return List.of(DEFAULT_PROFILE);
        }
    }

    public String activeProfile() {
        return activeProfile;
    }

    public boolean revealProfileFile(String name) {
        String sanitized = sanitizeProfileName(name);
        if (sanitized.isBlank()) {
            return false;
        }
        Path file = profileFile(sanitized).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            return false;
        }

        try {
            String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (operatingSystem.contains("win")) {
                new ProcessBuilder("explorer.exe", "/select," + file).start();
                return true;
            }
            if (operatingSystem.contains("mac")) {
                new ProcessBuilder("open", "-R", file.toString()).start();
                return true;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                    desktop.browseFileDirectory(file.toFile());
                    return true;
                }
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(file.getParent().toFile());
                    return true;
                }
            }
            new ProcessBuilder("xdg-open", file.getParent().toString()).start();
            return true;
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            System.err.println("[UniversalMod] Failed to reveal config file: " + file);
            return false;
        }
    }

    public ProfileInfo profileInfo(String name) {
        String sanitized = sanitizeProfileName(name);
        if (sanitized.isBlank()) {
            sanitized = DEFAULT_PROFILE;
        }

        Path file = profileFile(sanitized);
        long modifiedAt = fileModifiedAt(file);
        CachedProfileInfo cached = profileInfoCache.get(sanitized);
        if (cached != null && cached.modifiedAt() == modifiedAt) {
            return cached.info();
        }

        ProfileInfo info = readProfileInfo(sanitized, file);
        profileInfoCache.put(sanitized, new CachedProfileInfo(info, modifiedAt));
        return info;
    }

    public String createProfile() {
        JsonObject previousState = moduleStorage.save();
        String previousProfile = activeProfile;
        saveAll();
        String name = uniqueProfileName("config");
        try {
            applyProfile(null);
            activeProfile = name;
            writeActiveProfile();
            saveAll();
            return name;
        } catch (RuntimeException exception) {
            restoreProfile(previousState, previousProfile, exception);
            throw exception;
        }
    }

    public boolean selectProfile(String name) {
        String sanitized = sanitizeProfileName(name);
        if (sanitized.isBlank() || sanitized.equals(activeProfile)) {
            return false;
        }

        Path targetFile = profileFile(sanitized);
        if (!Files.isRegularFile(targetFile)) {
            return false;
        }

        JsonObject target = readModules(targetFile);
        JsonObject previousState = moduleStorage.save();
        String previousProfile = activeProfile;
        saveAll();
        try {
            applyProfile(target);
            activeProfile = sanitized;
            writeActiveProfile();
            dirty = false;
            return true;
        } catch (RuntimeException exception) {
            restoreProfile(previousState, previousProfile, exception);
            throw exception;
        }
    }

    public boolean deleteProfile(String name) {
        String sanitized = sanitizeProfileName(name);
        if (sanitized.isBlank() || DEFAULT_PROFILE.equals(sanitized)) {
            return false;
        }
        JsonObject previousState = null;
        String previousProfile = activeProfile;
        try {
            if (sanitized.equals(activeProfile)) {
                previousState = moduleStorage.save();
                Path defaultFile = profileFile(DEFAULT_PROFILE);
                JsonObject defaultState = Files.isRegularFile(defaultFile)
                        ? readModules(defaultFile)
                        : null;
                applyProfile(defaultState);
                activeProfile = DEFAULT_PROFILE;
                writeActiveProfile();
                dirty = false;
            }
            Files.deleteIfExists(profileFile(sanitized));
            profileInfoCache.remove(sanitized);
            return true;
        } catch (IOException exception) {
            if (previousState != null) {
                restoreProfile(previousState, previousProfile, exception);
            }
            return false;
        } catch (RuntimeException exception) {
            if (previousState != null) {
                restoreProfile(previousState, previousProfile, exception);
            }
            throw exception;
        }
    }

    public boolean renameProfile(String oldName, String newName) {
        String oldSanitized = sanitizeProfileName(oldName);
        String newSanitized = sanitizeProfileName(newName);
        if (oldSanitized.isBlank() || newSanitized.isBlank()
                || DEFAULT_PROFILE.equals(oldSanitized)
                || oldSanitized.equals(newSanitized)
                || Files.exists(profileFile(newSanitized))) {
            return false;
        }
        try {
            if (oldSanitized.equals(activeProfile)) {
                saveAll();
            }
            Files.move(profileFile(oldSanitized), profileFile(newSanitized));
            profileInfoCache.remove(oldSanitized);
            profileInfoCache.remove(newSanitized);
            if (oldSanitized.equals(activeProfile)) {
                activeProfile = newSanitized;
                writeActiveProfile();
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        if (dirty && System.currentTimeMillis() - dirtySince >= AUTO_SAVE_DELAY_MS) {
            saveAll();
        }
    }

    private JsonObject readModules(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("modules") || !root.get("modules").isJsonObject()) {
                throw new ConfigException("Module config has no valid modules object: " + file);
            }
            return root;
        } catch (Exception exception) {
            if (exception instanceof ConfigException configException) {
                throw configException;
            }
            throw new ConfigException("Failed to load module config: " + file, exception);
        }
    }

    private void applyProfile(JsonObject root) {
        ModuleManager moduleManager = moduleStorage.moduleManager();
        boolean wasApplying = applyingProfile;
        applyingProfile = true;
        moduleManager.beginConfigApply();
        try {
            resetModulesToDefaults();
            if (root != null) {
                moduleStorage.load(root);
            }
        } finally {
            moduleManager.endConfigApply();
            applyingProfile = wasApplying;
        }
    }

    private void restoreProfile(JsonObject state, String profile, Throwable failure) {
        try {
            applyProfile(state);
            activeProfile = profile;
            writeActiveProfile();
            dirty = false;
        } catch (RuntimeException rollbackException) {
            failure.addSuppressed(rollbackException);
        }
    }

    private void saveModules() {
        try {
            Files.createDirectories(profilesDirectory);
            Path file = profileFile(activeProfile);
            ProfileInfo info = profileInfo(activeProfile);
            JsonObject root = moduleStorage.save();
            root.add(PROFILE_METADATA_KEY, profileMetadata(info));
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            profileInfoCache.put(activeProfile, new CachedProfileInfo(info, fileModifiedAt(file)));
        } catch (IOException exception) {
            throw new ConfigException("Failed to save module config: " + profileFile(activeProfile), exception);
        }
    }

    private Path resolveModulesFileForRead() {
        Path activeFile = profileFile(activeProfile);
        if (Files.exists(activeFile)) {
            return activeFile;
        }

        Path oldSystem = systemDirectory.resolve("modules" + CONFIG_EXTENSION);
        if (Files.exists(oldSystem)) {
            return oldSystem;
        }

        Path legacyJson = legacyDirectory().resolve("modules.json");
        if (Files.exists(legacyJson)) {
            return legacyJson;
        }

        return activeFile;
    }

    private void ensureDefaultProfile() {
        try {
            Files.createDirectories(profilesDirectory);
            Path defaultFile = profileFile(DEFAULT_PROFILE);
            if (!Files.exists(defaultFile)) {
                Path oldSystem = systemDirectory.resolve("modules" + CONFIG_EXTENSION);
                if (Files.exists(oldSystem)) {
                    Files.copy(oldSystem, defaultFile);
                } else {
                    ProfileInfo info = new ProfileInfo(DEFAULT_PROFILE, System.currentTimeMillis(), currentCreatorName());
                    JsonObject root = moduleStorage.save();
                    root.add(PROFILE_METADATA_KEY, profileMetadata(info));
                    try (Writer writer = Files.newBufferedWriter(defaultFile, StandardCharsets.UTF_8)) {
                        gson.toJson(root, writer);
                    }
                }
            }
            if (!Files.exists(activeProfileFile)) {
                writeActiveProfile();
            }
        } catch (IOException exception) {
            throw new ConfigException("Failed to initialize config profiles: " + profilesDirectory, exception);
        }
    }

    private void resetModulesToDefaults() {
        for (var module : moduleStorage.moduleManager().getModules()) {
            module.resetToDefaults();
        }
    }

    private String readActiveProfile() {
        if (!Files.exists(activeProfileFile)) {
            return DEFAULT_PROFILE;
        }
        try (Reader reader = Files.newBufferedReader(activeProfileFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            String name = root.has("active") ? root.get("active").getAsString() : DEFAULT_PROFILE;
            String sanitized = sanitizeProfileName(name);
            return sanitized.isBlank() ? DEFAULT_PROFILE : sanitized;
        } catch (Exception ignored) {
            return DEFAULT_PROFILE;
        }
    }

    private void writeActiveProfile() {
        try {
            Files.createDirectories(systemDirectory);
            JsonObject root = new JsonObject();
            root.addProperty("active", activeProfile);
            try (Writer writer = Files.newBufferedWriter(activeProfileFile, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
        } catch (IOException exception) {
            throw new ConfigException("Failed to save active config profile: " + activeProfileFile, exception);
        }
    }

    private String uniqueProfileName(String base) {
        String sanitizedBase = sanitizeProfileName(base);
        if (sanitizedBase.isBlank()) {
            sanitizedBase = "config";
        }
        String candidate = sanitizedBase;
        int index = 1;
        while (Files.exists(profileFile(candidate))) {
            candidate = sanitizedBase + "_" + index++;
        }
        return candidate;
    }

    private Path profileFile(String name) {
        return profilesDirectory.resolve(sanitizeProfileName(name) + CONFIG_EXTENSION);
    }

    private ProfileInfo readProfileInfo(String name, Path file) {
        long createdAt = fileCreatedAt(file);
        String createdBy = currentCreatorName();

        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has(PROFILE_METADATA_KEY) && root.get(PROFILE_METADATA_KEY).isJsonObject()) {
                    JsonObject metadata = root.getAsJsonObject(PROFILE_METADATA_KEY);
                    if (metadata.has("createdAt")) {
                        long savedCreatedAt = metadata.get("createdAt").getAsLong();
                        if (savedCreatedAt > 0L) {
                            createdAt = savedCreatedAt;
                        }
                    }
                    if (metadata.has("createdBy")) {
                        String savedCreatedBy = metadata.get("createdBy").getAsString().trim();
                        if (!savedCreatedBy.isBlank()) {
                            createdBy = savedCreatedBy;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Invalid module data is still reported by readModules(). Metadata is optional.
            }
        }

        return new ProfileInfo(name, createdAt, createdBy);
    }

    private static JsonObject profileMetadata(ProfileInfo info) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("createdAt", info.createdAt());
        metadata.addProperty("createdBy", info.createdBy());
        return metadata;
    }

    private static long fileCreatedAt(Path file) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            long createdAt = attributes.creationTime().toMillis();
            if (createdAt > 0L) {
                return createdAt;
            }
            long modifiedAt = attributes.lastModifiedTime().toMillis();
            if (modifiedAt > 0L) {
                return modifiedAt;
            }
        } catch (IOException ignored) {
        }
        return System.currentTimeMillis();
    }

    private static long fileModifiedAt(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ignored) {
            return -1L;
        }
    }

    private static String currentCreatorName() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getUser() != null) {
            String name = client.getUser().getName();
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }
        return "Player";
    }

    private static String sanitizeProfileName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                builder.append(c);
            } else if (Character.isWhitespace(c)) {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    public record ProfileInfo(String name, long createdAt, String createdBy) {
    }

    private record CachedProfileInfo(ProfileInfo info, long modifiedAt) {
    }
}
