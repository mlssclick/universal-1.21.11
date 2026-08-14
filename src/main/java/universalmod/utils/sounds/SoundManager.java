package universalmod.utils.sounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public final class SoundManager {
    public static final SoundEvent MOAN1 = sound("moan1");
    public static final SoundEvent MOAN2 = sound("moan2");
    public static final SoundEvent MOAN3 = sound("moan3");
    public static final SoundEvent MOAN4 = sound("moan4");
    public static final SoundEvent CRIME = sound("crime");
    public static final SoundEvent METALLIC = sound("metallic");
    private static boolean initialized;

    private SoundManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        register(MOAN1);
        register(MOAN2);
        register(MOAN3);
        register(MOAN4);
        register(CRIME);
        register(METALLIC);
    }

    public static void playSound(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        mc.level.playSound(mc.player, mc.player.blockPosition(), sound, SoundSource.BLOCKS, volume, pitch);
    }

    public static void playSoundDirect(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch));
        }
    }

    private static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("universalmod", path));
    }

    private static void register(SoundEvent sound) {
        Registry.register(BuiltInRegistries.SOUND_EVENT, sound.location(), sound);
    }
}
