package universalmod.utils.player;

import net.minecraft.client.Minecraft;

public final class MathAngle {
    private MathAngle() {
    }

    public static Angle cameraAngle() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return Angle.DEFAULT;
        }
        return new Angle(client.player.getYRot(), client.player.getXRot());
    }
}
