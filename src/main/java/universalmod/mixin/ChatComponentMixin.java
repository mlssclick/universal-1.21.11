package universalmod.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.misc.BetterChat;
import universalmod.api.module.impl.render.Animations;
import universalmod.api.module.impl.misc.BetterChat.ProcessedChatMessage;
import universalmod.api.module.impl.render.Waypoints;
import universalmod.api.module.impl.utils.CustomDonate;
import universalmod.api.module.impl.utils.TotemCounter;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow
    public abstract void addMessage(Component message, MessageSignature signature, GuiMessageTag indicator);

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    private void refreshTrimmedMessages() {
    }

    @Unique
    private static final List<String> UNIVERSALMOD_ROUND_END_MESSAGES = List.of(
            "Winners:",
            "has won the round.",
            "has won the game!",
            "Winner: NONE!",
            "Match Complete"
    );

    @Unique
    private boolean universalmod$chatBypass;

    @Unique
    private boolean universalmod$smoothChatPushed;

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void universalmod$processBetterChatMessage(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
        if (universalmod$chatBypass) {
            return;
        }
        if (Waypoints.shouldHideChatMessage(message == null ? "" : message.getString())) {
            ci.cancel();
            return;
        }

        Component replaced = CustomDonate.replaceChatMessage(message);
        ProcessedChatMessage processed = BetterChat.prepareIncomingMessage(replaced);
        if (processed == null || processed.text() == null) {
            return;
        }

        boolean changedByDonate = replaced != message;
        boolean shouldReAdd = changedByDonate || processed.moduleActive() || processed.replacePrevious();
        if (!shouldReAdd) {
            return;
        }

        if (processed.replacePrevious()) {
            universalmod$removePreviousMessage();
        }

        universalmod$chatBypass = true;
        try {
            addMessage(processed.text(), signature, indicator);
        } finally {
            universalmod$chatBypass = false;
        }
        ci.cancel();
    }

    @Unique
    private void universalmod$removePreviousMessage() {
        if (allMessages.isEmpty()) {
            return;
        }
        GuiMessage removed = allMessages.remove(0);
        int addedTime = removed.addedTime();
        trimmedMessages.removeIf(line -> line.addedTime() == addedTime);
        refreshTrimmedMessages();
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"))
    private void universalmod$clearPopsOnRoundEnd(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
        String text = message == null ? "" : message.getString();
        for (String marker : UNIVERSALMOD_ROUND_END_MESSAGES) {
            if (text.contains(marker)) {
                TotemCounter.clearAll();
                return;
            }
        }
    }

    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
    private void universalmod$preventChatClear(boolean clearHistory, CallbackInfo ci) {
        if (BetterChat.shouldCancelChatClear()) {
            ci.cancel();
        }
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void universalmod$resetBetterChatState(boolean clearHistory, CallbackInfo ci) {
        BetterChat.resetSpamState();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("HEAD"))
    private void universalmod$beginSmoothChat(GuiGraphics graphics, Font font, int tickCount, int mouseX, int mouseY, boolean focused, boolean indicator, CallbackInfo ci) {
        if (Animations.active(Animations.MESSAGES)) {
            universalmod$smoothChatPushed = false;
            return;
        }
        float offset = BetterChat.getSmoothOffset();
        if (offset <= 0.01F) {
            universalmod$smoothChatPushed = false;
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, offset);
        universalmod$smoothChatPushed = true;
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("TAIL"))
    private void universalmod$endSmoothChat(GuiGraphics graphics, Font font, int tickCount, int mouseX, int mouseY, boolean focused, boolean indicator, CallbackInfo ci) {
        if (!universalmod$smoothChatPushed) {
            return;
        }
        graphics.pose().popMatrix();
        universalmod$smoothChatPushed = false;
    }
}
