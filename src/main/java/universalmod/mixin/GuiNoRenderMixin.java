package universalmod.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.misc.CustomCrosshair;
import universalmod.api.module.impl.utils.CustomDonate;
import universalmod.api.module.impl.render.Hud;
import universalmod.api.module.impl.render.NoRender;
import universalmod.api.module.impl.render.Scoreboard;
import universalmod.utils.render.crosshair.CustomCrosshairRenderer;

@Mixin(Gui.class)
public abstract class GuiNoRenderMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 1)
    private void universalmod$scoreboard(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.isActive("Scoreboard") || Scoreboard.isActive()) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I", ordinal = 1),
            index = 0,
            require = 0
    )
    private FormattedText universalmod$replaceDonateScoreboardWidth(FormattedText text) {
        if (text instanceof Component component) {
            return CustomDonate.replaceScoreboardLine(component);
        }
        return text;
    }

    @ModifyArg(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", ordinal = 1),
            index = 1,
            require = 0
    )
    private Component universalmod$replaceDonateScoreboardDraw(Component text) {
        return CustomDonate.replaceScoreboardLine(text);
    }

    @Inject(method = "renderBossOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$bossOverlay(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.isActive("Bossbar") || Hud.shouldReplaceVanillaBossbar()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$vanillaEffects(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (NoRender.isActive("Vanilla Effects")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$customCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        CustomCrosshair module = CustomCrosshair.getInstance();
        if (!CustomCrosshairRenderer.shouldRender(minecraft, module)) {
            return;
        }
        CustomCrosshairRenderer.render(graphics, minecraft, module);
        ci.cancel();
    }
}
