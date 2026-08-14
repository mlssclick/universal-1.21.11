package universalmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.misc.ShadersButton;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.theme.ThemeColors;

@Mixin(AbstractButton.class)
public abstract class GameMenuButtonMixin {
    @Unique
    private static final float UNIVERSALMOD_ANIMATION_SPEED = 0.28f;
    @Unique
    private float universalmod$hoverAnimation;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void universalmod$renderGameMenuButton(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!ShadersButton.isActive() || client == null || !(client.screen instanceof PauseScreen) || graphics == null) {
            return;
        }

        AbstractButton button = (AbstractButton) (Object) this;
        boolean hovered = button.isHovered() && button.active;
        universalmod$hoverAnimation = approach(universalmod$hoverAnimation, hovered ? 1.0f : 0.0f, UNIVERSALMOD_ANIMATION_SPEED);
        float hover = cubicOut(universalmod$hoverAnimation);

        float x = button.getX();
        float y = button.getY();
        float width = button.getWidth();
        float height = button.getHeight();
        float radius = Math.min(5.0f, height * 0.28f);
        int theme = themeColor();

        Render2D.beginFrame(graphics);
        try {
            int shadowAlpha = button.active ? Math.round(80.0f + 35.0f * hover) : 45;
            Render2D.blur(x, y, width, height, radius, 8.0f, 1.0f, ColorUtil.rgba(0, 0, 0, shadowAlpha));

            int background = ColorUtil.interpolateColor(
                    ColorUtil.rgba(16, 16, 21, button.active ? 205 : 145),
                    ColorUtil.rgba(27, 27, 35, 235),
                    hover
            );
            Render2D.rect(x, y, width, height, radius, background);

            int border = button.active
                    ? ColorUtil.multAlpha(theme, 0.28f + hover * 0.62f)
                    : ColorUtil.rgba(95, 95, 105, 70);
            Render2D.outline(x, y, width, height, radius, 0.45f, border, border, border, border);

            String label = button.getMessage().getString();
            float textSize = 7.5f;
            int textColor = button.active
                    ? ColorUtil.interpolateColor(
                    ColorUtil.rgba(205, 205, 214, 235),
                    ColorUtil.rgba(255, 255, 255, 255),
                    hover)
                    : ColorUtil.rgba(135, 135, 145, 170);
            float textX = x + (width - Render2D.textWidth(FontType.SEMIBOLD, label, textSize)) * 0.5f;
            float textY = y + (height - Render2D.textHeight(FontType.SEMIBOLD, label, textSize)) * 0.5f + 0.5f;
            Render2D.text(FontType.SEMIBOLD, label, textX, textY, textSize, textColor);
        } finally {
            Render2D.flush();
        }

        ci.cancel();
    }

    @Unique
    private static float approach(float current, float target, float speed) {
        return current + (target - current) * Math.max(0.0f, Math.min(1.0f, speed));
    }

    @Unique
    private static float cubicOut(float value) {
        float t = Math.max(0.0f, Math.min(1.0f, value)) - 1.0f;
        return t * t * t + 1.0f;
    }

    @Unique
    private static int themeColor() {
        return ColorUtil.withAlpha(ThemeColors.clickGuiSliderFillColor(255), 255);
    }
}
