package universalmod.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.IMinecraft;
import universalmod.api.events.impl.WorldRenderEvent;
import universalmod.api.module.impl.render.Ambience;
import universalmod.api.module.impl.render.BlockOverlay;
import universalmod.api.module.impl.render.FogBlur;
import universalmod.api.module.impl.render.NoRender;
import universalmod.manager.Manager;
import universalmod.utils.render.Render3D;
import universalmod.utils.render.RenderCompatibility;
import universalmod.utils.render.ui.blur.BlurFramebuffer;
import universalmod.utils.render.post.fogblur.FogBlurRenderer;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin implements IMinecraft {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderBuffers renderBuffers;
    @Shadow private ClientLevel level;

    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$removeClouds(FrameGraphBuilder frameGraphBuilder, CloudStatus cloudStatus, Vec3 cameraPos, long time, float partialTick, int cloudCount, float cloudHeight, CallbackInfo ci) {
        if (NoRender.isActive("Clouds")) {
            ci.cancel();
        }
    }

    @Inject(method = "addWeatherPass", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$removeWeather(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice fog, CallbackInfo ci) {
        if (NoRender.isActive("Weather")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void universalmod$replaceBlockOutline(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, boolean renderBlockOutline, LevelRenderState levelRenderState, CallbackInfo ci) {
        if (BlockOverlay.shouldSuppressVanillaOutline(minecraft) || NoRender.isActive("Block Overlay")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderHead(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f frustumMatrix, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        PoseStack captureStack = new PoseStack();
        captureStack.mulPose(new Matrix4f(positionMatrix));
        Render3D.lastProjMat.set(projectionMatrix);
        Render3D.lastModMat.set(positionMatrix);
        Render3D.lastWorldSpaceMatrix.set(captureStack.last().pose());
        Render3D.setLastWorldSpaceEntry(captureStack.last());
        Render3D.setLastTickDelta(tickCounter.getGameTimeDeltaPartialTick(true));
        Render3D.setLastCameraPos(camera.position());
        Render3D.setLastCameraRotation(new Quaternionf(camera.rotation()));
        if (fogColor == null) {
            return;
        }

        Ambience ambience = Ambience.getInstance();
        if (ambience != null && ambience.shouldApplyCustomFog(camera)) {
            int customColor = ambience.getCustomFogColor();
            fogColor.set(
                    ((customColor >> 16) & 0xFF) / 255.0f,
                    ((customColor >> 8) & 0xFF) / 255.0f,
                    (customColor & 0xFF) / 255.0f,
                    fogColor.w
            );
        }

        BlurFramebuffer.setSkyFallbackColor(fogColor.x, fogColor.y, fogColor.z);
        FogBlurRenderer.setFallbackColor(fogColor.x, fogColor.y, fogColor.z);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onRenderWorld(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f frustumMatrix, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        RenderCompatibility.primeFromCurrentContext();

        PoseStack stack = new PoseStack();
        stack.mulPose(new Matrix4f(positionMatrix));

        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int prevDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int prevBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int prevBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int prevBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int prevBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int[] prevScissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, prevScissorBox);

        try {
            WorldRenderEvent event = new WorldRenderEvent(stack, tickCounter.getGameTimeDeltaPartialTick(true));
            Manager.postEvent(event);
            Render3D.onWorldRender(event);
        } finally {
            restoreRenderState(
                    prevDepthTest,
                    prevBlend,
                    prevCull,
                    prevScissor,
                    prevDepthMask,
                    prevDepthFunc,
                    prevBlendSrcRgb,
                    prevBlendDstRgb,
                    prevBlendSrcAlpha,
                    prevBlendDstAlpha,
                    prevScissorBox
            );
        }
    }

    @Inject(
            method = "method_62214",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void universalmod$fragEffectAfterTranslucent(GpuBufferSlice fog, LevelRenderState levelRenderState, ProfilerFiller profiler, Matrix4f positionMatrix,
                                                  ResourceHandle<RenderTarget> mainTarget, ResourceHandle<RenderTarget> translucentTarget, boolean renderBlockOutline,
                                                  ResourceHandle<RenderTarget> itemEntityTarget, ResourceHandle<RenderTarget> entityOutlineTarget, CallbackInfo ci) {
        if (mc.level == null || mc.player == null) {
            return;
        }

        RenderTarget target = mainTarget != null ? mainTarget.get() : minecraft.getMainRenderTarget();
        FogBlur fogBlur = FogBlur.getInstance();
        if (fogBlur != null && fogBlur.isEnabled()) {
            fogBlur.onAfterTranslucent(target);
        }

    }

    private void restoreRenderState(boolean depthTest, boolean blend, boolean cull, boolean scissor, boolean depthMask, int depthFunc, int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha, int[] scissorBox) {
        if (depthTest) {
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._disableDepthTest();
        }
        GlStateManager._depthMask(depthMask);
        GlStateManager._depthFunc(depthFunc);
        GlStateManager._colorMask(true, true, true, true);

        if (blend) {
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        } else {
            GlStateManager._disableBlend();
        }

        if (cull) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }

        if (scissor) {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        } else {
            GlStateManager._disableScissorTest();
        }
    }
}
