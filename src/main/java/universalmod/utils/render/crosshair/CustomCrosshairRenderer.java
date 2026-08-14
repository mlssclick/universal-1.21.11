package universalmod.utils.render.crosshair;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import universalmod.api.module.impl.misc.CustomCrosshair;

import java.awt.Color;

public final class CustomCrosshairRenderer {
    private CustomCrosshairRenderer() {
    }

    public static boolean shouldRender(Minecraft minecraft, CustomCrosshair module) {
        if (minecraft == null || module == null || !module.isEnabled()) {
            return false;
        }
        if (minecraft.options == null || minecraft.options.hideGui) {
            return false;
        }
        if (minecraft.player == null || minecraft.level == null) {
            return false;
        }
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON && !module.shouldDisplayInThirdPerson()) {
            return false;
        }
        return true;
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, CustomCrosshair module) {
        if (!shouldRender(minecraft, module)) {
            return;
        }

        CrosshairCanvas canvas = module.getCanvas();
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        int originX = guiWidth / 2 - CrosshairCanvas.SIZE / 2;
        int originY = guiHeight / 2 - CrosshairCanvas.SIZE / 2;

        if (module.isVanillaBlending()) {
            renderVanillaBlended(graphics, canvas, originX, originY);
            return;
        }

        int color = resolveDynamicColor(minecraft, module).getRGB();
        for (int y = 0; y < CrosshairCanvas.SIZE; y++) {
            for (int x = 0; x < CrosshairCanvas.SIZE; x++) {
                if (canvas.isPixelActive(x, y)) {
                    graphics.fill(originX + x, originY + y, originX + x + 1, originY + y + 1, color);
                }
            }
        }
    }

    private static void renderVanillaBlended(GuiGraphics graphics, CrosshairCanvas canvas, int originX, int originY) {
        for (int y = 0; y < CrosshairCanvas.SIZE; y++) {
            for (int x = 0; x < CrosshairCanvas.SIZE; x++) {
                if (canvas.isPixelActive(x, y)) {
                    graphics.fill(RenderPipelines.GUI_INVERT, originX + x, originY + y, originX + x + 1, originY + y + 1, 0xFFFFFFFF);
                }
            }
        }
    }

    private static Color resolveDynamicColor(Minecraft minecraft, CustomCrosshair module) {
        Color base = module.getCrosshairColor();
        if (!module.isDynamicColor()) {
            return base;
        }

        Entity target = null;
        HitResult hitResult = minecraft.hitResult;
        if (hitResult instanceof EntityHitResult entityHitResult) {
            target = entityHitResult.getEntity();
        }
        if (!(target instanceof LivingEntity living)) {
            return base;
        }
        if (living instanceof Player) {
            return module.getPlayersColor();
        }
        if (living instanceof Enemy) {
            return module.getHostileColor();
        }
        return module.getNeutralColor();
    }
}
