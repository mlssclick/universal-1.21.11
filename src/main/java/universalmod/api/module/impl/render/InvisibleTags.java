package universalmod.api.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;

public final class InvisibleTags extends Module {
    private static InvisibleTags instance;

    public InvisibleTags() {
        super("Invisible Tags", "Shows names and health for invisible players.", ModuleCategory.RENDER);
        instance = this;
    }

    public static InvisibleTags getInstance() {
        return instance;
    }

    public static boolean shouldShowVanillaInvisibleTag(Player player) {
        InvisibleTags invisibleTags = instance;
        return invisibleTags != null && invisibleTags.isEnabled() && invisibleTags.isValidInvisible(player);
    }

    public static boolean applyVanillaInvisibleTag(Player player, EntityRenderState state, float tickDelta) {
        if (state == null || !shouldShowVanillaInvisibleTag(player)) {
            return false;
        }
        state.nameTag = player.getDisplayName();
        state.nameTagAttachment = player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, player.getYRot(tickDelta));
        return true;
    }

    private boolean isValidInvisible(Player player) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return false;
        }
        if (player == null || player == client.player || player.isRemoved() || player.isSpectator() || !player.isInvisible()) {
            return false;
        }
        if (client.player.distanceToSqr(player) > 128.0 * 128.0) {
            return false;
        }
        Vec3 from = client.player.getEyePosition();
        Vec3 to = player.getEyePosition();
        BlockHitResult hit = client.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }
}
