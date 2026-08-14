package universalmod.api.module.impl.render;

import net.minecraft.client.Minecraft;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.utils.render.fireglow.FireGlowConfig;
import universalmod.utils.render.fireglow.FireGlowFramebuffer;

import java.awt.Color;

public final class FireGlow extends Module {
    private static FireGlow instance;

    private final BooleanSetting customColor = register(new BooleanSetting(
            "Custom Color",
            "Uses a custom fire outline color instead of the vanilla burning tint.",
            false
    ));
    private final ColorSetting color = register(new ColorSetting(
            "Color",
            "Custom fire outline color.",
            new Color(255, 255, 255, 255)
    ));

    public FireGlow() {
        super("Fire Glow", "Applies the legacy glowing fire outline effect to first-person burning overlay.", ModuleCategory.RENDER);
        instance = this;
        color.visibleWhen(customColor::getValue);
        loadConfigIntoSettings();
    }

    public static FireGlow getInstance() {
        return instance;
    }

    public static boolean isActive() {
        FireGlow module = instance;
        return module != null && module.isEnabled();
    }

    public static boolean hasCustomColorEnabled() {
        FireGlow module = instance;
        return module != null && module.isEnabled() && module.customColor.getValue();
    }

    public static int getRedValue() {
        FireGlow module = instance;
        return module == null ? 255 : module.color.getValue().getRed();
    }

    public static int getGreenValue() {
        FireGlow module = instance;
        return module == null ? 255 : module.color.getValue().getGreen();
    }

    public static int getBlueValue() {
        FireGlow module = instance;
        return module == null ? 255 : module.color.getValue().getBlue();
    }

    @Override
    public void onTick(Minecraft client) {
        syncToConfig();
    }

    @Override
    protected void onDisable() {
        syncToConfig();
        FireGlowConfig.save();
        FireGlowFramebuffer.close();
    }

    public void shutdown() {
        syncToConfig();
        FireGlowConfig.save();
        FireGlowFramebuffer.close();
    }

    private void loadConfigIntoSettings() {
        FireGlowConfig.load();
        customColor.setValue(FireGlowConfig.hasCustomColor());
        color.setValue(new Color(FireGlowConfig.getRed(), FireGlowConfig.getGreen(), FireGlowConfig.getBlue(), 255));
    }

    private void syncToConfig() {
        FireGlowConfig.setCustomColor(customColor.getValue());
        Color currentColor = color.getValue();
        FireGlowConfig.setRed(currentColor.getRed());
        FireGlowConfig.setGreen(currentColor.getGreen());
        FireGlowConfig.setBlue(currentColor.getBlue());
    }
}
