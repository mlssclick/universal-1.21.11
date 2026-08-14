package universalmod.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import universalmod.manager.Manager;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void universalmod$toggleClickGui(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (event.key() != GLFW.GLFW_KEY_UNKNOWN && window == this.minecraft.getWindow().handle() && this.minecraft.screen == null) {
            Manager.postEvent(new universalmod.api.events.impl.KeyEvent(this.minecraft.screen, InputConstants.Type.KEYSYM, event.key(), action));
        }
    }
}
