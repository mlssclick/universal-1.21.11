package universalmod.utils.render.pipeline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import universalmod.utils.render.RenderCompatibility;
import universalmod.utils.render.RenderLayerFactory;

import java.util.function.Function;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

public final class ClientPipelines {

    private ClientPipelines() {
    }

    private static BlendFunction worldBlend() {
        return RenderCompatibility.useSafeWorldEffects() ? BlendFunction.TRANSLUCENT : BlendFunction.LIGHTNING;
    }

    public static final RenderPipeline WORLD_PARTICLES_GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("universalmod", "world_particles_glow"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(worldBlend())
                    .withSampler("Sampler0")
                    .build()
    );

    public static final RenderPipeline WORLD_HEALTH_ICONS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("universalmod", "world_health_icons"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withSampler("Sampler0")
                    .build()
    );

    public static final Function<Identifier, RenderType> WORLD_PARTICLES_GLOW =
            Util.memoize(texture -> RenderLayerFactory.create("world_particles_glow", 2048, WORLD_PARTICLES_GLOW_PIPELINE, texture));

    public static final Function<Identifier, RenderType> WORLD_HEALTH_ICONS =
            Util.memoize(texture -> RenderLayerFactory.create("world_health_icons", 2048, WORLD_HEALTH_ICONS_PIPELINE, texture));
}
