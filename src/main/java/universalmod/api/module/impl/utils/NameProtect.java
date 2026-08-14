package universalmod.api.module.impl.utils;

import net.minecraft.client.Minecraft;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.TextFactoryEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.StringSetting;

public final class NameProtect extends Module {
    private final StringSetting nameSetting = register(new StringSetting("Name", "Replacement nickname", "Protected", 32));
    public NameProtect() {
        super("Name Protect", "Hides your nickname in rendered text", ModuleCategory.UTILS);
    }

    @SubscribeEvent
    private void onTextFactory(TextFactoryEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.getUser() != null) {
            event.replaceText(client.getUser().getName(), nameSetting.getValue());
        }
    }
}
