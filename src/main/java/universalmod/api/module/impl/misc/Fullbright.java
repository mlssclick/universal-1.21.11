package universalmod.api.module.impl.misc;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;

public final class Fullbright extends Module {
    private static Fullbright instance;

    public Fullbright() {
        super("Fullbright", "Makes the world much brighter than ambience brightness.", ModuleCategory.MISC);
        instance = this;
    }

    public static Fullbright getInstance() {
        return instance;
    }

    public float getBrightnessValue() {
        return 100.0f;
    }
}
