package universalmod.mixin;

import universalmod.access.GuiRenderStateLayerAccessor;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin implements GuiRenderStateLayerAccessor {
    @Unique
    private int universalmod$layerSerial;

    @Override
    public int universalmod$getLayerSerial() {
        return universalmod$layerSerial;
    }

    @Inject(method = "nextStratum", at = @At("RETURN"))
    private void universalmod$trackNextStratum(CallbackInfo ci) {
        universalmod$layerSerial++;
    }

    @Inject(method = "up", at = @At("RETURN"))
    private void universalmod$trackUpLayer(CallbackInfo ci) {
        universalmod$layerSerial++;
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void universalmod$resetLayerSerial(CallbackInfo ci) {
        universalmod$layerSerial = 0;
    }
}
