package universalmod.api.module.impl.misc;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.utils.lang.LanguageCode;
import universalmod.utils.lang.LanguageManager;

import java.awt.Color;

/** Color settings for the single current ClickGUI and HUD visual style. */
public final class CustomTheme extends Module {
    private static volatile CustomTheme instance;

    private final ModeSetting language = register(new ModeSetting(
            "Language", "Interface language.", LanguageCode.RU_RU.modeName(),
            LanguageCode.RU_RU.modeName(), LanguageCode.EN_US.modeName(), LanguageCode.UK_UA.modeName()));
    private final ModeSetting clickGuiClose = register(new ModeSetting(
            "Click gui close", "Animation used when ClickGUI closes.", "Default", "Default", "World"));
    private final ColorSetting sliderColor = register(new ColorSetting(
            "Slider Color", "Custom color for sliders in ClickGUI.", new Color(255, 255, 255, 255)));
    private final ColorSetting clickGuiTitleColor = register(new ColorSetting(
            "ClickGUI Titles", "Custom color for titles and names in ClickGUI.", new Color(255, 255, 255, 255)));
    private final ColorSetting clickGuiBlurColor = register(new ColorSetting(
            "ClickGUI Blur", "Custom blur color for ClickGUI.", new Color(0, 0, 0, 255)));
    private final ColorSetting hudBlurColor = register(new ColorSetting(
            "HUD Blur", "Custom blur color for HUD panels.", new Color(0, 0, 0, 255)));
    private final ColorSetting hudTextColor = register(new ColorSetting(
            "HUD Text", "Custom text color for HUD panels.", new Color(255, 255, 255, 255)));

    public CustomTheme() {
        super("Custom Theme", "Applies custom colors to ClickGUI and HUD.", ModuleCategory.MISC);
        instance = this;
        LanguageManager.setCurrent(language.getValue());
        language.addListener((changedSetting, oldValue, newValue) -> LanguageManager.setCurrent(newValue));
    }

    public static CustomTheme getInstance() {
        return instance;
    }

    public boolean isClickGuiCloseWorld() {
        return clickGuiClose.is("World");
    }

    public Color getSliderColor() {
        return sliderColor.getValue();
    }

    public String getLanguage() {
        return language.getValue();
    }

    public Color getClickGuiTitleColor() {
        return clickGuiTitleColor.getValue();
    }

    public Color getClickGuiBlurColor() {
        return clickGuiBlurColor.getValue();
    }

    public Color getHudBlurColor() {
        return hudBlurColor.getValue();
    }

    public Color getHudTextColor() {
        return hudTextColor.getValue();
    }
}
