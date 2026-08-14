package universalmod.api.module.impl.render;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.ButtonSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.screens.clickgui.impl.ClickGuiController;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemReplacer extends Module {
    public static final String[] MODELS = {
            "abominableblade", "abominablegreatsaber", "abominablescythe", "aciddemon", "amethyst_shuriken",
            "ancient_royal_great_sword", "aquantic_sacred_blade", "aquantictrident", "arcanethyst", "ashura_blade",
            "bloodedge", "bloodydeath", "bramblethorn", "cariansword",
            "chrono_blade", "corruptedmythicblade", "creationsplitter", "crescentrose", "cyberkatana", "cybermantisblade",
            "cybernetickatana", "cyberneticknife", "cyberneticsawblade", "cybersword", "dainsleif", "dark_blade",
            "dark_cleaver", "death_knight_dagger", "death_knight_sword", "demigodsunholyblade", "demigodsunholyhalberd",
            "demonicblade", "demoniccleaver", "demonlordsgreataxe", "demonlordsword", "divine_justice", "divine_reaper",
            "divineaxerhitta", "divinepunisher", "dragonslayingblade", "edgeoftheastralplane", "emberblade", "enigma",
            "epicsword", "estoc", "excalibur", "fallengodspear", "fallengodsword", "floral_longsword", "floral_sabre",
            "forest_guardian_glaive", "frostaxe", "frostblade", "frostscythe", "greenscythe", "hearthflame", "herosword",
            "holymoonlightsword", "hornetsneedle", "icewhisper", "jadehalberd", "katana", "legendarysword", "longsword",
            "magiscythe", "masamune", "mjolnir", "moltenblade", "moltensword", "muramasa", "mysticalspellblade",
            "mythicblade", "partisan", "pharaohs_treasure", "pheonixgrace", "powerfusehammer", "powerfusesword",
            "requiem_of_hell", "ribboncleaver", "righteous_relic", "riversofblood", "royalchakram", "royalrapier", "sabre",
            "scissorblade", "sculkcleaver", "sculkscythe", "sculksword", "sentinels_will", "silverine_blade",
            "soul_collector", "soul_devourer", "soulclaws", "souledge", "soulharvester", "soulrender", "soulstealer",
            "steelsword", "stop_sign", "stormbringer", "storms_edge", "sunbreak", "tengensblade",
            "terrablade", "thousanddemondaggers", "thunderbrand", "thunderbringer", "vampiricneedle",
            "wakizashi", "watcher_claymore", "watching_warglaive", "waxweaver", "whisperwind", "wickpiercer", "yoru"
    };

    private static ItemReplacer instance;
    private final ModeSetting model = register(new ModeSetting("Item Replacer Model", "Selected replacement sword model.", false, false, MODELS[0], MODELS));
    private final Map<String, ItemStack> previews = new LinkedHashMap<>();

    public ItemReplacer() {
        super("Item Replacer", "Replaces rendered sword models.", ModuleCategory.MISC);
        instance = this;
        model.setVisible(false);
        for (String value : MODELS) {
            ItemStack stack = Items.STICK.getDefaultInstance();
            stack.set(DataComponents.ITEM_MODEL, modelId(value));
            previews.put(value, stack);
        }
        register(new ButtonSetting("Model", "Opens the item model gallery.", () -> ClickGuiController.openItemReplacerEditor(this)));
    }

    public static ItemReplacer getInstance() {
        return instance;
    }

    public String getSelectedModel() {
        return model.getValue();
    }

    public void setSelectedModel(String value) {
        model.setValue(value);
    }

    public ItemStack getPreviewStack(String value) {
        ItemStack stack = previews.get(value);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    public Identifier getSelectedModelId() {
        return modelId(model.getValue());
    }

    public ItemStack apply(ItemStack original) {
        if (!isEnabled() || original == null || original.isEmpty() || !original.is(ItemTags.SWORDS)) {
            return original;
        }
        ItemStack replacement = original.copy();
        replacement.set(DataComponents.ITEM_MODEL, getSelectedModelId());
        return replacement;
    }

    private static Identifier modelId(String value) {
        return Identifier.fromNamespaceAndPath("mre", "item_replacer/sword/" + value);
    }
}
