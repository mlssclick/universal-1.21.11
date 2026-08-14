package universalmod.api.module;

import universalmod.utils.lang.LanguageManager;

public enum ModuleCategory {
    RENDER("Render"),
    UTILS("Utils"),
    MISC("Misc"),
    CONFIGS("Configs");

    private final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return LanguageManager.translate(displayName);
    }
}
