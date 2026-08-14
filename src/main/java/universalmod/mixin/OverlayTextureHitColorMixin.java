package universalmod.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.module.impl.render.HitColor;
import universalmod.utils.render.hitcolor.HitColorOverlayRegistry;
import universalmod.utils.render.hitcolor.HitColorOverlayReloadable;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureHitColorMixin implements HitColorOverlayReloadable {
    @Shadow
    @Final
    private DynamicTexture texture;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void universalmod$initHitColorOverlay(CallbackInfo ci) {
        HitColorOverlayRegistry.register(this);
        this.universalmod$reloadHitColorOverlay();
    }

    @Override
    @Unique
    public void universalmod$reloadHitColorOverlay() {
        NativeImage nativeImage = this.texture.getPixels();
        if (nativeImage == null) {
            return;
        }

        HitColor hitColor = HitColor.getInstance();
        boolean useCustomColor = hitColor != null && hitColor.isEnabled();

        for (int y = 0; y < 16; ++y) {
            for (int x = 0; x < 16; ++x) {
                if (y < 8) {
                    if (useCustomColor) {
                        int[] rgba = hitColor.getHitColorRGBA();
                        nativeImage.setPixel(x, y, universalmod$getHitColorInt(rgba[0], rgba[1], rgba[2], rgba[3]));
                    } else {
                        nativeImage.setPixel(x, y, -1291911168);
                    }
                } else {
                    int vanillaAlpha = (int) ((1.0F - (float) x / 15.0F * 0.75F) * 255.0F);
                    nativeImage.setPixel(x, y, universalmod$getColorInt(255, 255, 255, vanillaAlpha));
                }
            }
        }

        this.texture.upload();
    }

    @Unique
    private static int universalmod$getColorInt(int red, int green, int blue, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (clampedAlpha << 24) | (red << 16) | (green << 8) | blue;
    }

    @Unique
    private static int universalmod$getHitColorInt(int red, int green, int blue, int alpha) {
        int correctedAlpha = 255 - Math.max(0, Math.min(255, alpha));
        return (correctedAlpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
