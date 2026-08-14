package universalmod.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.api.events.impl.HandledScreenEvent;
import universalmod.api.module.impl.render.Animations;
import universalmod.manager.Manager;
import universalmod.utils.serverhelper.ServerHelperItemInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {
    @Shadow
    @Final
    protected int imageWidth;

    @Shadow
    @Final
    protected int imageHeight;

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @org.spongepowered.asm.mixin.Unique
    private boolean universalmod$itemAnimationPosePushed;

    @Inject(method = "renderSlot", at = @At("HEAD"), require = 0)
    private void universalmod$beginItemAnimation(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        universalmod$itemAnimationPosePushed = false;
        if (!Animations.active(Animations.ITEMS) || slot == null) {
            return;
        }
        boolean focused = slot == hoveredSlot && slot.hasItem();
        float scale = Animations.getInstance().itemScale(slot, focused);
        graphics.pose().pushMatrix();
        graphics.pose().translate(slot.x + 8.0F, slot.y + 8.0F);
        graphics.pose().scale(scale);
        graphics.pose().translate(-(slot.x + 8.0F), -(slot.y + 8.0F));
        universalmod$itemAnimationPosePushed = true;
    }

    @Inject(method = "renderSlot", at = @At("RETURN"), require = 0)
    private void universalmod$endItemAnimation(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (universalmod$itemAnimationPosePushed) {
            graphics.pose().popMatrix();
            universalmod$itemAnimationPosePushed = false;
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void universalmod$handledScreenRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Manager.postEvent(new HandledScreenEvent(graphics, hoveredSlot, imageWidth, imageHeight));
    }

    @Inject(
            method = "renderSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"
            ),
            require = 0
    )
    private void universalmod$serverHelperSlotFill(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (graphics == null || slot == null) {
            return;
        }

        ItemStack stack = slot.getItem();
        int colorRgb = ServerHelperItemInfo.getSlotFillColor(stack);
        if (colorRgb == -1) {
            return;
        }

        double pulse = (Math.sin(System.currentTimeMillis() / 200.0D) + 1.0D) * 0.5D;
        int alpha = (int) (20.0D + 70.0D * pulse);
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, (alpha << 24) | colorRgb);
    }
}
