package universalmod.api.module.impl.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.utils.lang.LanguageManager;
import universalmod.utils.repository.friend.FriendUtils;
import universalmod.utils.string.chat.ChatMessage;

public final class Friends extends Module {
    private static Friends instance;

    public Friends() {
        super("Friends", "Add or remove friends with middle click on players.", ModuleCategory.UTILS);
        instance = this;
        setEnabled(true);
    }

    public static boolean handleMiddleClick(Minecraft minecraft) {
        Friends module = instance;
        if (module == null || !module.isEnabled() || minecraft == null || minecraft.player == null) {
            return false;
        }
        if (!(minecraft.hitResult instanceof EntityHitResult entityHitResult)) {
            return false;
        }
        if (!(entityHitResult.getEntity() instanceof Player player)) {
            return false;
        }
        if (player == minecraft.player) {
            return false;
        }

        String name = FriendUtils.normalizeName(player.getName().getString());
        if (name.isEmpty()) {
            return false;
        }

        boolean wasFriend = FriendUtils.isFriend(name);
        if (wasFriend) {
            FriendUtils.removeFriendAndSave(name);
            ChatMessage.brandmessage(Component.literal(LanguageManager.translateFormat("friends.removed", name)).withStyle(ChatFormatting.GREEN));
        } else {
            FriendUtils.addFriendAndSave(name);
            ChatMessage.brandmessage(Component.literal(LanguageManager.translateFormat("friends.added", name)).withStyle(ChatFormatting.GREEN));
        }
        return true;
    }

    public static Friends getInstance() {
        return instance;
    }
}
