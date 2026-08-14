package universalmod.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.FireGlow;
import universalmod.utils.render.fireglow.FireGlowFramebuffer;

@Mixin(LevelRenderer.class)
public abstract class FireGlowWorldRendererMixin {
    private static final Identifier VANILLA_ENTITY_OUTLINE = Identifier.withDefaultNamespace("entity_outline");
    private static final Identifier LEGACY_ENTITY_OUTLINE = Identifier.fromNamespaceAndPath("universalmod", "legacy_entity_outline");

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void universalmod$beginFireGlowFrame(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f frustumMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        if (FireGlow.isActive()) {
            FireGlowFramebuffer.beginFrame();
        }
    }

    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ShaderManager;getPostChain(Lnet/minecraft/resources/Identifier;Ljava/util/Set;)Lnet/minecraft/client/renderer/PostChain;"
            ),
            index = 0
    )
    private Identifier universalmod$useLegacyEntityOutline(Identifier effect) {
        if (FireGlow.isActive() && VANILLA_ENTITY_OUTLINE.equals(effect)) {
            return LEGACY_ENTITY_OUTLINE;
        }
        return effect;
    }
}
