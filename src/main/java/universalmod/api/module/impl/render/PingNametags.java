package universalmod.api.module.impl.render;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;

public final class PingNametags extends Module {
    private static PingNametags instance;

    public PingNametags() {
        super("Ping Nametags", "Appends player ping to vanilla name tags.", ModuleCategory.RENDER);
        instance = this;
        setEnabled(true);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
