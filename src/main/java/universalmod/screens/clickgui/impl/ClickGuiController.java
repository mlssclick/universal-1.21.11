package universalmod.screens.clickgui.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.module.ModuleManager;
import universalmod.api.module.impl.misc.ClickGuiModule;
import universalmod.api.module.impl.misc.CustomCrosshair;
import universalmod.api.module.impl.render.ItemReplacer;
import universalmod.api.config.ConfigManager;
import universalmod.manager.Manager;
import universalmod.utils.lang.LanguageCode;
import universalmod.utils.lang.LanguageManager;
import universalmod.screens.clickgui.ClickGui;
import universalmod.screens.clickgui.impl.crosshair.CustomCrosshairPanel;
import universalmod.screens.clickgui.impl.figura.FiguraModelsPanel;
import universalmod.screens.clickgui.impl.itemreplacer.ItemReplacerPanel;
import universalmod.screens.clickgui.impl.module.ModuleOption;
import universalmod.screens.clickgui.impl.module.ModuleOptionFactory;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.item.RenderItem;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.Render2DCoordinateSpace;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.theme.ThemeColors;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ClickGuiController {
    private static final SmoothAnimation OPEN_ANIMATION = new SmoothAnimation();
    private static final FiguraModelsPanel FIGURA_MODELS_PANEL = new FiguraModelsPanel();
    private static final CustomCrosshairPanel CUSTOM_CROSSHAIR_PANEL = new CustomCrosshairPanel();
    private static final ItemReplacerPanel ITEM_REPLACER_PANEL = new ItemReplacerPanel();
    private static final HwEventsPanel HW_EVENTS_PANEL = new HwEventsPanel();
    private static final DateTimeFormatter FOOTER_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter PROFILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Map<Module, ModuleOption> OPTIONS = new IdentityHashMap<>();
    private static final Map<String, SmoothAnimation> CONFIG_DELETE_HOVER_ANIMATIONS = new HashMap<>();
    private static final Map<String, SmoothAnimation> CONFIG_FOLDER_HOVER_ANIMATIONS = new HashMap<>();
    private static final Map<String, SmoothAnimation> CONFIG_RENAME_HOVER_ANIMATIONS = new HashMap<>();
    private static final List<CategoryTab> TABS = List.of(
            new CategoryTab("Render", ModuleCategory.RENDER, false),
            new CategoryTab("Utils", ModuleCategory.UTILS, false),
            new CategoryTab("Misc", ModuleCategory.MISC, false),
            new CategoryTab("Hw Events", ModuleCategory.UTILS, true),
            new CategoryTab("Configs", ModuleCategory.CONFIGS, false)
    );

    private static final float GUI_WIDTH = 575.0f;
    private static final float GUI_HEIGHT = 350.0f;
    private static final float SIDE_WIDTH = 124.0f;
    private static final float HEADER_HEIGHT = 50.0f;
    private static final float BODY_HEIGHT = GUI_HEIGHT - HEADER_HEIGHT;
    private static final float MAIN_WIDTH = GUI_WIDTH - SIDE_WIDTH;
    private static final int MODULE_COLUMNS = 3;
    private static final float MODULE_PANEL_PADDING_X = 16.0f;
    private static final float MODULE_PANEL_PADDING_Y = 13.0f;
    private static final float MODULE_GAP_X = 14.0f;
    private static final float MODULE_GAP_Y = 8.5f;
    private static final float MODULE_WIDTH = (MAIN_WIDTH - MODULE_PANEL_PADDING_X * 2.0f - MODULE_GAP_X * (MODULE_COLUMNS - 1)) / MODULE_COLUMNS;
    private static final float MODULE_HEIGHT = 27.0f;
    private static final float SEARCH_WIDTH = 148.0f;
    private static final float SEARCH_HEIGHT = 23.0f;
    private static final float CONFIGS_TOOLBAR_HEIGHT = 23.0f;
    private static final float CONFIGS_TOOLBAR_TOP = 9.0f;
    private static final int CONFIG_CARD_COLUMNS = MODULE_COLUMNS;
    private static final float CONFIG_CARD_WIDTH = MODULE_WIDTH;
    private static final float CONFIG_CARD_HEIGHT = MODULE_HEIGHT * 1.3f;
    private static final float CONFIG_CARD_GAP_X = MODULE_GAP_X;
    private static final float CONFIG_CARD_GAP_Y = MODULE_GAP_Y;
    private static final float CONFIG_CARD_RADIUS = 6.0f;
    private static final float CONFIG_ACTION_SIZE = 14.0f;
    private static final float CONFIG_ACTION_GAP = 3.0f;
    private static final float CONFIG_RENAME_SIZE = 9.0f;
    private static final float CONFIG_RENAME_GAP = 2.0f;
    private static final float CONFIG_CARD_SIDE_PADDING = 7.0f;
    private static final float SCROLL_STEP = 27.0f;
    private static final float DESIGN_SCALE = 0.546f;
    private static final String SIDEBAR_AVATAR = "clickgui/avatar.png";

    private static final SmoothAnimation scrollAnimation = new SmoothAnimation();
    private static ModuleCategory selectedCategory = ModuleCategory.RENDER;
    private static boolean selectedHwEvents;
    private static boolean figuraModelsEditorOpen;
    private static boolean customCrosshairEditorOpen;
    private static boolean itemReplacerEditorOpen;
    private static boolean closing;
    private static boolean overlayActive;
    private static boolean pendingOpenAnimation;
    private static boolean textWarmed;
    private static boolean warmupBasePrepared;
    private static float warmupScale = 0.84f;
    private static List<Module> warmupModules = List.of();
    private static int warmupModuleIndex;
    private static boolean searchFocused;
    private static boolean configSearchFocused;
    private static String query = "";
    private static String configQuery = "";
    private static float scrollTarget;
    private static float scrollLimit;
    private static float lastGuiX;
    private static float lastGuiY;
    private static float lastGuiScale = 1.0f;
    private static double mouseX;
    private static double mouseY;
    private static int graphicsWarmupStage;
    private static String editingConfigName;
    private static String editingConfigValue = "";

    // The old code rebuilt and re-sorted the same module list several times per render frame.
    // Keep it stable until the actual filter inputs change; this also removes needless work while dragging sliders.
    private static List<Module> visibleModulesCache = List.of();
    private static String visibleModulesCacheQuery;
    private static ModuleCategory visibleModulesCacheCategory;
    private static boolean visibleModulesCacheHwEvents;
    private static LanguageCode visibleModulesCacheLanguage;
    private static int visibleModulesCacheCount = -1;

    private ClickGuiController() {
    }

    public static void init() {
        closing = false;
        overlayActive = true;
        pendingOpenAnimation = true;
        searchFocused = false;
        configSearchFocused = false;
        HW_EVENTS_PANEL.resetInteraction();
        figuraModelsEditorOpen = false;
        customCrosshairEditorOpen = false;
        itemReplacerEditorOpen = false;
        scrollTarget = 0.0f;
        scrollAnimation.set(0.0);
        CONFIG_DELETE_HOVER_ANIMATIONS.clear();
        CONFIG_FOLDER_HOVER_ANIMATIONS.clear();
        CONFIG_RENAME_HOVER_ANIMATIONS.clear();
        ClickGuiWorldAnimation.stop();
        OPEN_ANIMATION.set(0.0);
    }

    public static void renderPanels(GuiGraphics graphics, int screenWidth, int screenHeight) {
        renderPanels(graphics, screenWidth, screenHeight, false);
    }

    public static void renderWorldPanels(GuiGraphics graphics, int screenWidth, int screenHeight) {
        renderPanels(graphics, screenWidth, screenHeight, true);
    }

    private static void renderPanels(GuiGraphics graphics, int screenWidth, int screenHeight, boolean worldDetached) {
        updateMousePosition();
        float coordinateScale = coordinateScale();
        float designWidth = Math.max(1.0f, screenWidth / coordinateScale);
        float designHeight = Math.max(1.0f, screenHeight / coordinateScale);
        float guiScale = Math.min(designWidth / GUI_WIDTH, designHeight / GUI_HEIGHT) * DESIGN_SCALE;
        guiScale = Math.min(1.0f, Math.max(0.42f, guiScale));
        float guiX = (designWidth - GUI_WIDTH * guiScale) * 0.5f;
        float guiY = (designHeight - GUI_HEIGHT * guiScale) * 0.5f;

        if (!worldDetached && pendingOpenAnimation) {
            pendingOpenAnimation = false;
            OPEN_ANIMATION.set(0.0);
            OPEN_ANIMATION.run(1.0, 0.28, Easings.CUBIC_OUT);
        }
        OPEN_ANIMATION.update();
        float progress = clamp(OPEN_ANIMATION.get(), 0.0f, 1.0f);
        lastGuiX = guiX;
        lastGuiY = guiY - (1.0f - progress) * 9.0f;
        lastGuiScale = guiScale;
        if (closing && !OPEN_ANIMATION.isAlive() && progress <= 0.01f) {
            closing = false;
            overlayActive = false;
            return;
        }
        if (progress <= 0.01f && !closing) {
            return;
        }

        updateModuleAnimations();
        updateScroll();

        RenderItem.beginFrame(graphics);
        Render2D.beginFrame(graphics);
        try {
            renderScaled(graphics, guiX, lastGuiY, guiScale, progress);
        } finally {
            Render2D.flush();
            RenderItem.flush();
        }
    }

    private static void renderScaled(GuiGraphics graphics, float guiX, float guiY, float scale, float alpha) {
        Render2D.pushScissor(graphics, guiX, guiY, GUI_WIDTH * scale, GUI_HEIGHT * scale);
        try {
            renderPanelShader(guiX, guiY, scale, alpha);
            renderSeparator(guiX + SIDE_WIDTH * scale, guiY, 0.45f, GUI_HEIGHT * scale, color(146, 137, 187, 82, alpha));
            renderSeparator(guiX, guiY + HEADER_HEIGHT * scale, GUI_WIDTH * scale, 0.45f, color(146, 137, 187, 72, alpha));
            renderHeaders(guiX, guiY, scale, alpha);
            renderTabs(guiX, guiY, scale, alpha);
            if (selectedCategory != ModuleCategory.CONFIGS || selectedHwEvents) {
                renderSearch(graphics, guiX, guiY, scale, alpha);
            }
            renderBody(graphics, guiX + SIDE_WIDTH * scale, guiY + HEADER_HEIGHT * scale, scale, alpha);
        } finally {
            Render2D.popScissor(graphics);
        }

        // Popups are true overlays. Rendering them after the root GUI scissor is released lets the
        // color picker extend above / left / right of ClickGUI without being cut by the panel bounds.
        renderModuleOverlays(graphics);
        if (selectedHwEvents) {
            HW_EVENTS_PANEL.renderOverlay(graphics, alpha, mouseX, mouseY);
        }
    }

    private static void renderHeaders(float guiX, float guiY, float scale, float alpha) {
        float logoX = guiX + 12.0f * scale;
        float logoY = guiY + 13.0f * scale;
        float logoSize = 22.0f * scale;
        Render2D.image(SIDEBAR_AVATAR, logoX, logoY, logoSize, logoSize, 6.0f * scale,
                color(255, 255, 255, 255, alpha));

        Render2D.text(FontType.BOLD, "Universal", guiX + 41.0f * scale, guiY + 12.7f * scale, 9.2f * scale, color(238, 240, 248, 255, alpha));
        Render2D.text(FontType.SEMIBOLD, "Mod", guiX + 41.0f * scale, guiY + 25.0f * scale, 6.4f * scale, color(158, 151, 184, 210, alpha));

        float headerIconSize = 15.0f * scale;
        float headerIconX = guiX + (SIDE_WIDTH + 10.5f) * scale;
        float headerIconY = guiY + 9.0f * scale;
        int activeAccent = color(137, 117, 199, 255, alpha);
        renderCategoryIcon(activeTitle(), headerIconX, headerIconY, headerIconSize, activeAccent);

        float mainHeaderX = headerIconX + 19.0f * scale;
        float titleSize = 9.8f * scale;
        float titleY = guiY + 11.8f * scale;
        Render2D.text(FontType.BOLD, activeTitle(), mainHeaderX, titleY, titleSize, color(238, 240, 248, 255, alpha));
        // The subtitle begins under the leading category icon rather than under the title text.
        Render2D.text(FontType.SEMIBOLD, activeDescription(), headerIconX, guiY + 25.4f * scale, 5.8f * scale,
                color(129, 127, 151, 210, alpha));
    }

    private static void renderTabs(float guiX, float guiY, float scale, float alpha) {
        float tabWidth = SIDE_WIDTH - 20.0f;
        float x = guiX + (SIDE_WIDTH - tabWidth) * 0.5f * scale;
        float y = guiY + (HEADER_HEIGHT + 21.0f) * scale;
        Render2D.text(FontType.SEMIBOLD, "Main", x, guiY + (HEADER_HEIGHT + 13.0f) * scale, 5.0f * scale, color(112, 113, 137, 205, alpha));
        for (int i = 0; i < TABS.size(); i++) {
            CategoryTab tab = TABS.get(i);
            if (i == 3) {
                Render2D.text(FontType.SEMIBOLD, "Other", x, y - 7.0f * scale, 5.0f * scale, color(112, 113, 137, 205, alpha));
            }
            boolean active = tab.active();
            boolean hovered = inside(mouseX, mouseY, x, y, tabWidth * scale, 24.0f * scale);
            Render2D.rect(x, y, tabWidth * scale, 24.0f * scale, 6.0f * scale,
                    active
                            ? color(48, 43, 68, 138, alpha)
                            : color(31, 33, 48, hovered ? 92 : 0, alpha));
            if (active || hovered) {
                Render2D.outline(x, y, tabWidth * scale, 24.0f * scale, 6.0f * scale, 0.35f * scale,
                        color(150, 130, 215, active ? 54 : 20, alpha));
            }
            int text = active ? color(248, 248, 255, 255, alpha) : color(192, 194, 211, 240, alpha);
            float tabHeight = 24.0f * scale;
            float tabTextSize = 7.65f * scale;
            float tabTextY = y + (tabHeight - tabTextSize) * 0.5f - 0.75f * scale;
            Render2D.text(FontType.BOLD, tab.label(), x + 9.0f * scale, tabTextY, tabTextSize, text);

            float iconBoxSize = 14.0f * scale;
            float iconBoxX = x + tabWidth * scale - iconBoxSize - 8.0f * scale;
            float iconBoxY = y + (tabHeight - iconBoxSize) * 0.5f;
            renderCategoryIcon(tab.label(), iconBoxX, iconBoxY, iconBoxSize,
                    active
                            ? color(137, 117, 199, 255, alpha)
                            : color(255, 255, 255, 238, alpha));
            y += (i == 2 ? 34.0f : 29.0f) * scale;
        }
        renderFooter(guiX, guiY, scale, alpha);
    }

    private static void renderSearch(GuiGraphics graphics, float guiX, float guiY, float scale, float alpha) {
        float x = guiX + (SIDE_WIDTH + MAIN_WIDTH - SEARCH_WIDTH - 12.0f) * scale;
        float y = guiY + (HEADER_HEIGHT - SEARCH_HEIGHT) * 0.5f * scale;
        float radius = 7.0f * scale;
        Render2D.blur(x, y, SEARCH_WIDTH * scale, SEARCH_HEIGHT * scale, radius, 3.8f * scale, 0.54f,
                color(17, 19, 32, searchFocused ? 228 : 198, alpha));
        Render2D.outline(x, y, SEARCH_WIDTH * scale, SEARCH_HEIGHT * scale, radius, 0.40f * scale,
                color(157, 143, 217, searchFocused ? 52 : 20, alpha));

        boolean placeholder = query.isEmpty() && !searchFocused;
        String display = placeholder ? animatedSearchPlaceholder() : query;
        int textColor = placeholder ? color(232, 232, 242, 245, alpha) : color(255, 255, 255, 255, alpha);
        float textSize = 7.15f * scale;
        float textY = y + (SEARCH_HEIGHT * scale - textSize) * 0.5f - 0.35f * scale;
        float textX = x + 9.0f * scale;
        float iconArea = 25.0f * scale;
        float textClipWidth = Math.max(1.0f, SEARCH_WIDTH * scale - 9.0f * scale - iconArea);
        Render2D.pushScissor(graphics, textX, y, textClipWidth, SEARCH_HEIGHT * scale);
        try {
            Render2D.text(FontType.SEMIBOLD, display, textX, textY, textSize, textColor);
        } finally {
            Render2D.popScissor(graphics);
        }
        renderSearchIcon(x + SEARCH_WIDTH * scale - 17.0f * scale,
                y + SEARCH_HEIGHT * scale * 0.5f, scale, alpha);

        if (searchFocused && (System.currentTimeMillis() / 340L) % 2L == 0L) {
            float caretX = textX + Render2D.textWidth(FontType.SEMIBOLD, query, textSize);
            float maxCaret = x + SEARCH_WIDTH * scale - iconArea;
            Render2D.rect(Math.min(caretX, maxCaret), y + 6.0f * scale,
                    0.65f * scale, 10.5f * scale, 0.3f * scale, color(255, 255, 255, 225, alpha));
        }
    }

    private static String animatedSearchPlaceholder() {
        return animatedPlaceholder("Search...");
    }

    private static String animatedPlaceholder(String text) {
        int length = text.length();
        int pauseFrames = 5;
        int totalFrames = length + pauseFrames + length + pauseFrames;
        int frame = (int) ((System.currentTimeMillis() / 120L) % totalFrames);
        int count;
        if (frame < length) {
            count = frame + 1;
        } else if (frame < length + pauseFrames) {
            count = length;
        } else if (frame < length + pauseFrames + length) {
            count = Math.max(0, length - (frame - length - pauseFrames) - 1);
        } else {
            count = 0;
        }
        return text.substring(0, Math.min(length, count));
    }

    private static void renderSearchIcon(float centerX, float centerY, float scale, float alpha) {
        int iconColor = color(248, 248, 255, 248, alpha);
        float circle = 7.2f * scale;
        float cx = centerX - 2.0f * scale;
        float cy = centerY - 2.0f * scale;
        Render2D.outline(cx - circle * 0.5f, cy - circle * 0.5f,
                circle, circle, circle * 0.5f, 1.15f * scale, iconColor);
        // Three tiny rounded steps form a clean diagonal handle without depending on an icon-font glyph.
        float dot = 1.45f * scale;
        Render2D.rect(cx + 2.4f * scale, cy + 2.4f * scale, dot, dot, dot * 0.5f, iconColor);
        Render2D.rect(cx + 3.35f * scale, cy + 3.35f * scale, dot, dot, dot * 0.5f, iconColor);
        Render2D.rect(cx + 4.30f * scale, cy + 4.30f * scale, dot, dot, dot * 0.5f, iconColor);
    }

    private static void renderBody(GuiGraphics graphics, float panelX, float panelY, float scale, float alpha) {
        if (figuraModelsEditorOpen) {
            FIGURA_MODELS_PANEL.render(graphics, panelX, panelY, MAIN_WIDTH * scale, BODY_HEIGHT * scale, scale, alpha, mouseX, mouseY);
            return;
        }
        if (customCrosshairEditorOpen) {
            CUSTOM_CROSSHAIR_PANEL.render(graphics, panelX, panelY, MAIN_WIDTH * scale, BODY_HEIGHT * scale, scale, alpha, mouseX, mouseY);
            return;
        }
        if (itemReplacerEditorOpen) {
            ITEM_REPLACER_PANEL.render(graphics, panelX, panelY, MAIN_WIDTH * scale, BODY_HEIGHT * scale, scale, alpha, mouseX, mouseY);
            return;
        }
        renderModules(graphics, panelX, panelY, scale, alpha);
    }

    private static void renderModules(GuiGraphics graphics, float panelX, float panelY, float scale, float alpha) {
        if (selectedHwEvents) {
            HW_EVENTS_PANEL.setSearchQuery(query);
            HW_EVENTS_PANEL.render(graphics, panelX, panelY, MAIN_WIDTH * scale, BODY_HEIGHT * scale,
                    scale, alpha, mouseX, mouseY);
            return;
        }
        if (selectedCategory == ModuleCategory.CONFIGS) {
            renderConfigsPanel(graphics, panelX, panelY, scale, alpha);
            return;
        }
        List<Module> modules = visibleModules();
        float contentY = panelY + MODULE_PANEL_PADDING_Y * scale;
        Render2D.pushScissor(graphics, panelX, panelY, MAIN_WIDTH * scale, BODY_HEIGHT * scale);
        try {
            if (modules.isEmpty()) {
                return;
            }
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i);
                ModuleOption option = optionFor(module);
                int rowStart = (i / MODULE_COLUMNS) * MODULE_COLUMNS;
                int rowCount = Math.min(MODULE_COLUMNS, modules.size() - rowStart);
                int col = i - rowStart;
                float rowX = panelX + MODULE_PANEL_PADDING_X * scale;
                float x = rowX + col * (MODULE_WIDTH + MODULE_GAP_X) * scale;
                float baseY = contentY + columnOffset(modules, i) * scale - scrollAnimation.get() * scale;
                if (baseY + option.totalHeight() * scale < panelY || baseY > panelY + BODY_HEIGHT * scale) {
                    option.beginInteractionFrame();
                    continue;
                }
                option.renderCard(graphics, x, baseY, MODULE_WIDTH, MODULE_HEIGHT, scale, alpha, panelX, panelY, MAIN_WIDTH * scale, BODY_HEIGHT * scale);
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private static void renderConfigsPanel(GuiGraphics graphics, float panelX, float panelY, float scale, float alpha) {
        float padding = MODULE_PANEL_PADDING_X * scale;
        float toolbarY = panelY + CONFIGS_TOOLBAR_TOP * scale;
        float toolbarH = CONFIGS_TOOLBAR_HEIGHT * scale;
        float radius = 7.0f * scale;
        float searchX = panelX + padding;
        float searchW = MAIN_WIDTH * scale - padding * 2.0f;
        boolean focused = configSearchFocused;
        Render2D.blur(searchX, toolbarY, searchW, toolbarH, radius, 4.4f * scale, 0.58f,
                color(14, 16, 29, focused ? 228 : 202, alpha));
        Render2D.rect(searchX, toolbarY, searchW, toolbarH, radius,
                color(10, 12, 24, focused ? 58 : 38, alpha));
        Render2D.outline(searchX, toolbarY, searchW, toolbarH, radius, 0.45f * scale,
                color(157, 143, 217, focused ? 58 : 28, alpha));

        String display = configQuery.isEmpty() && !focused ? "Search configs..." : configQuery;
        int textColor = color(235, 235, 245, configQuery.isEmpty() ? 205 : 250, alpha);
        float textSize = 7.0f * scale;
        float textX = searchX + 10.0f * scale;
        float textY = toolbarY + (toolbarH - Render2D.textHeight(FontType.SEMIBOLD, display, textSize)) * 0.5f;
        Render2D.pushScissor(graphics, textX, toolbarY, Math.max(1.0f, searchW - 18.0f * scale), toolbarH);
        try {
            Render2D.text(FontType.SEMIBOLD, display, textX, textY, textSize, textColor);
        } finally {
            Render2D.popScissor(graphics);
        }
        if (focused && (System.currentTimeMillis() / 340L) % 2L == 0L) {
            float caretX = textX + Render2D.textWidth(FontType.SEMIBOLD, configQuery, textSize);
            Render2D.rect(Math.min(caretX, searchX + searchW - 8.0f * scale), toolbarY + 7.0f * scale,
                    0.65f * scale, 13.0f * scale, 0.3f * scale, color(255, 255, 255, 220, alpha));
        }

        renderConfigCards(graphics, panelX, panelY, scale, alpha, searchX,
                toolbarY + toolbarH + CONFIG_CARD_GAP_Y * scale);
    }

    private static void renderConfigCards(GuiGraphics graphics, float panelX, float panelY, float scale, float alpha,
                                          float x, float y) {
        ConfigManager configManager = configManager();
        if (configManager == null) {
            return;
        }
        List<ConfigManager.ProfileInfo> profiles = filteredConfigProfiles(configManager).stream()
                .map(configManager::profileInfo)
                .toList();
        float cardsBottom = panelY + BODY_HEIGHT * scale;
        Render2D.pushScissor(graphics, panelX, y, MAIN_WIDTH * scale, Math.max(1.0f, cardsBottom - y));
        try {
            int itemCount = profiles.size() + 1;
            for (int i = 0; i < itemCount; i++) {
                int row = i / CONFIG_CARD_COLUMNS;
                int column = i % CONFIG_CARD_COLUMNS;
                float cardX = x + column * (CONFIG_CARD_WIDTH + CONFIG_CARD_GAP_X) * scale;
                float cardY = y + row * (CONFIG_CARD_HEIGHT + CONFIG_CARD_GAP_Y) * scale
                        - scrollAnimation.get() * scale;
                if (cardY + CONFIG_CARD_HEIGHT * scale >= panelY && cardY <= panelY + BODY_HEIGHT * scale) {
                    if (i == profiles.size()) {
                        renderNewConfigCard(cardX, cardY, CONFIG_CARD_WIDTH * scale,
                                CONFIG_CARD_HEIGHT * scale, scale, alpha);
                    } else {
                        renderConfigCard(graphics, profiles.get(i), cardX, cardY, CONFIG_CARD_WIDTH * scale,
                                CONFIG_CARD_HEIGHT * scale, scale, alpha, configManager);
                    }
                }
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private static void renderNewConfigCard(float x, float y, float width, float height,
                                            float scale, float alpha) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        float radius = CONFIG_CARD_RADIUS * scale;
        Render2D.rect(x, y, width, height, radius,
                color(7, 10, 21, hovered ? 78 : 54, alpha));
        Render2D.outline(x, y, width, height, radius, (hovered ? 0.8f : 0.4f) * scale,
                color(166, 135, 255, hovered ? 190 : 72, alpha));

        String label = "New config";
        float iconSize = 10.0f * scale;
        float textSize = 8.0f * scale;
        float gap = 4.0f * scale;
        float textWidth = Render2D.textWidth(FontType.BOLD, label, textSize);
        float groupWidth = iconSize + gap + textWidth;
        float iconX = x + (width - groupWidth) * 0.5f;
        float iconY = y + (height - iconSize) * 0.5f;
        float textY = y + (height - textSize) * 0.5f - 0.45f * scale;
        int contentColor = color(255, 255, 255, hovered ? 255 : 238, alpha);
        renderIconNewGlyph("z", iconX, iconY, iconSize, contentColor);
        Render2D.text(FontType.BOLD, label, iconX + iconSize + gap, textY, textSize, contentColor);
    }

    private static void renderConfigCard(GuiGraphics graphics, ConfigManager.ProfileInfo profileInfo,
                                         float x, float y, float width, float height,
                                         float scale, float alpha, ConfigManager configManager) {
        String profile = profileInfo.name();
        boolean active = profile.equals(configManager.activeProfile());
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        float radius = CONFIG_CARD_RADIUS * scale;
        float selected = active ? 1.0f : 0.0f;
        Render2D.rect(x, y, width, height, radius,
                color(7, 10, 21, Math.round(54.0f + 26.0f * selected), alpha));
        Render2D.outline(x, y, width, height, radius, (active ? 0.9f : 0.35f) * scale,
                active
                        ? color(166, 135, 255, 220, alpha)
                        : color(133, 119, 198, hovered ? 34 : 18, alpha));

        boolean deletable = !ConfigManager.DEFAULT_PROFILE.equals(profile);
        float actionSize = CONFIG_ACTION_SIZE * scale;
        float actionY = y + (height - actionSize) * 0.5f;
        float trashX = x + width - actionSize - CONFIG_CARD_SIDE_PADDING * scale;
        float folderX = deletable
                ? trashX - actionSize - CONFIG_ACTION_GAP * scale
                : trashX;
        float contentRight = folderX - 5.0f * scale;
        float contentLeft = x + 7.0f * scale;
        float renameSize = CONFIG_RENAME_SIZE * scale;
        float textY = y + 4.5f * scale;
        // The pencil's MSDF plane sits visually higher than the regular text plane.
        // Keep the title row left-aligned and compensate only its vertical bearing.
        float renameY = textY + 1.0f * scale;

        boolean editing = profile.equals(editingConfigName);
        String text = editing ? editingConfigValue : profile;
        float textSize = 8.6f * scale;
        float textWidth = Render2D.textWidth(FontType.BOLD, text, textSize);
        float availableTitleWidth = Math.max(1.0f, contentRight - contentLeft);
        float renameGap = deletable ? CONFIG_RENAME_GAP * scale : 0.0f;
        float maxVisibleTextWidth = Math.max(1.0f, availableTitleWidth - (deletable ? renameSize + renameGap : 0.0f));
        float renameX = contentLeft;
        float textX = deletable ? renameX + renameSize + renameGap : contentLeft;
        Render2D.pushScissor(graphics, contentLeft, y,
                Math.max(1.0f, contentRight - contentLeft), height);
        try {
            Render2D.text(FontType.BOLD, text, textX, textY, textSize,
                    color(240, 240, 250, active ? 255 : 232, alpha));

            String details = profileDate(profileInfo) + "  •  by " + profileInfo.createdBy();
            float detailsSize = 5.6f * scale;
            Render2D.text(FontType.SEMIBOLD, details, contentLeft, y + 23.5f * scale,
                    detailsSize, color(245, 245, 250, active ? 238 : 212, alpha));
        } finally {
            Render2D.popScissor(graphics);
        }

        if (editing && (System.currentTimeMillis() / 340L) % 2L == 0L) {
            float caretX = Math.min(textX + textWidth + 1.0f * scale, contentRight);
            Render2D.rect(caretX, y + 3.8f * scale, 0.6f * scale, 11.0f * scale,
                    0.3f * scale, color(255, 255, 255, 220, alpha));
        }

        if (deletable) {
            boolean renameHovered = inside(mouseX, mouseY, renameX, renameY, renameSize, renameSize);
            float renameHover = animateConfigHover(CONFIG_RENAME_HOVER_ANIMATIONS, profile, renameHovered, 0.12);
            int renameColor = ColorUtil.lerpColor(
                    color(188, 187, 201, 205, alpha), color(255, 255, 255, 255, alpha), renameHover);
            renderDeltaActionGlyph("K", renameX, renameY, renameSize, renameColor);
        }

        boolean folderHovered = inside(mouseX, mouseY, folderX, actionY, actionSize, actionSize);
        float folderHover = animateConfigHover(CONFIG_FOLDER_HOVER_ANIMATIONS, profile, folderHovered, 0.12);
        int folderColor = ColorUtil.lerpColor(
                color(255, 255, 255, 245, alpha), color(178, 180, 190, 235, alpha), folderHover);
        renderDeltaActionGlyph("x", folderX, actionY, actionSize, folderColor);

        if (deletable) {
            boolean closeHovered = inside(mouseX, mouseY, trashX, actionY, actionSize, actionSize);
            float deleteHover = animateConfigHover(CONFIG_DELETE_HOVER_ANIMATIONS, profile, closeHovered, 0.14);
            int iconColor = ColorUtil.lerpColor(
                    color(240, 240, 246, 235, alpha), color(255, 32, 62, 255, alpha), deleteHover);
            renderDeltaActionGlyph("Z", trashX, actionY, actionSize, iconColor);
        }
    }

    private static void renderModuleOverlays(GuiGraphics graphics) {
        // Popups (notably the color picker) are rendered after all module cards so they sit
        // on top of the setting that opened them instead of being buried/clipped by cards.
        for (Module module : visibleModules()) {
            ModuleOption option = OPTIONS.get(module);
            if (option != null) {
                option.renderOverlay(graphics);
            }
        }
    }

    private static float columnOffset(List<Module> modules, int index) {
        int col = index % MODULE_COLUMNS;
        float y = 0.0f;
        for (int previous = col; previous < index; previous += MODULE_COLUMNS) {
            y += MODULE_HEIGHT + MODULE_GAP_Y + optionFor(modules.get(previous)).extraHeight();
        }
        return y;
    }

    public static boolean warmupText() {
        if (textWarmed) {
            return true;
        }

        if (!warmupBasePrepared) {
            warmupBasePrepared = true;
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getWindow() != null) {
                float coordinateScale = coordinateScale();
                float designWidth = Math.max(1.0f, client.getWindow().getGuiScaledWidth() / coordinateScale);
                float designHeight = Math.max(1.0f, client.getWindow().getGuiScaledHeight() / coordinateScale);
                warmupScale = Math.min(designWidth / GUI_WIDTH, designHeight / GUI_HEIGHT) * DESIGN_SCALE;
                warmupScale = Math.min(1.0f, Math.max(0.42f, warmupScale));

                try {
                    client.getTextureManager().getTexture(
                            Identifier.fromNamespaceAndPath("universalmod", "images/clickgui/avatar.png")
                    );
                } catch (RuntimeException ignored) {
                }
            }

            // Base strings use the exact effective sizes used by the GUI, unlike the old warmup.
            Render2D.warmupText(FontType.BOLD, "Universal", 9.2f * warmupScale);
            Render2D.warmupText(FontType.SEMIBOLD, "Mod", 6.4f * warmupScale);
            Render2D.warmupText(FontType.SEMIBOLD, "Search...", 7.15f * warmupScale);
            Render2D.warmupText(FontType.SEMIBOLD, "Main", 5.0f * warmupScale);
            Render2D.warmupText(FontType.SEMIBOLD, "Other", 5.0f * warmupScale);
            Render2D.warmupText(FontType.GUI_ICONS, "RABS", 14.0f * warmupScale);
            for (CategoryTab tab : TABS) {
                Render2D.warmupText(FontType.BOLD, tab.label(), 7.65f * warmupScale);
            }
            Render2D.warmupText(FontType.SEMIBOLD, "Visuals feature for gameplay", 5.8f * warmupScale);
            Render2D.warmupText(FontType.SEMIBOLD, "Utilites for best game experience", 5.8f * warmupScale);
            Render2D.warmupText(FontType.SEMIBOLD, "Miscellanius for xz", 5.8f * warmupScale);
            Render2D.warmupText(FontType.BOLD, "ConfigВсеСолоДуоТриоКлан", 7.3f * warmupScale);
            Render2D.warmupText(FontType.SEMIBOLD, "Search events...", 7.0f * warmupScale);
            Render2D.warmupText(FontType.SEMIBOLD, "Search configs...", 7.0f * warmupScale);
            Render2D.warmupText(FontType.BOLD, "New config", 8.0f * warmupScale);
            Render2D.warmupText(FontType.BOLD, "Отображать ивенты: 11 из 11Анархии", 8.0f * warmupScale);
            Render2D.warmupText(FontType.SEMIBOLD, "SoloDuoTrioClan1-1718-3839-5758-74", 6.7f * warmupScale);

            ModuleManager manager = Manager.getModules();
            warmupModules = manager == null ? List.of() : List.copyOf(manager.getModules());
            warmupModuleIndex = 0;
        }

        // A few module titles per client tick: no giant first-open or startup spike.
        int budget = 10;
        while (budget-- > 0 && warmupModuleIndex < warmupModules.size()) {
            Module module = warmupModules.get(warmupModuleIndex++);
            Render2D.warmupText(FontType.BOLD, module.getDisplayName(), 7.7f * warmupScale);
        }

        if (warmupModuleIndex >= warmupModules.size()) {
            warmupModules = List.of();
            textWarmed = true;
        }
        return textWarmed;
    }

    /**
     * Warms the exact GPU primitives used only by ClickGUI while the player is already in-game.
     * The work is split across frames, so the first ClickGUI frame does not have to compile/allocate
     * all of these cold paths at once. The tiny primitives are practically invisible.
     */
    public static boolean warmupGraphics(GuiGraphics graphics) {
        if (graphicsWarmupStage >= 4 || graphics == null) {
            return true;
        }

        Render2D.beginFrame(graphics);
        try {
            float x = 0.25f;
            float y = 0.25f;
            switch (graphicsWarmupStage) {
                case 0 -> {
                    Render2D.rect(x, y, 1.0f, 1.0f, 0.5f, ColorUtil.rgba(255, 255, 255, 1));
                    Render2D.outline(x, y, 1.0f, 1.0f, 0.5f, 0.25f, ColorUtil.rgba(255, 255, 255, 1));
                }
                case 1 -> Render2D.image(SIDEBAR_AVATAR, x, y, 1.0f, 1.0f, 0.0f, ColorUtil.rgba(255, 255, 255, 1));
                case 2 -> Render2D.blur(x, y, 2.0f, 2.0f, 0.5f, 2.0f, 0.55f, ColorUtil.rgba(8, 10, 18, 1));
                case 3 -> {
                    Render2D.text(FontType.BOLD, "Universal", x, y, 7.7f, ColorUtil.rgba(255, 255, 255, 1));
                    Render2D.text(FontType.SEMIBOLD, "Search...", x, y, 7.15f, ColorUtil.rgba(255, 255, 255, 1));
                }
                default -> {
                }
            }
        } finally {
            Render2D.flush();
        }
        graphicsWarmupStage++;
        return graphicsWarmupStage >= 4;
    }

    public static boolean keyPressed(KeyEvent event) {
        if (selectedHwEvents && HW_EVENTS_PANEL.keyPressed(event)) {
            return true;
        }
        if (handleConfigEditKey(event)) {
            return true;
        }
        if (isBodyEditorOpen() && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeBodyEditor();
            return true;
        }
        if (itemReplacerEditorOpen && ITEM_REPLACER_PANEL.keyPressed(event)) {
            return true;
        }
        if (configSearchFocused) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                configSearchFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!configQuery.isEmpty()) {
                    configQuery = configQuery.substring(0, configQuery.length() - 1);
                    resetScroll();
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE) {
                configQuery = "";
                resetScroll();
                return true;
            }
        }
        if (searchFocused) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!query.isEmpty()) {
                    query = query.substring(0, query.length() - 1);
                    resetScroll();
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE) {
                query = "";
                resetScroll();
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || ClickGuiModule.matchesCloseKey(event)) {
            startClosing();
            return true;
        }
        for (ModuleOption option : OPTIONS.values()) {
            if (option.keyPressed(event)) {
                return true;
            }
        }
        return true;
    }

    public static boolean charTyped(CharacterEvent event) {
        if (selectedHwEvents && HW_EVENTS_PANEL.charTyped(event)) {
            return true;
        }
        if (editingConfigName != null) {
            if (event.isAllowedChatCharacter()) {
                String text = event.codepointAsString();
                if (text != null && !text.isBlank() && editingConfigValue.length() + text.length() <= 32) {
                    editingConfigValue += text;
                }
            }
            return true;
        }
        if (itemReplacerEditorOpen) {
            ITEM_REPLACER_PANEL.charTyped(event);
            return true;
        }
        if (isBodyEditorOpen()) {
            return true;
        }
        if (configSearchFocused) {
            if (event.isAllowedChatCharacter()) {
                String text = event.codepointAsString();
                if (text != null && !text.isBlank() && configQuery.length() + text.length() <= 48) {
                    configQuery += text;
                    resetScroll();
                }
            }
            return true;
        }
        if (searchFocused) {
            if (event.isAllowedChatCharacter()) {
                String text = event.codepointAsString();
                if (text != null && !text.isBlank() && query.length() + text.length() <= 48) {
                    query += text;
                    resetScroll();
                }
            }
            return true;
        }
        for (ModuleOption option : OPTIONS.values()) {
            if (option.charTyped(event)) {
                return true;
            }
        }
        return true;
    }

    public static boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        event = toDesignEvent(event);
        mouseX = event.x();
        mouseY = event.y();
        if (ClickGuiModule.matchesCloseMouse(event)) {
            startClosing();
            return true;
        }
        if (closing) {
            return true;
        }
        if (selectedHwEvents && HW_EVENTS_PANEL.mouseClicked(event, doubled)) {
            searchFocused = false;
            configSearchFocused = false;
            return true;
        }
        if (handleTabClick(event)) {
            return true;
        }
        if (figuraModelsEditorOpen) {
            searchFocused = false;
            configSearchFocused = false;
            FIGURA_MODELS_PANEL.mouseClicked(event.x(), event.y(), event.button());
            return true;
        }
        if (customCrosshairEditorOpen) {
            searchFocused = false;
            configSearchFocused = false;
            CUSTOM_CROSSHAIR_PANEL.mouseClicked(event.x(), event.y(), event.button());
            return true;
        }
        if (itemReplacerEditorOpen) {
            searchFocused = false;
            configSearchFocused = false;
            ITEM_REPLACER_PANEL.mouseClicked(event.x(), event.y(), event.button());
            return true;
        }
        if (handleConfigPanelClick(event, doubled)) {
            return true;
        }
        if (handleSearchClick(event)) {
            return true;
        }
        for (int i = visibleModules().size() - 1; i >= 0; i--) {
            ModuleOption option = optionFor(visibleModules().get(i));
            if (option.mouseClicked(event, doubled)) {
                return true;
            }
        }
        searchFocused = false;
        configSearchFocused = false;
        return true;
    }

    public static boolean mouseReleased(MouseButtonEvent event) {
        event = toDesignEvent(event);
        if (figuraModelsEditorOpen) {
            return true;
        }
        if (customCrosshairEditorOpen) {
            CUSTOM_CROSSHAIR_PANEL.mouseReleased();
            return true;
        }
        if (itemReplacerEditorOpen) {
            return true;
        }
        for (ModuleOption option : OPTIONS.values()) {
            if (option.mouseReleased(event)) {
                return true;
            }
        }
        return true;
    }

    public static boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        event = toDesignEvent(event);
        dragX /= coordinateScale();
        dragY /= coordinateScale();
        if (figuraModelsEditorOpen) {
            return true;
        }
        if (customCrosshairEditorOpen) {
            CUSTOM_CROSSHAIR_PANEL.mouseDragged(event.x(), event.y());
            return true;
        }
        if (itemReplacerEditorOpen) {
            return true;
        }
        for (ModuleOption option : OPTIONS.values()) {
            if (option.mouseDragged(event, dragX, dragY)) {
                return true;
            }
        }
        return true;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX /= coordinateScale();
        mouseY /= coordinateScale();
        ClickGuiController.mouseX = mouseX;
        ClickGuiController.mouseY = mouseY;
        if (selectedHwEvents && HW_EVENTS_PANEL.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (figuraModelsEditorOpen
                && inside(mouseX, mouseY, lastGuiX + SIDE_WIDTH * lastGuiScale, lastGuiY + HEADER_HEIGHT * lastGuiScale, MAIN_WIDTH * lastGuiScale, BODY_HEIGHT * lastGuiScale)) {
            FIGURA_MODELS_PANEL.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        if (customCrosshairEditorOpen
                && inside(mouseX, mouseY, lastGuiX + SIDE_WIDTH * lastGuiScale, lastGuiY + HEADER_HEIGHT * lastGuiScale, MAIN_WIDTH * lastGuiScale, BODY_HEIGHT * lastGuiScale)) {
            CUSTOM_CROSSHAIR_PANEL.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        if (itemReplacerEditorOpen
                && inside(mouseX, mouseY, lastGuiX + SIDE_WIDTH * lastGuiScale, lastGuiY + HEADER_HEIGHT * lastGuiScale, MAIN_WIDTH * lastGuiScale, BODY_HEIGHT * lastGuiScale)) {
            ITEM_REPLACER_PANEL.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        for (ModuleOption option : OPTIONS.values()) {
            if (option.mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }
        if (inside(mouseX, mouseY, lastGuiX + SIDE_WIDTH * lastGuiScale, lastGuiY + HEADER_HEIGHT * lastGuiScale, MAIN_WIDTH * lastGuiScale, BODY_HEIGHT * lastGuiScale)) {
            scrollTarget = clamp(scrollTarget - (float) scrollY * SCROLL_STEP, 0.0f, scrollLimit);
            scrollAnimation.run(scrollTarget, 0.22, Easings.CUBIC_OUT, true);
        }
        return true;
    }

    public static boolean onFilesDrop(List<java.nio.file.Path> paths) {
        if (!figuraModelsEditorOpen || paths == null || paths.isEmpty()) {
            return false;
        }
        return FIGURA_MODELS_PANEL.onFilesDrop(paths);
    }

    public static double mouseX() {
        return mouseX;
    }

    public static double mouseY() {
        return mouseY;
    }

    public static void startClosing() {
        if (closing) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (ThemeColors.isClickGuiCloseWorldEnabled() && client != null && client.screen instanceof ClickGui) {
            float progress = clamp(OPEN_ANIMATION.get(), 0.0f, 1.0f);
            overlayActive = false;
            pendingOpenAnimation = false;
            ClickGuiWorldAnimation.start(progress, progress);
            client.setScreen(null);
            return;
        }
        closing = true;
        overlayActive = true;
        searchFocused = false;
        configSearchFocused = false;
        HW_EVENTS_PANEL.resetInteraction();
        figuraModelsEditorOpen = false;
        customCrosshairEditorOpen = false;
        itemReplacerEditorOpen = false;
        OPEN_ANIMATION.run(0.0, 0.22, Easings.CUBIC_OUT);
        if (client != null && client.screen instanceof ClickGui) {
            client.setScreen(null);
        }
    }

    public static void resetOverlayState() {
        closing = false;
        overlayActive = false;
        pendingOpenAnimation = false;
        searchFocused = false;
        configSearchFocused = false;
        HW_EVENTS_PANEL.resetInteraction();
        figuraModelsEditorOpen = false;
        customCrosshairEditorOpen = false;
        itemReplacerEditorOpen = false;
        OPEN_ANIMATION.set(0.0);
        ClickGuiWorldAnimation.stop();
    }

    public static boolean shouldRenderOverlay() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            resetOverlayState();
            return false;
        }
        if (client.screen == null && !closing && ClickGuiWorldAnimation.isActive()) {
            return false;
        }
        if (client.screen == null && !closing) {
            resetOverlayState();
            return false;
        }
        if (client.screen != null && !(client.screen instanceof ClickGui)) {
            resetOverlayState();
            return false;
        }
        return overlayActive || client.screen instanceof ClickGui;
    }

    public static void validateState(Minecraft client) {
        if (client == null) {
            resetOverlayState();
            return;
        }
        if ((client.player == null || client.level == null) && client.screen instanceof ClickGui) {
            resetOverlayState();
            client.setScreen(null);
            return;
        }
        if (client.screen == null && !closing && overlayActive) {
            resetOverlayState();
        }
    }

    public static void tickMovementKeys() {
        ClickGuiWorldAnimation.tick();
    }

    private static void updateModuleAnimations() {
        if (isBodyEditorOpen()) {
            return;
        }
        // Do not instantiate every module/settings tree on the first frame. Only options from
        // the current tab are created; already-created hidden options can keep settling.
        for (Module module : visibleModules()) {
            optionFor(module);
        }
        for (ModuleOption option : OPTIONS.values()) {
            option.updateAnimation();
        }
    }

    private static void updateScroll() {
        if (isBodyEditorOpen()) {
            scrollLimit = 0.0f;
            scrollTarget = 0.0f;
            scrollAnimation.run(0.0f, 0.18, Easings.CUBIC_OUT, true);
            scrollAnimation.update();
            return;
        }
        if (selectedCategory == ModuleCategory.CONFIGS && !selectedHwEvents) {
            ConfigManager configManager = configManager();
            int count = 1 + (configManager == null ? 0 : filteredConfigProfiles(configManager).size());
            int rows = (count + CONFIG_CARD_COLUMNS - 1) / CONFIG_CARD_COLUMNS;
            float cardsHeight = rows <= 0
                    ? 0.0f
                    : rows * CONFIG_CARD_HEIGHT + Math.max(0, rows - 1) * CONFIG_CARD_GAP_Y;
            float viewportHeight = BODY_HEIGHT - CONFIGS_TOOLBAR_TOP - MODULE_PANEL_PADDING_Y
                    - CONFIGS_TOOLBAR_HEIGHT - CONFIG_CARD_GAP_Y;
            scrollLimit = Math.max(0.0f, cardsHeight - viewportHeight);
            scrollTarget = clamp(scrollTarget, 0.0f, scrollLimit);
            scrollAnimation.run(scrollTarget, 0.22, Easings.CUBIC_OUT, true);
            scrollAnimation.update();
            return;
        }
        List<Module> modules = visibleModules();
        float contentHeight = 0.0f;
        if (!modules.isEmpty()) {
            for (int col = 0; col < MODULE_COLUMNS; col++) {
                float columnHeight = 0.0f;
                boolean hasColumnItems = false;
                for (int index = col; index < modules.size(); index += MODULE_COLUMNS) {
                    hasColumnItems = true;
                    columnHeight += MODULE_HEIGHT + optionFor(modules.get(index)).extraHeight();
                    if (index + MODULE_COLUMNS < modules.size()) {
                        columnHeight += MODULE_GAP_Y;
                    }
                }
                if (hasColumnItems) {
                    contentHeight = Math.max(contentHeight, columnHeight);
                }
            }
        }
        scrollLimit = Math.max(0.0f, contentHeight - (BODY_HEIGHT - MODULE_PANEL_PADDING_Y * 2.0f));
        scrollTarget = clamp(scrollTarget, 0.0f, scrollLimit);
        scrollAnimation.run(scrollTarget, 0.22, Easings.CUBIC_OUT, true);
        scrollAnimation.update();
    }

    private static void resetScroll() {
        scrollTarget = 0.0f;
        scrollAnimation.run(0.0, 0.18, Easings.CUBIC_OUT);
    }

    private static boolean handleSearchClick(MouseButtonEvent event) {
        if (selectedCategory == ModuleCategory.CONFIGS && !isBodyEditorOpen()) {
            float panelX = lastGuiX + SIDE_WIDTH * lastGuiScale;
            float panelY = lastGuiY + HEADER_HEIGHT * lastGuiScale;
            float padding = MODULE_PANEL_PADDING_X * lastGuiScale;
            float toolbarY = panelY + CONFIGS_TOOLBAR_TOP * lastGuiScale;
            float searchX = panelX + padding;
            float searchW = MAIN_WIDTH * lastGuiScale - padding * 2.0f;
            if (event.button() == 0 && inside(event.x(), event.y(), searchX, toolbarY,
                    searchW, CONFIGS_TOOLBAR_HEIGHT * lastGuiScale)) {
                configSearchFocused = true;
                searchFocused = false;
                editingConfigName = null;
                editingConfigValue = "";
                return true;
            }
            searchFocused = false;
            configSearchFocused = false;
            return false;
        }
        float searchX = lastGuiX + (SIDE_WIDTH + MAIN_WIDTH - SEARCH_WIDTH - 12.0f) * lastGuiScale;
        float searchY = lastGuiY + (HEADER_HEIGHT - SEARCH_HEIGHT) * 0.5f * lastGuiScale;
        if (event.button() == 0 && inside(event.x(), event.y(), searchX, searchY, SEARCH_WIDTH * lastGuiScale, SEARCH_HEIGHT * lastGuiScale)) {
            searchFocused = true;
            configSearchFocused = false;
            return true;
        }
        searchFocused = false;
        configSearchFocused = false;
        return false;
    }

    private static boolean handleConfigPanelClick(MouseButtonEvent event, boolean doubled) {
        if (selectedCategory != ModuleCategory.CONFIGS || selectedHwEvents || isBodyEditorOpen()) {
            return false;
        }
        ConfigManager configManager = configManager();
        if (configManager == null) {
            return false;
        }
        float panelX = lastGuiX + SIDE_WIDTH * lastGuiScale;
        float panelY = lastGuiY + HEADER_HEIGHT * lastGuiScale;
        float padding = MODULE_PANEL_PADDING_X * lastGuiScale;
        float toolbarY = panelY + CONFIGS_TOOLBAR_TOP * lastGuiScale;
        float toolbarH = CONFIGS_TOOLBAR_HEIGHT * lastGuiScale;
        float cardsX = panelX + padding;
        float cardsY = toolbarY + toolbarH + CONFIG_CARD_GAP_Y * lastGuiScale;
        float cardW = CONFIG_CARD_WIDTH * lastGuiScale;
        float cardH = CONFIG_CARD_HEIGHT * lastGuiScale;

        List<String> profiles = filteredConfigProfiles(configManager);
        int newIndex = profiles.size();
        int newRow = newIndex / CONFIG_CARD_COLUMNS;
        int newColumn = newIndex % CONFIG_CARD_COLUMNS;
        float newCardX = cardsX + newColumn * (CONFIG_CARD_WIDTH + CONFIG_CARD_GAP_X) * lastGuiScale;
        float newCardY = cardsY + newRow * (CONFIG_CARD_HEIGHT + CONFIG_CARD_GAP_Y) * lastGuiScale
                - scrollAnimation.get() * lastGuiScale;
        float cardsBottom = panelY + BODY_HEIGHT * lastGuiScale;
        boolean insideCardsViewport = inside(event.x(), event.y(), panelX, cardsY,
                MAIN_WIDTH * lastGuiScale, Math.max(1.0f, cardsBottom - cardsY));
        if (event.button() == 0 && insideCardsViewport
                && inside(event.x(), event.y(), newCardX, newCardY, cardW, cardH)) {
            searchFocused = false;
            configSearchFocused = false;
            String created = configManager.createProfile();
            editingConfigName = created;
            editingConfigValue = created;
            return true;
        }

        if (event.button() != 0 || !inside(event.x(), event.y(), panelX, cardsY,
                MAIN_WIDTH * lastGuiScale, Math.max(1.0f, cardsBottom - cardsY))) {
            return false;
        }

        for (int i = 0; i < profiles.size(); i++) {
            String profile = profiles.get(i);
            int row = i / CONFIG_CARD_COLUMNS;
            int column = i % CONFIG_CARD_COLUMNS;
            float cardX = cardsX + column * (CONFIG_CARD_WIDTH + CONFIG_CARD_GAP_X) * lastGuiScale;
            float cardY = cardsY + row * (CONFIG_CARD_HEIGHT + CONFIG_CARD_GAP_Y) * lastGuiScale
                    - scrollAnimation.get() * lastGuiScale;
            if (inside(event.x(), event.y(), cardX, cardY, cardW, cardH)) {
                searchFocused = false;
                configSearchFocused = false;
                boolean deletable = !ConfigManager.DEFAULT_PROFILE.equals(profile);
                float actionSize = CONFIG_ACTION_SIZE * lastGuiScale;
                float actionY = cardY + (cardH - actionSize) * 0.5f;
                float trashX = cardX + cardW - actionSize - CONFIG_CARD_SIDE_PADDING * lastGuiScale;
                float folderX = deletable
                        ? trashX - actionSize - CONFIG_ACTION_GAP * lastGuiScale
                        : trashX;

                if (inside(event.x(), event.y(), folderX, actionY, actionSize, actionSize)) {
                    configManager.revealProfileFile(profile);
                    return true;
                }

                if (deletable) {
                    if (inside(event.x(), event.y(), trashX, actionY, actionSize, actionSize)) {
                        if (configManager.deleteProfile(profile)) {
                            CONFIG_DELETE_HOVER_ANIMATIONS.remove(profile);
                            CONFIG_FOLDER_HOVER_ANIMATIONS.remove(profile);
                            CONFIG_RENAME_HOVER_ANIMATIONS.remove(profile);
                            if (profile.equals(editingConfigName)) {
                                editingConfigName = null;
                                editingConfigValue = "";
                            }
                            // Preserve the current grid position after deletion. updateScroll()
                            // will only clamp it if the shortened list no longer reaches this far.
                        }
                        return true;
                    }

                    float contentLeft = cardX + 7.0f * lastGuiScale;
                    float contentRight = folderX - 5.0f * lastGuiScale;
                    float renameSize = CONFIG_RENAME_SIZE * lastGuiScale;
                    float renameX = contentLeft;
                    float renameY = cardY + 5.5f * lastGuiScale;
                    if (inside(event.x(), event.y(), renameX, renameY, renameSize, renameSize)) {
                        editingConfigName = profile;
                        editingConfigValue = profile;
                        return true;
                    }
                }
                if (doubled && deletable) {
                    editingConfigName = profile;
                    editingConfigValue = profile;
                    return true;
                }
                commitConfigRename();
                configManager.selectProfile(profile);
                resetScroll();
                return true;
            }
        }

        commitConfigRename();
        searchFocused = false;
        configSearchFocused = false;
        return true;
    }

    private static ConfigManager configManager() {
        Manager manager = Manager.getInstance();
        return manager == null ? null : manager.getConfigManager();
    }

    private static boolean handleConfigEditKey(KeyEvent event) {
        if (editingConfigName == null) {
            return false;
        }
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            editingConfigName = null;
            editingConfigValue = "";
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commitConfigRename();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!editingConfigValue.isEmpty()) {
                editingConfigValue = editingConfigValue.substring(0, editingConfigValue.length() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            editingConfigValue = "";
            return true;
        }
        return true;
    }

    private static void commitConfigRename() {
        if (editingConfigName == null) {
            return;
        }
        ConfigManager configManager = configManager();
        if (configManager != null) {
            String value = editingConfigValue == null ? "" : editingConfigValue.trim();
            if (!value.isBlank() && !value.equals(editingConfigName)) {
                configManager.renameProfile(editingConfigName, value);
            }
        }
        editingConfigName = null;
        editingConfigValue = "";
    }

    private static List<String> filteredConfigProfiles(ConfigManager configManager) {
        String needle = configQuery.trim().toLowerCase(Locale.ROOT);
        return configManager.profiles().stream()
                .filter(name -> needle.isBlank() || name.toLowerCase(Locale.ROOT).contains(needle))
                .sorted(ClickGuiController::compareNaturally)
                .toList();
    }

    private static int compareNaturally(String left, String right) {
        if (ConfigManager.DEFAULT_PROFILE.equals(left)) {
            return ConfigManager.DEFAULT_PROFILE.equals(right) ? 0 : -1;
        }
        if (ConfigManager.DEFAULT_PROFILE.equals(right)) {
            return 1;
        }

        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);
            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftRunStart = leftIndex;
                int rightRunStart = rightIndex;
                while (leftIndex < left.length() && Character.isDigit(left.charAt(leftIndex))) leftIndex++;
                while (rightIndex < right.length() && Character.isDigit(right.charAt(rightIndex))) rightIndex++;

                int leftNumberStart = leftRunStart;
                int rightNumberStart = rightRunStart;
                while (leftNumberStart + 1 < leftIndex && left.charAt(leftNumberStart) == '0') leftNumberStart++;
                while (rightNumberStart + 1 < rightIndex && right.charAt(rightNumberStart) == '0') rightNumberStart++;

                int lengthCompare = Integer.compare(leftIndex - leftNumberStart, rightIndex - rightNumberStart);
                if (lengthCompare != 0) return lengthCompare;
                for (int offset = 0; offset < leftIndex - leftNumberStart; offset++) {
                    int digitCompare = Character.compare(
                            left.charAt(leftNumberStart + offset), right.charAt(rightNumberStart + offset));
                    if (digitCompare != 0) return digitCompare;
                }
                int zeroCompare = Integer.compare(leftIndex - leftRunStart, rightIndex - rightRunStart);
                if (zeroCompare != 0) return zeroCompare;
                continue;
            }

            int characterCompare = Character.compare(
                    Character.toLowerCase(leftChar), Character.toLowerCase(rightChar));
            if (characterCompare != 0) return characterCompare;
            leftIndex++;
            rightIndex++;
        }
        int lengthCompare = Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
        return lengthCompare != 0 ? lengthCompare : left.compareTo(right);
    }

    private static boolean handleTabClick(MouseButtonEvent event) {
        if (event.button() != 0) {
            return false;
        }
        float tabWidth = SIDE_WIDTH - 20.0f;
        float x = lastGuiX + (SIDE_WIDTH - tabWidth) * 0.5f * lastGuiScale;
        float y = lastGuiY + (HEADER_HEIGHT + 21.0f) * lastGuiScale;
        for (int i = 0; i < TABS.size(); i++) {
            CategoryTab tab = TABS.get(i);
            if (inside(event.x(), event.y(), x, y, tabWidth * lastGuiScale, 24.0f * lastGuiScale)) {
                selectedCategory = tab.category();
                selectedHwEvents = tab.hwEvents();
                figuraModelsEditorOpen = false;
                customCrosshairEditorOpen = false;
                itemReplacerEditorOpen = false;
                searchFocused = false;
                configSearchFocused = false;
                HW_EVENTS_PANEL.resetInteraction();
                query = "";
                resetScroll();
                return true;
            }
            y += (i == 2 ? 34.0f : 29.0f) * lastGuiScale;
        }
        return false;
    }

    private static List<Module> visibleModules() {
        Collection<Module> source = modules();
        LanguageCode language = LanguageManager.current();
        if (Objects.equals(visibleModulesCacheQuery, query)
                && visibleModulesCacheCategory == selectedCategory
                && visibleModulesCacheHwEvents == selectedHwEvents
                && visibleModulesCacheLanguage == language
                && visibleModulesCacheCount == source.size()) {
            return visibleModulesCache;
        }

        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module module : source) {
            if (!matchesSelectedTab(module)) {
                continue;
            }
            if (!needle.isEmpty() && !module.getDisplayName().toLowerCase(Locale.ROOT).contains(needle)
                    && !module.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            result.add(module);
        }
        result.sort(Comparator.comparing(Module::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        visibleModulesCache = List.copyOf(result);
        visibleModulesCacheQuery = query;
        visibleModulesCacheCategory = selectedCategory;
        visibleModulesCacheHwEvents = selectedHwEvents;
        visibleModulesCacheLanguage = language;
        visibleModulesCacheCount = source.size();
        return visibleModulesCache;
    }

    private static boolean matchesSelectedTab(Module module) {
        if (module == null || selectedHwEvents) {
            return false;
        }
        return module.getCategory() == selectedCategory;
    }

    private static Collection<Module> modules() {
        ModuleManager manager = Manager.getModules();
        return manager == null ? List.of() : manager.getModules();
    }

    private static ModuleOption optionFor(Module module) {
        return OPTIONS.computeIfAbsent(module, ModuleOptionFactory::create);
    }

    public static void openFiguraModelsEditor() {
        figuraModelsEditorOpen = true;
        customCrosshairEditorOpen = false;
        itemReplacerEditorOpen = false;
        searchFocused = false;
        configSearchFocused = false;
        query = "";
        resetScroll();
        FIGURA_MODELS_PANEL.open();
    }

    private static void closeFiguraModelsEditor() {
        figuraModelsEditorOpen = false;
        searchFocused = false;
        configSearchFocused = false;
        resetScroll();
    }

    public static void openCustomCrosshairEditor(CustomCrosshair module) {
        customCrosshairEditorOpen = true;
        figuraModelsEditorOpen = false;
        itemReplacerEditorOpen = false;
        searchFocused = false;
        configSearchFocused = false;
        query = "";
        resetScroll();
        CUSTOM_CROSSHAIR_PANEL.open(module);
    }

    private static void closeCustomCrosshairEditor() {
        customCrosshairEditorOpen = false;
        searchFocused = false;
        configSearchFocused = false;
        resetScroll();
    }

    public static void openItemReplacerEditor(ItemReplacer module) {
        itemReplacerEditorOpen = true;
        figuraModelsEditorOpen = false;
        customCrosshairEditorOpen = false;
        searchFocused = false;
        configSearchFocused = false;
        query = "";
        resetScroll();
        ITEM_REPLACER_PANEL.open(module);
    }

    private static void closeItemReplacerEditor() {
        itemReplacerEditorOpen = false;
        searchFocused = false;
        configSearchFocused = false;
        resetScroll();
    }

    private static boolean isBodyEditorOpen() {
        return figuraModelsEditorOpen || customCrosshairEditorOpen || itemReplacerEditorOpen;
    }

    private static void closeBodyEditor() {
        if (figuraModelsEditorOpen) {
            closeFiguraModelsEditor();
        }
        if (customCrosshairEditorOpen) {
            closeCustomCrosshairEditor();
        }
        if (itemReplacerEditorOpen) {
            closeItemReplacerEditor();
        }
    }

    private static String activeTitle() {
        for (CategoryTab tab : TABS) {
            if (tab.active()) {
                return tab.label();
            }
        }
        return "Render";
    }

    private static String activeDescription() {
        return switch (activeTitle()) {
            case "Render" -> "Visuals feature for gameplay";
            case "Utils" -> "Utilites for best game experience";
            case "Misc" -> "Miscellanius for xz";
            case "Hw Events" -> "Events on holyworld";
            case "Configs" -> "Profile and config management";
            default -> "";
        };
    }

    private static void renderPanelShader(float x, float y, float scale, float alpha) {
        float width = GUI_WIDTH * scale;
        float height = GUI_HEIGHT * scale;
        float radius = 9.0f * scale;
        Render2D.blur(x, y, width, height, radius, 8.0f * scale, 0.64f,
                color(7, 10, 21, 246, alpha));
        Render2D.rect(x, y, width, height, radius,
                color(7, 9, 19, 72, alpha));
        Render2D.outline(x, y, width, height, radius, 0.45f * scale,
                color(150, 136, 210, 28, alpha));
    }

    private static void renderFooter(float guiX, float guiY, float scale, float alpha) {
        float iconX = guiX + 10.2f * scale;
        float iconY = guiY + (GUI_HEIGHT - 31.0f) * scale;
        float headSize = 19.5f * scale;
        renderCurrentPlayerHead(iconX, iconY, headSize, alpha);

        float textX = iconX + headSize + 6.0f * scale;
        Render2D.text(FontType.BOLD, currentPlayerName(), textX, iconY + 1.7f * scale, 6.8f * scale, color(238, 240, 248, 235, alpha));
        Render2D.text(FontType.SEMIBOLD, todayDate(), textX, iconY + 11.4f * scale, 5.2f * scale, color(139, 139, 162, 210, alpha));
    }

    private static String todayDate() {
        return LocalDate.now().format(FOOTER_DATE_FORMAT);
    }

    private static String profileDate(ConfigManager.ProfileInfo profileInfo) {
        long createdAt = Math.max(0L, profileInfo.createdAt());
        return Instant.ofEpochMilli(createdAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(PROFILE_DATE_FORMAT);
    }

    private static void renderCurrentPlayerHead(float x, float y, float size, float alpha) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player instanceof AbstractClientPlayer player) {
            String texture = player.getSkin().body().texturePath().toString();
            int tint = color(255, 255, 255, 255, alpha);
            Render2D.imageUvNearest(texture, x, y, size, size, 5.0f * lastGuiScale,
                    1.0f, 0.125f, 0.125f, 0.25f, 0.25f, tint);
            Render2D.imageUvNearest(texture, x, y, size, size, 5.0f * lastGuiScale,
                    1.0f, 0.625f, 0.125f, 0.75f, 0.25f, tint);
            return;
        }
        Render2D.image(SIDEBAR_AVATAR, x, y, size, size, 5.0f * lastGuiScale,
                color(255, 255, 255, 255, alpha));
    }

    private static String currentPlayerName() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            String playerName = client.player.getName().getString();
            if (playerName != null && !playerName.isBlank()) {
                return playerName;
            }
        }
        if (client != null && client.getUser() != null) {
            String userName = client.getUser().getName();
            if (userName != null && !userName.isBlank()) {
                return userName;
            }
        }
        return "Player";
    }

    private static String categoryIcon(String label) {
        return switch (label) {
            case "Render" -> "R";      // visual / palette
            case "Utils" -> "A";       // sliders / tools
            case "Misc" -> "B";        // gear / misc
            case "Hw Events" -> "S";   // event list
            case "Configs" -> "h";     // paper icon from iconnew
            default -> "A";
        };
    }

    private static void renderCategoryIcon(String label, float x, float y, float size, int color) {
        String icon = categoryIcon(label);
        if ("h".equals(icon)) {
            renderIconNewGlyph(icon, x, y, size, color);
            return;
        }

        // GUI_ICONS glyphs have very different source plane bounds. Normalize their actual
        // visible bounds, not merely the nominal font size, so every sidebar icon occupies
        // the same square and is centered against both the text and the tab itself.
        float left;
        float bottom;
        float right;
        float top;
        switch (icon) {
            case "R" -> { left = -0.011067708f; bottom = -0.046875f; right = 0.593098958f; top = 0.588541667f; }
            case "A" -> { left = 0.085286458f; bottom = 0.036458333f; right = 0.918619792f; top = 0.869791667f; }
            case "B" -> { left = -0.001302083f; bottom = -0.026041667f; right = 0.977864583f; top = 0.963541667f; }
            case "S" -> { left = -0.021809896f; bottom = 0.015625f; right = 0.613606771f; top = 0.661458333f; }
            default -> { left = 0.085286458f; bottom = 0.036458333f; right = 0.918619792f; top = 0.869791667f; }
        }

        float planeWidth = Math.max(0.001f, right - left);
        float planeHeight = Math.max(0.001f, top - bottom);
        // Optical calibration: source glyphs occupy their plane bounds very differently.
        // These factors make all four sidebar symbols look the same size to the eye.
        float opticalFactor = switch (icon) {
            case "R" -> 0.74f;
            case "A" -> 0.70f;
            case "B" -> 0.63f;
            case "S" -> 0.86f;
            default -> 0.72f;
        };
        float targetVisible = size * opticalFactor;
        float glyphSize = targetVisible / Math.max(planeWidth, planeHeight);
        float visibleWidth = planeWidth * glyphSize;
        float visibleHeight = planeHeight * glyphSize;
        float visibleX = x + (size - visibleWidth) * 0.5f;
        float visibleY = y + (size - visibleHeight) * 0.5f;
        float glyphX = visibleX - left * glyphSize;
        float glyphY = visibleY - (0.95f - top) * glyphSize;
        Render2D.text(FontType.GUI_ICONS, icon, glyphX, glyphY, glyphSize, color);
    }

    private static void renderIconNewGlyph(String icon, float x, float y, float size, int color) {
        float left;
        float bottom;
        float right;
        float top;
        switch (icon) {
            case "h" -> {
                left = 0.007835751f;
                bottom = -0.078125f;
                right = 0.992210751f;
                top = 0.953125f;
            }
            case "z" -> {
                left = 0.0703125f;
                bottom = 0.0078125f;
                right = 0.9296875f;
                top = 0.8671875f;
            }
            case "l" -> {
                left = 0.06986861f;
                bottom = 0.007368608f;
                right = 0.9292436f;
                top = 0.8667436f;
            }
            default -> {
                left = -0.015625f;
                bottom = -0.078125f;
                right = 1.015625f;
                top = 0.953125f;
            }
        }
        float planeWidth = Math.max(0.001f, right - left);
        float planeHeight = Math.max(0.001f, top - bottom);
        float targetVisible = size * ("z".equals(icon) ? 0.72f : 0.78f);
        float glyphSize = targetVisible / Math.max(planeWidth, planeHeight);
        float visibleWidth = planeWidth * glyphSize;
        float visibleHeight = planeHeight * glyphSize;
        float visibleX = x + (size - visibleWidth) * 0.5f;
        float visibleY = y + (size - visibleHeight) * 0.5f;
        float glyphX = visibleX - left * glyphSize;
        float glyphY = visibleY - (0.95f - top) * glyphSize;
        Render2D.text(FontType.ICONNEW, icon, glyphX, glyphY, glyphSize, color);
    }

    private static void renderDeltaActionGlyph(String glyph, float x, float y, float size, int color) {
        float left;
        float bottom;
        float right;
        float top;
        switch (glyph) {
            case "Z" -> {
                left = 0.015625f;
                bottom = -0.1015625f;
                right = 0.984375f;
                top = 0.9765625f;
            }
            case "K" -> {
                left = 0.0f;
                bottom = -0.0703125f;
                right = 1.0f;
                top = 0.9453125f;
            }
            case "x" -> {
                left = -0.0078125f;
                bottom = -0.0390625f;
                right = 1.0078125f;
                top = 0.9140625f;
            }
            default -> {
                return;
            }
        }
        float planeWidth = right - left;
        float planeHeight = top - bottom;
        float glyphSize = size * 0.78f / Math.max(planeWidth, planeHeight);
        float visibleWidth = planeWidth * glyphSize;
        float visibleHeight = planeHeight * glyphSize;
        float glyphX = x + (size - visibleWidth) * 0.5f - left * glyphSize;
        float glyphY = y + (size - visibleHeight) * 0.5f - (0.9375f - top) * glyphSize;
        Render2D.text(FontType.DELTA_ICONS, glyph, glyphX, glyphY, glyphSize, color);
    }

    private static void renderRect(float rootX, float rootY, float x, float y, float width, float height, float radius, float scale, int color) {
        Render2D.rect(rootX + x * scale, rootY + y * scale, width * scale, height * scale, radius * scale, color);
    }

    private static void renderRect(float x, float y, float width, float height, float r1, float r2, float r3, float r4, float scale, int color) {
        Render2D.rect(x, y, width * scale, height * scale, r1 * scale, r2 * scale, r3 * scale, r4 * scale, color);
    }

    private static void renderSeparator(float x, float y, float width, float height, int color) {
        Render2D.rect(x, y, width, height, 0.0f, color);
    }

    private static MouseButtonEvent toDesignEvent(MouseButtonEvent event) {
        float scale = coordinateScale();
        if (Math.abs(scale - 1.0f) <= 0.0001f) {
            return event;
        }
        MouseButtonInfo info = new MouseButtonInfo(event.button(), event.modifiers());
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, info);
    }

    private static float coordinateScale() {
        return Render2DCoordinateSpace.guiIndependentScale();
    }

    private static void updateMousePosition() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.mouseHandler == null || client.getWindow() == null) {
            return;
        }
        float scale = coordinateScale();
        mouseX = client.mouseHandler.getScaledXPos(client.getWindow()) / scale;
        mouseY = client.mouseHandler.getScaledYPos(client.getWindow()) / scale;
    }

    private static boolean inside(double px, double py, float x, float y, float width, float height) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    private static float animateConfigHover(Map<String, SmoothAnimation> animations, String profile,
                                            boolean hovered, double durationSeconds) {
        SmoothAnimation animation = animations.computeIfAbsent(profile, ignored -> new SmoothAnimation());
        animation.run(hovered ? 1.0 : 0.0, durationSeconds, Easings.CUBIC_OUT, true);
        animation.update();
        return clamp(animation.get(), 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int color(int red, int green, int blue, int alpha, float alphaMultiplier) {
        return ColorUtil.rgba(red, green, blue, Math.round(alpha * alphaMultiplier));
    }

    private record CategoryTab(String label, ModuleCategory category, boolean hwEvents) {
        boolean active() {
            return selectedCategory == category && selectedHwEvents == hwEvents;
        }
    }
}
