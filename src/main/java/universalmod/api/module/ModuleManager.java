package universalmod.api.module;

import net.minecraft.client.Minecraft;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.bus.EventBus;
import universalmod.api.events.impl.TickEvent;
import universalmod.api.module.impl.utils.AutoSprint;
import universalmod.api.module.impl.utils.EffectNotifier;
import universalmod.api.module.impl.utils.HotbarCooldowns;
import universalmod.api.module.impl.sounds.SoundReducer;
import universalmod.api.module.impl.misc.BetterChat;
import universalmod.api.module.impl.misc.ClickGuiModule;
import universalmod.api.module.impl.misc.CustomCrosshair;
import universalmod.api.module.impl.misc.Fullbright;
import universalmod.api.module.impl.misc.ServerHelper;
import universalmod.api.module.impl.misc.ShadersButton;
import universalmod.api.module.impl.misc.TapeMouse;
import universalmod.api.module.impl.other.CoinPrice;
import universalmod.api.module.impl.utils.FreeLook;
import universalmod.api.module.impl.utils.Friends;
import universalmod.api.module.impl.utils.NameProtect;
import universalmod.api.module.impl.utils.ItemScroller;
import universalmod.api.module.impl.utils.LockSlot;
import universalmod.api.module.impl.render.Ambience;
import universalmod.api.module.impl.render.Animations;
import universalmod.api.module.impl.render.AspectRatio;
import universalmod.api.module.impl.render.BlockOverlay;
import universalmod.api.module.impl.render.CustomHitBox;
import universalmod.api.module.impl.render.FiguraModels;
import universalmod.api.module.impl.utils.CustomDonate;
import universalmod.api.module.impl.misc.CustomTheme;
import universalmod.api.module.impl.render.FogBlur;
import universalmod.api.module.impl.render.Hands;
import universalmod.api.module.impl.render.HealthIndicator;
import universalmod.api.module.impl.render.HitColor;
import universalmod.api.module.impl.render.Hud;
import universalmod.api.module.impl.render.ItemPhysics;
import universalmod.api.module.impl.render.ItemReplacer;
import universalmod.api.module.impl.render.MotionBlur;
import universalmod.api.module.impl.render.InvisibleTags;
import universalmod.api.module.impl.render.NoRender;
import universalmod.api.module.impl.utils.Predictions;
import universalmod.api.module.impl.render.Emotions;
import universalmod.api.module.impl.render.FireGlow;
import universalmod.api.module.impl.render.RichDog;
import universalmod.api.module.impl.render.Scoreboard;
import universalmod.api.module.impl.render.SwingAnimation;
import universalmod.api.module.impl.utils.TotemCounter;
import universalmod.api.module.impl.utils.SaveKtLeave;
import universalmod.api.module.impl.render.TwoDItems;
import universalmod.api.module.impl.render.ViewModel;
import universalmod.api.module.impl.render.Waypoints;
import universalmod.api.module.impl.misc.Zoom;
import universalmod.api.module.impl.render.PingNametags;
import universalmod.api.settings.bind.KeyBind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ModuleManager {
    private final EventBus eventBus;
    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> byName = new HashMap<>();
    private final Map<Class<? extends Module>, Module> byType = new HashMap<>();
    private final Map<ModuleCategory, List<Module>> byCategory = new EnumMap<>(ModuleCategory.class);
    private final Map<ModuleCategory, List<Module>> byCategoryViews = new EnumMap<>(ModuleCategory.class);
    private Collection<Module> modulesView = List.of();
    private final Map<Module, Boolean> bindStates = new IdentityHashMap<>();
    private Runnable dirtyListener = () -> {};
    private int bindHandlingSuspensionDepth;

    public ModuleManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void init() {
        registerDefaults();
        eventBus.register(this);
    }

    public void setDirtyListener(Runnable dirtyListener) {
        this.dirtyListener = dirtyListener == null ? () -> {} : dirtyListener;
    }

    public void register(Module module) {
        String key = normalize(module.getName());
        if (byName.containsKey(key)) {
            return;
        }
        modules.add(module);
        byName.put(key, module);
        byType.putIfAbsent(module.getClass(), module);
        byCategory.computeIfAbsent(module.getCategory(), ignored -> new ArrayList<>()).add(module);
        module.addStateListener(changedModule -> dirtyListener.run());
        module.initialize(eventBus);
        sortViews();
    }

    public Collection<Module> getModules() {
        return modulesView;
    }

    public List<Module> getByCategory(ModuleCategory category) {
        return byCategoryViews.getOrDefault(category, List.of());
    }

    public Optional<Module> getByName(String name) {
        return Optional.ofNullable(byName.get(normalize(name)));
    }

    public <T extends Module> Optional<T> getByType(Class<T> type) {
        Module module = byType.get(type);
        if (type.isInstance(module)) {
            return Optional.of(type.cast(module));
        }
        for (Module candidate : modules) {
            if (type.isInstance(candidate)) {
                return Optional.of(type.cast(candidate));
            }
        }
        return Optional.empty();
    }

    public boolean isEnabled(Class<? extends Module> type) {
        return getByType(type).filter(Module::isEnabled).isPresent();
    }

    public void beginConfigApply() {
        bindHandlingSuspensionDepth++;
        bindStates.clear();
    }

    public void endConfigApply() {
        if (bindHandlingSuspensionDepth > 0) {
            bindHandlingSuspensionDepth--;
        }
        bindStates.clear();
    }

    @SubscribeEvent
    private void onTick(TickEvent.Post event) {
        Minecraft client = event.getClient();
        handleBinds(client);
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onTick(client);
            }
        }
    }

    private void registerDefaults() {
        register(new AutoSprint());
        register(new EffectNotifier());
        register(new HotbarCooldowns());
        register(new SoundReducer());
        register(new BetterChat());
        register(new ClickGuiModule());
        register(new CustomCrosshair());
        register(new Fullbright());
        register(new ServerHelper());
        register(new ShadersButton());
        register(new TapeMouse());
        register(new CoinPrice());
        register(new Ambience());
        register(new Animations());
        register(new AspectRatio());
        register(new BlockOverlay());
        register(new CustomHitBox());
        register(new FiguraModels());
        register(new CustomDonate());
        register(new CustomTheme());
        register(new FogBlur());
        register(new HealthIndicator());
        register(new HitColor());
        register(new Hud());
        register(new ItemPhysics());
        register(new ItemReplacer());
        register(new MotionBlur());
        register(new PingNametags());
        register(new TwoDItems());
        register(new InvisibleTags());
        register(new Predictions());
        register(new FireGlow());
        register(new RichDog());
        register(new Scoreboard());
        register(new Hands());
        register(new SwingAnimation());
        register(new ViewModel());
        register(new Waypoints());
        register(new Zoom());
        register(new Emotions());
        register(new TotemCounter());
        register(new SaveKtLeave());
        register(new NoRender());
        register(new FreeLook());
        register(new Friends());
        register(new NameProtect());
        register(new ItemScroller());
        register(new LockSlot());
    }

    private void handleBinds(Minecraft client) {
        if (bindHandlingSuspensionDepth > 0) {
            bindStates.clear();
            return;
        }
        if (client == null || client.getWindow() == null || client.screen != null) {
            bindStates.clear();
            return;
        }

        long handle = client.getWindow().handle();
        for (Module module : modules) {
            KeyBind bind = module.getBind();
            if (bind == null || !bind.isBound()) {
                bindStates.remove(module);
                continue;
            }
            boolean down = bind.isDown(handle);
            boolean wasDown = bindStates.getOrDefault(module, false);
            if (down && !wasDown) {
                module.toggle();
            }
            bindStates.put(module, down);
        }
    }

    private void register(Module module, String name, String description, ModuleCategory category) {
        module.configure(name, description, category);
        register(module);
    }

    private void sortViews() {
        modules.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        for (List<Module> categoryModules : byCategory.values()) {
            categoryModules.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        }
        modulesView = Collections.unmodifiableList(modules);
        byCategoryViews.clear();
        for (Map.Entry<ModuleCategory, List<Module>> entry : byCategory.entrySet()) {
            byCategoryViews.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
    }

    private String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
