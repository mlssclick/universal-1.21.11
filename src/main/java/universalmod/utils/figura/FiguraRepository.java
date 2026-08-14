package universalmod.utils.figura;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import universalmod.utils.render.ui.Render2D;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class FiguraRepository {
    private static final String AVATAR_FILE = "avatar.json";
    private static final String PREVIEW_FILE = "avatar.png";
    private static final List<FiguraEntry> entries = new ArrayList<>();
    private static boolean scanned;

    private FiguraRepository() {
    }

    public static Path directory() {
        return FabricLoader.getInstance().getGameDir().resolve("universalmod").resolve("figura models");
    }

    public static List<FiguraEntry> all() {
        if (!scanned) {
            rescan();
        }
        return entries;
    }

    public static FiguraEntry byId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (FiguraEntry entry : all()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public static void rescan() {
        scanned = true;
        entries.clear();
        Path root = directory();
        if (!Files.isDirectory(root)) {
            try {
                Files.createDirectories(root);
            } catch (Throwable ignored) {
            }
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve(AVATAR_FILE)))
                    .forEach(dir -> entries.add(toEntry(dir)));
        } catch (Throwable ignored) {
        }
        entries.sort(Comparator.comparing(entry -> entry.displayName().toLowerCase(Locale.ROOT)));
    }

    private static FiguraEntry toEntry(Path dir) {
        String folderName = dir.getFileName().toString();
        String label = folderName;
        int dash = label.indexOf(" - ");
        if (dash > 0) {
            label = label.substring(0, dash);
        }
        return new FiguraEntry(dir, folderName, label.replace('_', ' ').trim());
    }

    public static Identifier preview(FiguraEntry entry) {
        if (entry == null) {
            return null;
        }
        if (entry.previewLoaded()) {
            return entry.preview();
        }
        entry.setPreview(null);
        Path file = entry.folder().resolve(PREVIEW_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath("universalmod",
                    "figura_models/" + sanitize(entry.id()));
            Minecraft.getInstance().getTextureManager()
                    .register(id, new DynamicTexture(entry::id, image));
            entry.setPreview(id);
            return id;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String sanitize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-'
                    ? c : '_');
        }
        return out.toString();
    }
}
