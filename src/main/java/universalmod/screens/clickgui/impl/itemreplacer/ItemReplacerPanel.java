package universalmod.screens.clickgui.impl.itemreplacer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import universalmod.api.module.impl.render.ItemReplacer;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.item.RenderItem;
import universalmod.utils.render.item.RenderItemOptions;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.theme.ThemeRender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ItemReplacerPanel {
    private static final FontType FONT = FontType.SEMIBOLD;
    private static final int COLUMNS = 4;
    private static final float SEARCH_H = 20.0f;
    private static final float CELL_H = 54.0f;
    private static final float GAP = 8.0f;

    private final List<String> filteredModels = new ArrayList<>();
    private ItemReplacer module;
    private String query = "";
    private String appliedQuery = "";
    private String hoveredModel;
    private boolean searchFocused;
    private float viewX;
    private float viewY;
    private float viewW;
    private float viewH;
    private float searchX;
    private float searchY;
    private float searchW;
    private float searchH;
    private float gridX;
    private float gridY;
    private float gridW;
    private float gridH;
    private float cellW;
    private float cellH;
    private float cellGap;
    private float scroll;
    private float maxScroll;

    public void open(ItemReplacer module) {
        this.module = module;
        query = "";
        appliedQuery = "";
        searchFocused = false;
        hoveredModel = null;
        scroll = 0.0f;
        filteredModels.clear();
        filteredModels.addAll(List.of(ItemReplacer.MODELS));
    }

    public void render(GuiGraphics graphics, float x, float y, float w, float h, float scale, float alpha, double mx, double my) {
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;
        updateFilter();

        int a = Math.round(255.0f * alpha);
        int text = ColorUtil.rgba(225, 229, 238, a);
        int dim = ColorUtil.rgba(143, 148, 162, a);

        float padX = 16.0f * scale;
        float padTop = 12.0f * scale;
        float contentX = x + padX;
        float contentY = y + padTop;
        float contentW = Math.max(1.0f, w - padX * 2.0f);
        float contentH = Math.max(1.0f, h - padTop - 11.0f * scale);

        Render2D.text(FontType.BOLD, "Item Replacer models", contentX, contentY, 9.4f * scale, text);
        Render2D.text(FONT, "Choise custom sword model",
                contentX, contentY + 13.0f * scale, 5.8f * scale, dim);

        searchW = Math.min(152.0f * scale, contentW * 0.46f);
        searchH = SEARCH_H * scale;
        searchX = contentX + contentW - searchW;
        searchY = contentY + 2.0f * scale;
        renderSearch(alpha, scale, mx, my);

        gridX = contentX;
        gridY = Math.max(contentY + 24.0f * scale, searchY + searchH) + 10.0f * scale;
        gridW = contentW;
        gridH = Math.max(30.0f, contentY + contentH - 17.0f * scale - gridY);
        cellGap = GAP * scale;
        cellH = CELL_H * scale;
        cellW = (gridW - cellGap * (COLUMNS - 1)) / COLUMNS;

        hoveredModel = null;
        renderGrid(graphics, alpha, scale, mx, my);
        renderScrollbar(alpha, scale);
        renderFooter(contentX, contentY + contentH - 9.0f * scale, contentW, alpha, scale);
    }

    private void renderSearch(float alpha, float scale, double mx, double my) {
        boolean hover = hit(mx, my, searchX, searchY, searchW, searchH);
        float radius = 6.0f * scale;
        if (!ThemeRender.clickGuiGlass(searchX, searchY, searchW, searchH, radius, 10.0f, alpha)) {
            Render2D.rect(searchX, searchY, searchW, searchH, radius,
                    ColorUtil.rgba(24, 25, 34, Math.round((hover ? 118.0f : 96.0f) * alpha)));
        }
        Render2D.rect(searchX, searchY, searchW, searchH, radius,
                ColorUtil.rgba(12, 13, 19, Math.round((searchFocused ? 66.0f : hover ? 48.0f : 34.0f) * alpha)));
        Render2D.outline(searchX, searchY, searchW, searchH, radius, 0.35f * scale,
                ColorUtil.rgba(255, 255, 255, Math.round((searchFocused ? 42.0f : hover ? 28.0f : 16.0f) * alpha)));

        String display = query.isEmpty() && !searchFocused ? "Search models..." : query;
        int color = query.isEmpty() && !searchFocused
                ? ColorUtil.rgba(136, 140, 152, Math.round(225.0f * alpha))
                : ColorUtil.rgba(232, 235, 244, Math.round(245.0f * alpha));
        float textSize = 6.0f * scale;
        Render2D.text(FONT, trim(display, searchW - 12.0f * scale, textSize),
                searchX + 6.0f * scale, searchY + (searchH - textSize) * 0.5f - 0.45f * scale,
                textSize, color);
        if (searchFocused && (System.currentTimeMillis() / 450L) % 2L == 0L) {
            float tx = searchX + 6.0f * scale + Render2D.textWidth(FONT, query, textSize);
            Render2D.rect(Math.min(tx, searchX + searchW - 5.0f * scale), searchY + 5.0f * scale,
                    0.8f * scale, 10.0f * scale, 0.4f * scale,
                    ColorUtil.rgba(232, 235, 244, Math.round(230.0f * alpha)));
        }
    }

    private void renderGrid(GuiGraphics graphics, float alpha, float scale, double mx, double my) {
        if (filteredModels.isEmpty()) {
            maxScroll = 0.0f;
            String text = "No matching models";
            float textSize = 7.0f * scale;
            float tw = Render2D.textWidth(FONT, text, textSize);
            Render2D.text(FONT, text, gridX + (gridW - tw) * 0.5f, gridY + gridH * 0.5f - 4.0f * scale,
                    textSize, ColorUtil.rgba(143, 148, 162, Math.round(240.0f * alpha)));
            return;
        }

        int rows = rowCount();
        float contentH = rows * cellH + Math.max(0, rows - 1) * cellGap;
        maxScroll = Math.max(0.0f, contentH - gridH);
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);
        Render2D.pushScissor(graphics, gridX, gridY, gridW, gridH);
        try {
            int firstRow = Math.max(0, (int) Math.floor(scroll / (cellH + cellGap)));
            int lastRow = Math.min(rows - 1, (int) Math.floor((scroll + gridH - 0.001f) / (cellH + cellGap)));
            for (int row = firstRow; row <= lastRow; row++) {
                float cellY = gridY + row * (cellH + cellGap) - scroll;
                for (int column = 0; column < COLUMNS; column++) {
                    int index = row * COLUMNS + column;
                    if (index >= filteredModels.size()) {
                        break;
                    }
                    float cellX = gridX + column * (cellW + cellGap);
                    drawCell(filteredModels.get(index), cellX, cellY, alpha, scale, mx, my);
                }
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    private void drawCell(String model, float x, float y, float alpha, float scale, double mx, double my) {
        boolean hovered = hit(mx, my, x, y, cellW, cellH) && hit(mx, my, gridX, gridY, gridW, gridH);
        boolean selected = module != null && model.equals(module.getSelectedModel());
        if (hovered) {
            hoveredModel = model;
        }

        float radius = 6.0f * scale;
        if (!ThemeRender.clickGuiGlass(x, y, cellW, cellH, radius, 10.0f, alpha)) {
            Render2D.rect(x, y, cellW, cellH, radius,
                    ColorUtil.rgba(24, 25, 34, Math.round((hovered ? 122.0f : 96.0f) * alpha)));
        }
        Render2D.rect(x, y, cellW, cellH, radius,
                selected
                        ? ColorUtil.rgba(86, 72, 126, Math.round(70.0f * alpha))
                        : ColorUtil.rgba(13, 14, 20, Math.round((hovered ? 46.0f : 28.0f) * alpha)));
        Render2D.outline(x, y, cellW, cellH, radius, selected ? 0.75f * scale : 0.35f * scale,
                selected
                        ? ColorUtil.rgba(184, 165, 255, Math.round(190.0f * alpha))
                        : ColorUtil.rgba(255, 255, 255, Math.round((hovered ? 28.0f : 13.0f) * alpha)));

        ItemStack preview = module == null ? ItemStack.EMPTY : module.getPreviewStack(model);
        if (!preview.isEmpty()) {
            float iconSize = Math.min(38.0f * scale, cellH - 10.0f * scale);
            RenderItem.item(preview, x + (cellW - iconSize) * 0.5f, y + (cellH - iconSize) * 0.5f,
                    iconSize, RenderItemOptions.noDecorations(alpha));
        }
    }

    private void renderScrollbar(float alpha, float scale) {
        if (maxScroll <= 0.0f) {
            return;
        }
        int rows = rowCount();
        float contentH = rows * cellH + Math.max(0, rows - 1) * cellGap;
        float trackX = gridX + gridW + 4.0f * scale;
        float thumbH = Math.max(18.0f * scale, gridH * (gridH / contentH));
        float thumbY = gridY + (gridH - thumbH) * (scroll / maxScroll);
        Render2D.rect(trackX, gridY, 1.3f * scale, gridH, 0.65f * scale,
                ColorUtil.rgba(255, 255, 255, Math.round(24.0f * alpha)));
        Render2D.rect(trackX, thumbY, 1.3f * scale, thumbH, 0.65f * scale,
                ColorUtil.rgba(184, 165, 255, Math.round(150.0f * alpha)));
    }

    private void renderFooter(float x, float y, float w, float alpha, float scale) {
        String label = hoveredModel != null ? hoveredModel : (module == null ? "" : module.getSelectedModel());
        if (label == null || label.isBlank()) {
            return;
        }
        float textSize = 5.8f * scale;
        String fitted = trim(label, w, textSize);
        float tw = Render2D.textWidth(FONT, fitted, textSize);
        Render2D.text(FONT, fitted, x + (w - tw) * 0.5f, y, textSize,
                ColorUtil.rgba(176, 181, 194, Math.round(235.0f * alpha)));
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && hit(mx, my, searchX, searchY, searchW, searchH)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        if (button != 0 || !hit(mx, my, gridX, gridY, gridW, gridH)) {
            return hit(mx, my, viewX, viewY, viewW, viewH);
        }
        int column = (int) Math.floor((mx - gridX) / (cellW + cellGap));
        int row = (int) Math.floor((my - gridY + scroll) / (cellH + cellGap));
        if (column < 0 || column >= COLUMNS || row < 0) {
            return true;
        }
        float localX = (float) (mx - gridX - column * (cellW + cellGap));
        float localY = (float) (my - gridY + scroll - row * (cellH + cellGap));
        if (localX > cellW || localY > cellH) {
            return true;
        }
        int index = row * COLUMNS + column;
        if (index >= 0 && index < filteredModels.size() && module != null) {
            module.setSelectedModel(filteredModels.get(index));
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (hit(mx, my, gridX, gridY, gridW, gridH)) {
            scroll = Mth.clamp(scroll - (float) amount * 28.0f, 0.0f, maxScroll);
            return true;
        }
        return hit(mx, my, viewX, viewY, viewW, viewH);
    }

    public boolean keyPressed(KeyEvent event) {
        if (!searchFocused) {
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            searchFocused = false;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE) {
            query = "";
            return true;
        }
        return true;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!searchFocused || !event.isAllowedChatCharacter()) {
            return false;
        }
        String value = event.codepointAsString();
        if (value != null && !value.isEmpty() && query.length() + value.length() <= 64) {
            query += value;
        }
        return true;
    }

    private void updateFilter() {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(appliedQuery)) {
            return;
        }
        appliedQuery = normalized;
        filteredModels.clear();
        for (String model : ItemReplacer.MODELS) {
            if (normalized.isEmpty() || model.toLowerCase(Locale.ROOT).contains(normalized)) {
                filteredModels.add(model);
            }
        }
        scroll = 0.0f;
    }

    private int rowCount() {
        return (filteredModels.size() + COLUMNS - 1) / COLUMNS;
    }

    private static String trim(String text, float width, float size) {
        if (text == null || Render2D.textWidth(FONT, text, size) <= width) {
            return text == null ? "" : text;
        }
        String suffix = "...";
        int hi = text.length();
        for (int len = hi; len >= 0; len--) {
            String candidate = text.substring(0, len) + suffix;
            if (Render2D.textWidth(FONT, candidate, size) <= width) {
                return candidate;
            }
        }
        return suffix;
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
