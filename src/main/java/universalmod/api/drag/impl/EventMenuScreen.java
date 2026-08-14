package universalmod.api.drag.impl;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import universalmod.api.module.impl.render.Hud;
import universalmod.utils.lang.LanguageManager;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

public final class EventMenuScreen extends Screen {
    private static final float PANEL_WIDTH = 230.0F;
    private static final float ROW_HEIGHT = 14.0F;
    private static final float HEADER_HEIGHT = 18.0F;
    private static final float PADDING = 8.0F;
    private static final float BUTTON_WIDTH = 34.0F;
    private static final float BUTTON_HEIGHT = 10.0F;
    private static final int TITLE_COLOR = 0xFFF3F6FA;
    private static final int BUTTON_TEXT_ENABLED_COLOR = 0xFF84FF6A;
    private static final int BUTTON_TEXT_DISABLED_COLOR = 0xFFFF6A6A;
    private static final int EMPTY_COLOR = 0xFF96A0AA;

    private final Screen parent;
    private final CurrentEventsPanel panel = CurrentEventsPanel.getInstance();

    public EventMenuScreen(Screen parent) {
        super(Component.empty());
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Render2D.beginFrame(graphics);
        try {
            float panelHeight = HEADER_HEIGHT + (Math.max(1, panel.filters().size()) * ROW_HEIGHT) + 8.0F;
            float panelX = (this.width - PANEL_WIDTH) * 0.5F;
            float panelY = (this.height - panelHeight) * 0.5F;

            Hud.renderHudBackground(panelX, panelY, PANEL_WIDTH, panelHeight, 4.0F, 4.0F, 0.55F, ColorUtil.rgba(10, 12, 16, 222));
            Render2D.text(FontType.BOLD, LanguageManager.translate("Event"), panelX + PADDING, panelY + 5.0F, 8.0F, TITLE_COLOR);

            float rowY = panelY + HEADER_HEIGHT;
            if (panel.filters().isEmpty()) {
                Render2D.text(FontType.BOLD, LanguageManager.translate("No active events"), panelX + PADDING, rowY + 2.0F, 6.8F, EMPTY_COLOR);
            }

            for (CurrentEventsPanel.FilterEntry entry : panel.filters()) {
                float buttonX = panelX + PANEL_WIDTH - PADDING - BUTTON_WIDTH;
                renderEntry(graphics, panelX, rowY, entry, buttonX);
                rowY += ROW_HEIGHT;
            }
        } finally {
            Render2D.flush();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x();
        double mouseY = event.y();
        float panelHeight = HEADER_HEIGHT + (Math.max(1, panel.filters().size()) * ROW_HEIGHT) + 8.0F;
        float panelX = (this.width - PANEL_WIDTH) * 0.5F;
        float panelY = (this.height - panelHeight) * 0.5F;

        float fx = (float) mouseX;
        float fy = (float) mouseY;
        float buttonX = panelX + PANEL_WIDTH - PADDING - BUTTON_WIDTH;
        float rowY = panelY + HEADER_HEIGHT;

        for (CurrentEventsPanel.FilterEntry entry : panel.filters()) {
            if (inside(fx, fy, buttonX, rowY + 1.5F, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                panel.toggleEntry(entry);
                return true;
            }
            rowY += ROW_HEIGHT;
        }

        return inside(fx, fy, panelX, panelY, PANEL_WIDTH, panelHeight) || super.mouseClicked(event, doubled);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void renderEntry(GuiGraphics graphics, float panelX, float rowY, CurrentEventsPanel.FilterEntry entry, float buttonX) {
        Render2D.text(FontType.BOLD, entry.label, panelX + PADDING, rowY + 1.7F, 6.8F, entry.color);

        String text = entry.enabled ? "ON" : "OFF";
        float textWidth = Render2D.textWidth(FontType.BOLD, text, 6.8F);
        float textX = buttonX + (BUTTON_WIDTH - textWidth) * 0.5F;
        Render2D.text(FontType.BOLD, text, textX, rowY + 1.4F, 6.8F, entry.enabled ? BUTTON_TEXT_ENABLED_COLOR : BUTTON_TEXT_DISABLED_COLOR);
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
