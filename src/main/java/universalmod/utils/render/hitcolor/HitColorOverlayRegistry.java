package universalmod.utils.render.hitcolor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class HitColorOverlayRegistry {
    private static final Set<HitColorOverlayReloadable> OVERLAYS = Collections.newSetFromMap(new WeakHashMap<>());

    private HitColorOverlayRegistry() {
    }

    public static void register(HitColorOverlayReloadable overlay) {
        if (overlay != null) {
            OVERLAYS.add(overlay);
        }
    }

    public static void reloadAll() {
        for (HitColorOverlayReloadable overlay : new ArrayList<>(OVERLAYS)) {
            if (overlay != null) {
                overlay.universalmod$reloadHitColorOverlay();
            }
        }
    }
}
