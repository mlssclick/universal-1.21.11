package universalmod.mixin.figura;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.figuramc.figura.model.rendering.ImmediateFiguraRenderer", remap = false)
public abstract class FiguraImmediateRendererMixin {
    @Inject(method = "renderPivot", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void universalmod$skipFiguraPivotLines(@Coerce Object part, @Coerce Object customization, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderLineBox", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void universalmod$skipFiguraLineBoxes(@Coerce Object pose, @Coerce Object consumer,
                                                         double minX, double minY, double minZ,
                                                         double maxX, double maxY, double maxZ,
                                                         float red, float green, float blue, float alpha,
                                                         CallbackInfo ci) {
        ci.cancel();
    }
}
