package universalmod.api.module.impl.misc;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.utils.lang.LanguageCode;
import universalmod.utils.lang.LanguageManager;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;

import java.awt.Color;

public final class CustomTheme extends Module {
    private static volatile CustomTheme instance;
    private final SmoothAnimation hudLiquidGlassAnimation = new SmoothAnimation();
    private boolean hudLiquidGlassAnimationInitialized;
    private boolean lastHudLiquidGlassTarget;
    private final ModeSetting language = register(new ModeSetting(
            "Language", "Interface language.", LanguageCode.RU_RU.modeName(),
            LanguageCode.RU_RU.modeName(), LanguageCode.EN_US.modeName(), LanguageCode.UK_UA.modeName()));

    private final ModeSetting hudDesign = register(new ModeSetting(
            "HUD Design", "Visual style used by HUD panels.", "Default", "Default", "Liquid Glass", "Dark"));
    private final ModeSetting clickGuiClose = register(new ModeSetting(
            "Click gui close", "Animation used when ClickGUI closes.", "Default", "Default", "World"));

    private final ModeSetting hudStyle = register(new ModeSetting(
            "HUD Style", "Layout style for named HUD panels.", "Split", "Without Name", "Split", "Merge"));

    private final NumberSetting splitHeaderRounding = register(new NumberSetting(
            "Split Header Rounding", "Rounding of the separate HUD title panel in Split mode.",
            2.0, 0.0, 12.0, 0.5));
    private final BooleanSetting showSplitIcon = register(new BooleanSetting(
            "Show Icon", "Shows the HUD icon before the title in Split mode.", true));
    private final NumberSetting darkGradientStrength = register(new NumberSetting(
            "Gradient Strength", "Strength of the black-silver gradient in Dark design.",
            26.0, 0.0, 100.0, 1.0));
    private final NumberSetting darkOpacity = register(new NumberSetting(
            "Dark Opacity", "Opacity of Dark design panels.",
            100.0, 0.0, 100.0, 1.0));
    private final ColorSetting darkColor = register(new ColorSetting(
            "Dark Color", "Base color of Dark design. The silver gradient tone is generated automatically.",
            new Color(13, 15, 18, 255)));

    private final NumberSetting glassOpacity = register(new NumberSetting("Glass Opacity", "Liquid Glass overlay opacity.", 59.0, 0.0, 100.0, 1.0));
    private final NumberSetting glassStrength = register(new NumberSetting("Glass Strength", "Liquid Glass Fresnel power.", 25.0, 0.0, 100.0, 1.0));
    private final NumberSetting glassDistortion = register(new NumberSetting("Glass Distortion", "Liquid Glass refraction strength.", 0.10, -0.2, 0.2, 0.01));
    private final NumberSetting glassBlur = register(new NumberSetting("Glass Blur", "Kawase blur strength.", 0.8, 0.0, 8.0, 0.1));
    private final NumberSetting glassRounding = register(new NumberSetting("Glass Rounding", "Liquid Glass HUD rounding.", 8.0, 0.0, 8.0, 1.0));

    private final ColorSetting sliderColor = register(new ColorSetting("Slider Color", "Custom color for sliders in ClickGUI.", new Color(255, 255, 255, 255)));
    private final ColorSetting clickGuiTitleColor = register(new ColorSetting("ClickGUI Titles", "Custom color for titles and names in ClickGUI.", new Color(255, 255, 255, 255)));
    private final ColorSetting clickGuiBlurColor = register(new ColorSetting("ClickGUI Blur", "Custom blur color for ClickGUI.", new Color(0, 0, 0, 255)));
    private final ColorSetting hudBlurColor = register(new ColorSetting("HUD Blur", "Custom blur color for HUD panels.", new Color(0, 0, 0, 255)));
    private final ColorSetting hudTextColor = register(new ColorSetting("HUD Text", "Custom text color for HUD panels.", new Color(255, 255, 255, 255)));

