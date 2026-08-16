package universalmod.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import universalmod.screens.clickgui.impl.ClickGuiWorldAnimation;
import universalmod.utils.render.ui.blur.BlurFramebuffer;
import universalmod.utils.render.ui.emotionwheel.EmotionWheelArcRenderer;
import universalmod.utils.render.ui.glass.GlassRenderer;
import universalmod.utils.render.ui.hudchrome.HudChromeRenderer;
import universalmod.utils.render.ui.image.ImageRenderer;
import universalmod.utils.render.ui.outline.outlinedefault.DefaultOutlineRenderer;
import universalmod.utils.render.ui.outline.outlineglass.GlassOutlineRenderer;
import universalmod.utils.render.ui.rectangle.rectdefault.DefaultRectangleRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    private RenderPass universalmod$currentRenderPass;
    private boolean universalmod$blurDrawActive;
    private boolean universalmod$glassDrawActive;
    private boolean universalmod$hudChromeDrawActive;
    private boolean universalmod$glassOutlineDrawActive;
    private boolean universalmod$rectangleDrawActive;
    private boolean universalmod$outlineDrawActive;
    private boolean universalmod$imageDrawActive;
    private boolean universalmod$emotionWheelArcDrawActive;

    @Redirect(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V", ordinal = 0))
    private void universalmod$useClickGuiWorldProjection(GpuBufferSlice projection, ProjectionType projectionType) {
        GpuBufferSlice override = ClickGuiWorldAnimation.projectionOverride();
        if (override != null) {
            RenderSystem.setProjectionMatrix(override, ProjectionType.PERSPECTIVE);
            return;
        }
        RenderSystem.setProjectionMatrix(projection, projectionType);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void universalmod$beginBlurFrame(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        BlurFramebuffer.getInstance().beginGuiFrame();
        GlassRenderer.getInstance().beginGuiFrame();
        HudChromeRenderer.getInstance().beginGuiFrame();
        GlassOutlineRenderer.getInstance().beginGuiFrame();
        DefaultRectangleRenderer.getInstance().beginGuiFrame();
        DefaultOutlineRenderer.getInstance().beginGuiFrame();
        ImageRenderer.getInstance().beginGuiFrame();
        EmotionWheelArcRenderer.getInstance().beginGuiFrame();
    }

    @Inject(method = "prepare", at = @At("HEAD"))
    private void universalmod$preparePendingBlurResources(CallbackInfo ci) {
        BlurFramebuffer.getInstance().preparePending();
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void universalmod$prepareRenderUniforms(CallbackInfo ci) {
        BlurFramebuffer.getInstance().prepareBuffers();
        GlassRenderer.getInstance().prepareBuffers();
        HudChromeRenderer.getInstance().prepareBuffers();
        GlassOutlineRenderer.getInstance().prepareBuffers();
        DefaultRectangleRenderer.getInstance().prepareBuffers();
        DefaultOutlineRenderer.getInstance().prepareBuffers();
        ImageRenderer.getInstance().prepareBuffers();
        EmotionWheelArcRenderer.getInstance().prepareBuffers();
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void universalmod$prepareBlurCapture(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        BlurFramebuffer.getInstance().prepareGuiDraw();
    }

    @Inject(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V", shift = At.Shift.BEFORE))
    private void universalmod$prepareBlurCaptureAfterBeforeBlur(GpuBufferSlice fogBuffer, CallbackInfo ci) {
        BlurFramebuffer.getInstance().prepareGuiDraw();
    }

    @Redirect(method = "executeDraw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void universalmod$trackPipeline(RenderPass renderPass, RenderPipeline pipeline) {
        universalmod$currentRenderPass = renderPass;
        universalmod$blurDrawActive = BlurFramebuffer.getInstance().isBlurPipeline(pipeline);
        universalmod$glassDrawActive = GlassRenderer.getInstance().isGlassPipeline(pipeline);
        universalmod$hudChromeDrawActive = HudChromeRenderer.getInstance().isPipeline(pipeline);
        universalmod$glassOutlineDrawActive = GlassOutlineRenderer.getInstance().isGlassOutlinePipeline(pipeline);
        universalmod$rectangleDrawActive = DefaultRectangleRenderer.getInstance().isRectanglePipeline(pipeline);
        universalmod$outlineDrawActive = DefaultOutlineRenderer.getInstance().isOutlinePipeline(pipeline);
        universalmod$imageDrawActive = ImageRenderer.getInstance().isImagePipeline(pipeline);
        universalmod$emotionWheelArcDrawActive = EmotionWheelArcRenderer.getInstance().isPipeline(pipeline);
        renderPass.setPipeline(pipeline);
    }

    @Inject(method = "executeDraw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V", shift = At.Shift.BEFORE))
    private void universalmod$bindBlurParams(@Coerce Object draw, RenderPass renderPass, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType, CallbackInfo ci) {
        if (universalmod$blurDrawActive && universalmod$currentRenderPass != null) {
            BlurFramebuffer.getInstance().bindBlurParams(universalmod$currentRenderPass);
        }
        if (universalmod$glassDrawActive && universalmod$currentRenderPass != null) {
            GlassRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$hudChromeDrawActive && universalmod$currentRenderPass != null) {
            HudChromeRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$glassOutlineDrawActive && universalmod$currentRenderPass != null) {
            GlassOutlineRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$rectangleDrawActive && universalmod$currentRenderPass != null) {
            DefaultRectangleRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$outlineDrawActive && universalmod$currentRenderPass != null) {
            DefaultOutlineRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$imageDrawActive && universalmod$currentRenderPass != null) {
            ImageRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
        if (universalmod$emotionWheelArcDrawActive && universalmod$currentRenderPass != null) {
            EmotionWheelArcRenderer.getInstance().bindParams(universalmod$currentRenderPass);
        }
    }

    @Inject(method = "executeDraw", at = @At("RETURN"))
    private void universalmod$clearTrackedPipeline(@Coerce Object draw, RenderPass renderPass, GpuBuffer gpuBuffer, VertexFormat.IndexType indexType, CallbackInfo ci) {
        universalmod$clearTrackedState();
    }

    private void universalmod$clearTrackedState() {
        universalmod$currentRenderPass = null;
        universalmod$clearTrackedFlags();
    }

    private void universalmod$clearTrackedFlags() {
        universalmod$blurDrawActive = false;
        universalmod$glassDrawActive = false;
        universalmod$hudChromeDrawActive = false;
        universalmod$glassOutlineDrawActive = false;
        universalmod$rectangleDrawActive = false;
        universalmod$outlineDrawActive = false;
        universalmod$imageDrawActive = false;
        universalmod$emotionWheelArcDrawActive = false;
    }
}
