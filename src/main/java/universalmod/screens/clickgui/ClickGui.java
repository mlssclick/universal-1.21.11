package universalmod.screens.clickgui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import universalmod.screens.clickgui.impl.ClickGuiController;
import universalmod.utils.lang.LanguageManager;

import java.nio.file.Path;
import java.util.List;

public final class ClickGui extends Screen {
    public ClickGui() {
        super(Component.literal(LanguageManager.translate("ClickGUI")));
    }

    @Override
    protected void init() {
        ClickGuiController.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ClickGuiController.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (ClickGuiController.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        return ClickGuiController.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return ClickGuiController.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return ClickGuiController.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (ClickGuiController.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!ClickGuiController.onFilesDrop(paths)) {
            super.onFilesDrop(paths);
        }
    }

    @Override
    public void onClose() {
        ClickGuiController.startClosing();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void renderPanels(GuiGraphics graphics, int screenWidth, int screenHeight) {
        ClickGuiController.renderPanels(graphics, screenWidth, screenHeight);
    }

    public static void renderWorldPanels(GuiGraphics graphics, int screenWidth, int screenHeight) {
        ClickGuiController.renderWorldPanels(graphics, screenWidth, screenHeight);
    }

    public static boolean warmupText() {
        return ClickGuiController.warmupText();
    }

    public static boolean warmupGraphics(GuiGraphics graphics) {
        return ClickGuiController.warmupGraphics(graphics);
    }

    public static void startClosing() {
        ClickGuiController.startClosing();
    }

    public static void resetOverlayState() {
        ClickGuiController.resetOverlayState();
    }

    public static boolean shouldRenderOverlay() {
        return ClickGuiController.shouldRenderOverlay();
    }

    public static void validateState(Minecraft client) {
        ClickGuiController.validateState(client);
    }

    public static void tickMovementKeys() {
        ClickGuiController.tickMovementKeys();
    }
}