    public CustomTheme() {
        super("Custom Theme", "Applies custom colors and design to ClickGUI and HUD.", ModuleCategory.MISC);
        instance = this;
        LanguageManager.setCurrent(language.getValue());
        language.addListener((changedSetting, oldValue, newValue) -> LanguageManager.setCurrent(newValue));
        glassOpacity.visibleWhen(this::isAnyLiquidGlassDesign);
        glassStrength.visibleWhen(this::isAnyLiquidGlassDesign);
        glassDistortion.visibleWhen(this::isAnyLiquidGlassDesign);
        glassBlur.visibleWhen(this::isAnyLiquidGlassDesign);
        glassRounding.visibleWhen(this::isAnyLiquidGlassDesign);
        splitHeaderRounding.visibleWhen(() -> hudStyle.is("Split"));
        showSplitIcon.visibleWhen(() -> hudStyle.is("Split"));
        darkGradientStrength.visibleWhen(this::isAnyDarkDesign);
        darkOpacity.visibleWhen(this::isAnyDarkDesign);
        darkColor.visibleWhen(this::isAnyDarkDesign);
        hudBlurColor.visibleWhen(() -> !isHudDarkDesign());
    }

    public static CustomTheme getInstance() { return instance; }
    public String getHudDesign() { return hudDesign.getValue(); }
    public String getClickGuiClose() { return clickGuiClose.getValue(); }
    public String getHudStyle() { return hudStyle.getValue(); }
    public float getSplitHeaderRounding() { return splitHeaderRounding.getFloat(); }
    public boolean isShowSplitIcon() { return showSplitIcon.getValue(); }
    public float getDarkGradientStrength() { return Math.max(0.0f, Math.min(1.0f, darkGradientStrength.getFloat() / 100.0f)); }
    public float getDarkOpacity() { return Math.max(0.0f, Math.min(1.0f, darkOpacity.getFloat() / 100.0f)); }
    public Color getDarkColor() { return darkColor.getValue(); }

    public boolean isClickGuiLiquidGlassDesign() { return false; }
    public boolean isHudLiquidGlassDesign() { return hudDesign.is("Liquid Glass"); }
    public boolean isClickGuiDarkDesign() { return false; }
    public boolean isHudDarkDesign() { return hudDesign.is("Dark"); }
    public boolean isClickGuiCloseWorld() { return clickGuiClose.is("World"); }
    private boolean isAnyLiquidGlassDesign() { return isHudLiquidGlassDesign(); }
    private boolean isAnyDarkDesign() { return isHudDarkDesign(); }

    public float getClickGuiLiquidGlassProgress() {
        return 0.0f;
    }

    public float getHudLiquidGlassProgress() {
        boolean target = isEnabled() && isHudLiquidGlassDesign();
        if (!hudLiquidGlassAnimationInitialized) {
            hudLiquidGlassAnimationInitialized = true;
            lastHudLiquidGlassTarget = target;
            hudLiquidGlassAnimation.set(0.0);
            if (target) hudLiquidGlassAnimation.run(1.0, 0.5, Easings.FIGMA_EASE_IN_OUT);
        } else if (target != lastHudLiquidGlassTarget) {
            lastHudLiquidGlassTarget = target;
            hudLiquidGlassAnimation.run(target ? 1.0 : 0.0, 0.5, Easings.FIGMA_EASE_IN_OUT);
        }
        hudLiquidGlassAnimation.update();
        return Math.max(0.0f, Math.min(1.0f, hudLiquidGlassAnimation.get()));
    }

    public float getGlassOpacity() { return glassOpacity.getFloat(); }
    public float getGlassStrength() { return glassStrength.getFloat(); }
    public float getGlassDistortion() { return glassDistortion.getFloat(); }
    public float getGlassBlur() { return glassBlur.getFloat(); }
    public float getGlassRounding() { return glassRounding.getFloat(); }
    public Color getSliderColor() { return sliderColor.getValue(); }
    public String getLanguage() { return language.getValue(); }
    public Color getClickGuiTitleColor() { return clickGuiTitleColor.getValue(); }
    public Color getClickGuiBlurColor() { return clickGuiBlurColor.getValue(); }
    public Color getHudBlurColor() { return hudBlurColor.getValue(); }
    public Color getHudTextColor() { return hudTextColor.getValue(); }
}
