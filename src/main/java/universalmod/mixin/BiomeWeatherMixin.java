package universalmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.api.module.impl.render.Ambience;
import universalmod.api.module.impl.render.NoRender;

@Mixin(Biome.class)
public abstract class BiomeWeatherMixin {
    @Inject(method = "hasPrecipitation", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$hasPrecipitation(CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.isActive("Weather")) {
            cir.setReturnValue(false);
            return;
        }

        Ambience ambience = Ambience.getInstance();
        Biome.Precipitation precipitation = ambience != null ? ambience.getForcedPrecipitation() : null;
        if (precipitation != null) {
            cir.setReturnValue(precipitation != Biome.Precipitation.NONE);
        }
    }

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$getPrecipitationAt(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (NoRender.isActive("Weather")) {
            cir.setReturnValue(Biome.Precipitation.NONE);
            return;
        }

        Ambience ambience = Ambience.getInstance();
        Biome.Precipitation precipitation = ambience != null ? ambience.getForcedPrecipitation() : null;
        if (precipitation != null) {
            cir.setReturnValue(precipitation);
        }
    }

    @Inject(method = "coldEnoughToSnow", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$coldEnoughToSnow(BlockPos pos, int seaLevel, CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.isActive("Weather")) {
            cir.setReturnValue(false);
            return;
        }

        Ambience ambience = Ambience.getInstance();
        Biome.Precipitation precipitation = ambience != null ? ambience.getForcedPrecipitation() : null;
        if (precipitation == Biome.Precipitation.SNOW) {
            cir.setReturnValue(true);
        } else if (precipitation == Biome.Precipitation.RAIN || precipitation == Biome.Precipitation.NONE) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "warmEnoughToRain", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$warmEnoughToRain(BlockPos pos, int seaLevel, CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.isActive("Weather")) {
            cir.setReturnValue(false);
            return;
        }

        Ambience ambience = Ambience.getInstance();
        Biome.Precipitation precipitation = ambience != null ? ambience.getForcedPrecipitation() : null;
        if (precipitation == Biome.Precipitation.SNOW) {
            cir.setReturnValue(false);
        } else if (precipitation == Biome.Precipitation.RAIN) {
            cir.setReturnValue(true);
        }
    }
}
