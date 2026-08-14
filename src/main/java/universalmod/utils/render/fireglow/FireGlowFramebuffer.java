package universalmod.utils.render.fireglow;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.Set;

public final class FireGlowFramebuffer {
    private static final Identifier EFFECT_ID = Identifier.fromNamespaceAndPath("universalmod", "legacy_fire_outline");

    private static TextureTarget maskFramebuffer;
    private static boolean maskWritten;

    private FireGlowFramebuffer() {
    }

    public static void beginFrame() {
        maskWritten = false;
        RenderTarget target = getFramebuffer();
        if (target != null) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), 0);
        }
    }

    public static void markMaskWritten() {
        maskWritten = true;
    }

    public static RenderTarget getFramebuffer() {
        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() == null) {
            return null;
        }

        int width = client.getWindow().getWidth();
        int height = client.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }

        if (maskFramebuffer == null) {
            maskFramebuffer = new TextureTarget("UNIVERSALMOD_fire_glow_mask", width, height, false);
        } else if (maskFramebuffer.width != width || maskFramebuffer.height != height) {
            maskFramebuffer.resize(width, height);
        }

        return maskFramebuffer;
    }

    public static void composite(GraphicsResourceAllocator allocator) {
        Minecraft client = Minecraft.getInstance();
        RenderTarget mainTarget = client.getMainRenderTarget();
        if (!maskWritten || maskFramebuffer == null || client.player == null || mainTarget == null || mainTarget.getColorTextureView() == null) {
            return;
        }

        PostChain processor = client.getShaderManager().getPostChain(EFFECT_ID, Set.of(PostChain.MAIN_TARGET_ID));
        if (processor == null) {
            return;
        }

        processor.process(maskFramebuffer, allocator);
        maskFramebuffer.blitAndBlendToTexture(mainTarget.getColorTextureView());
    }

    public static void close() {
        if (maskFramebuffer != null) {
            maskFramebuffer.destroyBuffers();
            maskFramebuffer = null;
        }
        maskWritten = false;
    }
}
