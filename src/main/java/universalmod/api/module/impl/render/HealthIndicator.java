package universalmod.api.module.impl.render;

import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.WorldRenderEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.utils.render.world.HealthIndicatorOverlay;

public final class HealthIndicator extends Module {
    private final HealthIndicatorOverlay overlay = new HealthIndicatorOverlay();

    public HealthIndicator() {
        super("Health Indicator", "Shows vanilla heart health above players.", ModuleCategory.RENDER);
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        overlay.render(event, mc);
    }
}
