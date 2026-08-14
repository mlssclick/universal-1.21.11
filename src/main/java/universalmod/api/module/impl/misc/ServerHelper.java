package universalmod.api.module.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import universalmod.api.settings.bind.KeyBind;
import universalmod.api.settings.impl.BindSetting;
import universalmod.utils.cooldown.HolyWorldHealingCooldown;
import universalmod.api.events.annotation.SubscribeEvent;
import universalmod.api.events.impl.DrawEvent;
import universalmod.api.events.impl.WorldRenderEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.utils.serverhelper.ServerHelperDetectionTracker;
import universalmod.utils.serverhelper.ServerHelperRenderer;

import java.awt.Color;
import java.util.EnumMap;

public final class ServerHelper extends Module {
    private static ServerHelper instance;

    private final BooleanSetting helperEnabled = register(new BooleanSetting("Enabled", "Enables HW helper logic.", true));
    private final BooleanSetting trapkaEnabled = register(new BooleanSetting("Trapka", "Shows trapka preview.", true));
    private final BooleanSetting explosionTrapEnabled = register(new BooleanSetting("Explosion Trap", "Shows explosion trap preview.", true));
    private final BooleanSetting stunEnabled = register(new BooleanSetting("Stun", "Shows stun preview and timer.", true));
    private final BooleanSetting stunMemoryEnabled = register(new BooleanSetting("Remember Stun Zone", "Remembers and displays stun zones.", true));
    private final BooleanSetting fillEnabled = register(new BooleanSetting("Fill", "Fills helper boxes.", false));
    private final BooleanSetting goldenSpawnersEnabled = register(new BooleanSetting("Golden Spawners", "Shows extended golden spawner NBT info.", true));
    private final BooleanSetting goldenSpawnersSlotFill = register(new BooleanSetting("Golden Spawners Slot Fill", "Fills slots with golden spawner info.", true));
    private final BooleanSetting compassCooldownsEnabled = register(new BooleanSetting("Compass Cooldowns", "Shows compass cooldowns in item names.", true));
    private final BooleanSetting compassCooldownsSlotFill = register(new BooleanSetting("Compass Cooldowns Slot Fill", "Fills slots for ready compass cooldowns.", true));
    private final BooleanSetting macrosExpanded = register(new BooleanSetting("Macros", "Shows Server Helper hotbar macro binds.", false));
    private final ColorSetting outlineColor = register(new ColorSetting("Outline Color", "Default helper outline color.", new Color(255, 255, 255, 255)));
    private final ColorSetting insideColor = register(new ColorSetting("Inside Color", "Color when a visible player is inside the zone.", new Color(255, 59, 48, 255)));
    private final EnumMap<MacroAction, BindSetting> macroBinds = new EnumMap<>(MacroAction.class);
    private final EnumMap<MacroAction, Boolean> macroKeyStates = new EnumMap<>(MacroAction.class);

    public ServerHelper() {
        super("Server Helper", "Provides helper overlays and zone tracking.", ModuleCategory.MISC);
        instance = this;
        ServerHelperDetectionTracker.init();
        trapkaEnabled.visibleWhen(helperEnabled::getValue);
        explosionTrapEnabled.visibleWhen(helperEnabled::getValue);
        stunEnabled.visibleWhen(helperEnabled::getValue);
        stunMemoryEnabled.visibleWhen(helperEnabled::getValue);
        fillEnabled.visibleWhen(helperEnabled::getValue);
        goldenSpawnersEnabled.visibleWhen(helperEnabled::getValue);
        goldenSpawnersSlotFill.visibleWhen(() -> helperEnabled.getValue() && goldenSpawnersEnabled.getValue());
        compassCooldownsEnabled.visibleWhen(helperEnabled::getValue);
        compassCooldownsSlotFill.visibleWhen(() -> helperEnabled.getValue() && compassCooldownsEnabled.getValue());
        macrosExpanded.visibleWhen(helperEnabled::getValue);
        outlineColor.visibleWhen(helperEnabled::getValue);
        insideColor.visibleWhen(helperEnabled::getValue);

        for (MacroAction action : MacroAction.values()) {
            BindSetting bind = register(new BindSetting(
                    action.bindSettingName(),
                    "Hotbar macro bind for " + action.translationKey() + ".",
                    KeyBind.NONE
            ));
            bind.visibleWhen(() -> helperEnabled.getValue() && macrosExpanded.getValue());
            macroBinds.put(action, bind);
            macroKeyStates.put(action, false);
        }
    }

    public static ServerHelper getInstance() {
        return instance;
    }

    public static boolean isTrackerEnabled() {
        ServerHelper helper = instance;
        return helper != null && helper.isEnabled() && helper.helperEnabled.getValue();
    }

