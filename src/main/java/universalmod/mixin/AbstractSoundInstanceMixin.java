package universalmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import universalmod.api.module.impl.sounds.SoundReducer;

@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {
    @ModifyReturnValue(method = "getVolume", at = @At("RETURN"), require = 0)
    private float universalmod$reduceSelectedSounds(float original) {
        return SoundReducer.adjustVolume((SoundInstance) this, original);
    }
}
