package universalmod.mixin;

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import universalmod.api.module.impl.render.HitColor;
import universalmod.utils.render.hitcolor.HitColorArmorRenderContext;

@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentRendererHitColorMixin {
    @Redirect(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"
            ),
            require = 0
    )
    private RenderType universalmod$useEntityLayerForHitColorArmor(Identifier texture) {
        return shouldApplyArmorHitColor()
                ? RenderTypes.entityCutoutNoCull(texture)
                : RenderTypes.armorCutoutNoCull(texture);
    }

    @ModifyArg(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
            ),
            index = 5,
            require = 0
    )
    private int universalmod$applyHitColorOverlayToArmor(int overlay) {
        return shouldApplyArmorHitColor() ? 0x00030000 : overlay;
    }

    private static boolean shouldApplyArmorHitColor() {
        if (!HitColorArmorRenderContext.isActive()) {
            return false;
        }
        HitColor hitColor = HitColor.getInstance();
        return hitColor != null && hitColor.isEnabled() && hitColor.shouldTintArmor();
    }
}
