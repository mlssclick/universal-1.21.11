package universalmod.screens.clickgui.impl.crosshair;

import net.minecraft.client.gui.GuiGraphics;
import universalmod.api.module.impl.misc.CustomCrosshair;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.crosshair.CrosshairCanvas;
import universalmod.utils.render.crosshair.CrosshairCanvasPreset;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.theme.ThemeRender;

public final class CustomCrosshairPanel {
    private static final FontType FONT = FontType.SEMIBOLD;
    private static final int GRID_SIZE = CrosshairCanvas.SIZE;
    private static final float HEADER_H = 33.0f;
    private static final float GRID_PIXEL_SIZE = 206.0f;
    private static final float PRESET_SIZE = 25.0f;
    private static final float PRESET_GAP = 7.0f;
    private static final int PRESETS_PER_SIDE = 5;
    private static final float GRID_BACKDROP_PAD = 10.0f;

    private CustomCrosshair module;
    private CrosshairCanvas draftCanvas = CrosshairCanvasPreset.DEFAULT.getCanvas();
    private DragMode dragMode = DragMode.NONE;
    private CrosshairCanvas draggingCanvas;
    private int lastPixelX = -1;
    private int lastPixelY = -1;

    private float viewX;
    private float viewY;
    private float viewW;
    private float viewH;
    private float gridX;
    private float gridY;
    private float gridSize;
    private float cellSize;
    private float presetsX;
    private float presetsY;
    private float presetSize;
    private float presetGap;
    private float resetX;
    private float resetY;
    private float resetW;
    private float resetH;
    private float clearX;
    private float clearY;
    private float clearW;
    private float clearH;

    public void open(CustomCrosshair module) {
        this.module = module;
        this.draftCanvas = module == null ? CrosshairCanvasPreset.DEFAULT.getCanvas() : module.getCanvas().copy();
        dragMode = DragMode.NONE;
        draggingCanvas = null;
        lastPixelX = -1;
        lastPixelY = -1;
    }

    public void render(GuiGraphics graphics, float x, float y, float w, float h, float scale, float alpha, double mx, double my) {
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;
        int a = Math.round(255.0f * alpha);
        int text = ColorUtil.rgba(225, 229, 238, a);
        int dim = ColorUtil.rgba(143, 148, 162, a);

        float padX = 16.0f * scale;
        float padTop = 12.0f * scale;
        float contentX = x + padX;
        float contentY = y + padTop;
        float contentW = Math.max(1.0f, w - padX * 2.0f);
        float contentH = Math.max(1.0f, h - padTop - 11.0f * scale);

        Render2D.text(FontType.BOLD, "Custom Crosshair", contentX, contentY, 9.4f * scale, text);
        Render2D.text(FONT, "Left draw, right erase, middle toggle", contentX, contentY + 13.0f * scale,
                5.8f * scale, dim);

        clearW = 48.0f * scale;
        clearH = 18.0f * scale;
        clearX = contentX + contentW - clearW;
        clearY = contentY + 1.0f * scale;
        drawButton("Clear", clearX, clearY, clearW, clearH, hit(mx, my, clearX, clearY, clearW, clearH), alpha, scale);

        resetW = 56.0f * scale;
        resetH = clearH;
        resetX = clearX - resetW - 6.0f * scale;
        resetY = clearY;
        drawButton("Default", resetX, resetY, resetW, resetH, hit(mx, my, resetX, resetY, resetW, resetH), alpha, scale);

        float bodyTop = contentY + HEADER_H * scale;
        float bodyH = Math.max(20.0f, contentH - HEADER_H * scale);
        renderEditorPanel(graphics, contentX, bodyTop, contentW, bodyH, scale, alpha, mx, my);
    }

    private void renderEditorPanel(GuiGraphics graphics, float x, float y, float w, float h, float scale, float alpha, double mx, double my) {
        presetSize = PRESET_SIZE * scale;
        presetGap = PRESET_GAP * scale;

        float editorTop = y;
        float editorBottom = y + h;
        float editorH = Math.max(1.0f, editorBottom - editorTop);
        gridSize = Math.min(GRID_PIXEL_SIZE * scale, Math.min(w - 36.0f * scale, editorH - 20.0f * scale));
        cellSize = Math.max(1.0f, gridSize / GRID_SIZE);
        gridX = x + (w - gridSize) * 0.5f;
        gridY = editorTop + (editorH - gridSize) * 0.5f;
        renderGridBackdrop(alpha, scale);
        renderGrid(alpha, mx, my, scale);
        updatePresetAnchors(scale);
        renderPresets(graphics, alpha, mx, my);
    }

    private void updatePresetAnchors(float scale) {
        float sideGap = 20.0f * scale;
        float columnHeight = PRESETS_PER_SIDE * presetSize + (PRESETS_PER_SIDE - 1) * presetGap;
        presetsX = gridX - GRID_BACKDROP_PAD * scale - sideGap - presetSize;
        presetsY = gridY + (gridSize - columnHeight) * 0.5f;
    }

