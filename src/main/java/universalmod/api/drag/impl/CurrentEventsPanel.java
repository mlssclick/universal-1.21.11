package universalmod.api.drag.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import universalmod.api.module.impl.render.Hud;
import universalmod.utils.lang.LanguageManager;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class CurrentEventsPanel extends HudPanel {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "universalmod-current-events.json";
    private static final URI EVENTS_URI = URI.create("https://api.holyworld.me/v1/events");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ScheduledExecutorService POLLER = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "universalmod-current-events-poller");
            thread.setDaemon(true);
            return thread;
        }
    });

    private static final float PANEL_WIDTH = 154.0F;
    private static final float PANEL_MIN_HEIGHT = 77.0F;
    private static final float DEFAULT_X = 18.0F;
    private static final float DEFAULT_Y = 18.0F;
    private static final float TITLE_HEIGHT = 18.0F;
    private static final float PANEL_GAP = 3.0F;
    private static final float BODY_MIN_HEIGHT = PANEL_MIN_HEIGHT - TITLE_HEIGHT - PANEL_GAP;
    private static final float CONTENT_PADDING_TOP = 4.0F;
    private static final float ROW_HEIGHT = 14.0F;
    private static final float CONTENT_PADDING_BOTTOM = 8.0F;
    private static final int MAX_VISIBLE_EVENTS = 10;
    private static final String TITLE_TEXT = "Current Events";
    private static final String EMPTY_TEXT = "No active events";
    private static final String NEW_ANARCHY_FILTER_LABEL = "1.20 Events";
    private static final Map<String, String> DEFAULT_ANARCHY_RANGES = Map.of(
            "Solo", "1-17",
            "Duo", "18-38",
            "Trio", "39-57",
            "Clan", "58-74"
    );
    private static final int TITLE_COLOR = 0xFFF3F6FA;
    private static final int EMPTY_COLOR = 0xFF96A0AA;
    private static final int TAG_COLOR = 0xFFE7EDF3;
    private static final float GEAR_SIZE = 9.0F;
    private static final float TAG_COLUMN_CENTER_X = PANEL_WIDTH - 10.0F;
    private static final float GEAR_HIT_PADDING = 4.0F;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final long POLL_INTERVAL_SECONDS = 5L;
    private static final String GEAR_TEXTURE = "universalmod:textures/hud/event_gear.png";
    private static final List<FilterDefinition> FILTER_DEFINITIONS = List.of(
            new FilterDefinition("Босс", 0xFFB861FF, new String[]{"босс"}),
            new FilterDefinition("Тыпо", 0xFF7CFF3A, new String[]{"опытный типо", "опытный тыпо", "типо", "тыпо", "jaycob"}),
            new FilterDefinition("Корабль", 0xFF8A5A2B, new String[]{"таинственный корабль", "корабль", "ship"}),
            new FilterDefinition("Контейнер", 0xFF5DBBFF, new String[]{"контейнер", "container"}),
            new FilterDefinition("Лихорадка", 0xFFF1C24A, new String[]{"золотая лихорадка", "лихорадка", "fever"}),
            new FilterDefinition("Посылка", 0xFF8A5A2B, new String[]{"посылка", "parcel"}),
            new FilterDefinition("Груз", 0xFFF34B4B, new String[]{"ценный груз", "груз", "cargo"}),
            new FilterDefinition("Цветочная поляна", 0xFF4CAF50, new String[]{"цветочная поляна", "snowquarry", "glade"}),
            new FilterDefinition("Смертельная шахта", 0xFF8B1E1E, new String[]{"смертельная шахта", "mine"}),
            new FilterDefinition("Кубик", 0xFF5DAEFF, new String[]{"кубик", "cube"}),
            new FilterDefinition(NEW_ANARCHY_FILTER_LABEL, 0xFFB388FF, new String[0])
    );

    private static final CurrentEventsPanel INSTANCE = new CurrentEventsPanel();

    private final Map<String, FilterEntry> filtersByName = new LinkedHashMap<>();
    private final Map<String, Boolean> savedEnabledStates = new LinkedHashMap<>();
    private final Map<String, String> anarchyRanges = new LinkedHashMap<>(DEFAULT_ANARCHY_RANGES);
    private final Object stateLock = new Object();
    private volatile List<LiveEvent> liveEvents = List.of();
    private volatile List<FilterEntry> filters = List.of();
    private int scrollOffset;
    private boolean loaded;
    private boolean pollingStarted;

    public CurrentEventsPanel() {
        super("event", "Event", DEFAULT_X, DEFAULT_Y, PANEL_WIDTH, PANEL_MIN_HEIGHT);
        for (FilterDefinition definition : FILTER_DEFINITIONS) {
            FilterEntry entry = new FilterEntry(definition.label(), definition.color(), definition.aliases());
            filtersByName.put(entry.stateKey(), entry);
        }
        filters = List.copyOf(filtersByName.values());
        loadState();
        applySavedStates();
        startPolling();
    }

    public static CurrentEventsPanel getInstance() {
        return INSTANCE;
    }

    @Override
    public void render() {
        List<LiveEvent> visibleEvents = visibleEvents();
        int displayCount = Math.min(MAX_VISIBLE_EVENTS, visibleEvents.size());
        int maxScroll = Math.max(0, visibleEvents.size() - MAX_VISIBLE_EVENTS);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }

        float alpha = contentAlpha(true);
        if (alpha <= 0.0F) {
            return;
        }

        float panelHeight = calculatePanelHeight(displayCount, false);
        size(PANEL_WIDTH, panelHeight);

        float x = drag.x();
        float y = drag.y();
        float bodyY = y + TITLE_HEIGHT;
        float bodyHeight = Math.max(BODY_MIN_HEIGHT, panelHeight - TITLE_HEIGHT);
        String translatedTitle = LanguageManager.translate(TITLE_TEXT);
        float titleY = y + (TITLE_HEIGHT - Render2D.textHeight(FontType.BOLD, translatedTitle, 8.0F)) * 0.5F;
        float gearY = y + (TITLE_HEIGHT - GEAR_SIZE) * 0.5F;
        float gearX = x + TAG_COLUMN_CENTER_X - GEAR_SIZE * 0.5F;
        int panelBackgroundColor = ColorUtil.rgba(10, 12, 16, Math.round(255.0F * alpha));

        Hud.renderHudBackground(x, y, PANEL_WIDTH, panelHeight, 5.0F, 4.0F, 0.55F, panelBackgroundColor);
        Render2D.rect(x + 6.0F, y + 6.0F, 1.0F, 5.0F, 0.5F,
                ColorUtil.rgba(228, 157, 91, Math.round(255.0F * alpha)));
        Render2D.text(FontType.BOLD, translatedTitle, x + 11.0F, titleY, 7.0F, 0xFFFFFFFF);
        Render2D.image(GEAR_TEXTURE, gearX, gearY, GEAR_SIZE, GEAR_SIZE, 0.0F, 0xFFFFFFFF);
        Render2D.rect(x + 5.0F, bodyY - 0.5F, PANEL_WIDTH - 10.0F, 0.5F, 0.0F,
                ColorUtil.rgba(255, 255, 255, Math.round(20.0F * alpha)));

        float rowY = bodyY + CONTENT_PADDING_TOP;
        if (visibleEvents.isEmpty()) {
            Render2D.text(FontType.BOLD, LanguageManager.translate(EMPTY_TEXT), x + 8.0F, rowY + 2.0F, 6.8F, EMPTY_COLOR);
            return;
        }

        int endIndex = Math.min(visibleEvents.size(), scrollOffset + MAX_VISIBLE_EVENTS);
        List<LiveEvent> displayedEvents = visibleEvents.subList(scrollOffset, endIndex);
        for (LiveEvent event : displayedEvents) {
            Render2D.text(FontType.BOLD, event.displayLabel(), x + 8.0F, rowY + 1.7F, 6.8F, brighten(event.color, 0.12F));
            String apiTag = event.apiTag();
            if (!apiTag.isEmpty()) {
                float tagWidth = Render2D.textWidth(FontType.BOLD, apiTag, 6.8F);
                Render2D.text(FontType.BOLD, apiTag, x + TAG_COLUMN_CENTER_X - tagWidth * 0.5F, rowY + 1.0F, 6.8F, TAG_COLOR);
            }
            rowY += ROW_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        if (event == null || event.button() != 0 || !isGearHit(event.x(), event.y())) {
            return false;
        }

        openMenu(mc == null ? null : mc.screen);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!drag.bounds().contains((float) mouseX, (float) mouseY)) {
            return false;
        }

        List<LiveEvent> visibleEvents = visibleEvents();
        int maxScroll = Math.max(0, visibleEvents.size() - MAX_VISIBLE_EVENTS);
        if (maxScroll <= 0) {
            return false;
        }

        int nextScroll = scrollOffset - (scrollY > 0.0D ? 1 : -1);
        int clamped = Math.max(0, Math.min(maxScroll, nextScroll));
        if (clamped == scrollOffset) {
            return false;
        }

        scrollOffset = clamped;
        return true;
    }

    public List<FilterEntry> filters() {
        return filters;
    }

    public List<EventFilterSnapshot> filterSnapshots() {
        List<EventFilterSnapshot> snapshots = new ArrayList<>(filters.size());
        for (FilterEntry entry : filters) {
            snapshots.add(new EventFilterSnapshot(entry.stateKey(), entry.label, entry.enabled));
        }
        return List.copyOf(snapshots);
    }

    public List<EventSnapshot> visibleEventSnapshots() {
        List<LiveEvent> current = visibleEvents();
        List<EventSnapshot> snapshots = new ArrayList<>(current.size());
        for (LiveEvent event : current) {
            snapshots.add(new EventSnapshot(event.id, event.displayName, event.rarityLabel(),
                    event.apiNumber, brighten(event.color, 0.12F)));
        }
        return List.copyOf(snapshots);
    }

    public void toggleFilter(String stateKey) {
        String normalized = normalize(stateKey);
        for (FilterEntry entry : filtersByName.values()) {
            if (entry.stateKey().equals(normalized)) {
                toggleEntry(entry);
                return;
            }
        }
    }

    public String anarchyRange(String category) {
        synchronized (stateLock) {
            return anarchyRanges.getOrDefault(category, DEFAULT_ANARCHY_RANGES.getOrDefault(category, ""));
        }
    }

    public boolean setAnarchyRange(String category, String range) {
        if (!DEFAULT_ANARCHY_RANGES.containsKey(category) || !isValidAnarchyRange(range)) {
            return false;
        }
        String normalized = normalizeAnarchyRange(range);
        synchronized (stateLock) {
            anarchyRanges.put(category, normalized);
        }
        saveState();
        return true;
    }

    public boolean matchesAnarchyRange(String category, int anarchyNumber) {
        if (anarchyNumber < 0) {
            return false;
        }
        String range = anarchyRange(category);
        int dash = range.indexOf('-');
        if (dash <= 0 || dash >= range.length() - 1) {
            return false;
        }
        try {
            int start = Integer.parseInt(range.substring(0, dash));
            int end = Integer.parseInt(range.substring(dash + 1));
            return anarchyNumber >= start && anarchyNumber <= end;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static boolean isValidAnarchyRange(String range) {
        String normalized = normalizeAnarchyRange(range);
        if (!normalized.matches("\\d{1,3}-\\d{1,3}")) {
            return false;
        }
        int dash = normalized.indexOf('-');
        try {
            int start = Integer.parseInt(normalized.substring(0, dash));
            int end = Integer.parseInt(normalized.substring(dash + 1));
            return start >= 0 && start <= end;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String normalizeAnarchyRange(String range) {
        return range == null ? "" : range.replace(" ", "").trim();
    }

    public void toggleEntry(FilterEntry entry) {
        if (entry == null) {
            return;
        }

        entry.enabled = !entry.enabled;
        synchronized (stateLock) {
            savedEnabledStates.put(entry.stateKey(), entry.enabled);
        }
        saveState();
    }

    public boolean isEnabled(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return false;
        }

        String normalized = normalize(displayName);
        for (FilterEntry entry : filtersByName.values()) {
            if (entry.matches(normalized)) {
                return entry.enabled;
            }
        }
        return false;
    }

    private boolean isEventEnabled(String apiKey, String id, String displayName) {
        if (isNewAnarchyEvent(apiKey, id) && !isNewAnarchyFilterEnabled()) {
            return false;
        }
        return isEnabled(displayName);
    }

    private boolean isNewAnarchyFilterEnabled() {
        FilterEntry entry = filtersByName.get(normalize(NEW_ANARCHY_FILTER_LABEL));
        return entry == null || entry.enabled;
    }

    private static boolean isNewAnarchyEvent(String apiKey, String id) {
        return containsNewToken(apiKey) || containsNewToken(id);
    }

    private static boolean containsNewToken(String value) {
        String canonical = normalize(value);
        if (canonical.isEmpty()) {
            return false;
        }
        canonical = canonical.replaceAll("[^\\p{L}0-9]+", "_");
        canonical = canonical.replaceAll("^_+|_+$", "");
        canonical = canonical.replaceAll("_+", "_");
        return canonical.equals("new")
                || canonical.startsWith("new_")
                || canonical.endsWith("_new")
                || canonical.contains("_new_");
    }

    public void openMenu(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        minecraft.setScreen(new EventMenuScreen(parent));
    }

    private void startPolling() {
        if (pollingStarted) {
            return;
        }
        pollingStarted = true;
        POLLER.scheduleWithFixedDelay(this::refreshFromApiSafely, 0L, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void refreshFromApiSafely() {
        if (!shouldPollEvents()) {
            return;
        }
        try {
            refreshFromApi();
        } catch (Exception exception) {
            System.err.println("Failed to refresh events from " + EVENTS_URI + " (" + exception.getMessage() + ")");
        }
    }

    private boolean shouldPollEvents() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null
                && minecraft.player != null
                && minecraft.level != null
                && !minecraft.isLocalServer();
    }

    private void refreshFromApi() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(EVENTS_URI)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Unexpected status " + response.statusCode());
        }

        JsonElement rootElement = JsonParser.parseString(response.body());
        if (rootElement == null || !rootElement.isJsonObject()) {
            throw new IllegalStateException("API returned invalid JSON");
        }

        List<LiveEvent> freshLiveEvents = new ArrayList<>();
        JsonObject root = rootElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> apiEntry : root.entrySet()) {
            String apiKey = apiEntry.getKey();
            JsonElement value = apiEntry.getValue();
            if (value == null || !value.isJsonArray()) {
                continue;
            }

            JsonArray array = value.getAsJsonArray();
            for (JsonElement eventElement : array) {
                if (eventElement == null || !eventElement.isJsonObject()) {
                    continue;
                }

                JsonObject eventObject = eventElement.getAsJsonObject();
                String id = stringOrEmpty(eventObject, "id");
                JsonObject metadata = eventObject.has("metadata") && eventObject.get("metadata").isJsonObject()
                        ? eventObject.getAsJsonObject("metadata")
                        : null;
                String displayName = metadata == null ? "" : stringOrEmpty(metadata, "displayName");
                String rare = metadata == null ? "" : stringOrEmpty(metadata, "rare");
                if (displayName.isEmpty()) {
                    continue;
                }

                freshLiveEvents.add(new LiveEvent(apiKey, id, displayName, rare, colorFor(apiKey, id, displayName, rare)));
            }
        }

        liveEvents = List.copyOf(freshLiveEvents);
    }

    private void loadState() {
        if (loaded) {
            return;
        }
        loaded = true;

        Path configFile = configFile();
        if (configFile == null || !Files.exists(configFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root == null) {
                return;
            }
            synchronized (stateLock) {
                if (root.has("entries") && root.get("entries").isJsonObject()) {
                    JsonObject entriesObject = root.getAsJsonObject("entries");
                    savedEnabledStates.clear();
                    for (Map.Entry<String, JsonElement> entry : entriesObject.entrySet()) {
                        if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                            savedEnabledStates.put(normalize(entry.getKey()), entry.getValue().getAsBoolean());
                        }
                    }
                }
                if (root.has("anarchyRanges") && root.get("anarchyRanges").isJsonObject()) {
                    JsonObject rangesObject = root.getAsJsonObject("anarchyRanges");
                    for (String category : DEFAULT_ANARCHY_RANGES.keySet()) {
                        if (rangesObject.has(category) && rangesObject.get(category).isJsonPrimitive()) {
                            String range = rangesObject.get(category).getAsString();
                            if (isValidAnarchyRange(range)) {
                                anarchyRanges.put(category, normalizeAnarchyRange(range));
                            }
                        }
                    }
                }
            }
        } catch (Exception exception) {
            System.err.println("Failed to load event HUD state: " + configFile + " (" + exception.getMessage() + ")");
        }
    }

    private void applySavedStates() {
        synchronized (stateLock) {
            for (FilterEntry entry : filtersByName.values()) {
                Boolean saved = savedEnabledStates.get(entry.stateKey());
                if (saved == null && NEW_ANARCHY_FILTER_LABEL.equals(entry.label)) {
                    saved = savedEnabledStates.get(normalize("1.20.1 anarchy"));
                }
                if (saved != null) {
                    entry.enabled = saved;
                }
            }
        }
    }

    private void saveState() {
        try {
            Path configFile = configFile();
            if (configFile == null) {
                return;
            }

            Files.createDirectories(configFile.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);
            JsonObject entriesObject = new JsonObject();
            JsonObject rangesObject = new JsonObject();
            synchronized (stateLock) {
                for (Map.Entry<String, Boolean> entry : savedEnabledStates.entrySet()) {
                    entriesObject.addProperty(entry.getKey(), entry.getValue());
                }
                for (String category : List.of("Solo", "Duo", "Trio", "Clan")) {
                    rangesObject.addProperty(category, anarchyRanges.getOrDefault(category,
                            DEFAULT_ANARCHY_RANGES.get(category)));
                }
            }
            root.add("entries", entriesObject);
            root.add("anarchyRanges", rangesObject);
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception exception) {
            System.err.println("Failed to save event HUD state: " + exception.getMessage());
        }
    }

    private static Path configFile() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            if (configDir == null) {
                return null;
            }
            return configDir.resolve(CONFIG_FILE_NAME);
        } catch (Exception exception) {
            return null;
        }
    }

    private List<LiveEvent> visibleEvents() {
        List<LiveEvent> currentEvents = liveEvents;
        if (currentEvents.isEmpty()) {
            return currentEvents;
        }

        List<LiveEvent> visible = new ArrayList<>(currentEvents.size());
        for (LiveEvent event : currentEvents) {
            if (isEventEnabled(event.apiKey, event.id, event.displayName)) {
                visible.add(event);
            }
        }
        return visible;
    }

    private static float calculatePanelHeight(int visibleEntryCount, boolean split) {
        visibleEntryCount = Math.min(MAX_VISIBLE_EVENTS, visibleEntryCount);
        float bodyHeight = Math.max(
                BODY_MIN_HEIGHT,
                CONTENT_PADDING_TOP + (visibleEntryCount * ROW_HEIGHT) + CONTENT_PADDING_BOTTOM
        );
        return TITLE_HEIGHT + (split ? PANEL_GAP : 0.0F) + bodyHeight;
    }

    private static String stringOrEmpty(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception exception) {
            return "";
        }
    }

    private static int colorFor(String apiKey, String id, String displayName, String rare) {
        String key = normalize(apiKey + " " + id + " " + displayName + " " + rare);
        if (key.contains("лихорад") || key.contains("fever")) {
            return 0xFFF1C24A;
        }
        if (key.contains("кораб") || key.contains("ship")) {
            return 0xFF8A5A2B;
        }
        if (key.contains("посыл") || key.contains("parcel")) {
            return 0xFF8A5A2B;
        }
        if (key.contains("груз") || key.contains("cargo")) {
            return 0xFFF34B4B;
        }
        if (key.contains("шахт") || key.contains("mine")) {
            return 0xFF8B1E1E;
        }
        if (key.contains("полян") || key.contains("glade") || key.contains("snowquarry")) {
            return 0xFF4CAF50;
        }
        if (key.contains("контейн") || key.contains("container")) {
            return 0xFF5DBBFF;
        }
        if (key.contains("кубик") || key.contains("cube")) {
            return 0xFF5DAEFF;
        }
        if (key.contains("типо") || key.contains("jaycob")) {
            return 0xFF7CFF3A;
        }
        return 0xFFF3F6FA;
    }

    private static int brighten(int color, float amount) {
        amount = Math.max(0.0F, Math.min(1.0F, amount));
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean isGearHit(double mouseX, double mouseY) {
        float x = drag.x();
        float y = drag.y();

        float fx = localMouseX((float) mouseX);
        float fy = localMouseY((float) mouseY);
        float gearX = x + TAG_COLUMN_CENTER_X - GEAR_SIZE * 0.5F;
        float gearY = y + (TITLE_HEIGHT - GEAR_SIZE) * 0.5F;
        return inside(fx, fy,
                gearX - GEAR_HIT_PADDING,
                gearY - GEAR_HIT_PADDING,
                GEAR_SIZE + (GEAR_HIT_PADDING * 2.0F),
                GEAR_SIZE + (GEAR_HIT_PADDING * 2.0F));
    }

    static final class FilterEntry {
        final String label;
        final int color;
        final String[] aliases;
        boolean enabled = true;

        FilterEntry(String label, int color, String[] aliases) {
            this.label = label == null ? "" : label;
            this.color = color;
            this.aliases = aliases == null ? new String[0] : aliases;
        }

        String stateKey() {
            return normalize(label);
        }

        boolean matches(String normalizedDisplayName) {
            if (normalizedDisplayName == null || normalizedDisplayName.isBlank()) {
                return false;
            }
            if (normalize(label).equals(normalizedDisplayName)) {
                return true;
            }
            for (String alias : aliases) {
                if (normalize(alias).equals(normalizedDisplayName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record FilterDefinition(String label, int color, String[] aliases) {
    }

    public record EventFilterSnapshot(String key, String label, boolean enabled) {
    }

    public record EventSnapshot(String id, String displayName, String rarity, int anarchyNumber, int color) {
    }

    static final class LiveEvent {
        final String apiKey;
        final String id;
        final String displayName;
        final String rare;
        final int color;
        final int apiNumber;

        LiveEvent(String apiKey, String id, String displayName, String rare, int color) {
            this.apiKey = apiKey == null ? "" : apiKey;
            this.id = id == null ? "" : id;
            this.displayName = displayName == null ? "" : displayName;
            this.rare = rare == null ? "" : rare;
            this.color = color;
            this.apiNumber = parseApiNumber(this.apiKey);
        }

        String displayLabel() {
            String normalizedRare = normalizeRare(displayName, rare);
            if (normalizedRare.isEmpty()) {
                return displayName;
            }
            return displayName + " - " + normalizedRare;
        }

        private static String normalizeRare(String displayName, String rare) {
            String value = canonicalizeRare(rare);
            if (value.isEmpty()) {
                return "";
            }

            boolean parcel = isParcelDisplayName(displayName);
            return switch (value) {
                case "обычный", "обычная" -> parcel ? "обычная" : "обычный";
                case "редкий", "редкая" -> "редкая";
                case "эпический", "эпическая" -> "эпический";
                case "легендарный", "легендарная" -> parcel ? "легендарная" : "легендарный";
                case "мифический", "мифическая" -> "мифическая";
                case "взрывной", "взрывная" -> "взрывной";
                case "смертельный", "смертельная" -> "смертельный";
                case "мирный", "мирная" -> "мирный";
                case "default" -> parcel ? "обычная" : "обычный";
                case "normal", "normal_plains", "normal_desert" -> parcel ? "обычная" : "обычный";
                case "rare_desert", "rare_plains" -> "редкая";
                case "epic" -> "эпический";
                case "legendary", "legendary_desert" -> parcel ? "легендарная" : "легендарный";
                case "mythical" -> "мифическая";
                case "explosive" -> "взрывной";
                case "deadly" -> "смертельный";
                case "legendary_plains" -> parcel ? "легендарная" : "легендарный";
                case "ship_default", "ship_default_f", "container_default", "container_normal", "cargo_peaceful" -> "обычный";
                case "ship_zajit", "ship_zajit_f" -> "зажиточный";
                case "ship_roskoshni", "ship_roskoshni_f" -> "роскошный";
                case "peaceful" -> "мирный";
                case "rare" -> "обычный";
                default -> rare;
            };
        }

        private static boolean isParcelDisplayName(String displayName) {
            String value = normalize(displayName);
            return value.contains("посылка") || value.contains("parcel");
        }

        private static String canonicalizeRare(String rare) {
            String value = normalize(rare);
            if (value.isEmpty()) {
                return "";
            }

            value = value.replaceAll("[^\\p{L}0-9]+", "_");
            value = value.replaceAll("^_+|_+$", "");
            value = value.replaceAll("_+", "_");
            return value;
        }

        String apiTag() {
            return apiNumber >= 0 ? Integer.toString(apiNumber) : "";
        }

        String rarityLabel() {
            return normalizeRare(displayName, rare);
        }

        private static int parseApiNumber(String apiKey) {
            if (apiKey == null) {
                return -1;
            }

            int underscore = apiKey.lastIndexOf('_');
            if (underscore < 0 || underscore == apiKey.length() - 1) {
                return -1;
            }

            try {
                return Integer.parseInt(apiKey.substring(underscore + 1));
            } catch (NumberFormatException exception) {
                return -1;
            }
        }
    }
}
