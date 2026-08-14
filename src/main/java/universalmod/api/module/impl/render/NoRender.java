package universalmod.api.module.impl.render;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrowingPlantBodyBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.MultiModeSetting;

public class NoRender extends Module {
    private static NoRender instance;

    private final MultiModeSetting elements = register(new MultiModeSetting("Elements", "Render elements to hide.",
            new String[]{
                    "Hurt Cam", "Fire", "Particles", "Block Particles", "Hit Particles", "Block Overlay",
                    "Plants", "Weather", "Clouds", "Glow", "Fog",
                    "Damage", "Scoreboard", "Bossbar", "Vanilla Effects", "Wither Hearts", "Totem Animation"
            },
            "Hurt Cam", "Fire", "Particles", "Block Particles", "Hit Particles", "Block Overlay",
            "Weather", "Clouds", "Glow",
            "Damage", "Wither Hearts", "Totem Animation"
    ));

    private boolean plantsReloadState;

    public NoRender() {
        super("No Render", "Hides selected visual effects.", ModuleCategory.RENDER);
        instance = this;
    }

    public static NoRender getInstance() {
        return instance;
    }

    public static boolean isActive(String element) {
        return instance != null && instance.isEnabled() && instance.elements.isSelected(element);
    }

    public static boolean isNoFogEnabled() {
        return instance != null && instance.isEnabled() && instance.elements.isSelected("Fog");
    }

    public static boolean shouldRemoveVanillaFog(Camera camera) {
        if (!isNoFogEnabled()) {
            return false;
        }

        Ambience ambience = Ambience.getInstance();
        return ambience == null || !ambience.shouldApplyCustomFog(camera);
    }

    @Override
    protected void onEnable() {
        plantsReloadState = shouldHidePlants();
        if (plantsReloadState) {
            reloadWorldRenderer();
        }
    }

    @Override
    public void onTick(net.minecraft.client.Minecraft client) {
        boolean hidePlants = shouldHidePlants();
        if (hidePlants != plantsReloadState) {
            plantsReloadState = hidePlants;
            reloadWorldRenderer();
        }
    }

    public boolean shouldHidePlants() {
        return isEnabled() && elements.isSelected("Plants");
    }

    public boolean shouldHideHitParticles() {
        return isEnabled() && elements.isSelected("Hit Particles");
    }

    public boolean shouldHideParticles() {
        return isEnabled() && elements.isSelected("Particles");
    }

    public boolean shouldHideBlockParticles() {
        return isEnabled() && elements.isSelected("Block Particles");
    }

    public boolean shouldHideGlow() {
        return isEnabled() && elements.isSelected("Glow");
    }

    public boolean shouldHideWeather() {
        return isEnabled() && elements.isSelected("Weather");
    }

    public boolean shouldHideClouds() {
        return isEnabled() && elements.isSelected("Clouds");
    }

    public boolean isPlantState(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.getBlock() instanceof VegetationBlock
                || state.getBlock() instanceof BushBlock
                || state.getBlock() instanceof GrowingPlantBodyBlock
                || state.getBlock() instanceof CropBlock
                || state.getBlock() instanceof SaplingBlock
                || state.getBlock() instanceof MushroomBlock
                || state.getBlock() instanceof SweetBerryBushBlock
                || state.getBlock() instanceof TallGrassBlock
                || state.getBlock() instanceof DoublePlantBlock
                || state.getBlock() instanceof FlowerBlock
                || state.getBlock() instanceof FlowerPotBlock
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS);
    }

    private void reloadWorldRenderer() {
        if (mc.level != null && mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }

    @Override
    protected void onDisable() {
        boolean hadPlantsHidden = plantsReloadState;
        plantsReloadState = false;
        if (hadPlantsHidden) {
            reloadWorldRenderer();
        }
    }
}
