package universalmod.screens.clickgui.impl;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.api.drag.impl.CurrentEventsPanel;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.network.AnarchySwitcher;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.theme.ThemeRender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class HwEventsPanel {
    private static final List<String> FILTER_LABELS = List.of("Все", "Соло", "Дуо", "Трио", "Клан");
    private static final List<String> RANGE_KEYS = List.of("Solo", "Duo", "Trio", "Clan");
    private static final float TOOLBAR_BUTTON_SCALE = 1.30f;
    private static final float TOOLBAR_HEIGHT = 15.333333f * TOOLBAR_BUTTON_SCALE;
    private static final float TOOLBAR_BUTTON_WIDTH = 48.0f * TOOLBAR_BUTTON_SCALE;
    private static final float TOOLBAR_BUTTON_GAP = 2.0f;
    private static final float SIDE_PADDING = 16.0f;
    private static final float TOP_PADDING = 3.0f;
    private static final float CARD_GAP_X = 14.0f;
    private static final float CARD_GAP_Y = 8.5f;
    private static final float CARD_HEIGHT = 35.1f;
    private static final int COLUMNS = 3;
    private static final float POPUP_WIDTH = 220.0f;
    private static final float POPUP_HEIGHT = 110.0f;
    private static final float POPUP_CHIP_TEXT_SIZE = 5.6f;
    private static final float POPUP_CHIP_HEIGHT = 11.0f;
    private static final float POPUP_CHIP_GAP_X = 3.0f;
    private static final float POPUP_CHIP_GAP_Y = 3.0f;
    private static final float POPUP_CHIP_PADDING_X = 4.0f;
    private static final float POPUP_RANGE_HEIGHT = 11.0f;
    private static final float POPUP_RANGE_INPUT_WIDTH = 27.0f;
    private static final float POPUP_RANGE_LABEL_GAP = 2.0f;
    private static final float POPUP_RANGE_GROUP_GAP = 6.0f;
    private static final float EVENT_PLAY_BUTTON_SIZE = 12.0f;
    private static final float EVENT_PLAY_BUTTON_RIGHT_PADDING = 7.0f;
    private static final float EVENT_PLAY_ALPHA_LEFT = 1.0f / 12.0f;
    private static final float EVENT_PLAY_ALPHA_TOP = 1.0f / 12.0f;
    private static final float EVENT_PLAY_ALPHA_RIGHT = 10.0f / 12.0f;
    private static final float EVENT_PLAY_ALPHA_BOTTOM = 11.0f / 12.0f;
    private static final String PLAY_ICON = "music_player/play.png";

    private final CurrentEventsPanel events = CurrentEventsPanel.getInstance();
    private final SmoothAnimation scrollAnimation = new SmoothAnimation();
    private final SmoothAnimation popupAnimation = new SmoothAnimation();
    private final Map<String, SmoothAnimation> filterAnimations = new LinkedHashMap<>();
    private final Map<String, String> rangeDrafts = new LinkedHashMap<>();
    private final List<EventPlayHitTarget> playHitTargets = new ArrayList<>();
    private String query = "";
    private boolean popupOpen;
    private int focusedRange = -1;
    private int selectedFilter;
    private float scrollTarget;
    private float scrollLimit;
    private float panelX;
    private float panelY;
    private float panelWidth;
    private float panelHeight;
    private float scale = 1.0f;
    private float popupX;
    private float popupY;
    private float popupWidth;
    private float popupHeight;

    void resetInteraction() {
        focusedRange = -1;
        popupOpen = false;
        popupAnimation.set(0.0);
        playHitTargets.clear();
    }

    void setSearchQuery(String query) {
        this.query = query == null ? "" : query;
    }

    void render(GuiGraphics graphics, float x, float y, float width, float height,
                float scale, float alpha, double mouseX, double mouseY) {
        this.panelX = x;
        this.panelY = y;
        this.panelWidth = width;
        this.panelHeight = height;
        this.scale = scale;

        List<CurrentEventsPanel.EventSnapshot> visible = filteredEvents();
        updateScroll(visible.size());
        renderToolbar(graphics, alpha, mouseX, mouseY);
        renderCards(graphics, visible, alpha);
    }

    void renderOverlay(GuiGraphics graphics, float alpha, double mouseX, double mouseY) {
        popupAnimation.run(popupOpen ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT, true);
        popupAnimation.update();
        float progress = popupAnimation.get();
        if (progress <= 0.01f) {
            return;
        }

        popupWidth = POPUP_WIDTH * scale;
        popupHeight = POPUP_HEIGHT * scale;
        popupX = toolbarStartX() + (TOOLBAR_BUTTON_WIDTH + TOOLBAR_BUTTON_GAP) * scale;
        popupY = panelY + TOP_PADDING * scale;
        float popupAlpha = alpha * progress;
        float radius = 8.0f * scale;

        if (!ThemeRender.clickGuiGlass(popupX, popupY, popupWidth, popupHeight,
                radius, 9.0f, popupAlpha)) {
            Render2D.blur(popupX, popupY, popupWidth, popupHeight, radius, 7.0f * scale, 0.66f,
                    color(7, 9, 19, 218, popupAlpha));
        }
        Render2D.rect(popupX, popupY, popupWidth, popupHeight, radius,
                color(7, 9, 19, 108, popupAlpha));
        Render2D.outline(popupX, popupY, popupWidth, popupHeight, radius, 0.7f * scale,
                color(166, 135, 255, 82, popupAlpha));

        List<CurrentEventsPanel.EventFilterSnapshot> filters = events.filterSnapshots();
        long enabled = filters.stream().filter(CurrentEventsPanel.EventFilterSnapshot::enabled).count();
        float left = popupX + 12.0f * scale;
        float right = popupX + popupWidth - 12.0f * scale;
        float headerY = popupY + 10.0f * scale;
        String count = "Отображать ивенты: " + enabled + " из " + filters.size();
        Render2D.text(FontType.BOLD, count, left, headerY, 6.5f * scale,
                color(245, 245, 252, 250, popupAlpha));

        PopupFilterLayout filterLayout = popupFilterLayout(filters, left, right, popupY + 22.0f * scale);
        for (FilterChip chip : filterLayout.chips()) {
            CurrentEventsPanel.EventFilterSnapshot filter = chip.filter();
            boolean hovered = inside(mouseX, mouseY, chip.x(), chip.y(), chip.width(), chip.height());
            SmoothAnimation selectionAnimation = filterAnimations.computeIfAbsent(filter.key(), ignored -> {
                SmoothAnimation animation = new SmoothAnimation();
                animation.set(filter.enabled() ? 1.0 : 0.0);
                return animation;
            });
            selectionAnimation.run(filter.enabled() ? 1.0 : 0.0, 0.16, Easings.CIRC_IN, true);
            selectionAnimation.update();
            float selection = clamp(selectionAnimation.get(), 0.0f, 1.0f);
            int fill = ColorUtil.lerpColor(
                    color(86, 87, 91, hovered ? 178 : 155, popupAlpha),
                    color(137, 117, 199, hovered ? 225 : 205, popupAlpha), selection);
            int outline = color(255, 255, 255, Math.round(70.0f + 32.0f * selection), popupAlpha);
            Render2D.rect(chip.x(), chip.y(), chip.width(), chip.height(), 2.4f * scale, fill);
            Render2D.outline(chip.x(), chip.y(), chip.width(), chip.height(), 2.4f * scale,
                    0.6f * scale, outline);
            drawCentered(filter.label(), chip.x(), chip.y(), chip.width(), chip.height(),
                    POPUP_CHIP_TEXT_SIZE * scale,
                    ColorUtil.lerpColor(
                            color(185, 187, 205, hovered ? 245 : 230, popupAlpha),
                            color(255, 255, 255, 245, popupAlpha), selection));
        }

        float separatorY = filterLayout.bottom() + 5.0f * scale;
        Render2D.rect(left, separatorY, right - left, 0.5f * scale, 0.0f,
                color(145, 133, 190, 42, popupAlpha));
        float sectionY = separatorY + 5.0f * scale;
        Render2D.text(FontType.BOLD, "Анархии", left, sectionY, 6.2f * scale,
                color(244, 244, 252, 245, popupAlpha));

        float fieldsY = sectionY + 10.0f * scale;
        for (RangeField field : rangeFieldLayout(left, right, fieldsY)) {
            int i = field.index();
            String key = field.key();
            boolean focused = focusedRange == i;
            String value = rangeDrafts.getOrDefault(key, events.anarchyRange(key));
            boolean valid = CurrentEventsPanel.isValidAnarchyRange(value);
            drawCentered(key, field.labelX(), field.y(), field.labelWidth(), field.height(), 5.2f * scale,
                    color(205, 207, 220, 235, popupAlpha));
            Render2D.rect(field.inputX(), field.y(), field.inputWidth(), field.height(), 2.4f * scale,
                    focused ? color(137, 117, 199, 205, popupAlpha)
                            : color(86, 87, 91, 155, popupAlpha));
            Render2D.outline(field.inputX(), field.y(), field.inputWidth(), field.height(), 2.4f * scale, 0.55f * scale,
                    valid
                            ? color(255, 255, 255, focused ? 102 : 70, popupAlpha)
                            : color(255, 64, 82, focused ? 210 : 130, popupAlpha));
            drawCentered(value, field.inputX(), field.y(), field.inputWidth(), field.height(), 5.3f * scale,
                    focused ? color(255, 255, 255, 245, popupAlpha)
                            : color(205, 207, 220, 235, popupAlpha));
            if (focused && (System.currentTimeMillis() / 340L) % 2L == 0L) {
                float textSize = 5.3f * scale;
                float textWidth = Render2D.textWidth(FontType.BOLD, value, textSize);
                float caretX = Math.min(field.inputX() + (field.inputWidth() + textWidth) * 0.5f + 0.8f * scale,
                        field.inputX() + field.inputWidth() - 2.0f * scale);
                Render2D.rect(caretX, field.y() + 2.5f * scale, 0.6f * scale, 6.0f * scale,
                        0.2f * scale, color(255, 255, 255, 220, popupAlpha));
            }
        }
    }

    boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (popupAnimation.get() > 0.01f || popupOpen) {
            if (handlePopupClick(event)) {
                return true;
            }
            if (!inside(event.x(), event.y(), popupX, popupY, popupWidth, popupHeight)) {
                closePopup();
            }
            return true;
        }

        float toolbarY = panelY + TOP_PADDING * scale;
        float buttonWidth = TOOLBAR_BUTTON_WIDTH * scale;
        float gap = TOOLBAR_BUTTON_GAP * scale;
        float toolbarX = toolbarStartX();
        if (event.button() == 0 && inside(event.x(), event.y(), toolbarX, toolbarY,
                buttonWidth, TOOLBAR_HEIGHT * scale)) {
            openPopup();
            return true;
        }

        if (event.button() == 0) {
            for (int i = 0; i < FILTER_LABELS.size(); i++) {
                float x = toolbarX + (i + 1) * (buttonWidth + gap);
                if (inside(event.x(), event.y(), x, toolbarY, buttonWidth, TOOLBAR_HEIGHT * scale)) {
                    selectedFilter = i;
                    scrollTarget = 0.0f;
                    return true;
                }
            }

            for (EventPlayHitTarget target : playHitTargets) {
                if (!target.bounds().contains(event.x(), event.y())) {
                    continue;
                }
                if (doubled && target.anarchyNumber() >= 0) {
                    AnarchySwitcher.getInstance().start(target.anarchyNumber());
                }
                return true;
            }
        }
        return false;
    }

    boolean keyPressed(KeyEvent event) {
        if (popupOpen || popupAnimation.get() > 0.01f) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                if (focusedRange >= 0) {
                    commitFocusedRange();
                    focusedRange = -1;
                } else {
                    closePopup();
                }
                return true;
            }
            if (focusedRange >= 0) {
                String key = RANGE_KEYS.get(focusedRange);
                String value = rangeDrafts.getOrDefault(key, "");
                if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!value.isEmpty()) rangeDrafts.put(key, value.substring(0, value.length() - 1));
                    return true;
                }
                if (event.key() == GLFW.GLFW_KEY_DELETE) {
                    rangeDrafts.put(key, "");
                    return true;
                }
                if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                    commitFocusedRange();
                    focusedRange = -1;
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    boolean charTyped(CharacterEvent event) {
        if (focusedRange >= 0 && (popupOpen || popupAnimation.get() > 0.01f)) {
            String text = event.codepointAsString();
            if (text != null && text.length() == 1) {
                char character = text.charAt(0);
                String key = RANGE_KEYS.get(focusedRange);
                String value = rangeDrafts.getOrDefault(key, "");
                boolean asciiDigit = character >= '0' && character <= '9';
                if ((asciiDigit || character == '-') && value.length() < 7
                        && (character != '-' || !value.contains("-"))) {
                    rangeDrafts.put(key, value + character);
                }
            }
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (popupOpen || popupAnimation.get() > 0.01f) return true;
        if (!inside(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight) || scrollLimit <= 0.0f) {
            return false;
        }
        scrollTarget = clamp(scrollTarget - (float) scrollY * 27.0f, 0.0f, scrollLimit);
        return true;
    }

    boolean hasOpenPopup() {
        return popupOpen || popupAnimation.get() > 0.01f;
    }

    private void renderToolbar(GuiGraphics graphics, float alpha, double mouseX, double mouseY) {
        float y = panelY + TOP_PADDING * scale;
        float startX = toolbarStartX();
        float buttonWidth = TOOLBAR_BUTTON_WIDTH * scale;
        float gap = TOOLBAR_BUTTON_GAP * scale;
        float height = TOOLBAR_HEIGHT * scale;
        int buttonCount = FILTER_LABELS.size() + 1;
        for (int i = 0; i < buttonCount; i++) {
            float x = startX + i * (buttonWidth + gap);
            String label = i == 0 ? "Config" : FILTER_LABELS.get(i - 1);
            boolean hovered = inside(mouseX, mouseY, x, y, buttonWidth, height);
            if (i == 0) {
                float radius = 4.666667f * TOOLBAR_BUTTON_SCALE * scale;
                Render2D.blur(x, y, buttonWidth, height, radius,
                        2.933333f * TOOLBAR_BUTTON_SCALE * scale, 0.58f,
                        color(128, 96, 222, hovered ? 235 : 205, alpha));
                Render2D.rect(x, y, buttonWidth, height, radius,
                        color(128, 96, 222, hovered ? 62 : 44, alpha));
                Render2D.outline(x, y, buttonWidth, height, radius, 0.3f * scale,
                        color(190, 169, 255, hovered ? 96 : 62, alpha));
                drawCentered(label, x, y, buttonWidth, height,
                        4.866667f * TOOLBAR_BUTTON_SCALE * scale,
                        color(255, 255, 255, 250, alpha));
                continue;
            }

            boolean selected = selectedFilter == i - 1;
            float radius = 4.0f * TOOLBAR_BUTTON_SCALE * scale;
            Render2D.rect(x, y, buttonWidth, height, radius,
                    color(7, 10, 21, selected ? 80 : hovered ? 66 : 54, alpha));
            Render2D.outline(x, y, buttonWidth, height, radius,
                    (selected ? 0.6f : 0.266667f) * scale,
                    selected ? color(166, 135, 255, 220, alpha)
                            : color(133, 119, 198, hovered ? 52 : 24, alpha));
            drawCentered(label, x, y, buttonWidth, height,
                    4.8f * TOOLBAR_BUTTON_SCALE * scale,
                    color(244, 244, 252, selected ? 255 : hovered ? 242 : 220, alpha));
        }
    }

    private float toolbarStartX() {
        int buttonCount = FILTER_LABELS.size() + 1;
        float totalWidth = (buttonCount * TOOLBAR_BUTTON_WIDTH
                + (buttonCount - 1) * TOOLBAR_BUTTON_GAP) * scale;
        return panelX + (panelWidth - totalWidth) * 0.5f;
    }

    private void renderCards(GuiGraphics graphics, List<CurrentEventsPanel.EventSnapshot> visible, float alpha) {
        playHitTargets.clear();
        float cardsX = panelX + SIDE_PADDING * scale;
        float cardsY = panelY + (TOP_PADDING + TOOLBAR_HEIGHT + CARD_GAP_Y) * scale;
        float bottom = panelY + panelHeight - SIDE_PADDING * scale;
        float cardWidth = (panelWidth - (SIDE_PADDING * 2.0f + CARD_GAP_X * (COLUMNS - 1)) * scale) / COLUMNS;
        float cardHeight = CARD_HEIGHT * scale;
        Render2D.pushScissor(graphics, panelX, cardsY, panelWidth, Math.max(1.0f, bottom - cardsY));
        try {
            if (visible.isEmpty()) {
                String empty = "Нет активных ивентов";
                float size = 7.2f * scale;
                float width = Render2D.textWidth(FontType.SEMIBOLD, empty, size);
                Render2D.text(FontType.SEMIBOLD, empty, panelX + (panelWidth - width) * 0.5f,
                        cardsY + 12.0f * scale, size, color(170, 170, 188, 220, alpha));
                return;
            }
            for (int i = 0; i < visible.size(); i++) {
                int row = i / COLUMNS;
                int col = i % COLUMNS;
                float x = cardsX + col * (cardWidth + CARD_GAP_X * scale);
                float y = cardsY + row * (cardHeight + CARD_GAP_Y * scale) - scrollAnimation.get() * scale;
                if (y + cardHeight < cardsY || y > bottom) continue;
                PlayButtonBounds renderedButton = playButtonBounds(x, y, cardWidth, cardHeight);
                float visibleLeft = renderedButton.x() + renderedButton.width() * EVENT_PLAY_ALPHA_LEFT;
                float visibleRight = renderedButton.x() + renderedButton.width() * EVENT_PLAY_ALPHA_RIGHT;
                float visibleTop = Math.max(
                        renderedButton.y() + renderedButton.height() * EVENT_PLAY_ALPHA_TOP, cardsY);
                float visibleBottom = Math.min(
                        renderedButton.y() + renderedButton.height() * EVENT_PLAY_ALPHA_BOTTOM, bottom);
                if (visibleBottom > visibleTop) {
                    playHitTargets.add(new EventPlayHitTarget(
                            visible.get(i).anarchyNumber(),
                            new PlayButtonBounds(visibleLeft, visibleTop,
                                    visibleRight - visibleLeft, visibleBottom - visibleTop)
                    ));
                }
                renderEventCard(graphics, visible.get(i), x, y, cardWidth, cardHeight, alpha);
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private void renderEventCard(GuiGraphics graphics, CurrentEventsPanel.EventSnapshot event, float x, float y,
                                 float width, float height, float alpha) {
        float radius = 6.0f * scale;
        Render2D.rect(x, y, width, height, radius, color(7, 10, 21, 56, alpha));
        Render2D.outline(x, y, width, height, radius, 0.4f * scale,
                color(133, 119, 198, 22, alpha));
        float titleSize = 7.4f * scale;
        float detailsSize = 6.96f * scale;
        float left = x + 7.0f * scale;
        PlayButtonBounds playButton = playButtonBounds(x, y, width, height);
        float contentRight = playButton.x() - 5.0f * scale;
        Render2D.pushScissor(graphics, left, y, Math.max(1.0f, contentRight - left), height);
        try {
            String name = event.displayName();
            Render2D.text(FontType.BOLD, name, left, y + 6.0f * scale, titleSize,
                    withAlpha(event.color(), 0.98f, alpha));
            if (event.anarchyNumber() >= 0) {
                String tag = "#" + event.anarchyNumber();
                float tagX = left + Render2D.textWidth(FontType.BOLD, name, titleSize) + 3.0f * scale;
                Render2D.text(FontType.BOLD, tag, tagX, y + 6.0f * scale, titleSize,
                        color(216, 102, 31, 252, alpha));
            }
            String rarity = event.rarity().isBlank() ? "Обычный" : event.rarity();
            Render2D.text(FontType.SEMIBOLD, rarity, left, y + 21.5f * scale, detailsSize,
                    rarityColor(rarity, alpha));
        } finally {
            Render2D.popScissor(graphics);
        }
        Render2D.image(PLAY_ICON, playButton.x(), playButton.y(),
                playButton.width(), playButton.height(), 0.0f,
                color(255, 255, 255, 230, alpha));
    }

    private PlayButtonBounds playButtonBounds(float cardX, float cardY, float cardWidth, float cardHeight) {
        float size = EVENT_PLAY_BUTTON_SIZE * scale;
        return new PlayButtonBounds(
                cardX + cardWidth - size - EVENT_PLAY_BUTTON_RIGHT_PADDING * scale,
                cardY + (cardHeight - size) * 0.5f,
                size,
                size
        );
    }

    private List<CurrentEventsPanel.EventSnapshot> filteredEvents() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<CurrentEventsPanel.EventSnapshot> result = new ArrayList<>();
        for (CurrentEventsPanel.EventSnapshot event : events.visibleEventSnapshots()) {
            if (selectedFilter > 0 && !events.matchesAnarchyRange(RANGE_KEYS.get(selectedFilter - 1), event.anarchyNumber())) {
                continue;
            }
            String haystack = (event.displayName() + " " + event.id() + " " + event.rarity()
                    + " " + event.anarchyNumber()).toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && !haystack.contains(needle)) continue;
            result.add(event);
        }
        result.sort(Comparator.comparingInt(CurrentEventsPanel.EventSnapshot::anarchyNumber)
                .thenComparing(CurrentEventsPanel.EventSnapshot::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private PopupFilterLayout popupFilterLayout(List<CurrentEventsPanel.EventFilterSnapshot> filters,
                                                float left, float right, float top) {
        List<FilterChip> chips = new ArrayList<>(filters.size());
        float cursorX = left;
        float cursorY = top;
        float height = POPUP_CHIP_HEIGHT * scale;
        float gapX = POPUP_CHIP_GAP_X * scale;
        float gapY = POPUP_CHIP_GAP_Y * scale;
        float textSize = POPUP_CHIP_TEXT_SIZE * scale;
        for (CurrentEventsPanel.EventFilterSnapshot filter : filters) {
            float textWidth = Render2D.textWidth(FontType.SEMIBOLD, filter.label(), textSize);
            float width = Math.min(right - left, textWidth + POPUP_CHIP_PADDING_X * 2.0f * scale);
            if (cursorX > left && cursorX + width > right + 0.01f) {
                cursorX = left;
                cursorY += height + gapY;
            }
            chips.add(new FilterChip(filter, cursorX, cursorY, width, height));
            cursorX += width + gapX;
        }
        return new PopupFilterLayout(List.copyOf(chips), cursorY + height);
    }

    private List<RangeField> rangeFieldLayout(float left, float right, float y) {
        List<RangeField> fields = new ArrayList<>(RANGE_KEYS.size());
        float cursorX = left;
        float height = POPUP_RANGE_HEIGHT * scale;
        float inputWidth = POPUP_RANGE_INPUT_WIDTH * scale;
        float labelGap = POPUP_RANGE_LABEL_GAP * scale;
        float groupGap = POPUP_RANGE_GROUP_GAP * scale;
        float labelSize = 5.2f * scale;
        for (int i = 0; i < RANGE_KEYS.size(); i++) {
            String key = RANGE_KEYS.get(i);
            float labelWidth = Render2D.textWidth(FontType.BOLD, key, labelSize);
            float remaining = Math.max(1.0f, right - cursorX);
            float actualInputWidth = Math.min(inputWidth, Math.max(1.0f, remaining - labelWidth - labelGap));
            float inputX = cursorX + labelWidth + labelGap;
            fields.add(new RangeField(i, key, cursorX, labelWidth, inputX, actualInputWidth, y, height));
            cursorX = inputX + actualInputWidth + groupGap;
        }
        return List.copyOf(fields);
    }

    private boolean handlePopupClick(MouseButtonEvent event) {
        if (event.button() != 0 || !inside(event.x(), event.y(), popupX, popupY, popupWidth, popupHeight)) {
            return false;
        }
        List<CurrentEventsPanel.EventFilterSnapshot> filters = events.filterSnapshots();
        float left = popupX + 12.0f * scale;
        float right = popupX + popupWidth - 12.0f * scale;
        PopupFilterLayout filterLayout = popupFilterLayout(filters, left, right, popupY + 22.0f * scale);
        for (FilterChip chip : filterLayout.chips()) {
            if (inside(event.x(), event.y(), chip.x(), chip.y(), chip.width(), chip.height())) {
                events.toggleFilter(chip.filter().key());
                return true;
            }
        }
        float separatorY = filterLayout.bottom() + 5.0f * scale;
        float fieldsY = separatorY + 15.0f * scale;
        for (RangeField field : rangeFieldLayout(left, right, fieldsY)) {
            if (inside(event.x(), event.y(), field.inputX(), field.y(), field.inputWidth(), field.height())) {
                commitFocusedRange();
                focusedRange = field.index();
                return true;
            }
        }
        commitFocusedRange();
        focusedRange = -1;
        return true;
    }

    private void openPopup() {
        popupOpen = true;
        focusedRange = -1;
        rangeDrafts.clear();
        for (String key : RANGE_KEYS) rangeDrafts.put(key, events.anarchyRange(key));
        popupAnimation.run(1.0, 0.18, Easings.CUBIC_OUT);
    }

    private void closePopup() {
        commitFocusedRange();
        for (String key : RANGE_KEYS) {
            String value = rangeDrafts.get(key);
            if (CurrentEventsPanel.isValidAnarchyRange(value)) events.setAnarchyRange(key, value);
            else rangeDrafts.put(key, events.anarchyRange(key));
        }
        focusedRange = -1;
        popupOpen = false;
        popupAnimation.run(0.0, 0.18, Easings.CUBIC_OUT);
    }

    private void commitFocusedRange() {
        if (focusedRange < 0 || focusedRange >= RANGE_KEYS.size()) return;
        String key = RANGE_KEYS.get(focusedRange);
        String value = rangeDrafts.getOrDefault(key, "");
        if (events.setAnarchyRange(key, value)) rangeDrafts.put(key, events.anarchyRange(key));
    }

    private void updateScroll(int count) {
        int rows = (count + COLUMNS - 1) / COLUMNS;
        float cardsHeight = rows <= 0 ? 0.0f : rows * CARD_HEIGHT + Math.max(0, rows - 1) * CARD_GAP_Y;
        float viewport = panelHeight / Math.max(0.0001f, scale)
                - TOP_PADDING - TOOLBAR_HEIGHT - CARD_GAP_Y - SIDE_PADDING;
        scrollLimit = Math.max(0.0f, cardsHeight - viewport);
        scrollTarget = clamp(scrollTarget, 0.0f, scrollLimit);
        scrollAnimation.run(scrollTarget, 0.22, Easings.CUBIC_OUT, true);
        scrollAnimation.update();
    }

    private void drawCentered(String text, float x, float y, float width, float height, float size, int color) {
        float textWidth = Render2D.textWidth(FontType.BOLD, text, size);
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(FontType.BOLD, text, size);
        float textX = bounds.empty()
                ? x + (width - textWidth) * 0.5f
                : x + (width - bounds.width()) * 0.5f - bounds.minX();
        float textY = bounds.empty()
                ? y + (height - size) * 0.5f
                : y + (height - bounds.height()) * 0.5f - bounds.minY();
        Render2D.text(FontType.BOLD, text, textX, textY, size, color);
    }

    private static int color(int r, int g, int b, int a, float alpha) {
        return ColorUtil.rgba(r, g, b, Math.round(a * Math.max(0.0f, Math.min(1.0f, alpha))));
    }

    private static int withAlpha(int value, float opacity, float alpha) {
        int sourceAlpha = (value >>> 24) & 0xFF;
        int resultAlpha = Math.round(sourceAlpha * opacity * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (resultAlpha << 24) | (value & 0x00FFFFFF);
    }

    private static int rarityColor(String rarity, float alpha) {
        String value = rarity == null ? "" : rarity.trim().toLowerCase(Locale.ROOT);
        if (containsAny(value, "миф", "myth", "кров", "адск")) {
            return color(255, 72, 88, 245, alpha);
        }
        if (containsAny(value, "легендар", "legend", "смертель", "deadly", "роскош", "luxur")) {
            return color(255, 205, 72, 245, alpha);
        }
        if (containsAny(value, "эпич", "epic", "зажит", "взрыв", "explosive")) {
            return color(180, 104, 255, 245, alpha);
        }
        if (containsAny(value, "обыч", "normal", "default", "редк", "rare", "мирн", "peaceful")) {
            return color(92, 196, 255, 245, alpha);
        }
        return color(92, 196, 255, 235, alpha);
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record FilterChip(CurrentEventsPanel.EventFilterSnapshot filter,
                              float x, float y, float width, float height) {
    }

    private record PopupFilterLayout(List<FilterChip> chips, float bottom) {
    }

    private record RangeField(int index, String key, float labelX, float labelWidth,
                              float inputX, float inputWidth, float y, float height) {
    }

    private record EventPlayHitTarget(int anarchyNumber, PlayButtonBounds bounds) {
    }

    private record PlayButtonBounds(float x, float y, float width, float height) {
        private boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, x, y, width, height);
        }
    }
}
