package universalmod.manager;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import universalmod.api.command.CommandManager;
import universalmod.api.config.ConfigManager;
import universalmod.api.drag.core.ElementManager;
import universalmod.api.events.Event;
import universalmod.api.events.bus.EventBus;
import universalmod.api.events.impl.TickEvent;
import universalmod.api.module.ModuleManager;
import universalmod.screens.clickgui.ClickGui;
import universalmod.utils.network.AnarchySwitcher;
import universalmod.utils.repository.friend.FriendUtils;

public class Manager {
    private static Manager instance;

    private EventBus eventBus;
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private boolean clickGuiTextWarmed;

    public void initClient() {
        instance = this;
        eventBus = new EventBus();
        FriendUtils.load();
        moduleManager = new ModuleManager(eventBus);
        moduleManager.init();
        commandManager = new CommandManager();
        commandManager.init();
        configManager = new ConfigManager(moduleManager);
        moduleManager.setDirtyListener(configManager::markDirty);
        configManager.init();
        ElementManager.getInstance().load();
        configManager.loadAll();
        eventBus.register(configManager);
        eventBus.register(commandManager);

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            warmClickGuiText();
            ClickGui.validateState(client);
            ClickGui.tickMovementKeys();
            postEvent(new TickEvent.Pre(client));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AnarchySwitcher.getInstance().tick(client);
            postEvent(new TickEvent.Post(client));
        });
    }

    public static Manager getInstance() {
        return instance;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public static ModuleManager getModules() {
        return instance == null ? null : instance.moduleManager;
    }

    public static <T extends Event> T postEvent(T event) {
        if (instance == null || instance.eventBus == null) {
            return event;
        }
        return instance.eventBus.post(event);
    }

    public static void toggleClickGui(Minecraft client) {
        if (client.screen instanceof ClickGui) {
            client.screen.onClose();
            return;
        }
        client.setScreen(new ClickGui());
    }

    public static boolean isClickGuiOpen(Minecraft client) {
        return client != null && client.screen instanceof ClickGui;
    }

    private void warmClickGuiText() {
        if (clickGuiTextWarmed) {
            return;
        }
        // MSDF atlases are resource-backed and warm safely on the client/render thread.
        clickGuiTextWarmed = ClickGui.warmupText();
    }
}
