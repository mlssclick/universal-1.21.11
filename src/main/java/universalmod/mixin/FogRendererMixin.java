package universalmod.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import universalmod.api.module.impl.render.Ambience;
import universalmod.api.module.impl.render.NoRender;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Unique
    private static final float UNREACHABLE = 1_000_000.0F;

    @Unique
    private static final float NO_FOG_START = 1_000_000.0F;

    @Unique
    private static final float NO_FOG_END = 1_000_001.0F;

    @Unique
    private static final String SODIUM_FOG_PARAMETERS =
            "net.caffeinemc.mods.sodium.client.util.FogParameters";

    @Shadow
    @Final
    private MappableRingBuffer regularBuffer;

    @Shadow
    @Final
    private static int FOG_UBO_SIZE;

    @Unique
    private boolean universalmod$customFogActive;

    @Unique
    private boolean universalmod$noRenderFogActive;

    @Unique private float universalmod$vanillaFogRed;
    @Unique private float universalmod$vanillaFogGreen;
    @Unique private float universalmod$vanillaFogBlue;
    @Unique private float universalmod$vanillaFogAlpha = 1.0F;

    @Unique
    private static Constructor<?> universalmod$sodiumParametersConstructor;

    @Unique
    private static Field universalmod$sodiumParametersField;

    @Unique
    private static boolean universalmod$sodiumLookupFailed;

    @Invoker("updateBuffer")
    abstract void universalmod$writeBuffer(
            ByteBuffer buffer,
            int offset,
            Vector4f color,
            float environmentalStart,
            float environmentalEnd,
            float renderDistanceStart,
            float renderDistanceEnd,
            float skyEnd,
            float cloudEnd
    );

    @Inject(method = "setupFog", at = @At("HEAD"))
    private void universalmod$selectFogMode(
            Camera camera,
            int viewDistance,
            DeltaTracker deltaTracker,
            float skyDarkness,
            ClientLevel world,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        Ambience ambience = Ambience.getInstance();
        this.universalmod$customFogActive =
                ambience != null && ambience.shouldApplyCustomFog(camera);
        this.universalmod$noRenderFogActive =
                NoRender.shouldRemoveVanillaFog(camera);
    }

    @Redirect(
            method = "setupFog",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
            )
    )
    private void universalmod$replaceUploadedFog(
            FogRenderer ignored,
            ByteBuffer buffer,
            int offset,
            Vector4f vanillaColor,
            float environmentalStart,
            float environmentalEnd,
            float renderDistanceStart,
            float renderDistanceEnd,
            float skyEnd,
            float cloudEnd
    ) {
        if (vanillaColor != null) {
            this.universalmod$vanillaFogRed = vanillaColor.x;
            this.universalmod$vanillaFogGreen = vanillaColor.y;
            this.universalmod$vanillaFogBlue = vanillaColor.z;
            this.universalmod$vanillaFogAlpha = vanillaColor.w;
        } else {
            this.universalmod$vanillaFogRed = 0.0F;
            this.universalmod$vanillaFogGreen = 0.0F;
            this.universalmod$vanillaFogBlue = 0.0F;
            this.universalmod$vanillaFogAlpha = 1.0F;
        }

        if (this.universalmod$customFogActive) {
            Ambience ambience = Ambience.getInstance();
            if (ambience != null) {
                float distance = ambience.getFogDistance();
                float start = ambience.getFogStart();

                this.universalmod$writeBuffer(
                        buffer,
                        offset,
                        universalmod$color(ambience, 1.0F),
                        0.0F,
                        UNREACHABLE,
                        start,
                        distance,
                        distance,
                        distance
                );
                return;
            }
        }

        if (this.universalmod$noRenderFogActive) {

            this.universalmod$writeBuffer(
                    buffer,
                    offset,
                    vanillaColor,
                    NO_FOG_START,
                    NO_FOG_END,
                    NO_FOG_START,
                    NO_FOG_END,
                    NO_FOG_END,
                    NO_FOG_END
            );
            return;
        }

        this.universalmod$writeBuffer(
                buffer,
                offset,
                vanillaColor,
                environmentalStart,
                environmentalEnd,
                renderDistanceStart,
                renderDistanceEnd,
                skyEnd,
                cloudEnd
        );
    }

    @Inject(
            method = "setupFog",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void universalmod$replaceSodiumFog(
            Camera camera,
            int viewDistance,
            DeltaTracker deltaTracker,
            float skyDarkness,
            ClientLevel world,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        if ((!this.universalmod$customFogActive && !this.universalmod$noRenderFogActive)
                || universalmod$sodiumLookupFailed) {
            return;
        }

        try {
            if (universalmod$sodiumParametersConstructor == null
                    || universalmod$sodiumParametersField == null) {
                universalmod$findSodiumParameters();
            }

            Object parameters;
            if (this.universalmod$customFogActive) {
                Ambience ambience = Ambience.getInstance();
                if (ambience == null) {
                    return;
                }

                int rgb = ambience.getCustomFogColor() & 0x00FFFFFF;
                parameters = universalmod$sodiumParametersConstructor.newInstance(
                        ((rgb >> 16) & 0xFF) / 255.0F,
                        ((rgb >> 8) & 0xFF) / 255.0F,
                        (rgb & 0xFF) / 255.0F,
                        1.0F,
                        0.0F,
                        UNREACHABLE,
                        ambience.getFogStart(),
                        ambience.getFogDistance()
                );
            } else {

                parameters = universalmod$sodiumParametersConstructor.newInstance(
                        this.universalmod$vanillaFogRed,
                        this.universalmod$vanillaFogGreen,
                        this.universalmod$vanillaFogBlue,
                        this.universalmod$vanillaFogAlpha,
                        NO_FOG_START,
                        NO_FOG_END,
                        NO_FOG_START,
                        NO_FOG_END
                );
            }

            universalmod$sodiumParametersField.set(this, parameters);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            universalmod$sodiumLookupFailed = true;
        }
    }

    @Unique
    private static void universalmod$findSodiumParameters() throws ReflectiveOperationException {
        Class<?> parameterType = Class.forName(
                SODIUM_FOG_PARAMETERS,
                false,
                FogRendererMixin.class.getClassLoader()
        );
        Constructor<?> constructor = parameterType.getDeclaredConstructor(
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class
        );

        for (Field field : FogRenderer.class.getDeclaredFields()) {
            if (field.getType() == parameterType) {
                field.setAccessible(true);
                universalmod$sodiumParametersConstructor = constructor;
                universalmod$sodiumParametersField = field;
                return;
            }
        }

        throw new NoSuchFieldException("Sodium fog parameters");
    }

    @Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
    private void universalmod$replaceClearColor(
            Camera camera,
            float tickDelta,
            ClientLevel world,
            int viewDistance,
            float skyDarkness,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        Ambience ambience = Ambience.getInstance();
        if (ambience == null || !ambience.shouldApplyCustomFog(camera)) {
            return;
        }

        Vector4f original = cir.getReturnValue();
        if (original != null) {
            cir.setReturnValue(universalmod$color(ambience, original.w));
        }
    }

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void universalmod$selectFogBuffer(
            FogRenderer.FogMode mode,
            CallbackInfoReturnable<GpuBufferSlice> cir
    ) {
        if (mode == FogRenderer.FogMode.WORLD && this.universalmod$customFogActive) {
            cir.setReturnValue(this.regularBuffer.currentBuffer().slice(0L, FOG_UBO_SIZE));
        }
    }

    @Unique
    private static Vector4f universalmod$color(Ambience ambience, float alpha) {
        int rgb = ambience.getCustomFogColor() & 0x00FFFFFF;
        return new Vector4f(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F,
                alpha
        );
    }
}
