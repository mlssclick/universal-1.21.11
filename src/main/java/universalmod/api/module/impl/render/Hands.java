package universalmod.api.module.impl.render;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.utils.render.hand.HandsRenderer;

import java.awt.Color;

public final class Hands extends Module {
    private static Hands instance;

    private final ModeSetting mode = register(new ModeSetting("Mode", "Hands render mode.", "Shader", "Shader", "Shader+Outline", "Outline", "Smoke"));
    private final ModeSetting shaderMode = register(new ModeSetting("Shader", "Hands shader mode.", "Wave", "Wave", "Nebula"));
    private final ColorSetting shaderColor = register(new ColorSetting("Color", "Hands shader color.", new Color(178, 58, 196, 255), "Shader Color"));
    private final NumberSetting waveSpeed = register(new NumberSetting("Wave Speed", "Hands shader speed.", 110.0, 10.0, 500.0, 1.0));
    private final NumberSetting waveScale = register(new NumberSetting("Wave Scale", "Hands shader scale.", 160.0, 100.0, 300.0, 1.0));
    private final NumberSetting fill = register(new NumberSetting("Fill", "Hands shader fill.", 59.0, 0.0, 100.0, 1.0));
    private final NumberSetting alpha = register(new NumberSetting("Alpha", "Hands shader alpha.", 100.0, 1.0, 100.0, 1.0));

    private final ModeSetting outlineColorMode = register(new ModeSetting("Outline Color Mode", "Outline color style.", "Gradient", "Gradient", "Static"));
    private final ColorSetting outlineColor = register(new ColorSetting("Outline Color", "Primary outline color.", new Color(255, 255, 255, 255)));
    private final ColorSetting outlineSecondColor = register(new ColorSetting("Second Color", "Secondary outline color.", new Color(127, 242, 255, 255)));
    private final NumberSetting outlineRadius = register(new NumberSetting("Radius", "Outline glow radius.", 8.0, 2.0, 24.0, 1.0));
    private final NumberSetting glowStrength = register(new NumberSetting("Glow Strength", "Outline glow strength.", 1.8, 0.25, 5.0, 0.05));
    private final NumberSetting outlineWidth = register(new NumberSetting("Outline Width", "Outline width.", 1.5, 0.5, 6.0, 0.5));
    private final NumberSetting outlineOpacity = register(new NumberSetting("Outline Opacity", "Outline opacity.", 90.0, 5.0, 100.0, 1.0));
    private final ColorSetting smokeColor = register(new ColorSetting("Color", "Smoke color.", new Color(255, 255, 255, 230)));
    private final NumberSetting smokeStrength = register(new NumberSetting("Strength", "Smoke strength.", 0.85, 0.0, 2.0, 0.01));
    private final NumberSetting smokeRiseSpeed = register(new NumberSetting("Rise Speed", "Smoke rise speed.", 0.0, 0.0, 2.0, 0.01));
    private final NumberSetting smokeWobble = register(new NumberSetting("Wobble", "Smoke wobble amount.", 0.65, 0.0, 2.0, 0.01));
    private final NumberSetting smokeLength = register(new NumberSetting("Length", "Smoke length.", 0.95, 0.1, 2.5, 0.01));
    private final NumberSetting smokeBrightness = register(new NumberSetting("Brightness", "Smoke brightness.", 0.9, 0.0, 2.0, 0.01));

    public Hands() {
        super("Hands", "Applies shader and outline effects to first-person hands.", ModuleCategory.RENDER);
        instance = this;
        shaderMode.visibleWhen(this::showsShaderSettings);
        shaderColor.visibleWhen(this::showsShaderSettings);
        waveSpeed.visibleWhen(this::showsShaderSettings);
        waveScale.visibleWhen(this::showsShaderSettings);
        fill.visibleWhen(this::showsShaderSettings);
        alpha.visibleWhen(this::showsShaderSettings);

        outlineColorMode.visibleWhen(this::showsOutlineSettings);
        outlineColor.visibleWhen(this::showsOutlineSettings);
        outlineSecondColor.visibleWhen(() -> showsOutlineSettings() && outlineColorMode.is("Gradient"));
        outlineRadius.visibleWhen(this::showsOutlineSettings);
        glowStrength.visibleWhen(this::showsOutlineSettings);
        outlineWidth.visibleWhen(this::showsOutlineSettings);
        outlineOpacity.visibleWhen(this::showsOutlineSettings);

        smokeColor.visibleWhen(this::showsSmokeSettings);
        smokeStrength.visibleWhen(this::showsSmokeSettings);
        smokeRiseSpeed.visibleWhen(this::showsSmokeSettings);
        smokeWobble.visibleWhen(this::showsSmokeSettings);
        smokeLength.visibleWhen(this::showsSmokeSettings);
        smokeBrightness.visibleWhen(this::showsSmokeSettings);
    }

    public static Hands getInstance() {
        return instance;
    }

    public static boolean isActive() {
        Hands module = instance;
        if (module == null || !module.isEnabled()) {
            return false;
        }
        if (module.shouldRenderSmoke()) {
            return true;
        }
        if (module.shouldRenderShader() && module.getFill() > 0.0F && module.getAlpha() > 0.0F) {
            return true;
        }
        return module.shouldRenderOutline() && module.getOutlineOpacity() > 0.0F;
    }

    public boolean shouldRenderShader() {
        return mode.is("Shader") || mode.is("Shader+Outline");
    }

    public boolean shouldRenderOutline() {
        return mode.is("Outline") || mode.is("Shader+Outline");
    }

    public boolean shouldRenderSmoke() {
        return mode.is("Smoke");
    }

    public String getShaderMode() {
        return shaderMode.getValue();
    }

    public int getShaderColor() {
        return shaderColor.getValue().getRGB();
    }

    public float getWaveSpeed() {
        return waveSpeed.getFloat();
    }

    public float getWaveScale() {
        return waveScale.getFloat();
    }

    public float getFill() {
        return fill.getFloat();
    }

    public float getAlpha() {
        return alpha.getFloat();
    }

    public int getOutlineTopColor() {
        return outlineColor.getValue().getRGB();
    }

    public int getOutlineBottomColor() {
        return outlineColorMode.is("Static") ? outlineColor.getValue().getRGB() : outlineSecondColor.getValue().getRGB();
    }

    public boolean isOutlineStatic() {
        return outlineColorMode.is("Static");
    }

    public float getOutlineRadius() {
        return outlineRadius.getFloat();
    }

    public float getGlowStrength() {
        return glowStrength.getFloat();
    }

    public float getOutlineWidth() {
        return outlineWidth.getFloat();
    }

    public float getOutlineOpacity() {
        return outlineOpacity.getFloat() / 100.0F;
    }

    public float getSmokeStrength() {
        return smokeStrength.getFloat();
    }

    public float getSmokeRiseSpeed() {
        return smokeRiseSpeed.getFloat();
    }

    public float getSmokeWobble() {
        return smokeWobble.getFloat();
    }

    public float getSmokeLength() {
        return smokeLength.getFloat();
    }

    public float getSmokeBrightness() {
        return smokeBrightness.getFloat();
    }

    public int getSmokeResolvedColor() {
        return smokeColor.getValue().getRGB();
    }

    private boolean showsShaderSettings() {
        return shouldRenderShader();
    }

    private boolean showsOutlineSettings() {
        return shouldRenderOutline();
    }

    private boolean showsSmokeSettings() {
        return shouldRenderSmoke();
    }

    @Override
    protected void onDisable() {
        HandsRenderer.reset();
    }
}
