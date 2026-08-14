package universalmod.utils.serverhelper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import universalmod.api.module.impl.misc.ServerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerHelperItemInfo {
    private static final String ABSENT = "отсутствует";
    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+(?:[\\.,]\\d+)?)\\s*%");

    private ServerHelperItemInfo() {
    }

    public static void appendTooltip(ItemStack stack, List<Component> lines) {
        if (stack == null || lines == null) {
            return;
        }

        if (shouldShowGoldenSpawners()) {
            CompoundTag nbt = getSpawnerData(stack);
            if (nbt != null) {
                appendSpawnerTooltip(nbt, lines);
            }
        }
    }

    public static Component appendCompassCooldownToName(ItemStack stack, Component original) {
        if (!shouldShowCompassCooldowns() || !isCompassCooldownStack(stack)) {
            return original;
        }

        Long cooldownEnd = getCompassCooldownEnd(stack);
        if (cooldownEnd == null) {
            return original;
        }

        long remainingMs = Math.max(0L, cooldownEnd - System.currentTimeMillis());
        String suffix = " (" + (remainingMs == 0L ? "готово" : formatDuration(remainingMs)) + ")";
        int color = remainingMs == 0L ? 0x55FF55 : 0xFF5555;
        MutableComponent name = original == null ? Component.empty() : original.copy();
        return name.append(Component.literal(suffix).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
    }

    public static boolean isCompassCooldownOverlayStack(ItemStack stack) {
        return shouldShowCompassCooldowns() && isCompassCooldownStack(stack) && getCompassCooldownEnd(stack) != null;
    }

    public static int getSlotFillColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }

        ServerHelper helper = ServerHelper.getInstance();
        if (helper == null || !helper.isEnabled()) {
            return -1;
        }

        if (helper.fillsGoldenSpawnerSlots() && isSpawnerStack(stack)) {
            Float chance = getDestructionChance(stack);
            if (chance != null) {
                return destructionChanceColor(chance);
            }

            CompoundTag nbt = getSpawnerData(stack);
            if (nbt == null || !containsKey(nbt, "gs_sword_item")) {
                return -1;
            }

            SwordInfo sword = readSwordInfo(nbt);
            if (sword.hasFarmerEnchant) {
                return 0x00FF00;
            }
            return swordColor(sword.rawType);
        }

        if (helper.fillsCompassCooldownSlots() && isCompassCooldownStack(stack)) {
            Long cooldownEnd = getCompassCooldownEnd(stack);
            if (cooldownEnd != null && System.currentTimeMillis() >= cooldownEnd) {
                return 0x00FF00;
            }
        }

        return -1;
    }

    public static boolean isSpawnerStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.SPAWNER);
    }

    public static CompoundTag getSpawnerData(ItemStack stack) {
        if (!isSpawnerStack(stack)) {
            return null;
        }

        CompoundTag tag = customData(stack);
        if (tag == null) {
            return null;
        }

        return findSpawnerData(tag, 0);
    }

    private static void appendSpawnerTooltip(CompoundTag nbt, List<Component> lines) {
        String mobType = translateMobName(getString(nbt, "gs_mob_type", ""));
        int eggs = getInt(nbt, "gs_total_eggs", 0);
        int killsTotal = getInt(nbt, "gs_kills_total", 0);
        int killsRemain = getInt(nbt, "gs_kills_remaining", 0);
        String farmer = getString(nbt, "gs_farmer", ABSENT);
        SwordInfo sword = readSwordInfo(nbt);

        lines.add(Component.empty());

        int swordColor = swordColor(sword.rawType);
        int nameColor = sword.type.equals(ABSENT) ? 0xAAAAAA : swordColor;
        addLine(lines, "Моб: ", mobType, 0x55FF7F);
        addLine(lines, "Меч: ", sword.name, nameColor);
        addLine(lines, "Тип меча: ", sword.type, swordColor);

        if (sword.enchants.isEmpty()) {
            addLine(lines, "Чары меча: ", "отсутствуют", 0xAAAAAA);
        } else {
            addLine(lines, "Чары меча:", "", 0xFFFFFF);
            for (int i = 0; i < sword.enchants.size(); i += 2) {
                String enchants = "  • " + sword.enchants.get(i);
                if (i + 1 < sword.enchants.size()) {
                    enchants += ", " + sword.enchants.get(i + 1);
                }
                addLine(lines, "", enchants, 0xFF55FF);
            }
        }

        int farmerColor = farmer.equals(ABSENT) ? 0xFF5555 : 0x55FF55;
        addLine(lines, "Фармер: ", farmer, farmerColor);
        addLine(lines, "Яйца: ", String.valueOf(eggs), 0xFFFF55);
        addLine(lines, "Киллы всего: ", formatNumber(killsTotal), 0x55FF55);
        addLine(lines, "Киллы осталось: ", formatNumber(killsRemain), 0xFF5555);

        lines.add(Component.empty());
    }

    private static boolean shouldShowGoldenSpawners() {
        ServerHelper helper = ServerHelper.getInstance();
        return helper != null && helper.isEnabled() && helper.showsGoldenSpawners();
    }

    private static boolean shouldShowCompassCooldowns() {
        ServerHelper helper = ServerHelper.getInstance();
        return helper != null && helper.isEnabled() && helper.showsCompassCooldowns();
    }

    private static boolean isCompassCooldownStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.is(Items.COMPASS) || stack.is(Items.CLOCK));
    }

    private static Long getCompassCooldownEnd(ItemStack stack) {
        CompoundTag tag = customData(stack);
        if (tag == null) {
            return null;
        }

        CompoundTag radarData = null;
        if (tag.contains("region-radar")) {
            radarData = tag.getCompoundOrEmpty("region-radar");
        } else if (tag.contains("region_radar")) {
            radarData = tag.getCompoundOrEmpty("region_radar");
        }

        if (radarData == null || !radarData.contains("delay")) {
            return null;
        }

        return radarData.getLongOr("delay", 0L);
    }

    private static String formatDuration(long remainingMs) {
        long totalSeconds = (remainingMs + 999L) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = totalSeconds % 3600L / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0L) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static Float getDestructionChance(ItemStack stack) {
        CompoundTag tag = customData(stack);
        return tag == null ? null : findDestructionChance(tag, 0);
    }

    private static SwordInfo readSwordInfo(CompoundTag nbt) {
        SwordInfo info = new SwordInfo();
        String swordJson = getString(nbt, "gs_sword_item", "");
        if (swordJson.isEmpty()) {
            return info;
        }

        try {
            JsonObject swordObj = JsonParser.parseString(swordJson).getAsJsonObject();
            if (swordObj.has("type")) {
                info.rawType = swordObj.get("type").getAsString();
                info.type = translateSwordType(info.rawType);
            }
            if (swordObj.has("name")) {
                info.name = swordObj.get("name").getAsString().replaceAll("§[0-9a-fk-orx]", "");
            }
            if (swordObj.has("enchants")) {
                for (Map.Entry<String, JsonElement> entry : swordObj.getAsJsonObject("enchants").entrySet()) {
                    String enchantKey = entry.getKey();
                    String lower = enchantKey.toLowerCase();
                    if (lower.contains("farmer") || lower.contains("фермер")) {
                        info.hasFarmerEnchant = true;
                    }
                    info.enchants.add(translateEnchant(enchantKey) + " " + entry.getValue().getAsInt());
                }
            }
        } catch (Exception ignored) {
        }

        return info;
    }

    private static void addLine(List<Component> lines, String key, String value, int valueHex) {
        MutableComponent prefix = color("▍", 0xFCE400).withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE);
        MutableComponent keyText = color(key.isEmpty() ? " " : " " + key, 0xFFFFFF);
        MutableComponent valueText = color(value, valueHex);
        lines.add(Component.empty().append(prefix).append(keyText).append(valueText));
    }

    private static MutableComponent color(String text, int rgb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    private static Float findDestructionChance(CompoundTag tag, int depth) {
        if (tag == null || depth > 4) {
            return null;
        }

        for (String key : tag.keySet()) {
            Tag child = tag.get(key);
            if (child instanceof CompoundTag compound) {
                Float result = findDestructionChance(compound, depth + 1);
                if (result != null) {
                    return result;
                }
                continue;
            }
            if (child instanceof ListTag list) {
                Float result = findDestructionChance(list);
                if (result != null) {
                    return result;
                }
                continue;
            }
            Float result = parseDestructionChanceLine(String.valueOf(child));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static Float findDestructionChance(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            Tag child = list.get(i);
            if (child instanceof CompoundTag compound) {
                Float result = findDestructionChance(compound, 0);
                if (result != null) {
                    return result;
                }
                continue;
            }
            if (child instanceof ListTag nested) {
                Float result = findDestructionChance(nested);
                if (result != null) {
                    return result;
                }
                continue;
            }
            Float result = parseDestructionChanceLine(list.getStringOr(i, ""));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static Float parseDestructionChanceLine(String rawLine) {
        String line = normalizeTooltipLine(rawLine).toLowerCase();
        if (!line.contains("уничтож") && !line.contains("destroy")) {
            return null;
        }

        Matcher matcher = PERCENT_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        try {
            float chance = Float.parseFloat(matcher.group(1).replace(',', '.'));
            return Math.max(0.0F, Math.min(100.0F, chance));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeTooltipLine(String rawLine) {
        return (rawLine == null ? "" : rawLine)
                .replace("\\\"", "\"")
                .replaceAll("§[0-9a-fk-orx]", "")
                .replaceAll("(?i)\\\\u00a7[0-9a-fk-orx]", "")
                .replaceAll("[{}\\[\\]\",:]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static CompoundTag findSpawnerData(CompoundTag tag, int depth) {
        if (tag == null || depth > 4) {
            return null;
        }

        if (containsKey(tag, "gs_mob_type")) {
            return tag;
        }

        for (String key : tag.keySet()) {
            Tag child = tag.get(key);
            if (child instanceof CompoundTag compound) {
                CompoundTag result = findSpawnerData(compound, depth + 1);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static boolean containsKey(CompoundTag tag, String wantedKey) {
        return findActualKey(tag, wantedKey) != null;
    }

    private static String getString(CompoundTag tag, String wantedKey, String fallback) {
        String actualKey = findActualKey(tag, wantedKey);
        return actualKey == null ? fallback : tag.getStringOr(actualKey, fallback);
    }

    private static int getInt(CompoundTag tag, String wantedKey, int fallback) {
        String actualKey = findActualKey(tag, wantedKey);
        return actualKey == null ? fallback : tag.getIntOr(actualKey, fallback);
    }

    private static String findActualKey(CompoundTag tag, String wantedKey) {
        if (tag.contains(wantedKey)) {
            return wantedKey;
        }

        for (String key : tag.keySet()) {
            String normalized = normalizeKey(key);
            if (normalized.equals(wantedKey)) {
                return key;
            }
        }
        return null;
    }

    private static String normalizeKey(String key) {
        int namespaceIndex = key.indexOf(':');
        String withoutNamespace = namespaceIndex >= 0 ? key.substring(namespaceIndex + 1) : key;
        return withoutNamespace.replace('-', '_').toLowerCase();
    }

    private static CompoundTag customData(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        return data.copyTag();
    }

    private static int swordColor(String rawType) {
        String lowerType = rawType == null ? "" : rawType.toLowerCase();
        if (lowerType.contains("diamond")) {
            return 0x55FFFF;
        }
        if (lowerType.contains("netherite")) {
            return 0xAA00AA;
        }
        return 0xAAAAAA;
    }

    private static int destructionChanceColor(float chance) {
        if (chance <= 15.0F) {
            return 0x00FF00;
        }
        if (chance <= 35.0F) {
            return lerpColor(0x00FF00, 0xFFFF00, (chance - 15.0F) / 20.0F);
        }
        return lerpColor(0xFFFF00, 0xFF0000, (chance - 35.0F) / 65.0F);
    }

    private static int lerpColor(int from, int to, float progress) {
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        int red = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * clamped);
        int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * clamped);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
        return red << 16 | green << 8 | blue;
    }

    private static String formatNumber(int num) {
        String raw = Integer.toString(Math.abs(num));
        StringBuilder formatted = new StringBuilder(raw);
        for (int index = formatted.length() - 3; index > 0; index -= 3) {
            formatted.insert(index, ' ');
        }
        return num < 0 ? "-" + formatted : formatted.toString();
    }

    private static String translateSwordType(String raw) {
        if (raw == null) {
            return ABSENT;
        }
        return switch (raw.replace("minecraft:", "").toLowerCase()) {
            case "wooden_sword" -> "Деревянный меч";
            case "stone_sword" -> "Каменный меч";
            case "iron_sword" -> "Железный меч";
            case "golden_sword" -> "Золотой меч";
            case "diamond_sword" -> "Алмазный меч";
            case "netherite_sword" -> "Незеритовый меч";
            default -> raw.replace("minecraft:", "").toLowerCase();
        };
    }

    private static String translateMobName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "Неизвестно";
        }
        return switch (raw.toUpperCase()) {
            case "GHAST" -> "Гаст";
            case "BLAZE" -> "Ифрит";
            case "MAGMA_CUBE" -> "Магмовый куб";
            case "ZOMBIE" -> "Зомби";
            case "SKELETON" -> "Скелет";
            case "SPIDER" -> "Паук";
            case "CREEPER" -> "Крипер";
            case "PIGLIN" -> "Пиглин";
            case "WITCH" -> "Ведьма";
            case "ENDERMAN" -> "Эндермен";
            case "COW" -> "Корова";
            case "SHEEP" -> "Овца";
            case "PIG" -> "Свинья";
            case "CHICKEN" -> "Курица";
            case "IRON_GOLEM" -> "Железный голем";
            default -> titleCase(raw);
        };
    }

    private static String translateEnchant(String raw) {
        String key = raw == null ? "" : raw.replace("minecraft:", "").replace("enchantments:", "");
        return switch (key) {
            case "bane_of_arthropods" -> "Бич";
            case "looting" -> "Добыча";
            case "sharpness" -> "Острота";
            case "unbreaking" -> "Прочность";
            case "smite" -> "Кара";
            case "sweeping", "sweeping_edge" -> "Разящий клинок";
            case "fire_aspect" -> "Заговор огня";
            case "mending" -> "Починка";
            case "critical-enchant-custom" -> "Критический";
            case "rich-enchant-custom" -> "Богач";
            case "destroyer-enchant-custom" -> "Разрушитель";
            default -> key;
        };
    }

    private static String titleCase(String raw) {
        String[] parts = raw.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                builder.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase())
                        .append(' ');
            }
        }
        return builder.toString().trim();
    }

    private static final class SwordInfo {
        private String rawType = "";
        private String type = ABSENT;
        private String name = ABSENT;
        private boolean hasFarmerEnchant;
        private final List<String> enchants = new ArrayList<>();
    }
}
