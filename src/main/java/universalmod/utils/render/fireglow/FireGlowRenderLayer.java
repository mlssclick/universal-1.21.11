package universalmod.utils.render.fireglow;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.TextureAtlas;
import universalmod.mixin.accessor.MixinRenderType;

public final class FireGlowRenderLayer {
    private static final RenderPipeline FIRE_MASK_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("universalmod", "pipeline/fire_glow_outline"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("universalmod", "core/fire_glow_outline"))
                    .withCull(false)
                    .build()
    );

    private static final OutputTarget FIRE_MASK_TARGET =
            new OutputTarget("universalmod_fire_glow_outline", FireGlowFramebuffer::getFramebuffer);

    private static final RenderType FIRE_MASK = MixinRenderType.universalmod$create(
            "universalmod_fire_glow_outline_mask",
            RenderSetup.builder(FIRE_MASK_PIPELINE)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .setOutputTarget(FIRE_MASK_TARGET)
                    .bufferSize(1536)
                    .createRenderSetup()
    );
    private static final RenderType FIRE_BLOCKS = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);

    private FireGlowRenderLayer() {
    }

    public static RenderType fireMask() {
        return FIRE_MASK;
    }

    public static RenderType fireBlocks() {
        return FIRE_BLOCKS;
    }
}
