package universalmod;

import net.fabricmc.api.ClientModInitializer;
import universalmod.api.module.impl.render.FireGlow;
import universalmod.manager.Manager;
import universalmod.utils.media.MusicTracker;
import universalmod.utils.network.SaveKtAttackTracker;
import universalmod.utils.render.post.motionblur.MotionBlurRenderer;

public class Engine implements ClientModInitializer {

    private final Manager manager = new Manager();

    @Override
    public void onInitializeClient() {
        manager.initClient();
        SaveKtAttackTracker.init();
    }

    public static void shutdown() {
        Manager activeManager = Manager.getInstance();
        if (activeManager != null && activeManager.getConfigManager() != null) {
            try {

                activeManager.getConfigManager().saveAll();
            } catch (RuntimeException exception) {
                System.err.println("[UniversalMod] Failed to save config during shutdown:");
                exception.printStackTrace(System.err);
            }
        }

        FireGlow glow = FireGlow.getInstance();
        if (glow != null) {
            glow.shutdown();
        }
        MotionBlurRenderer.close();
        MusicTracker.getInstance().shutdown();
    }
}
