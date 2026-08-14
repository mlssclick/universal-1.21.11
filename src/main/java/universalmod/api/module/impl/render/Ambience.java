package universalmod.api.module.impl.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.utils.render.ambience.SkyShaderRenderer;

import java.awt.Color;

public class Ambience extends Module {
    private static Ambience instance;

    private final ModeSetting mode = register(new ModeSetting("Mode", "World time mode.", "Day", "Day", "Midday", "Night", "Midnight", "Custom"));
    private final NumberSetting customTime = register(new NumberSetting("Time", "Custom day time.", 1000.0, 0.0, 24000.0, 100.0));
    private final ModeSetting weather = register(new ModeSetting("Weather", "Client weather override.", "Sunny", "Sunny", "Rain", "Thunder", "Snow"));
    private final NumberSetting saturation = register(new NumberSetting("Saturation", "World saturation multiplier offset.", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting brightness = register(new NumberSetting("Brightness", "World brightness offset.", 0.0, -1.0, 1.0, 0.05));
    private final BooleanSetting customFog = register(new BooleanSetting("Custom Fog", "Use custom fog color and distance.", false));
    private final ColorSetting customFogColor = register(new ColorSetting("Fog Color", "Custom fog color.", new Color(200, 214, 229, 255)));
    private final NumberSetting fogDistance = register(new NumberSetting(
            "Fog Distance", "Distance in blocks where custom fog becomes fully opaque.",
            96.0, 2.0, 512.0, 1.0));
    private final NumberSetting fogDensity = register(new NumberSetting(
            "Fog Density", "Controls how early custom fog starts before its maximum distance.",
            0.50, 0.0, 1.0, 0.01));
    private final BooleanSetting skyShader = register(new BooleanSetting("Sky Shader", "Replaces the vanilla sky with a procedural shader.", false));
    private final ModeSetting skyShaderMode = register(new ModeSetting("Sky Mode", "Procedural sky style.", false, false, "Aurora", "Aurora", "Северное сияние", "Energy", "Nebula", "Cosmic Veil", "Deep Space", "Void", "Plasma"));
    private final NumberSetting skySpeed = register(new NumberSetting("Sky Speed", "Sky shader animation speed.", 1.0, 0.01, 6.0, 0.01));
    private final NumberSetting plasmaScale = register(new NumberSetting("Plasma Scale", "Plasma shader scale.", 1.0, 0.2, 3.0, 0.05));
    private final NumberSetting plasmaSpeed = register(new NumberSetting("Plasma Speed", "Plasma shader animation speed.", 1.0, 0.0, 3.0, 0.05));
    private final BooleanSetting showStars = register(new BooleanSetting("Stars", "Shows stars inside supported sky shaders.", true));
    private final ColorSetting skyShaderColor = register(new ColorSetting("Shader Color", "Custom sky shader color.", new Color(108, 99, 210, 255)));

    private boolean weatherOverrideActive;
    private boolean cachedServerRaining;
    private float cachedServerRainLevel;
    private float cachedServerThunderLevel;
    private ClientLevel weatherSnapshotLevel;

    public Ambience() {
        super("Ambience", "Changes time, weather and world atmosphere.", ModuleCategory.RENDER);
        customTime.visibleWhen(() -> mode.is("Custom"));
        customFogColor.visibleWhen(customFog::getValue);
        fogDistance.visibleWhen(customFog::getValue);
        fogDensity.visibleWhen(customFog::getValue);
        skyShaderMode.visibleWhen(skyShader::getValue);
        skySpeed.visibleWhen(skyShader::getValue);
        plasmaScale.visibleWhen(() -> skyShader.getValue() && skyShaderMode.is("Plasma"));
        plasmaSpeed.visibleWhen(() -> skyShader.getValue() && skyShaderMode.is("Plasma"));
        showStars.visibleWhen(skyShader::getValue);
        skyShaderColor.visibleWhen(skyShader::getValue);
        instance = this;
    }

    public static Ambience getInstance() {
        return instance;
    }

    @Override
    protected void onEnable() {
        SkyShaderRenderer.resetTime();
    }

    @Override
    public void onTick(net.minecraft.client.Minecraft client) {
        if (skyShader.getValue()) {
            SkyShaderRenderer.updateConfig(this);
        }
    }

    public boolean isSkyShaderEnabled() {
        return isEnabled() && skyShader.getValue();
    }

    public String getSkyShaderMode() {
        return skyShaderMode.getValue();
    }

    public boolean isSkyStarsEnabled() {
        return showStars.getValue();
    }

    public float getSkyStarDensity() {
        return 0.7F;
    }

    public float getSkyNebulaStrength() {
        return 0.8F;
    }

    public float getSkyPlasmaScale() {
        return plasmaScale.getFloat();
    }

    public float getSkyPlasmaSpeed() {
        return plasmaSpeed.getFloat();
    }

    public float getSkySpeed() {
        return skySpeed.getFloat();
    }

    public int getSkyShaderColor() {
        return skyShaderColor.getValue().getRGB();
    }

    public float getBrightnessValue() {
        return brightness.getFloat();
    }

    public float getSaturationFactor() {
        return Math.clamp(1.0f + saturation.getFloat(), 0.0f, 2.0f);
    }

    public boolean hasCustomFog() {
        return isEnabled() && customFog.getValue();
    }

    public boolean shouldApplyCustomFog(Camera camera) {
        if (!hasCustomFog() || camera == null || camera.getFluidInCamera() != FogType.NONE) {
            return false;
        }

        if (camera.entity() instanceof LivingEntity living) {
            if (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS)) {
                return false;
            }
        }

        return true;
    }

    public int getCustomFogColor() {
        return customFogColor.getValue().getRGB();
    }

    public float getFogDistance() {
        return Math.clamp(fogDistance.getFloat(), 2.0F, 512.0F);
    }

    public float getFogDensity() {
        return Math.clamp(fogDensity.getFloat(), 0.0F, 1.0F);
    }

    public float getFogStart() {
        float distance = getFogDistance();
        return distance * (1.0F - getFogDensity());
    }

    public long getInternalTime() {
        if (mode.is("Day")) {
            return 1000L;
        }
        if (mode.is("Midday")) {
            return 6000L;
        }
        if (mode.is("Night")) {
            return 13000L;
        }
        if (mode.is("Midnight")) {
            return 18000L;
        }
        return (long) customTime.getValue().doubleValue();
    }

    public void syncWeather(ClientLevel level, ClientLevel.ClientLevelData levelData) {
        if (level == null || levelData == null) {
            clearWeatherSnapshot();
            return;
        }

        if (weatherSnapshotLevel != level) {
            clearWeatherSnapshot();
        }

        if (!weatherOverrideActive) {
            cachedServerRaining = levelData.isRaining();
            cachedServerRainLevel = level.getRainLevel(1.0f);
            cachedServerThunderLevel = level.getThunderLevel(1.0f);
            weatherOverrideActive = true;
            weatherSnapshotLevel = level;
        }

        boolean raining = shouldForcePrecipitation();
        levelData.setRaining(raining);
        level.setRainLevel(raining ? 1.0f : 0.0f);
        level.setThunderLevel(weather.is("Thunder") ? 1.0f : 0.0f);
    }

    public Biome.Precipitation getForcedPrecipitation() {
        if (!isEnabled()) {
            return null;
        }
        if (weather.is("Snow")) {
            return Biome.Precipitation.SNOW;
        }
        if (weather.is("Rain") || weather.is("Thunder")) {
            return Biome.Precipitation.RAIN;
        }
        return Biome.Precipitation.NONE;
    }

    public boolean shouldForceSnow() {
        return getForcedPrecipitation() == Biome.Precipitation.SNOW;
    }

    @Override
    protected void onDisable() {
        if (mc.level instanceof ClientLevel level) {
            restoreWeather(level, level.getLevelData());
        } else {
            clearWeatherSnapshot();
        }
    }

    private boolean shouldForcePrecipitation() {
        return weather.is("Rain") || weather.is("Thunder") || weather.is("Snow");
    }

    private void restoreWeather(ClientLevel level, ClientLevel.ClientLevelData levelData) {
        if (!weatherOverrideActive) {
            return;
        }
        levelData.setRaining(cachedServerRaining);
        level.setRainLevel(cachedServerRainLevel);
        level.setThunderLevel(cachedServerThunderLevel);
        clearWeatherSnapshot();
    }

    private void clearWeatherSnapshot() {
        weatherOverrideActive = false;
        weatherSnapshotLevel = null;
    }
}
