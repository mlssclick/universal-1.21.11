package universalmod.api.module.impl.render;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.NumberSetting;

public class AspectRatio extends Module {
    private static final float ANIMATION_SECONDS = 0.24F;
    private static final float ANIMATION_EPSILON = 0.0005F;
    private static AspectRatio instance;

    private final NumberSetting ratio = register(new NumberSetting("Ratio", "Screen aspect ratio.", 1.5, 1.0, 2.0, 0.01));
    private boolean animationInitialized;
    private float animationStart;
    private float animationTarget;
    private float animationValue;
    private long animationStartNanos;

    public AspectRatio() {
        super("Aspect Ratio", "Changes the world projection aspect ratio.", ModuleCategory.RENDER);
        instance = this;
    }

    public static float resolveAnimatedWidth(float currentWidth, float currentHeight) {
        AspectRatio module = instance;
        if (module == null || currentHeight <= 0.0F) {
            return currentWidth;
        }
        float vanillaRatio = currentWidth / currentHeight;
        float animatedRatio = module.getAnimatedRatio(vanillaRatio);
        if (!module.isEnabled() && Math.abs(animatedRatio - vanillaRatio) <= ANIMATION_EPSILON) {
            return currentWidth;
        }
        return currentHeight * animatedRatio;
    }

    public static float resolveRatio(float currentWidth, float currentHeight) {
        return resolveAnimatedWidth(currentWidth, currentHeight);
    }

    private synchronized float getAnimatedRatio(float vanillaRatio) {
        long now = System.nanoTime();
        if (!animationInitialized) {
            animationInitialized = true;
            animationStart = vanillaRatio;
            animationTarget = vanillaRatio;
            animationValue = vanillaRatio;
            animationStartNanos = now;
        }

        updateAnimation(now);
        float target = isEnabled() ? ratio.getFloat() : vanillaRatio;
        if (Math.abs(target - animationTarget) > ANIMATION_EPSILON) {
            animationStart = animationValue;
            animationTarget = target;
            animationStartNanos = now;
        }
        updateAnimation(now);
        return animationValue;
    }

    private void updateAnimation(long now) {
        float distance = Math.abs(animationTarget - animationStart);
        if (distance <= ANIMATION_EPSILON) {
            animationValue = animationTarget;
            return;
        }

        float elapsedSeconds = (now - animationStartNanos) / 1_000_000_000.0F;
        float progress = Math.max(0.0F, Math.min(1.0F, elapsedSeconds / ANIMATION_SECONDS));
        float eased = progress * progress * (3.0F - 2.0F * progress);
        animationValue = animationStart + (animationTarget - animationStart) * eased;
        if (progress >= 1.0F) {
            animationStart = animationTarget;
            animationValue = animationTarget;
        }
    }
}
