package universalmod.utils.render.fireglow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FireGlowConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("UniversalMod/FireGlow");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("fireglow.json");

    private static boolean customColor;
    private static int red = 255;
    private static int green = 255;
    private static int blue = 255;

    private FireGlowConfig() {
    }

    public static void load() {
        if (!Files.isRegularFile(PATH)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(PATH)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            customColor = getBoolean(root, "customColor", false);
            red = getInt(root, "red", 255);
            green = getInt(root, "green", 255);
            blue = getInt(root, "blue", 255);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not load Fire Glow config", exception);
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("customColor", customColor);
        root.addProperty("red", red);
        root.addProperty("green", green);
        root.addProperty("blue", blue);

        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not save Fire Glow config", exception);
        }
    }

    public static boolean hasCustomColor() {
        return customColor;
    }

    public static void setCustomColor(boolean value) {
        customColor = value;
    }

    public static int getRed() {
        return red;
    }

    public static void setRed(int value) {
        red = Mth.clamp(value, 0, 255);
    }

    public static int getGreen() {
        return green;
    }

    public static void setGreen(int value) {
        green = Mth.clamp(value, 0, 255);
    }

    public static int getBlue() {
        return blue;
    }

    public static void setBlue(int value) {
        blue = Mth.clamp(value, 0, 255);
    }

    private static boolean getBoolean(JsonObject root, String key, boolean fallback) {
        return root.has(key) ? root.get(key).getAsBoolean() : fallback;
    }

    private static int getInt(JsonObject root, String key, int fallback) {
        return root.has(key) ? Mth.clamp(root.get(key).getAsInt(), 0, 255) : fallback;
    }
}
