package universalmod.api.settings.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import universalmod.api.settings.Setting;
import universalmod.api.settings.SettingType;
import universalmod.utils.lang.LanguageManager;

import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;
    private final boolean renderDescription;
    private final boolean translateModes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        this(name, description, true, true, defaultValue, modes);
    }

    public ModeSetting(String name, String description, boolean renderDescription, String defaultValue, String... modes) {
        this(name, description, renderDescription, true, defaultValue, modes);
    }

    public ModeSetting(String name, String description, boolean renderDescription, boolean translateModes, String defaultValue, String... modes) {
        super(name, description, defaultValue, SettingType.MODE);
        this.modes = List.of(modes);
        this.renderDescription = renderDescription;
        this.translateModes = translateModes;
        setValue(defaultValue);
    }

    public List<String> getModes() {
        return modes;
    }

    public List<String> getDisplayModes() {
        return translateModes ? modes.stream().map(LanguageManager::translate).toList() : modes;
    }

    public String getDisplayMode(String mode) {
        return translateModes ? LanguageManager.translate(mode) : mode;
    }

    public String getDisplayValue() {
        return getDisplayMode(getValue());
    }

    public boolean shouldRenderDescription() {
        return renderDescription;
    }

    public boolean is(String mode) {
        return getValue().equals(normalize(mode));
    }

    public boolean isSelected(String mode) {
        return is(mode);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            setValue(element.getAsString());
        }
    }

    @Override
    protected String normalize(String value) {
        if (value == null || modes.isEmpty()) {
            return modes.isEmpty() ? "" : modes.getFirst();
        }
        for (String mode : modes) {
            if (mode.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return modes.getFirst();
    }
}
