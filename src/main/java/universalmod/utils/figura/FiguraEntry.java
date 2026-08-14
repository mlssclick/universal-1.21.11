package universalmod.utils.figura;

import net.minecraft.resources.Identifier;

import java.nio.file.Path;

public final class FiguraEntry {
    private final Path folder;
    private final String id;
    private final String displayName;
    private Identifier preview;
    private boolean previewLoaded;

    FiguraEntry(Path folder, String id, String displayName) {
        this.folder = folder;
        this.id = id;
        this.displayName = displayName;
    }

    public Path folder() {
        return folder;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Identifier preview() {
        return preview;
    }

    boolean previewLoaded() {
        return previewLoaded;
    }

    void setPreview(Identifier preview) {
        this.preview = preview;
        this.previewLoaded = true;
    }
}
