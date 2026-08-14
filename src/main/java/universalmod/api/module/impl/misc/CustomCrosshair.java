package universalmod.api.module.impl.misc;

import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ButtonSetting;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.api.settings.impl.StringSetting;
import universalmod.screens.clickgui.impl.ClickGuiController;
import universalmod.utils.render.crosshair.CrosshairCanvas;
import universalmod.utils.render.crosshair.CrosshairCanvasPreset;

import java.awt.Color;

public final class CustomCrosshair extends Module {
    private static volatile CustomCrosshair instance;

    private final StringSetting canvasData = register(new StringSetting(
            "Canvas Data",
            "Stored custom crosshair canvas.",
            CrosshairCanvasPreset.DEFAULT.getCanvas().encode(),
            512
    ));

    private final ButtonSetting editCrosshair = register(new ButtonSetting(
            "Edit Crosshair",
            "Open the custom crosshair editor.",
            this::openEditor
    ));
    private final BooleanSetting displayInThirdPerson = register(new BooleanSetting(
            "Display in Third Person",
            "Show the custom crosshair outside first person.",
            false
    ));
    private final BooleanSetting vanillaBlending = register(new BooleanSetting(
            "Vanilla Blending",
            "Use the vanilla blended crosshair mode.",
            true
    ));
    private final ColorSetting color = register(new ColorSetting(
            "Color",
            "Crosshair color when vanilla blending is disabled.",
            new Color(255, 255, 255, 255)
    ));
    private final BooleanSetting dynamicColor = register(new BooleanSetting(
            "Dynamic Color",
            "Use separate colors for neutral, hostile and player targets.",
            false
    ));
    private final ColorSetting neutralColor = register(new ColorSetting(
            "Neutral",
            "Color used for neutral living entities.",
            new Color(255, 255, 255, 255)
    ));
    private final ColorSetting hostileColor = register(new ColorSetting(
            "Hostile",
            "Color used for hostile entities.",
            new Color(255, 64, 64, 255)
    ));
    private final ColorSetting playersColor = register(new ColorSetting(
            "Players",
            "Color used for players.",
            new Color(64, 180, 255, 255)
    ));

    private CrosshairCanvas canvas;

    public CustomCrosshair() {
        super("Custom Crosshair", "Replaces the vanilla crosshair with an editable custom one.", ModuleCategory.MISC);
        instance = this;
        canvasData.setVisible(false);
        this.canvas = CrosshairCanvas.decode(canvasData.getValue());
        canvasData.addListener((setting, oldValue, newValue) -> this.canvas = CrosshairCanvas.decode(newValue));
        color.visibleWhen(() -> !Boolean.TRUE.equals(vanillaBlending.getValue()));
        dynamicColor.visibleWhen(() -> !Boolean.TRUE.equals(vanillaBlending.getValue()));
        neutralColor.visibleWhen(() -> !Boolean.TRUE.equals(vanillaBlending.getValue()) && Boolean.TRUE.equals(dynamicColor.getValue()));
        hostileColor.visibleWhen(() -> !Boolean.TRUE.equals(vanillaBlending.getValue()) && Boolean.TRUE.equals(dynamicColor.getValue()));
        playersColor.visibleWhen(() -> !Boolean.TRUE.equals(vanillaBlending.getValue()) && Boolean.TRUE.equals(dynamicColor.getValue()));
    }

    public static CustomCrosshair getInstance() {
        return instance;
    }

    public CrosshairCanvas getCanvas() {
        return canvas == null ? CrosshairCanvasPreset.DEFAULT.getCanvas() : canvas.copy();
    }

    public void setCanvas(CrosshairCanvas canvas) {
        CrosshairCanvas safeCanvas = canvas == null ? CrosshairCanvasPreset.DEFAULT.getCanvas() : canvas.copy();
        this.canvas = safeCanvas;
        canvasData.setValue(safeCanvas.encode());
    }

    public boolean shouldDisplayInThirdPerson() {
        return Boolean.TRUE.equals(displayInThirdPerson.getValue());
    }

    public boolean isVanillaBlending() {
        return Boolean.TRUE.equals(vanillaBlending.getValue());
    }

    public boolean isDynamicColor() {
        return Boolean.TRUE.equals(dynamicColor.getValue());
    }

    public Color getCrosshairColor() {
        return color.getValue();
    }

    public Color getNeutralColor() {
        return neutralColor.getValue();
    }

    public Color getHostileColor() {
        return hostileColor.getValue();
    }

    public Color getPlayersColor() {
        return playersColor.getValue();
    }

    private void openEditor() {
        ClickGuiController.openCustomCrosshairEditor(this);
    }
}
