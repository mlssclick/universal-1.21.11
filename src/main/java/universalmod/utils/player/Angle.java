package universalmod.utils.player;

import net.minecraft.util.Mth;

public class Angle {
    public static final Angle DEFAULT = new Angle(0.0F, 0.0F);

    private float yaw;
    private float pitch;

    public Angle(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = Mth.clamp(pitch, -90.0F, 90.0F);
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(float pitch) {
        this.pitch = Mth.clamp(pitch, -90.0F, 90.0F);
    }
}
