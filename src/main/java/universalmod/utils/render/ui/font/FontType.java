package universalmod.utils.render.ui.font;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public enum FontType {
    SEMIBOLD("semibold", "sf_medium", true),
    BOLD("bold", "sf_medium", true),
    DEFAULT("default", "sf_regular", true),
    ONEST("onest", "onest_regular", true),
    GT("gt", "gt_regular", true),
    DELTA_ICONS("delta_icons", "delta_icons", true),
    ICONS("icons", "icons", true),
    GUI_ICONS("guiicons", "guiicons", true),
    ICONNEW("iconnew", "iconnew", true),
    DIVINE("divine", "divine", true);

    private static final Map<String, String> REGISTRY = new LinkedHashMap<>();

    static {
        for (FontType font : values()) {
            REGISTRY.put(font.name, font.path);
        }
    }

    private final String name;
    private final String path;
    private final boolean msdf;

    FontType(String name, String path, boolean msdf) {
        this.name = name;
        this.path = path;
        this.msdf = msdf;
    }

    public String fontName() {
        return name;
    }

    public String path() {
        return path;
    }

    public static Map<String, String> registry() {
        return REGISTRY;
    }

    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT.fontName();
        }
        return name.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public static FontType resolve(String name) {
        String normalized = normalizeName(name);
        for (FontType font : values()) {
            if (font.name.equals(normalized) || font.path.equals(normalized)) {
                return font;
            }
        }
        return DEFAULT;
    }

    public boolean msdf() {
        return msdf;
    }
}
