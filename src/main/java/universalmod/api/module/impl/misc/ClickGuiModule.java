package universalmod.api.module.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.bind.InputType;
import universalmod.api.settings.bind.KeyBind;
import universalmod.manager.Manager;

public final class ClickGuiModule extends Module {
    private static ClickGuiModule instance;

    public ClickGuiModule() {
        super("ClickGUI", "Opens the ClickGUI and lets you bind it.", ModuleCategory.MISC);
        instance = this;
        setDefaultBind(KeyBind.keyboard(GLFW.GLFW_KEY_RIGHT_SHIFT));
    }

    public static boolean matchesCloseKey(KeyEvent event) {
        return instance != null
                && event != null
                && instance.getBind().getType() == InputType.KEYBOARD
                && instance.getBind().getCode() == event.key();
    }

    public static boolean matchesCloseMouse(MouseButtonEvent event) {
        return instance != null
                && event != null
                && instance.getBind().getType() == InputType.MOUSE
                && instance.getBind().getCode() == event.button();
    }

    @Override
    protected void onEnable() {
        Minecraft client = mc;
        try {
            if (client != null && (client.screen == null || Manager.isClickGuiOpen(client))) {
                Manager.toggleClickGui(client);
            }
        } finally {
            setEnabled(false);
        }
    }
}
