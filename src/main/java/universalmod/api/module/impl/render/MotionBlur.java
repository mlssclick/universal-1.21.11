package universalmod.api.module.impl.render;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.utils.render.post.motionblur.MotionBlurRenderer;

public final class MotionBlur extends Module {
    private static MotionBlur instance;

    private final NumberSetting amount = register(new NumberSetting(
            "Amount",
            "Percentage of the previous frame blended into the current frame.",
            50.0,
            0.0,
            100.0,
            1.0
    ));

    public MotionBlur() {
        super(
                "Motion Blur",
                "Blends the previous rendered frame into the current frame.",
                ModuleCategory.RENDER
        );
        instance = this;
    }

    public static MotionBlur getInstance() {
        return instance;
    }

    public float getBlendFactor() {
        double value = amount.getValue();
        if (!Double.isFinite(value)) {
            return 0.0F;
        }
        return (float) (Math.min(Math.max(value, 0.0), 99.0) / 100.0);
    }

    public void renderFrame() {
        float blendFactor = getBlendFactor();
        if (blendFactor <= 0.0F) {
            MotionBlurRenderer.resetHistory();
            return;
        }
        MotionBlurRenderer.apply(blendFactor);
    }

    @Override
    protected void onEnable() {
        MotionBlurRenderer.recover();
    }

    @Override
    protected void onDisable() {
        MotionBlurRenderer.close();
    }
}
