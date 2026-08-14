package universalmod.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.LevelTargetBundle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelTargetBundle.class)
public interface LevelTargetBundleAccessor {
    @Accessor("main")
    ResourceHandle<RenderTarget> universalmod$getMain();

    @Accessor("main")
    void universalmod$setMain(ResourceHandle<RenderTarget> handle);
}
