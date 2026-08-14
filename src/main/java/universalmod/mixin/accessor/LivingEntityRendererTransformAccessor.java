package universalmod.mixin.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererTransformAccessor<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    @Invoker("setupRotations")
    void universalmod$setupRotations(S state, PoseStack poseStack, float bodyYaw, float scale);

    @Invoker("scale")
    void universalmod$scale(S state, PoseStack poseStack);
}
