package universalmod.api.module.impl.sounds;

import net.minecraft.client.resources.sounds.SoundInstance;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.MultiModeSetting;
import universalmod.api.settings.impl.NumberSetting;

import java.util.Locale;

public final class SoundReducer extends Module {
    private static final String REDUCE = "Reduce";
    private static final String DISABLE = "Disable";
    private static SoundReducer instance;

    private final ModeSetting processing = register(new ModeSetting(
            "Processing Method", "How matching sounds are processed.", REDUCE, REDUCE, DISABLE
    ));
    private final MultiModeSetting sounds = register(new MultiModeSetting(
            "Sounds", "Selected game sounds.",
            new String[]{
                    "Potion/Experience Throw", "Creeper Explosion", "TNT Explosion", "Anvils",
                    "Bow Shot", "Ghast Scream", "Skeleton Steps", "Nether Portal", "Event",
                    "Wither", "Phantoms", "Trident", "Totem", "Warden", "Sculk Sensor"
            },
            "Potion/Experience Throw", "Anvils", "Event", "Wither", "Phantoms", "Trident"
    ));
    private final NumberSetting volume = register(new NumberSetting(
            "Sound Volume", "Volume multiplier for reduced sounds.", 0.5D, 0.0D, 1.0D, 0.01D
    ));

    public SoundReducer() {
        super("Sound Reducer", "Reduces the volume of selected game sounds.", ModuleCategory.UTILS);
        volume.visibleWhen(() -> processing.is(REDUCE));
        instance = this;
    }

    public static float adjustVolume(SoundInstance sound, float original) {
        SoundReducer module = instance;
        if (module == null || !module.isEnabled() || sound == null || sound.getIdentifier() == null || original <= 0.0F) {
            return original;
        }
        String path = sound.getIdentifier().toString().toLowerCase(Locale.ROOT);
        if (!module.matches(path)) {
            return original;
        }
        return module.processing.is(DISABLE) ? 0.0F : original * module.volume.getFloat();
    }

    private boolean matches(String path) {
        return sounds.isSelected("Potion/Experience Throw") && containsAny(path, "splash_potion", "experience_orb", "lingering_potion")
                || sounds.isSelected("Creeper Explosion") && (path.contains("creeper") || containsGenericExplosion(path))
                || sounds.isSelected("TNT Explosion") && (path.contains("tnt") || containsGenericExplosion(path))
                || sounds.isSelected("Anvils") && path.contains("anvil")
                || sounds.isSelected("Bow Shot") && containsAny(path, "arrow", "bow")
                || sounds.isSelected("Ghast Scream") && path.contains("ghast")
                || sounds.isSelected("Skeleton Steps") && path.contains("skeleton") && !path.contains("skeleton_horse")
                || sounds.isSelected("Nether Portal") && path.contains("portal")
                || sounds.isSelected("Event") && path.contains("entity.ender_dragon.ambient")
                || sounds.isSelected("Wither") && path.contains("wither") && !path.contains("wither_skeleton")
                || sounds.isSelected("Phantoms") && path.contains("phantom")
                || sounds.isSelected("Trident") && path.contains("trident")
                || sounds.isSelected("Totem") && path.contains("totem")
                || sounds.isSelected("Warden") && path.contains("warden")
                || sounds.isSelected("Sculk Sensor") && path.contains("sculk");
    }

    private static boolean containsGenericExplosion(String path) {
        return path.contains("generic") && path.contains("explode");
    }

    private static boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
