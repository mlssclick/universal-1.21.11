package universalmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.drag.core.ElementManager;
import universalmod.api.drag.impl.ScoreboardStyleMenu;
import universalmod.api.events.impl.KeyEvent;
import universalmod.api.events.impl.MouseRotationEvent;
import universalmod.manager.Manager;
import universalmod.api.module.impl.render.Hud;
import universalmod.api.module.impl.render.ViewModel;
import universalmod.api.module.impl.misc.Zoom;
import universalmod.api.module.impl.utils.Friends;
import universalmod.screens.clickgui.ClickGui;
import universalmod.screens.clickgui.impl.ClickGuiController;
import universalmod.utils.render.ui.Render2DCoordinateSpace;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

@Mixin(MouseHandler.class)
public class MouseMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void universalmod$onMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        int button = buttonInfo.button();
        if (button != GLFW.GLFW_KEY_UNKNOWN && window == minecraft.getWindow().handle()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                    && action == GLFW.GLFW_PRESS
                    && Friends.handleMiddleClick(minecraft)) {
                ci.cancel();
                return;
            }
            if (minecraft.screen == null) {
                Manager.postEvent(new KeyEvent(minecraft.screen, InputConstants.Type.MOUSE, button, action));
            }
            if (action == GLFW.GLFW_RELEASE && minecraft.screen instanceof ChatScreen) {
                MouseButtonEvent event = new MouseButtonEvent(
                        minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                        minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()),
                        buttonInfo
                );
                ElementManager.getInstance().handleMouseReleased(universalmod$toDragEvent(event));
                ViewModel viewModel = ViewModel.getInstance();
                if (viewModel != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    viewModel.finishEditorDrag();
                }
            }
        }
    }

    @Inject(method = "onMove", at = @At("TAIL"), require = 0)
    private void universalmod$betterViewModelPointerMove(long window, double x, double y, CallbackInfo ci) {
        if (window != minecraft.getWindow().handle() || !(minecraft.screen instanceof ChatScreen)) {
            return;
        }

        ViewModel viewModel = ViewModel.getInstance();
        if (viewModel == null || !viewModel.isEditorDragging()) {
            return;
        }

        viewModel.handleChatMouseMoved(
                minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                minecraft.mouseHandler.getScaledYPos(minecraft.getWindow())
        );
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void universalmod$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == minecraft.getWindow().handle() && minecraft.screen instanceof ClickGui) {
            double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
            double mouseY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
            ClickGuiController.mouseScrolled(mouseX, mouseY, horizontal, vertical);
            ci.cancel();
            return;
        }
        if (window != minecraft.getWindow().handle() || !(minecraft.screen instanceof ChatScreen)) {
            return;
        }

        double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        double mouseY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        float coordinateScale = Math.max(0.0001F, Render2DCoordinateSpace.guiIndependentScale());
        if (ScoreboardStyleMenu.getInstance().mouseScrolled(mouseX / coordinateScale, mouseY / coordinateScale, vertical)) {
            ci.cancel();
            return;
        }
        ViewModel viewModel = ViewModel.getInstance();
        if (viewModel != null && viewModel.handleChatMouseScrolled(mouseX, mouseY, vertical)) {
            ci.cancel();
            return;
        }

        Hud hud = Hud.getInstance();
        if (hud != null && hud.handleMouseScrolled(
                minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()),
                vertical
        )) {
            ci.cancel();
            return;
        }

    }

    @Inject(method = "onScroll", at = @At("RETURN"))
    private void universalmod$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Zoom zoom = Zoom.getInstance();
        if (zoom != null) {
            zoom.onMouseScroll(vertical);
        }
    }

    @WrapWithCondition(
            method = "onScroll(JDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"),
            require = 0
    )
    private boolean universalmod$zoomBlockHotbarScroll(Inventory inventory, int slot) {
        Zoom zoom = Zoom.getInstance();
        return zoom == null || !zoom.isEnabled() || !zoom.isZoomHeld();
    }

    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),
            require = 0
    )
    private void universalmod$modifyMouseRotationInput(
            LocalPlayer player,
            double cursorDeltaX,
            double cursorDeltaY,
            Operation<Void> original
    ) {
        MouseRotationEvent event = Manager.postEvent(new MouseRotationEvent((float) cursorDeltaX, (float) cursorDeltaY));
        if (event.isCancelled()) {
            return;
        }
        original.call(player, (double) event.getCursorDeltaX(), (double) event.getCursorDeltaY());
    }

    @Unique
    private static MouseButtonEvent universalmod$toDragEvent(MouseButtonEvent event) {
        float scale = Render2DCoordinateSpace.guiIndependentScale();
        if (Math.abs(scale - 1.0F) <= 0.0001F) {
            return event;
        }

        MouseButtonInfo info = new MouseButtonInfo(event.button(), event.modifiers());
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, info);
    }
}
