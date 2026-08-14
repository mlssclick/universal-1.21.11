package universalmod.mixin;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import universalmod.access.EmotionEntityCarrier;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateEmotionMixin implements EmotionEntityCarrier {
    @Unique
    private LivingEntity universalmod$emotionEntity;

    @Override
    public LivingEntity universalmod$getEmotionEntity() {
        return universalmod$emotionEntity;
    }

    @Override
    public void universalmod$setEmotionEntity(LivingEntity entity) {
        universalmod$emotionEntity = entity;
    }
}
