package universalmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.FireGlow;
import universalmod.utils.render.fireglow.LegacyFireOverlayRenderer;

@Mixin(SubmitNodeStorage.class)
public abstract class FireGlowSubmitNodeStorageMixin {
    @Inject(method = "submitFlame", at = @At("HEAD"), cancellable = true)
    private void universalmod$submitFireGlow(PoseStack matrices, EntityRenderState renderState, Quaternionf rotation, CallbackInfo ci) {
        if (!FireGlow.isActive() || !renderState.appearsGlowing()) {
            return;
        }

        LegacyFireOverlayRenderer.submit(matrices, (SubmitNodeCollector) this, renderState, rotation);
        ci.cancel();
    }
}
