package universalmod.utils.render.ui.font;

import java.util.Locale;

public enum FontType {
    SEMIBOLD("semibold", "sf_medium", true),
    BOLD("bold", "sf_medium", true),
    DEFAULT("default", "sf_regular", true),
    DELTA_ICONS("delta_icons", "delta_icons", true),
    GUI_ICONS("guiicons", "guiicons", true),
    ICONNEW("iconnew", "iconnew", true),
    VIREX_WONDERFUL("virex_wonderful", "virex_wonderful", true);

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
