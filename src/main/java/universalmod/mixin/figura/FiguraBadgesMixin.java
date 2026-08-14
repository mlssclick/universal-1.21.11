package universalmod.mixin.figura;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Pseudo
@Mixin(targets = "org.figuramc.figura.avatar.Badges", remap = false)
public abstract class FiguraBadgesMixin {
    @Inject(method = "fetchBadges", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void universalmod$hideFiguraBadges(UUID uuid, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(Component.empty());
    }
}
