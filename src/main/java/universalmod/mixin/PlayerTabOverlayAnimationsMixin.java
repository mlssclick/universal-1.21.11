package universalmod.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.Animations;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayAnimationsMixin {
    @Unique
    private boolean universalmod$tabPosePushed;

    @Inject(method = "setVisible", at = @At("HEAD"), require = 0)
    private void universalmod$animateTabVisibility(boolean visible, CallbackInfo ci) {
        if (!Animations.active(Animations.TAB)) {
            return;
        }
        Animations.getInstance().setTabVisible(visible);
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void universalmod$beginTabAnimation(GuiGraphics graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        universalmod$tabPosePushed = false;
        if (!Animations.active(Animations.TAB)) {
            return;
        }
        float progress = Animations.getInstance().tabProgress();
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, -200.0F * (1.0F - progress));
        universalmod$tabPosePushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void universalmod$endTabAnimation(GuiGraphics graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (universalmod$tabPosePushed) {
            graphics.pose().popMatrix();
            universalmod$tabPosePushed = false;
        }
    }
}
