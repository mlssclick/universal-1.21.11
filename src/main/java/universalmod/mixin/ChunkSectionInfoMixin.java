package universalmod.mixin;

import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import universalmod.api.module.impl.render.Ambience;
import universalmod.api.module.impl.render.NoRender;

@Mixin(DynamicUniforms.ChunkSectionInfo.class)
public abstract class ChunkSectionInfoMixin {
    @Redirect(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;"
            )
    )
    private Std140Builder universalmod$removeExtraChunkFog(
            Std140Builder builder,
            float vanillaVisibility
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft != null && minecraft.gameRenderer != null
                ? minecraft.gameRenderer.getMainCamera()
                : null;

        Ambience ambience = Ambience.getInstance();
        boolean customFog = ambience != null && ambience.shouldApplyCustomFog(camera);
        boolean noRenderFog = NoRender.shouldRemoveVanillaFog(camera);

        if (customFog || noRenderFog) {
            return builder.putFloat(1.0F);
        }

        return builder.putFloat(vanillaVisibility);
    }
}
