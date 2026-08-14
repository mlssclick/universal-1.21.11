package universalmod.utils.theme;

import universalmod.api.module.ModuleManager;
import universalmod.api.module.impl.misc.CustomTheme;
import universalmod.manager.Manager;
import universalmod.utils.render.color.ColorUtil;

import java.awt.Color;

public final class ThemeColors {
    private ThemeColors() {
    }

    public static int clickGuiTextColor(int alpha, float alphaMultiplier) {
        return themed(clickGuiTheme(), 255, 255, 255, alpha, alphaMultiplier);
    }

    public static int clickGuiMutedColor(int alpha, float alphaMultiplier) {
        if (!enabled()) {
            return ColorUtil.rgba(175, 175, 175, Math.round(alpha * alphaMultiplier));
        }
        return themed(scale(clickGuiTheme(), 0.72f), 175, 175, 175, alpha, alphaMultiplier);
    }

    public static int clickGuiBlurColor(int alpha, float alphaMultiplier) {
        return themed(clickGuiBlurTheme(), 0, 0, 0, alpha, alphaMultiplier);
    }

    public static int clickGuiSliderFillColor(float alphaMultiplier) {
        return themed(sliderTheme(), 255, 255, 255, 230, alphaMultiplier);
    }

    public static int clickGuiSliderKnobColor(float alphaMultiplier) {
        if (!enabled()) {
            return ColorUtil.rgba(215, 215, 215, Math.round(245.0f * alphaMultiplier));
        }
        return themed(brighten(sliderTheme(), 0.18f), 215, 215, 215, 245, alphaMultiplier);
    }

    public static int clickGuiModuleTextColor(float alphaMultiplier, float enabledProgress, float bindProgress) {
        if (!enabled()) {
            int moduleColor = Math.round(185.0f + 70.0f * enabledProgress);
            return ColorUtil.rgba(moduleColor, moduleColor, moduleColor, Math.round(255.0f * (1.0f - bindProgress) * alphaMultiplier));
        }
        float shade = 0.73f + 0.27f * clamp01(enabledProgress);
        return themed(scale(clickGuiTheme(), shade), 255, 255, 255, Math.round(255.0f * (1.0f - bindProgress)), alphaMultiplier);
    }

    public static int clickGuiModuleBarColor(float alphaMultiplier, float enabledProgress, float bindProgress) {
        if (!enabled()) {
            int moduleColor = Math.round(185.0f + 70.0f * enabledProgress);
            return ColorUtil.rgba(moduleColor, moduleColor, moduleColor, Math.round(50.0f * (1.0f - bindProgress) * alphaMultiplier));
        }
        float shade = 0.73f + 0.27f * clamp01(enabledProgress);
        return themed(scale(clickGuiTheme(), shade), 255, 255, 255, Math.round(50.0f * (1.0f - bindProgress)), alphaMultiplier);
    }

