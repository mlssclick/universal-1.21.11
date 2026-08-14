package universalmod.screens.clickgui.impl.module;

import universalmod.api.module.Module;
import universalmod.api.settings.Setting;
import universalmod.api.settings.impl.BindSetting;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ButtonSetting;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.MultiModeSetting;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.api.settings.impl.StringSetting;
import universalmod.screens.clickgui.impl.options.BindOption;
import universalmod.screens.clickgui.impl.options.BooleanOption;
import universalmod.screens.clickgui.impl.options.ButtonOption;
import universalmod.screens.clickgui.impl.options.ClickGuiOption;
import universalmod.screens.clickgui.impl.options.ColorOption;
import universalmod.screens.clickgui.impl.options.MultiOption;
import universalmod.screens.clickgui.impl.options.SingleOption;
import universalmod.screens.clickgui.impl.options.SliderOption;
import universalmod.screens.clickgui.impl.options.TextOption;

import java.util.ArrayList;
import java.util.List;

public final class ModuleOptionFactory {
    private ModuleOptionFactory() {
    }

    public static ModuleOption create(Module module) {
        // Keep module cards cheap. The setting widgets are built only when a card is expanded.
        return new ModuleOption(module);
    }

    static boolean hasSupportedSettings(Module module) {
        if (module == null) {
            return false;
        }
        for (Setting<?> setting : module.getSettings()) {
            if (isSupported(setting)) {
                return true;
            }
        }
        return false;
    }

    static List<ClickGuiOption> createSettings(Module module) {
        List<ClickGuiOption> options = new ArrayList<>();
        List<ClickGuiOption> buttonOptions = new ArrayList<>();

        for (Setting<?> setting : module.getSettings()) {
            ClickGuiOption option = createSettingOption(setting);
            if (option == null) {
                continue;
            }
            option.setSetting(setting);
            if (setting instanceof ButtonSetting) {
                buttonOptions.add(option);
            } else {
                options.add(option);
            }
        }

        options.addAll(buttonOptions);
        return List.copyOf(options);
    }

    private static boolean isSupported(Setting<?> setting) {
        return setting instanceof ModeSetting
                || setting instanceof MultiModeSetting
                || setting instanceof NumberSetting
                || setting instanceof BooleanSetting
                || setting instanceof StringSetting
                || setting instanceof ColorSetting
                || setting instanceof BindSetting
                || setting instanceof ButtonSetting;
    }

    private static ClickGuiOption createSettingOption(Setting<?> setting) {
        if (setting instanceof ModeSetting modeSetting) {
            return new SingleOption(modeSetting);
        }
        if (setting instanceof MultiModeSetting multiModeSetting) {
            return new MultiOption(multiModeSetting);
        }
        if (setting instanceof NumberSetting numberSetting) {
            return new SliderOption(numberSetting, "");
        }
        if (setting instanceof BooleanSetting booleanSetting) {
            return new BooleanOption(booleanSetting);
        }
        if (setting instanceof StringSetting stringSetting) {
            return new TextOption(stringSetting);
        }
        if (setting instanceof ColorSetting colorSetting) {
            return new ColorOption(colorSetting);
        }
        if (setting instanceof BindSetting bindSetting) {
            return new BindOption(bindSetting);
        }
        if (setting instanceof ButtonSetting buttonSetting) {
            return new ButtonOption(buttonSetting, "Editor");
        }
        return null;
    }
}
