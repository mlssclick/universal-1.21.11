package universalmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.api.module.impl.render.Animations;
import universalmod.api.module.impl.utils.FreeLook;
import universalmod.utils.figura.FiguraBridge;
import universalmod.utils.player.Angle;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$animatePerspective(float desiredDistance, CallbackInfoReturnable<Float> cir) {
        if (Animations.active(Animations.PERSPECTIVE)) {
            cir.setReturnValue(desiredDistance * Animations.getInstance().perspectiveProgress());
        }
    }

    @Inject(method = "setup", at = @At("HEAD"), require = 0)
    private void universalmod$sanitizeFiguraCamera(Level level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        FiguraBridge.sanitizeCameraOverrides();
    }

    @ModifyExpressionValue(
            method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F")
    )
    private float universalmod$freeLookYaw(float original) {
        Angle angle = FreeLook.getActiveAngle();
        return angle != null ? angle.getYaw() : original;
    }

    @ModifyExpressionValue(
            method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F")
    )
    private float universalmod$freeLookPitch(float original) {
        Angle angle = FreeLook.getActiveAngle();
        return angle != null ? angle.getPitch() : original;
    }
}