    public static int hudBlurColor(int fallbackColor) {
        if (!enabled()) {
            return fallbackColor;
        }
        Color color = hudBlurTheme();
        int alpha = Math.round(ColorUtil.getAlpha(fallbackColor) * (color.getAlpha() / 255.0f));
        return ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static int hudTextColor(int alpha) {
        return themed(hudTextTheme(), 255, 255, 255, alpha, 1.0f);
    }

    public static int hudMutedColor(int alpha) {
        if (!enabled()) {
            return ColorUtil.rgba(185, 190, 196, alpha);
        }
        return themed(scale(hudTextTheme(), 0.74f), 185, 190, 196, alpha, 1.0f);
    }

    public static int hudAccentColor(int alpha) {
        if (!enabled()) {
            return ColorUtil.rgba(127, 242, 255, alpha);
        }
        return themed(brighten(hudTextTheme(), 0.12f), 127, 242, 255, alpha, 1.0f);
    }

    public static boolean isClickGuiLiquidGlassDesignEnabled() {
        CustomTheme theme = theme();
        return theme != null && theme.isEnabled() && theme.isClickGuiLiquidGlassDesign();
    }

    public static boolean isHudLiquidGlassDesignEnabled() {
        CustomTheme theme = theme();
        return theme != null && theme.isEnabled() && HudStyleOverrides.DESIGN_LIQUID_GLASS.equalsIgnoreCase(resolvedHudDesign());
    }

    public static boolean isClickGuiDarkDesignEnabled() {
        CustomTheme theme = theme();
        return theme != null && theme.isEnabled() && theme.isClickGuiDarkDesign();
    }

    public static boolean isClickGuiCloseWorldEnabled() {
        CustomTheme theme = theme();
        return theme != null && theme.isEnabled() && theme.isClickGuiCloseWorld();
    }

    public static boolean isHudDarkDesignEnabled() {
        CustomTheme theme = theme();
        return theme != null && theme.isEnabled() && HudStyleOverrides.DESIGN_DARK.equalsIgnoreCase(resolvedHudDesign());
    }

    public static float clickGuiLiquidGlassProgress() {
        CustomTheme theme = theme();
        return theme == null ? 0.0f : theme.getClickGuiLiquidGlassProgress();
    }

    public static float hudLiquidGlassProgress() {
        CustomTheme theme = theme();
        if (theme == null) {
            return 0.0f;
        }
        String elementId = HudStyleContext.currentElementId();
        if (elementId != null && HudStyleOverrides.getInstance().hasCustomPreset(elementId)) {
            return HudStyleOverrides.DESIGN_LIQUID_GLASS.equalsIgnoreCase(HudStyleOverrides.getInstance().resolveHudDesign(elementId)) ? 1.0f : 0.0f;
        }
        return theme.getHudLiquidGlassProgress();
    }

    public static float clickGuiMinimalismProgress() { return 1.0f - clickGuiLiquidGlassProgress(); }
    public static float hudMinimalismProgress() { return 1.0f - hudLiquidGlassProgress(); }

    public static float glassOpacity() { CustomTheme t = theme(); return t == null ? 20.0f : t.getGlassOpacity(); }
    public static float glassStrength() { CustomTheme t = theme(); return t == null ? 25.0f : t.getGlassStrength(); }
    public static float glassDistortion() { CustomTheme t = theme(); return t == null ? 0.08f : t.getGlassDistortion(); }
    public static float glassBlur() { CustomTheme t = theme(); return t == null ? 0.5f : t.getGlassBlur(); }
    public static float glassRounding() { CustomTheme t = theme(); return t == null ? 7.0f : t.getGlassRounding(); }

    public static float splitHeaderRounding() { CustomTheme t = theme(); return t == null ? 4.0f : t.getSplitHeaderRounding(); }
    public static boolean showSplitIcon() { CustomTheme t = theme(); return t != null && t.isEnabled() && t.isShowSplitIcon(); }
    public static float darkGradientStrength() {
        CustomTheme t = theme();
        return t == null ? 0.70f : t.getDarkGradientStrength();
    }

    public static float darkOpacity() {
        CustomTheme t = theme();
        return t == null ? 1.0f : t.getDarkOpacity();
    }

    public static int darkColor() {
        CustomTheme t = theme();
        Color color = t == null ? new Color(13, 15, 18, 255) : t.getDarkColor();
        return ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), 255);
    }

    public static String hudStyle() {
        CustomTheme theme = theme();
        String fallback = theme == null || !theme.isEnabled() ? "Split" : theme.getHudStyle();
        return HudStyleOverrides.getInstance().resolveHudStyle(HudStyleContext.currentElementId() == null ? "" : HudStyleContext.currentElementId());
    }

    public static boolean isHudWithoutName() {
        return "Without Name".equalsIgnoreCase(hudStyle());
    }

    public static boolean isHudSplit() {
        return "Split".equalsIgnoreCase(hudStyle());
    }

    public static boolean isHudMerge() {
        return "Merge".equalsIgnoreCase(hudStyle());
    }

    private static String resolvedHudDesign() {
        CustomTheme theme = theme();
        String fallback = theme == null || !theme.isEnabled() ? HudStyleOverrides.DESIGN_DEFAULT : theme.getHudDesign();
        String elementId = HudStyleContext.currentElementId();
        if (elementId == null || elementId.isBlank()) {
            return fallback;
        }
        return HudStyleOverrides.getInstance().resolveHudDesign(elementId);
    }

    private static boolean enabled() {
        CustomTheme theme = theme();
        return theme != null && theme.isEnabled();
    }

    private static CustomTheme theme() {
        ModuleManager modules = Manager.getModules();
        if (modules == null) {
            return null;
        }
        return modules.getByType(CustomTheme.class).orElse(null);
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

    private static int themed(Color themeColor, int defaultRed, int defaultGreen, int defaultBlue, int alpha, float alphaMultiplier) {
        if (!enabled()) {
            return ColorUtil.rgba(defaultRed, defaultGreen, defaultBlue, Math.round(alpha * alphaMultiplier));
        }
        int scaledAlpha = Math.round(alpha * (themeColor.getAlpha() / 255.0f) * alphaMultiplier);
        return ColorUtil.rgba(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), scaledAlpha);
    }

    private static Color scale(Color color, float multiplier) {
        float factor = clamp01(multiplier);
        return new Color(
                Math.round(color.getRed() * factor),
                Math.round(color.getGreen() * factor),
                Math.round(color.getBlue() * factor),
                color.getAlpha()
        );
    }

    private static Color brighten(Color color, float amount) {
        float factor = clamp01(amount);
        return new Color(
                Math.round(color.getRed() + (255.0f - color.getRed()) * factor),
                Math.round(color.getGreen() + (255.0f - color.getGreen()) * factor),
                Math.round(color.getBlue() + (255.0f - color.getBlue()) * factor),
                color.getAlpha()
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
