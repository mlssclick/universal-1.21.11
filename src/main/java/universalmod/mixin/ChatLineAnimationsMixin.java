package universalmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import universalmod.api.module.impl.render.Animations;
import universalmod.mixin.accessor.ChatGraphicsAccessInvoker;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public abstract class ChatLineAnimationsMixin {
    @WrapOperation(
            method = "accept(Lnet/minecraft/client/GuiMessage$Line;IF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"
            ),
            require = 0
    )
    private boolean universalmod$animateMessage(
            @Coerce Object graphics,
            int lineIndex,
            float vanillaAlpha,
            FormattedCharSequence text,
            Operation<Boolean> original,
            GuiMessage.Line line,
            int originalLineIndex,
            float originalAlpha
    ) {
        if (!Animations.active(Animations.MESSAGES) || line == null
                || !(graphics instanceof ChatGraphicsAccessInvoker invoker)) {
            return original.call(graphics, lineIndex, vanillaAlpha, text);
        }

        float progress = universalmod$messageProgress(line);
        float offset = -(1.0F - progress) * 8.0F;
        invoker.universalmod$updatePose(matrix -> matrix.translate(offset, 0.0F));
        try {
            return original.call(graphics, lineIndex, vanillaAlpha * progress, text);
        } finally {
            invoker.universalmod$updatePose(matrix -> matrix.translate(-offset, 0.0F));
        }
    }

    @Unique
    private static float universalmod$messageProgress(GuiMessage.Line line) {
        Minecraft client = Minecraft.getInstance();
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double raw = Math.clamp(((client.gui.getGuiTicks() - line.addedTime()) + partialTick) / 9.0D, 0.0D, 1.0D);
        return raw >= 1.0D ? 1.0F : (float) (1.0D - Math.pow(2.0D, -10.0D * raw));
    }
}
