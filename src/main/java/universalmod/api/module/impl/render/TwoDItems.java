package universalmod.api.module.impl.render;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;

public final class TwoDItems extends Module {
    private static TwoDItems instance;

    public TwoDItems() {
        super("2D Items", "Makes dropped item entities render flatter in the world.", ModuleCategory.UTILS);
        instance = this;
    }

    public static boolean isFeatureEnabled() {
        return instance != null && instance.isEnabled();
    }

    public static boolean shouldCastShadows() {
        return isFeatureEnabled();
    }

    public static boolean shouldRenderSidesOfItems() {
        return isFeatureEnabled();
    }

    public static boolean shouldAffect3DModels() {
        return isFeatureEnabled();
    }
}
