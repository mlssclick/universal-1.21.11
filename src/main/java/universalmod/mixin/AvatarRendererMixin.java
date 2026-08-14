package universalmod.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.utils.Friends;
import universalmod.api.module.impl.render.InvisibleTags;
import universalmod.utils.player.PingNametagHelper;
import universalmod.utils.repository.friend.FriendUtils;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    private static final int FRIEND_ARMOR_GREEN = 0x33F233;
    private static final ItemStack FRIEND_HELMET = dyedLeather(Items.LEATHER_HELMET);
    private static final ItemStack FRIEND_CHESTPLATE = dyedLeather(Items.LEATHER_CHESTPLATE);
    private static final ItemStack FRIEND_LEGGINGS = dyedLeather(Items.LEATHER_LEGGINGS);
    private static final ItemStack FRIEND_BOOTS = dyedLeather(Items.LEATHER_BOOTS);

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"), require = 0)
    private void universalmod$updateAvatarLabels(Avatar entity, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        if (entity instanceof Player player) {
            InvisibleTags.applyVanillaInvisibleTag(player, state, tickDelta);
            state.nameTag = PingNametagHelper.appendPing(player, state.nameTag);
        }
        universalmod$replaceFriendArmor(entity, state);
    }

    private void universalmod$replaceFriendArmor(Avatar entity, AvatarRenderState state) {
        Friends friends = Friends.getInstance();
        if (friends == null || !friends.isEnabled() || !FriendUtils.isFriend(entity)) {
            return;
        }

        if (!state.headEquipment.isEmpty()) {
            state.headEquipment = FRIEND_HELMET;
        }
        if (!state.chestEquipment.isEmpty()) {
            state.chestEquipment = FRIEND_CHESTPLATE;
        }
        if (!state.legsEquipment.isEmpty()) {
            state.legsEquipment = FRIEND_LEGGINGS;
        }
        if (!state.feetEquipment.isEmpty()) {
            state.feetEquipment = FRIEND_BOOTS;
        }
    }

    private static ItemStack dyedLeather(net.minecraft.world.level.ItemLike item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(FRIEND_ARMOR_GREEN));
        return stack;
    }
}
