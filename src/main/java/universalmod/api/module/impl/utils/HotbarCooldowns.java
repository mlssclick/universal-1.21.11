package universalmod.api.module.impl.utils;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;

public final class HotbarCooldowns extends Module {
    private static HotbarCooldowns instance;

    public HotbarCooldowns() {
        super("Cooldowns In Hotbar", "Shows cooldown progress on hotbar items.", ModuleCategory.UTILS);
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
