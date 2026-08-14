package universalmod.mixin.accessor;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.model.EntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererLayersAccessor<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Accessor("layers")
    List<RenderLayer<S, M>> universalmod$getLayers();
}
