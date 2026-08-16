package universalmod.utils.theme;

import universalmod.api.module.ModuleManager;
import universalmod.api.module.impl.misc.CustomTheme;
import universalmod.manager.Manager;
import universalmod.utils.render.color.ColorUtil;

import java.awt.Color;

/** Color helpers for the single current GUI and HUD visual style. */
public final class ThemeColors {
    private ThemeColors() {
    }

    public static int clickGuiTextColor(int alpha, float multiplier) {
        return themed(clickGuiTheme(), 255, 255, 255, alpha, multiplier);
    }

    public static int clickGuiMutedColor(int alpha, float multiplier) {
        return themed(scale(clickGuiTheme(), 0.72F), 175, 175, 175, alpha, multiplier);
    }

    public static int clickGuiBlurColor(int alpha, float multiplier) {
        return themed(clickGuiBlurTheme(), 0, 0, 0, alpha, multiplier);
    }

    public static int clickGuiSliderFillColor(float multiplier) {
        return themed(sliderTheme(), 255, 255, 255, 230, multiplier);
    }

    public static int clickGuiSliderKnobColor(float multiplier) {
        return themed(brighten(sliderTheme(), 0.18F), 215, 215, 215, 245, multiplier);
    }

    public static int clickGuiModuleTextColor(float multiplier, float enabledProgress, float bindProgress) {
        float shade = 0.73F + 0.27F * clamp01(enabledProgress);
        return themed(scale(clickGuiTheme(), shade), 255, 255, 255,
                Math.round(255.0F * (1.0F - bindProgress)), multiplier);
    }

    public static int clickGuiModuleBarColor(float multiplier, float enabledProgress, float bindProgress) {
        float shade = 0.73F + 0.27F * clamp01(enabledProgress);
        return themed(scale(clickGuiTheme(), shade), 255, 255, 255,
                Math.round(50.0F * (1.0F - bindProgress)), multiplier);
    }

    public static int hudBlurColor(int fallbackColor) {
        Color color = hudBlurTheme();
        int alpha = Math.round(ColorUtil.getAlpha(fallbackColor) * (color.getAlpha() / 255.0F));
        return ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static int hudTextColor(int alpha) {
        return themed(hudTextTheme(), 255, 255, 255, alpha, 1.0F);
    }

    public static int hudMutedColor(int alpha) {
        return themed(scale(hudTextTheme(), 0.74F), 185, 190, 196, alpha, 1.0F);
    }

    public static int hudAccentColor(int alpha) {
        return themed(brighten(hudTextTheme(), 0.12F), 127, 242, 255, alpha, 1.0F);
    }

    public static boolean isClickGuiCloseWorldEnabled() {
        CustomTheme theme = theme();
        return theme != null && theme.isEnabled() && theme.isClickGuiCloseWorld();
    }

    private static CustomTheme theme() {
        ModuleManager modules = Manager.getModules();
        return modules == null ? null : modules.getByType(CustomTheme.class).orElse(null);
    }

    private static Color sliderTheme() {
        CustomTheme theme = theme();
        return theme == null ? new Color(255, 255, 255, 255) : theme.getSliderColor();
    }

    private static Color clickGuiTheme() {
        CustomTheme theme = theme();
        return theme == null ? new Color(255, 255, 255, 255) : theme.getClickGuiTitleColor();
    }

    private static Color clickGuiBlurTheme() {
        CustomTheme theme = theme();
        return theme == null ? new Color(0, 0, 0, 255) : theme.getClickGuiBlurColor();
    }

    private static Color hudBlurTheme() {
        CustomTheme theme = theme();
        return theme == null ? new Color(0, 0, 0, 255) : theme.getHudBlurColor();
    }

    private static Color hudTextTheme() {
        CustomTheme theme = theme();
        return theme == null ? new Color(255, 255, 255, 255) : theme.getHudTextColor();
    }

    private static int themed(Color color, int red, int green, int blue, int alpha, float multiplier) {
        if (color == null) {
            return ColorUtil.rgba(red, green, blue, Math.round(alpha * multiplier));
        }
        int scaledAlpha = Math.round(alpha * (color.getAlpha() / 255.0F) * multiplier);
        return ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), scaledAlpha);
    }

    private static Color scale(Color color, float multiplier) {
        float value = clamp01(multiplier);
        return new Color(Math.round(color.getRed() * value), Math.round(color.getGreen() * value),
                Math.round(color.getBlue() * value), color.getAlpha());
    }

    private static Color brighten(Color color, float amount) {
        float value = clamp01(amount);
        return new Color(Math.round(color.getRed() + (255.0F - color.getRed()) * value),
                Math.round(color.getGreen() + (255.0F - color.getGreen()) * value),
                Math.round(color.getBlue() + (255.0F - color.getBlue()) * value), color.getAlpha());
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