    public static boolean isStunMemoryActive() {
        ServerHelper helper = instance;
        return helper != null && helper.isEnabled() && helper.helperEnabled.getValue() && helper.stunMemoryEnabled.getValue();
    }

    public boolean rendersTrapka() {
        return trapkaEnabled.getValue();
    }

    public boolean rendersExplosionTrap() {
        return explosionTrapEnabled.getValue();
    }

    public boolean rendersStun() {
        return stunEnabled.getValue();
    }

    public boolean remembersStun() {
        return stunMemoryEnabled.getValue();
    }

    public boolean fillsBoxes() {
        return fillEnabled.getValue();
    }

    public boolean showsGoldenSpawners() {
        return helperEnabled.getValue() && goldenSpawnersEnabled.getValue();
    }

    public boolean fillsGoldenSpawnerSlots() {
        return showsGoldenSpawners() && goldenSpawnersSlotFill.getValue();
    }

    public boolean showsCompassCooldowns() {
        return helperEnabled.getValue() && compassCooldownsEnabled.getValue();
    }

    public boolean fillsCompassCooldownSlots() {
        return showsCompassCooldowns() && compassCooldownsSlotFill.getValue();
    }

    public int outlineColor() {
        return outlineColor.getValue().getRGB();
    }

    public int insideColor() {
        return insideColor.getValue().getRGB();
    }

    public KeyBind getMacroBind(MacroAction action) {
        BindSetting setting = action == null ? null : macroBinds.get(action);
        return setting == null ? KeyBind.NONE : setting.getValue();
    }

    public void setMacroBind(MacroAction action, KeyBind bind) {
        BindSetting setting = action == null ? null : macroBinds.get(action);
        if (setting != null) {
            setting.setValue(bind == null ? KeyBind.NONE : bind);
        }
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null || client.getWindow() == null || client.screen != null) {
            resetMacroKeyStates();
            return;
        }

        long window = client.getWindow().handle();
        for (MacroAction action : MacroAction.values()) {
            KeyBind bind = getMacroBind(action);
            boolean down = bind.isDown(window);
            boolean wasDown = macroKeyStates.getOrDefault(action, false);
            if (down && !wasDown) {
                switchHotbarTo(client, action);
            }
            macroKeyStates.put(action, down);
        }
    }

    private void switchHotbarTo(Minecraft client, MacroAction action) {
        if (client == null || client.player == null || action == null) {
            return;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (matchesMacro(stack, action)) {

                client.player.getInventory().setSelectedSlot(slot);
                return;
            }
        }
    }

    private boolean matchesMacro(ItemStack stack, MacroAction action) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return switch (action) {
            case STUN -> stack.getItem() == Items.NETHER_STAR;
            case EXPLOSION_TRAP -> stack.getItem() == Items.PRISMARINE_SHARD;
            case TRAPKA -> stack.getItem() == Items.POPPED_CHORUS_FRUIT;
            case SNOWBALL -> stack.getItem() == Items.SNOWBALL;
            case HEALING_POTION -> stack.getItem() == Items.POTION && HolyWorldHealingCooldown.isHealingPotion(stack);
            case SPLASH_HEALING_POTION -> stack.getItem() == Items.SPLASH_POTION && HolyWorldHealingCooldown.isHealingPotion(stack);
            case BACKPACK -> stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
        };
    }

    private void resetMacroKeyStates() {
        for (MacroAction action : MacroAction.values()) {
            macroKeyStates.put(action, false);
        }
    }

    @Override
    protected void onDisable() {
        ServerHelperDetectionTracker.reset();
        resetMacroKeyStates();
    }

    @SubscribeEvent
    private void onWorldRender(WorldRenderEvent event) {
        ServerHelperRenderer.renderWorld(mc, event, this);
    }

    @SubscribeEvent
    private void onDraw(DrawEvent event) {
        ServerHelperRenderer.renderHud(mc, event, this);
    }

    public enum MacroAction {
        STUN("Macro Stun", "Macro Bind Stun"),
        EXPLOSION_TRAP("Macro Explosion Trap", "Macro Bind Explosion Trap"),
        TRAPKA("Macro Trapka", "Macro Bind Trapka"),
        SNOWBALL("Macro Snowball", "Macro Bind Snowball"),
        HEALING_POTION("Macro Healing Potion", "Macro Bind Healing Potion"),
        SPLASH_HEALING_POTION("Macro Splash Healing Potion", "Macro Bind Splash Healing Potion"),
        BACKPACK("Macro Backpack", "Macro Bind Backpack");

        private final String translationKey;
        private final String bindSettingName;

        MacroAction(String translationKey, String bindSettingName) {
            this.translationKey = translationKey;
            this.bindSettingName = bindSettingName;
        }

        public String translationKey() {
            return translationKey;
        }

        public String bindSettingName() {
            return bindSettingName;
        }
    }

}
