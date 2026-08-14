package universalmod.api.module.impl.misc;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.bind.KeyBind;
import universalmod.api.settings.impl.BindSetting;
import universalmod.api.settings.impl.NumberSetting;

public final class Zoom extends Module {
    private static final double MIN_ZOOM_LEVEL = 1.0D;
    private static final double MAX_ZOOM_LEVEL = 30.0D;
    private static final double FALLBACK_ZOOM_LEVEL = 3.0D;
    private static final double SENSITIVITY_EPSILON = 1.0E-4D;

    private static Zoom instance;

    private final BindSetting zoomBind = register(new BindSetting(
            "Zoom Bind",
            "Hold this key to zoom the camera.",
            KeyBind.keyboard(GLFW.GLFW_KEY_V)
    ));

    private final NumberSetting defaultZoom = register(new NumberSetting(
            "Default Zoom",
            "Default zoom multiplier while holding the zoom key.",
            FALLBACK_ZOOM_LEVEL,
            MIN_ZOOM_LEVEL,
            MAX_ZOOM_LEVEL,
            0.1D
    ));

    private Double currentLevel;
    private Double defaultMouseSensitivity;
    private Double appliedSensitivity;

    public Zoom() {
        super("Zoom", "Zooms the camera while holding a bind.", ModuleCategory.MISC);
        instance = this;
        setEnabled(true);
    }

    public static Zoom getInstance() {
        return instance;
    }

    public float changeFovBasedOnZoom(float fov) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) {
            return fov;
        }

        if (currentLevel == null) {
            currentLevel = defaultZoom.getValue();
        }
        currentLevel = clampZoomLevel(currentLevel);

        if (!isZooming(client)) {
            currentLevel = defaultZoom.getValue();
            restoreMouseSensitivity(client);
            return fov;
        }

        if (defaultMouseSensitivity == null) {
            defaultMouseSensitivity = client.options.sensitivity().get();
        }

        double targetSensitivity = defaultMouseSensitivity / currentLevel;
        if (appliedSensitivity == null
                || Math.abs(appliedSensitivity - targetSensitivity) > SENSITIVITY_EPSILON) {
            client.options.sensitivity().set(targetSensitivity);
            appliedSensitivity = targetSensitivity;
        }

        return (float) (fov / currentLevel);
    }

    public void onMouseScroll(double amount) {
        double level = currentLevel == null
                ? defaultZoom.getValue()
                : currentLevel;

        if (amount > 0.0D) {
            currentLevel = clampZoomLevel(level * 1.1D);
        } else if (amount < 0.0D) {
            currentLevel = clampZoomLevel(level * 0.9D);
        }
    }

    public boolean isZoomHeld() {
        return isZooming(Minecraft.getInstance());
    }

    private boolean isZooming(Minecraft client) {
        return isEnabled()
                && client != null
                && client.getWindow() != null
                && zoomBind.getValue().isDown(client.getWindow().handle());
    }

    @Override
    protected void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null) {
            restoreMouseSensitivity(client);
        } else {
            defaultMouseSensitivity = null;
            appliedSensitivity = null;
        }

        currentLevel = null;
    }

    private void restoreMouseSensitivity(Minecraft client) {
        if (defaultMouseSensitivity != null) {
            client.options.sensitivity().set(defaultMouseSensitivity);
        }

        defaultMouseSensitivity = null;
        appliedSensitivity = null;
    }

    private static double clampZoomLevel(Double value) {
        double safeValue = value != null && Double.isFinite(value)
                ? value
                : FALLBACK_ZOOM_LEVEL;

        return Math.max(MIN_ZOOM_LEVEL, Math.min(MAX_ZOOM_LEVEL, safeValue));
    }
}
