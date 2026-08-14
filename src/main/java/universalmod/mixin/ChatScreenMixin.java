package universalmod.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.api.drag.core.ElementManager;
import universalmod.api.drag.impl.HudElementStyleMenu;
import universalmod.api.drag.impl.ScoreboardStyleMenu;
import universalmod.api.module.impl.render.Hud;
import universalmod.api.module.impl.render.Scoreboard;
import universalmod.api.module.impl.render.ViewModel;
import universalmod.utils.network.SaveKtManager;
import universalmod.utils.render.ui.Render2DCoordinateSpace;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(
            method = "keyPressed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/ChatScreen;handleChatInput(Ljava/lang/String;Z)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true,
            require = 0
    )
    private void universalmod$saveKtKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (SaveKtManager.openScreen) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$dragMouseClicked(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        MouseButtonEvent dragEvent = universalmod$toDragEvent(event);
        ScoreboardStyleMenu scoreboardMenu = ScoreboardStyleMenu.getInstance();
        if (scoreboardMenu.mouseClicked(dragEvent)) {
            cir.setReturnValue(true);
            return;
        }
        Hud hud = Hud.getInstance();
        if (hud != null && hud.handleMouseClicked(dragEvent, doubled)) {
            cir.setReturnValue(true);
            return;
        }
        if (dragEvent.button() == 1) {
            Scoreboard scoreboard = Scoreboard.getInstance();
            if (scoreboard != null && scoreboard.editorHit((float) dragEvent.x(), (float) dragEvent.y())) {
                HudElementStyleMenu.getInstance().close();
                scoreboardMenu.open(scoreboard, (float) dragEvent.x());
                cir.setReturnValue(true);
                return;
            }
        }
        if (ElementManager.getInstance().handleMouseClicked(dragEvent)) {
            cir.setReturnValue(true);
            return;
        }
        ViewModel viewModel = ViewModel.getInstance();
        if (viewModel != null && viewModel.handleChatMouseClicked(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void universalmod$renderHudHoverHint(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Hud hud = Hud.getInstance();
        if (hud != null) {
            hud.renderHudHoverHint(graphics, mouseX, mouseY);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void universalmod$cancelDragOnRemoved(CallbackInfo ci) {
        ElementManager.getInstance().cancelActiveElement();
        HudElementStyleMenu.getInstance().close();
        ScoreboardStyleMenu.getInstance().close();
        ViewModel viewModel = ViewModel.getInstance();
        if (viewModel != null) {
            viewModel.finishEditorDrag();
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), require = 0)
    private void universalmod$cancelDragOnClose(CallbackInfo ci) {
        ElementManager.getInstance().cancelActiveElement();
        HudElementStyleMenu.getInstance().close();
        ScoreboardStyleMenu.getInstance().close();
        ViewModel viewModel = ViewModel.getInstance();
        if (viewModel != null) {
            viewModel.finishEditorDrag();
        }
    }

    @Unique
    private MouseButtonEvent universalmod$toDragEvent(MouseButtonEvent event) {
        float scale = Render2DCoordinateSpace.guiIndependentScale();
        if (Math.abs(scale - 1.0F) <= 0.0001F) {
            return event;
        }

        MouseButtonInfo info = new MouseButtonInfo(event.button(), event.modifiers());
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, info);
    }
}
