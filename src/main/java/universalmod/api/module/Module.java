package universalmod.api.module;

import net.minecraft.client.Minecraft;
import universalmod.IMinecraft;
import universalmod.api.events.bus.EventBus;
import universalmod.api.module.exception.ModuleException;
import universalmod.api.settings.Setting;
import universalmod.api.settings.bind.KeyBind;
import universalmod.api.settings.impl.BindSetting;
import universalmod.utils.lang.LanguageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module implements IMinecraft {
    private String name;
    private String description;
    private ModuleCategory category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Setting<?>> settingsView = Collections.unmodifiableList(settings);
    private final List<ModuleStateListener> listeners = new ArrayList<>();
    private final BindSetting bindSetting = new BindSetting("Bind", "Module key bind.", KeyBind.NONE, false);
    private KeyBind defaultBind = KeyBind.NONE;
    private EventBus eventBus;
    private boolean enabled;
    private boolean hidden;
    private boolean starred;

    protected Module() {
        bindSetting.addListener((changedSetting, oldValue, newValue) -> notifyChanged());
    }

    protected Module(String name, String description, ModuleCategory category) {
        this();
        configure(name, description, category);
    }

    final void configure(String name, String description, ModuleCategory category) {
        if (name == null || name.isBlank()) {
            throw new ModuleException("Module name cannot be empty");
        }
        this.name = name;
        this.description = description == null ? "" : description;
        this.category = category == null ? ModuleCategory.MISC : category;
    }

    public final String getName() {
        return name;
    }

    public final String getDisplayName() {
        return LanguageManager.translate(name);
    }

    public final String getDescription() {
        return description;
    }

    public final String getDisplayDescription() {
        return LanguageManager.translate(description);
    }

    public final ModuleCategory getCategory() {
        return category;
    }

    public final List<Setting<?>> getSettings() {
        return settingsView;
    }

    public final BindSetting getBindSetting() {
        return bindSetting;
    }

    public final KeyBind getBind() {
        return bindSetting.getValue();
    }

    public final void setBind(KeyBind bind) {
        bindSetting.setValue(bind);
    }

    protected final void setDefaultBind(KeyBind bind) {
        defaultBind = bind == null ? KeyBind.NONE : bind;
        setBind(defaultBind);
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            if (eventBus != null) {
                eventBus.register(this);
            }
            onEnable();
        } else {
            onDisable();
            if (eventBus != null) {
                eventBus.unregister(this);
            }
        }
        notifyChanged();
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void resetToDefaults() {
        // A profile switch must unregister the module before any of its bind
        // settings are reset.  Keeping an enabled module registered while its
        // bind temporarily becomes NONE exposes code -1 to its input handlers.
        setEnabled(false);
        setBind(defaultBind);
        setHidden(false);
        setStarred(false);
        for (Setting<?> setting : settings) {
            setting.reset();
        }
    }

    public final boolean isHidden() {
        return hidden;
    }

    public final void setHidden(boolean hidden) {
        if (this.hidden == hidden) {
            return;
        }
        this.hidden = hidden;
        notifyChanged();
    }

    public final boolean isStarred() {
        return starred;
    }

    public final void setStarred(boolean starred) {
        if (this.starred == starred) {
            return;
        }
        this.starred = starred;
        notifyChanged();
    }

    public final void addStateListener(ModuleStateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public final void initialize(EventBus eventBus) {
        this.eventBus = eventBus;
        if (enabled && eventBus != null) {
            eventBus.register(this);
        }
    }

    public void onTick(Minecraft client) {
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected final <S extends Setting<?>> S register(S setting) {
        if (setting == null) {
            throw new ModuleException("Cannot register null setting in " + name);
        }
        settings.add(setting);
        setting.addListener((changedSetting, oldValue, newValue) -> notifyChanged());
        return setting;
    }

    private void notifyChanged() {
        for (ModuleStateListener listener : listeners) {
            listener.onChanged(this);
        }
    }
}
