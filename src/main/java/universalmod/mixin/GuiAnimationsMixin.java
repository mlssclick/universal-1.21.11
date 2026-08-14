package universalmod.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.DisplaySlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.Animations;

@Mixin(Gui.class)
public abstract class GuiAnimationsMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private PlayerTabOverlay tabList;

    @Unique
    private boolean universalmod$hotbarPosePushed;

    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), require = 0)
    private void universalmod$beginRaisedHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        universalmod$hotbarPosePushed = false;
        if (!Animations.active(Animations.RAISED_HOTBAR)) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, -16.0F * Animations.getInstance().hotbarLiftProgress());
        universalmod$hotbarPosePushed = true;
    }

    @Inject(method = "renderHotbarAndDecorations", at = @At("RETURN"), require = 0)
    private void universalmod$endRaisedHotbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (universalmod$hotbarPosePushed) {
            graphics.pose().popMatrix();
            universalmod$hotbarPosePushed = false;
        }
    }

    @ModifyArg(
            method = "renderItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 1
            ),
            index = 2,
            require = 0
    )
    private int universalmod$animateSelectedHotbarSlot(int x) {
        if (!Animations.active(Animations.HOTBAR_SLOT) || minecraft == null || minecraft.player == null) {
            return x;
        }
        int slot = minecraft.player.getInventory().getSelectedSlot();
        float animated = Animations.getInstance().selectedSlot(slot);
        return Math.round(x - slot * 20.0F + animated * 20.0F);
    }

    @Inject(method = "renderTabList", at = @At("TAIL"), require = 0)
    private void universalmod$renderClosingTab(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!Animations.active(Animations.TAB) || minecraft == null || minecraft.level == null
                || minecraft.options.keyPlayerList.isDown() || Animations.getInstance().tabProgress() <= 0.001F) {
            return;
        }
        var scoreboard = minecraft.level.getScoreboard();
        tabList.render(graphics, graphics.guiWidth(), scoreboard, scoreboard.getDisplayObjective(DisplaySlot.LIST));
    }
}
