package universalmod.api.module.impl.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;

public final class AutoSprint extends Module {
    public AutoSprint() {
        super("Auto Sprint", "Automatically keeps the player sprinting.", ModuleCategory.UTILS);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null || client.options == null) {
            return;
        }
        client.options.keySprint.setDown(false);
        boolean forward = client.options.keyUp.isDown();
        boolean water = client.player.isInWater();
        boolean underwater = client.player.isUnderWater();
        boolean canSprint = forward
                && (!water || underwater)
                && !client.player.isFallFlying()
                && !client.player.isUsingItem()
                && !client.player.hasEffect(MobEffects.BLINDNESS)
                && !client.player.isShiftKeyDown()
                && !client.player.horizontalCollision;
        client.player.setSprinting(canSprint);
    }
}
