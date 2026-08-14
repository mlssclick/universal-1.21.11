package universalmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.HitColor;
import universalmod.utils.figura.FiguraBridge;
import universalmod.utils.render.hitcolor.HitColorArmorRenderContext;

@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorFeatureRendererHitColorMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$captureHitColorArmorState(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            HumanoidRenderState state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci
    ) {
        if (FiguraBridge.shouldHideVanillaArmor(state)) {
            HitColorArmorRenderContext.clear();
            ci.cancel();
            return;
        }
        HitColor hitColor = HitColor.getInstance();
        HitColorArmorRenderContext.setActive(hitColor != null
                && hitColor.isEnabled()
                && hitColor.shouldTintArmor()
                && state != null
                && state.hasRedOverlay);
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("TAIL"), require = 0)
    private void universalmod$clearHitColorArmorState(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            HumanoidRenderState state,
            float limbAngle,
            float limbDistance,
            CallbackInfo ci
    ) {
        HitColorArmorRenderContext.clear();
    }
}
