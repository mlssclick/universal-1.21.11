package universalmod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.CustomHitBox;

@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$cancelVanillaHitboxGizmos(double x, double y, double z, DebugValueAccess debugValueAccess, Frustum frustum, float tickDelta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (CustomHitBox.shouldReplaceVanilla(client)) {
            CustomHitBox.emitCustomGizmos(client, frustum, tickDelta);
            ci.cancel();
        }
    }
}