    private void renderGridBackdrop(float alpha, float scale) {
        float pad = GRID_BACKDROP_PAD * scale;
        float x = gridX - pad;
        float y = gridY - pad;
        float size = gridSize + pad * 2.0f;
        float radius = 12.0f * scale;
        if (!ThemeRender.clickGuiGlass(x, y, size, size, radius, 10.0f, alpha)) {
            Render2D.rect(x, y, size, size, radius, ColorUtil.rgba(24, 25, 34, Math.round(86.0f * alpha)));
        }
        Render2D.rect(x, y, size, size, radius, ColorUtil.rgba(14, 15, 22, Math.round(40.0f * alpha)));
        Render2D.outline(x, y, size, size, radius, 0.35f * scale,
                ColorUtil.rgba(255, 255, 255, Math.round(14.0f * alpha)));
    }

    private void renderPresets(GuiGraphics graphics, float alpha, double mx, double my) {
        CrosshairCanvasPreset selected = CrosshairCanvasPreset.of(draftCanvas);
        CrosshairCanvasPreset[] presets = CrosshairCanvasPreset.values();
        for (int i = 0; i < presets.length; i++) {
            float x = presetX(i);
            float y = presetY(i);
            boolean active = presets[i] == selected;
            boolean hover = hit(mx, my, x, y, presetSize, presetSize);
            float radius = 5.5f * Math.min(1.0f, presetSize / PRESET_SIZE);
            if (!ThemeRender.clickGuiGlass(x, y, presetSize, presetSize, radius, 10.0f, alpha)) {
                Render2D.rect(x, y, presetSize, presetSize, radius,
                        ColorUtil.rgba(24, 25, 34, Math.round((hover ? 126.0f : 98.0f) * alpha)));
            }
            Render2D.rect(x, y, presetSize, presetSize, radius,
                    active
                            ? ColorUtil.rgba(86, 72, 126, Math.round(82.0f * alpha))
                            : ColorUtil.rgba(13, 14, 20, Math.round((hover ? 46.0f : 28.0f) * alpha)));
            Render2D.outline(x, y, presetSize, presetSize, radius, 0.35f,
                    active
                            ? ColorUtil.rgba(184, 165, 255, Math.round(205.0f * alpha))
                            : ColorUtil.rgba(255, 255, 255, Math.round((hover ? 28.0f : 13.0f) * alpha)));
            renderCanvas(presets[i].getCanvas(), x, y, presetSize,
                    active
                            ? ColorUtil.rgba(245, 246, 249, Math.round(255.0f * alpha))
                            : ColorUtil.rgba(218, 221, 229, Math.round(235.0f * alpha)),
                    0.0f);
        }
    }

