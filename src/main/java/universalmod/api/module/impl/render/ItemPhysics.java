package universalmod.api.module.impl.render;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
public class ItemPhysics extends Module {
    private static ItemPhysics instance;

    public ItemPhysics() {
        super("Item Physics", "Adds ground physics to dropped items.", ModuleCategory.UTILS);
        instance = this;
    }

    public static ItemPhysics getInstance() {
        return instance;
    }
}
