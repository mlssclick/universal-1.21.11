package universalmod.screens.pvp;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import universalmod.utils.network.ClientPlayNetworkHandlerHelper;
import universalmod.utils.network.SaveKtManager;

import java.awt.Color;

public class PvpLeaveConfirmScreen extends Screen {
    private int centerX;
    private int centerY;
    private final String command;

    public PvpLeaveConfirmScreen(String command) {
        super(Component.empty());
        this.command = command;
    }

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = height / 2;

        Button exit = Button.builder(Component.literal("Выйти").withStyle(ChatFormatting.RED), press -> {
            if (command != null) {
                Minecraft client = minecraft;
                if (client != null) {
                    if (client.getConnection() instanceof ClientPlayNetworkHandlerHelper helper) {
                        helper.sendFinalCommand(command);
                    }
                }
            } else {
                SaveKtManager.disc();
            }

            onClose();
        }).bounds(centerX - 90, centerY + 20, 80, 20).build();

        Button cancel = Button.builder(Component.literal("Отменить").withStyle(ChatFormatting.GREEN), press -> {
            onClose();
        }).bounds(centerX + 10, centerY + 20, 80, 20).build();

        addRenderableWidget(cancel);
        addRenderableWidget(exit);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        String message = "Вы уверены, что хотите выйти из сервера во время PvP?";
        int x = centerX - font.width(message) / 2;
        context.drawString(font, Component.literal(message), x, centerY - 10, Color.WHITE.getRGB());
    }

    @Override
    public void onClose() {
        SaveKtManager.reset();
        super.onClose();
    }
}