    private void renderGrid(float alpha, double mx, double my, float scale) {
        int hoverX = resolveGridColumn(mx);
        int hoverY = resolveGridRow(my);
        Render2D.rect(gridX, gridY, gridSize, gridSize, 0.0f,
                ColorUtil.rgba(10, 11, 17, Math.round(72.0f * alpha)));

        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                float left = cellLeft(x);
                float top = cellTop(y);
                boolean hover = x == hoverX && y == hoverY;
                if (hover) {
                    Render2D.rect(left, top, cellSize, cellSize, 0.0f,
                            ColorUtil.rgba(184, 165, 255, Math.round(34.0f * alpha)));
                }
                if (draftCanvas.isPixelActive(x, y)) {
                    float inset = 0.45f * scale;
                    Render2D.rect(
                            left + inset,
                            top + inset,
                            Math.max(1.0f, cellSize - inset * 2.0f),
                            Math.max(1.0f, cellSize - inset * 2.0f),
                            0.0f,
                            ColorUtil.rgba(245, 246, 249, Math.round(238.0f * alpha))
                    );
                }
            }
        }
        renderGridLines(alpha, scale);
        Render2D.outline(gridX, gridY, gridSize, gridSize, 0.0f, 0.45f * scale,
                ColorUtil.rgba(255, 255, 255, Math.round(26.0f * alpha)));
    }

    private void renderGridLines(float alpha, float scale) {
        float thickness = 1.0f;
        int lineColor = ColorUtil.rgba(255, 255, 255, Math.round(76.0f * alpha));
        for (int i = 1; i < GRID_SIZE; i++) {
            float offset = i * cellSize;
            Render2D.rect(gridX + offset - thickness * 0.5f, gridY, thickness, gridSize, 0.0f, lineColor);
            Render2D.rect(gridX, gridY + offset - thickness * 0.5f, gridSize, thickness, 0.0f, lineColor);
        }
    }

    private void renderCanvas(CrosshairCanvas canvas, float x, float y, float size, int color, float radius) {
        float pixel = Math.max(1.0f, size / GRID_SIZE);
        for (int py = 0; py < GRID_SIZE; py++) {
            for (int px = 0; px < GRID_SIZE; px++) {
                if (canvas.isPixelActive(px, py)) {
                    Render2D.rect(x + px * pixel, y + py * pixel, Math.max(1.0f, pixel), Math.max(1.0f, pixel), radius, color);
                }
            }
        }
    }

    private void drawButton(String label, float x, float y, float w, float h, boolean hover, float alpha, float scale) {
        Render2D.rect(x, y, w, h, h * 0.5f, ColorUtil.rgba(20, 21, 29, Math.round((hover ? 238.0f : 210.0f) * alpha)));
        Render2D.outline(x, y, w, h, h * 0.5f, 0.35f * scale, ColorUtil.rgba(255, 255, 255, Math.round((hover ? 30.0f : 18.0f) * alpha)));
        float textSize = 5.7f * scale;
        float tw = Render2D.textWidth(FONT, label, textSize);
        Render2D.text(FONT, label, x + (w - tw) * 0.5f, y + (h - textSize) * 0.5f - 0.5f * scale, textSize,
                ColorUtil.rgba(222, 226, 235, Math.round(255.0f * alpha)));
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (hit(mx, my, clearX, clearY, clearW, clearH)) {
            draftCanvas = new CrosshairCanvas();
            commit();
            return true;
        }
        if (hit(mx, my, resetX, resetY, resetW, resetH)) {
            draftCanvas = CrosshairCanvasPreset.DEFAULT.getCanvas();
            commit();
            return true;
        }
        if (handlePresetClick(mx, my)) {
            return true;
        }

        dragMode = switch (button) {
            case 0 -> DragMode.ENABLE;
            case 1 -> DragMode.DISABLE;
            case 2 -> DragMode.TOGGLE;
            default -> DragMode.NONE;
        };
        if (dragMode == DragMode.NONE || !isInsideGrid(mx, my)) {
            dragMode = DragMode.NONE;
            return false;
        }
        draggingCanvas = draftCanvas.copy();
        return applyAt(mx, my);
    }

    public boolean mouseDragged(double mx, double my) {
        if (dragMode == DragMode.NONE) {
            return false;
        }
        if (!isInsideGrid(mx, my)) {
            return true;
        }
        applyAt(mx, my);
        return true;
    }

    public boolean mouseReleased() {
        dragMode = DragMode.NONE;
        draggingCanvas = null;
        lastPixelX = -1;
        lastPixelY = -1;
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return hit(mx, my, viewX, viewY, viewW, viewH);
    }

    private boolean handlePresetClick(double mx, double my) {
        CrosshairCanvasPreset[] presets = CrosshairCanvasPreset.values();
        for (int i = 0; i < presets.length; i++) {
            float x = presetX(i);
            float y = presetY(i);
            if (hit(mx, my, x, y, presetSize, presetSize)) {
                draftCanvas = presets[i].getCanvas().copy();
                commit();
                return true;
            }
        }
        return false;
    }

    private float presetX(int index) {
        if (index < PRESETS_PER_SIDE) {
            return presetsX;
        }
        float scale = presetSize / PRESET_SIZE;
        return gridX + gridSize + GRID_BACKDROP_PAD * scale + 20.0f * scale;
    }

    private float presetY(int index) {
        int sideIndex = index % PRESETS_PER_SIDE;
        return presetsY + sideIndex * (presetSize + presetGap);
    }

    private boolean applyAt(double mx, double my) {
        int pixelX = resolveGridColumn(mx);
        int pixelY = resolveGridRow(my);
        if (pixelX < 0 || pixelY < 0 || pixelX >= GRID_SIZE || pixelY >= GRID_SIZE) {
            return false;
        }
        if (pixelX == lastPixelX && pixelY == lastPixelY) {
            return false;
        }
        lastPixelX = pixelX;
        lastPixelY = pixelY;

        switch (dragMode) {
            case ENABLE -> draftCanvas.enablePixel(pixelX, pixelY);
            case DISABLE -> draftCanvas.disablePixel(pixelX, pixelY);
            case TOGGLE -> {
                if (draggingCanvas != null && draggingCanvas.isPixelActive(pixelX, pixelY)) {
                    draftCanvas.disablePixel(pixelX, pixelY);
                } else {
                    draftCanvas.enablePixel(pixelX, pixelY);
                }
            }
            default -> {
                return false;
            }
        }
        commit();
        return true;
    }

    private void commit() {
        if (module != null) {
            module.setCanvas(draftCanvas.copy());
        }
    }

    private boolean isInsideGrid(double mx, double my) {
        return mx >= gridX && mx < gridX + gridSize && my >= gridY && my < gridY + gridSize;
    }

    private int resolveGridColumn(double mx) {
        if (mx < gridX || mx >= gridX + gridSize) {
            return -1;
        }
        int column = (int) ((mx - gridX) / (gridSize / GRID_SIZE));
        return Math.max(0, Math.min(GRID_SIZE - 1, column));
    }

    private int resolveGridRow(double my) {
        if (my < gridY || my >= gridY + gridSize) {
            return -1;
        }
        int row = (int) ((my - gridY) / (gridSize / GRID_SIZE));
        return Math.max(0, Math.min(GRID_SIZE - 1, row));
    }

    private float cellLeft(int x) {
        return gridX + x * cellSize;
    }

    private float cellTop(int y) {
        return gridY + y * cellSize;
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private enum DragMode {
        NONE,
        ENABLE,
        DISABLE,
        TOGGLE
    }
}
