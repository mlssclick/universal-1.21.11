package universalmod.mixin;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import universalmod.api.module.impl.render.ItemReplacer;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @ModifyVariable(method = "updateForTopItem", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private ItemStack universalmod$replaceSwordModel(ItemStack stack) {
        ItemReplacer replacer = ItemReplacer.getInstance();
        return replacer == null ? stack : replacer.apply(stack);
    }
}
