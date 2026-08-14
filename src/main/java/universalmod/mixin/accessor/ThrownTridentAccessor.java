package universalmod.mixin.accessor;

import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ThrownTrident.class)
public interface ThrownTridentAccessor {
    @Accessor("clientSideReturnTridentTickCount")
    int universalmod$getClientSideReturnTickCount();
}
