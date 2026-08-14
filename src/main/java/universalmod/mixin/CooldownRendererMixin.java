package universalmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.utils.HotbarCooldowns;
import universalmod.utils.cooldown.CooldownStateStorage;
import universalmod.utils.serverhelper.ServerHelperItemInfo;

import java.util.Locale;

@Mixin(GuiGraphics.class)
public abstract class CooldownRendererMixin {
    private static final float TICKS_PER_SECOND = 20.0F;
    private static final float MAX_SCALE = 0.87F;
    private static final float MIN_SCALE = 0.62F;
    private static final float MAX_TEXT_WIDTH = 14.0F;

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"), require = 0)
    private void universalmod$renderCooldownText(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        if (!HotbarCooldowns.isActive() || stack == null || stack.isEmpty()) {
            return;
        }

        if (ServerHelperItemInfo.isCompassCooldownOverlayStack(stack)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        if (!minecraft.player.getCooldowns().isOnCooldown(stack)) {
            return;
        }

        Identifier group = minecraft.player.getCooldowns().getCooldownGroup(stack);
        int totalTicks = CooldownStateStorage.getDuration(group);
        if (totalTicks <= 0) {
            return;
        }

        float partialTicks = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float remainingPercent = Math.clamp(minecraft.player.getCooldowns().getCooldownPercent(stack, partialTicks), 0.0F, 1.0F);
        if (remainingPercent <= 0.0F) {
            return;
        }

        String cooldownText = formatCooldownSeconds(remainingPercent * totalTicks / TICKS_PER_SECOND);
        int textWidth = font.width(cooldownText);
        float scale = textWidth <= 0 ? MAX_SCALE : Math.clamp(MAX_TEXT_WIDTH / textWidth, MIN_SCALE, MAX_SCALE);
        GuiGraphics graphics = (GuiGraphics) (Object) this;

        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x + 1.0F, y + 1.0F);
            graphics.pose().scale(scale);
            graphics.drawString(font, cooldownText, 0, 0, getCooldownColor(remainingPercent), true);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static String formatCooldownSeconds(float seconds) {
        float clamped = Math.max(0.0F, seconds);
        return clamped >= 9.95F ? (int) Math.ceil(clamped) + "s" : String.format(Locale.US, "%.1fs", clamped);
    }

    private static int getCooldownColor(float percent) {
        float clamped = Math.clamp(percent, 0.0F, 1.0F);
        float scaled = clamped * 100.0F;
        if (scaled <= 50.0F) {
            return lerpColor(0xFF55FF55, 0xFFFFFF55, scaled / 50.0F);
        }
        return lerpColor(0xFFFFFF55, 0xFFFF5555, (scaled - 50.0F) / 50.0F);
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = Math.clamp(t, 0.0F, 1.0F);
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * clamped);
        int r = Math.round(((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * clamped);
        int g = Math.round(((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * clamped);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
