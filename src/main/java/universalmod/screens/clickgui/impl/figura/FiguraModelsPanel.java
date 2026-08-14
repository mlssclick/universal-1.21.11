package universalmod.screens.clickgui.impl.figura;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import universalmod.api.module.impl.render.FiguraModels;
import universalmod.utils.figura.FiguraBridge;
import universalmod.utils.figura.FiguraEntry;
import universalmod.utils.figura.FiguraRepository;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class FiguraModelsPanel {
    private static final FontType FONT = FontType.SEMIBOLD;
    private static final int COLUMNS = 3;
    private static final float CARD_H = 60.0f;
    private static final float GAP = 7.0f;
    private static final float HEADER_H = 43.0f;
    private static final float PREVIEW_GAP = 10.0f;

    private FiguraEntry hovered;
    private FiguraEntry selected;
    private float scroll;
    private float maxScroll;
    private float viewX;
    private float viewY;
    private float viewW;
    private float viewH;
    private float gridW;
    private float listTop;
    private float listX;
    private float listH;
    private float cardW;
    private float cardH;
    private float cardGap;
    private float clearX;
    private float clearY;
    private float clearW;
    private float clearH;
    private float rescanX;
    private float rescanY;
    private float rescanW;
    private float rescanH;
    private String status = "";
    private boolean statusOk;
    private long statusUntil;

    public void open() {
        scroll = 0.0f;
        maxScroll = 0.0f;
        hovered = null;
        selected = null;
        FiguraRepository.rescan();
    }

    public void render(GuiGraphics graphics, float x, float y, float w, float h, float scale, float alpha, double mx, double my) {
        viewX = x;
        viewY = y;
        viewW = w;
        viewH = h;
        int a = Math.round(255.0f * alpha);
        int text = ColorUtil.rgba(225, 229, 238, a);
        int dim = ColorUtil.rgba(132, 137, 150, a);
        int accent = ColorUtil.rgba(184, 165, 255, a);

        float padX = 16.0f * scale;
        float padTop = 12.0f * scale;
        float contentX = x + padX;
        float contentY = y + padTop;
        float contentW = Math.max(1.0f, w - padX * 2.0f);
        float contentH = Math.max(1.0f, h - padTop - 11.0f * scale);

        Render2D.text(FontType.BOLD, "Custom Figura Models", contentX, contentY, 9.4f * scale, text);
        String subtitle = FiguraBridge.isAvailable()
                ? "Drag or install models in universal/figura models"
                : "Figura is not installed, please reinstall it or close this panel";
        Render2D.text(FONT, subtitle, contentX, contentY + 13.0f * scale, 5.8f * scale,
                FiguraBridge.isAvailable() ? dim : ColorUtil.rgba(225, 150, 130, a));

        clearW = 48.0f * scale;
        clearH = 18.0f * scale;
        clearX = contentX + contentW - clearW;
        clearY = contentY + 1.0f * scale;
        drawButton("Clear", clearX, clearY, clearW, clearH, hit(mx, my, clearX, clearY, clearW, clearH), alpha, scale);

        rescanW = 56.0f * scale;
        rescanH = clearH;
        rescanX = clearX - rescanW - 6.0f * scale;
        rescanY = clearY;
        drawButton("Rescan", rescanX, rescanY, rescanW, rescanH, hit(mx, my, rescanX, rescanY, rescanW, rescanH), alpha, scale);

        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            Render2D.text(FONT, status, contentX, contentY + 25.0f * scale, 5.6f * scale,
                    statusOk ? ColorUtil.rgba(145, 220, 170, a) : ColorUtil.rgba(230, 145, 135, a));
        }

        listTop = contentY + HEADER_H * scale;
        listX = contentX;
        listH = Math.max(20.0f, contentH - HEADER_H * scale);
        gridW = (contentW - PREVIEW_GAP * scale) * 0.58f;
        float previewX = contentX + gridW + PREVIEW_GAP * scale;
        float previewW = contentW - gridW - PREVIEW_GAP * scale;

        List<FiguraEntry> list = FiguraRepository.all();
        hovered = null;
        if (list.isEmpty()) {
            maxScroll = 0.0f;
            String empty = "No avatars in " + FiguraRepository.directory();
            Render2D.text(FONT, trim(empty, gridW - 4.0f * scale, 5.9f * scale),
                    contentX, listTop + 6.0f * scale, 5.9f * scale, dim);
            renderPreview(graphics, previewX, listTop, previewW, listH, scale, alpha, mx, my);
            return;
        }

        cardH = CARD_H * scale;
        cardGap = GAP * scale;
        cardW = (gridW - cardGap * (COLUMNS - 1)) / COLUMNS;
        int rows = (list.size() + COLUMNS - 1) / COLUMNS;
        float contentHeight = rows * cardH + Math.max(0, rows - 1) * cardGap;
        maxScroll = Math.max(0.0f, contentHeight - listH);
        scroll = Math.min(scroll, maxScroll);

        Render2D.pushScissor(graphics, contentX, listTop, gridW, listH);
        try {
            for (int i = 0; i < list.size(); i++) {
                float cx = listX + (i % COLUMNS) * (cardW + cardGap);
                float cy = listTop + (i / COLUMNS) * (cardH + cardGap) - scroll;
                if (cy + cardH < listTop || cy > listTop + listH) {
                    continue;
                }
                drawCard(list.get(i), cx, cy, cardW, cardH, scale, alpha, mx, my);
            }
        } finally {
            Render2D.popScissor(graphics);
        }
        renderPreview(graphics, previewX, listTop, previewW, listH, scale, alpha, mx, my);
    }

    private void renderPreview(GuiGraphics graphics, float x, float y, float w, float h, float scale, float alpha, double mx, double my) {
        int a = Math.round(255.0f * alpha);
        int accent = ColorUtil.rgba(184, 165, 255, a);
        Render2D.rect(x, y, w, h, 7.0f * scale, ColorUtil.rgba(10, 11, 17, Math.round(216.0f * alpha)));
        Render2D.outline(x, y, w, h, 7.0f * scale, 0.35f * scale, ColorUtil.rgba(255, 255, 255, Math.round(18.0f * alpha)));

        FiguraEntry applied = FiguraRepository.byId(FiguraBridge.appliedId());
        FiguraEntry shown = hovered != null ? hovered : (selected != null ? selected : applied);
        float pad = 8.0f * scale;
        float captionH = 28.0f * scale;
        float box = Math.min(w - pad * 2.0f, h - pad * 2.0f - captionH);
        float boxX = x + (w - box) * 0.5f;
        float boxY = y + pad;

        if (!renderPlayerModel(graphics, boxX, boxY, box, mx, my)) {
            drawThumb(shown, boxX, boxY, box, scale, alpha);
        } else if (shown != null && shown != applied) {
            float inset = box * 0.30f;
            float insetX = boxX + box - inset - 4.0f * scale;
            float insetY = boxY + 4.0f * scale;
            drawThumb(shown, insetX, insetY, inset, scale, alpha);
            Render2D.outline(insetX, insetY, inset, inset, 5.0f * scale, 0.35f * scale,
                    ColorUtil.rgba(255, 255, 255, Math.round(28.0f * alpha)));
        }

        if (shown == null) {
            String hint = applied == null ? "No model applied" : "Hover a model";
            float textSize = 5.9f * scale;
            float hintW = Render2D.textWidth(FONT, hint, textSize);
            Render2D.text(FONT, hint, x + (w - hintW) * 0.5f, boxY + box + 8.0f * scale, textSize,
                    ColorUtil.rgba(130, 135, 148, a));
            return;
        }

        float nameSize = 6.3f * scale;
        String name = trim(shown.displayName(), w - pad * 2.0f, nameSize);
        float nameW = Render2D.textWidth(FONT, name, nameSize);
        Render2D.text(FONT, name, x + (w - nameW) * 0.5f, boxY + box + 7.0f * scale, nameSize,
                ColorUtil.rgba(225, 229, 238, a));

        boolean appliedState = FiguraBridge.isApplied(shown);
        String state = appliedState ? "Applied" : "Click to apply";
        float stateSize = 5.4f * scale;
        float stateW = Render2D.textWidth(FONT, state, stateSize);
        Render2D.text(FONT, state, x + (w - stateW) * 0.5f, boxY + box + 17.0f * scale, stateSize,
                appliedState ? accent : ColorUtil.rgba(130, 135, 148, a));
    }

    private boolean renderPlayerModel(GuiGraphics graphics, float x, float y, float box, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (graphics == null || mc == null || mc.player == null || box < 10.0f) {
            return false;
        }
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    Math.round(x),
                    Math.round(y),
                    Math.round(x + box),
                    Math.round(y + box),
                    Math.max(10, Math.round(box * 0.42f)),
                    0.0625f,
                    (float) mx,
                    (float) my,
                    mc.player);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void drawCard(FiguraEntry entry, float x, float y, float w, float h, float scale, float alpha, double mx, double my) {
        boolean hover = hit(mx, my, x, y, w, h);
        if (hover) {
            hovered = entry;
        }
        boolean applied = FiguraBridge.isApplied(entry);
        boolean marked = applied || entry == selected;
        int accent = ColorUtil.rgba(184, 165, 255, Math.round(255.0f * alpha));
        Render2D.rect(x, y, w, h, 6.0f * scale,
                ColorUtil.rgba(14, 15, 22, Math.round((hover ? 238.0f : 210.0f) * alpha)));
        Render2D.outline(x, y, w, h, 6.0f * scale, 0.35f * scale,
                marked ? ColorUtil.withAlpha(accent, Math.round((applied ? 180.0f : 95.0f) * alpha)) : ColorUtil.rgba(255, 255, 255, Math.round(16.0f * alpha)));

        float thumb = h - 21.0f * scale;
        float thumbX = x + (w - thumb) * 0.5f;
        drawThumb(entry, thumbX, y + 5.0f * scale, thumb, scale, alpha);

        float textSize = 5.0f * scale;
        String name = trim(entry.displayName(), w - 5.0f * scale, textSize);
        float nameW = Render2D.textWidth(FONT, name, textSize);
        Render2D.text(FONT, name, x + (w - nameW) * 0.5f, y + h - 9.0f * scale, textSize,
                applied ? accent : ColorUtil.rgba(214, 218, 228, Math.round(255.0f * alpha)));
    }

    private void drawThumb(FiguraEntry entry, float x, float y, float size, float scale, float alpha) {
        Identifier preview = entry == null ? null : FiguraRepository.preview(entry);
        if (preview != null) {
            Render2D.image(preview.toString(), x, y, size, size, 5.0f * scale,
                    ColorUtil.rgba(255, 255, 255, Math.round(255.0f * alpha)));
            return;
        }
        Render2D.rect(x, y, size, size, 5.0f * scale, ColorUtil.rgba(255, 255, 255, Math.round(13.0f * alpha)));
        if (size > 34.0f * scale) {
            String none = "no preview";
            float textSize = 5.2f * scale;
            float noneW = Render2D.textWidth(FONT, none, textSize);
            Render2D.text(FONT, none, x + (size - noneW) * 0.5f, y + size * 0.5f - 3.0f * scale, textSize,
                    ColorUtil.rgba(125, 130, 144, Math.round(255.0f * alpha)));
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
        if (button != 0) {
            return false;
        }
        if (hit(mx, my, clearX, clearY, clearW, clearH)) {
            boolean ok = FiguraBridge.clear();
            if (ok) {
                FiguraModels.rememberFromEditor("");
            }
            setStatus(ok ? "Model removed" : "Figura unavailable", ok);
            return true;
        }
        if (hit(mx, my, rescanX, rescanY, rescanW, rescanH)) {
            FiguraRepository.rescan();
            setStatus("Folder rescanned", true);
            return true;
        }

        List<FiguraEntry> list = FiguraRepository.all();
        if (list.isEmpty()) {
            return false;
        }
        if (my < listTop || my > listTop + listH || mx < listX || mx > listX + gridW) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            float cx = listX + (i % COLUMNS) * (cardW + cardGap);
            float cy = listTop + (i / COLUMNS) * (cardH + cardGap) - scroll;
            if (hit(mx, my, cx, cy, cardW, cardH)) {
                FiguraEntry entry = list.get(i);
                selected = entry;
                if (FiguraBridge.isApplied(entry)) {
                    boolean ok = FiguraBridge.clear();
                    if (ok) {
                        FiguraModels.rememberFromEditor("");
                    }
                    setStatus(ok ? "Model removed" : "Figura unavailable", ok);
                } else {
                    boolean ok = FiguraBridge.apply(entry);
                    if (ok) {
                        FiguraModels.rememberFromEditor(entry.id());
                        FiguraModels.activateFromEditor();
                    }
                    setStatus(ok ? entry.displayName() + " applied" : "Figura unavailable", ok);
                }
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (maxScroll <= 0.0f || !hit(mx, my, viewX, viewY, viewW, viewH)) {
            return false;
        }
        scroll = Math.max(0.0f, Math.min(maxScroll, scroll - (float) amount * 22.0f * inferScale()));
        return true;
    }

    public boolean onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return false;
        }
        int imported = 0;
        int skipped = 0;
        String lastError = "";
        for (Path path : paths) {
            try {
                ImportResult result = importPath(path);
                imported += result.imported();
                skipped += result.skipped();
            } catch (Exception exception) {
                skipped++;
                lastError = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            }
        }
        FiguraRepository.rescan();
        if (imported > 0) {
            setStatus("Imported " + imported + " model" + (imported == 1 ? "" : "s") + (skipped > 0 ? ", skipped " + skipped : ""), true);
            return true;
        }
        setStatus(lastError.isBlank() ? "Drop folders with avatar.json" : lastError, false);
        return true;
    }

    private ImportResult importPath(Path rawPath) throws IOException {
        if (rawPath == null) {
            return new ImportResult(0, 1);
        }
        Path source = rawPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            return new ImportResult(0, 1);
        }
        if (Files.isRegularFile(source.resolve("avatar.json"))) {
            copyAvatarFolder(source);
            return new ImportResult(1, 0);
        }

        int imported = 0;
        int skipped = 0;
        try (Stream<Path> stream = Files.list(source)) {
            for (Path child : stream.toList()) {
                if (Files.isDirectory(child) && Files.isRegularFile(child.resolve("avatar.json"))) {
                    copyAvatarFolder(child.toAbsolutePath().normalize());
                    imported++;
                } else {
                    skipped++;
                }
            }
        }
        return new ImportResult(imported, skipped);
    }

    private void copyAvatarFolder(Path source) throws IOException {
        Path root = FiguraRepository.directory().toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (source.startsWith(root)) {
            return;
        }
        String folderName = sanitizeFolderName(source.getFileName() == null ? "model" : source.getFileName().toString());
        Path target = uniqueTarget(root, folderName);
        if (!target.startsWith(root)) {
            throw new IOException("Unsafe target folder");
        }
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path from : stream.toList()) {
                Path relative = source.relativize(from);
                Path to = target.resolve(relative).normalize();
                if (!to.startsWith(target)) {
                    throw new IOException("Unsafe file in dropped folder");
                }
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to);
                } else if (Files.isRegularFile(from)) {
                    Files.createDirectories(to.getParent());
                    Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private Path uniqueTarget(Path root, String baseName) {
        Path target = root.resolve(baseName).normalize();
        if (!Files.exists(target)) {
            return target;
        }
        for (int i = 2; i < 10_000; i++) {
            Path candidate = root.resolve(baseName + " (" + i + ")").normalize();
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return root.resolve(baseName + " " + System.currentTimeMillis()).normalize();
    }

    private String sanitizeFolderName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            value = "model";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c < 32 || "\\/:*?\"<>|".indexOf(c) >= 0) {
                out.append('_');
            } else {
                out.append(c);
            }
        }
        String sanitized = out.toString().trim();
        String lower = sanitized.toLowerCase(Locale.ROOT);
        if (sanitized.isEmpty() || lower.equals("con") || lower.equals("prn") || lower.equals("aux") || lower.equals("nul")) {
            return "model";
        }
        return sanitized;
    }

    private float inferScale() {
        return Math.max(0.001f, viewW / 451.0f);
    }

    private void setStatus(String message, boolean ok) {
        status = message;
        statusOk = ok;
        statusUntil = System.currentTimeMillis() + 2500L;
    }

    private String trim(String value, float maxWidth, float size) {
        if (value == null) {
            return "";
        }
        if (Render2D.textWidth(FONT, value, size) <= maxWidth) {
            return value;
        }
        String cut = value;
        while (cut.length() > 1 && Render2D.textWidth(FONT, cut + "...", size) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private record ImportResult(int imported, int skipped) {
    }
}
