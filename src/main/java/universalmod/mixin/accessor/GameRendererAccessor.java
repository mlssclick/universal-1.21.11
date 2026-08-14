package universalmod.mixin.accessor;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.joml.Matrix4f;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("getFov")
    float universalmod$getFov(Camera camera, float tickDelta, boolean changingFov);

    @Invoker("renderItemInHand")
    void universalmod$renderItemInHand(float partialTicks, boolean renderBlockOutline, Matrix4f projectionMatrix);
}
