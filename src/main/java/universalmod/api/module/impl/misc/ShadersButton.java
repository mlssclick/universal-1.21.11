package universalmod.api.module.impl.misc;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;

public final class ShadersButton extends Module {
    private static ShadersButton instance;

    public ShadersButton() {
        super("Shaders Button", "Renders vanilla menu buttons with UniversalMod shader styling.", ModuleCategory.MISC);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
