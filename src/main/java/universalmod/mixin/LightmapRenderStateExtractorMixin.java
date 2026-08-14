package universalmod.mixin;

import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import universalmod.api.module.impl.misc.Fullbright;
import universalmod.api.module.impl.render.Ambience;

@Mixin(LightTexture.class)
public class LightmapRenderStateExtractorMixin {
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1), require = 0)
    private float universalmod$getBrightness(Double value) {
        float baseValue = value.floatValue();
        Fullbright fullbright = Fullbright.getInstance();
        if (fullbright != null && fullbright.isEnabled()) {
            return Math.max(baseValue, fullbright.getBrightnessValue() * 10.0f);
        }
        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.isEnabled()) {
            float brightness = ambience.getBrightnessValue();
            if (brightness >= 0.0f) {
                return Math.max(baseValue, brightness * 10.0f);
            }
            return Math.max(baseValue * (1.0f + brightness), 0.08f);
        }
        return baseValue;
    }
}
