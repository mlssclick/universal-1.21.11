package universalmod.api.module.impl.utils;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.utils.network.SaveKtManager;

public final class SaveKtLeave extends Module {
    private static SaveKtLeave instance;

    public SaveKtLeave() {
        super("Save Kt Leave", "Protects against leaving the server while PvP is active.", ModuleCategory.UTILS);
        instance = this;
    }

    public static SaveKtLeave getInstance() {
        return instance;
    }

    @Override
    protected void onDisable() {
        SaveKtManager.reset();
    }
}
