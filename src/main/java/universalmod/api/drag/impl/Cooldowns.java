package universalmod.api.drag.impl;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import universalmod.utils.cooldown.CooldownStateStorage;
import universalmod.utils.cooldown.HolyWorldHealingCooldown;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.animation.SmoothAnimatedNumber;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.item.RenderItem;
import universalmod.utils.render.item.RenderItemOptions;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.theme.ThemeColors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Cooldowns extends HudPanel {
    private static final String HEADER_ICON_TEXTURE = "universalmod:textures/hud/header_cooldowns_clock.png";
    private static final float HEADER_HEIGHT = 18.0F;
    private static final float HEADER_GAP = 5.0F;
    private static final float BODY_PADDING_Y = 5.0F;
    private static final float BODY_PADDING_X = 6.0F;
    private static final float ROW_HEIGHT = 10.0F;
    private static final float ROW_GAP = 2.0F;
    private static final float ROW_STEP = ROW_HEIGHT + ROW_GAP;
    private static final float ITEM_SIZE = 8.0F;
    private static final float ITEM_TEXT_GAP = 5.0F;
    private static final float NAME_TIMER_GAP = 10.0F;
    private static final float HEADER_ICON_EDGE_PADDING = 4.0F;
    private static final float HEADER_TEXT_GAP = 4.0F;
    private static final float HEADER_ICON_SIZE = 8.0F;
    private static final float PANEL_ANIM = 0.24F;
    private static final float ROW_ANIM = 0.22F;
    private static final float PANEL_WIDTH = 90.0F;
    private static final float PANEL_HEIGHT = BODY_PADDING_Y * 2.0F + ROW_HEIGHT;
    private static final float BODY_BLUR_RADIUS = 4.0F;
    private static final float BODY_BLUR_SMOOTHNESS = 0.55F;
    private static final Comparator<RowState> ROW_STATE_COMPARATOR = Comparator.comparingDouble(RowState::offset);
    private static final ItemStack PREVIEW_STACK = Items.SUGAR.getDefaultInstance();
    private static final String HOLYWORLD_HEALING_ROW_KEY = "__holyworld_healing_potion";

    private final Map<CooldownKey, CooldownInfo> infoByItem = new LinkedHashMap<>();
    private final List<RowEntry> rowEntries = new ArrayList<>();
    private final List<CooldownInfo> activeCooldowns = new ArrayList<>();
    private final List<RowState> rowStates = new ArrayList<>();
    private final SmoothAnimation panelAnimation = new SmoothAnimation();
    private final CooldownInfo holyWorldHealingInfo = new CooldownInfo();

    public Cooldowns() {
        super("cooldowns", "Cooldowns", 10.0F, 40.0F, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render() {
        CooldownsState state = logics();
        if (state == null) {
            return;
        }
        renderCooldowns(state);
    }

    private CooldownsState logics() {
        List<CooldownInfo> active = collectActive();
        boolean preview = active.isEmpty() && editPreview();
        boolean targetVisible = !active.isEmpty() || preview;

        float headerExpansion = ThemeColors.isHudWithoutName()
                ? 0.0F
                : HEADER_HEIGHT + (ThemeColors.isHudSplit() ? HEADER_GAP : 0.0F);
        hitExpansion(0.0F, headerExpansion, 0.0F, 0.0F);
        panelAnimation.update();
        panelAnimation.run(targetVisible ? 1.0F : 0.0F, PANEL_ANIM, targetVisible ? Easings.EXPO_OUT : Easings.EXPO_IN, true);

        for (RowEntry entry : rowEntries) {
            entry.active = false;
            entry.alpha.update();
            entry.y.update();
        }

        int targetRows = 0;
        if (preview) {
            RowEntry entry = row("__preview", PREVIEW_STACK, "Sugar", "**:**", 6.0F, 0.0F);
            entry.active = true;
            entry.alpha.run(1.0F, ROW_ANIM, Easings.EXPO_OUT, true);
            entry.y.run(0.0F, ROW_ANIM, Easings.EXPO_OUT, true);
            targetRows = 1;
        } else {
            for (CooldownInfo info : active) {
                float targetY = targetRows * ROW_STEP;
                RowEntry entry = row(info.rowKey(), info.stack, info.displayName, info.remainingText(), 6.0F, targetY);
                entry.active = true;
                entry.alpha.run(1.0F, ROW_ANIM, Easings.EXPO_OUT, true);
                entry.y.run(targetY, ROW_ANIM, Easings.EXPO_OUT, true);
                targetRows++;
            }
        }

        for (RowEntry entry : rowEntries) {
            if (!entry.active) {
                entry.alpha.run(0.0F, ROW_ANIM, Easings.EXPO_IN, true);
            }
        }
        rowEntries.removeIf(entry -> !entry.active && entry.alpha.get() <= 0.01F && !entry.alpha.isAlive());

        float panelAlpha = panelAnimation.get();
        boolean visible = targetVisible || panelAlpha > 0.01F || !rowEntries.isEmpty();
        contentVisible(visible);
        if (!visible) {
            return null;
        }

        float width = PANEL_WIDTH;
        for (RowEntry entry : rowEntries) {
            if (entry.alpha.get() > 0.01F || entry.active) {
                width = Math.max(width,
                        BODY_PADDING_X + ITEM_SIZE + ITEM_TEXT_GAP
                                + Render2D.textWidth(TEXT_FONT, entry.name, 6.0F)
                                + NAME_TIMER_GAP + timerWidth(entry.time, entry.timeSize) + BODY_PADDING_X);
            }
        }
        size(width, bodyHeight(Math.max(1, targetRows)));

        rowStates.clear();
        for (RowEntry entry : rowEntries) {
            float alpha = entry.alpha.get();
            if (alpha > 0.01F || entry.active) {
                rowStates.add(new RowState(entry.stack, entry.name, entry.time, entry.timeSize, entry.y.get(), alpha, entry.secondsAnimation));
            }
        }
        if (rowStates.size() > 1) {
            rowStates.sort(ROW_STATE_COMPARATOR);
        }

        return new CooldownsState(rowStates, panelAlpha, drag.x(), drag.y(), logicalWidth(), logicalHeight());
    }

    private void renderCooldowns(CooldownsState state) {
        float alpha = state.alpha;
        String headerText = "Cooldowns";
        float headerTextSize = 9.0F;
        int headerTextColor = hudTextColor(Math.round(255.0F * alpha));
        int backgroundColor = ColorUtil.rgba(0, 0, 0, Math.round(255.0F * alpha));
        boolean withoutName = ThemeColors.isHudWithoutName();
        boolean split = ThemeColors.isHudSplit();

        if (withoutName) {
            HudRenderCompat.background(state.x, state.y, state.width, state.height, 4.0F,
                    BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
        } else if (split) {
            boolean showIcon = ThemeColors.showSplitIcon();
            float headerTextWidth = Render2D.textWidth(TEXT_FONT, headerText, headerTextSize);
            float headerPadding = 6.0F;
            float iconPart = showIcon ? HEADER_ICON_SIZE + HEADER_TEXT_GAP : 0.0F;
            float headerWidth = headerTextWidth + iconPart + headerPadding * 2.0F;
            float headerX = state.x + (state.width - headerWidth) * 0.5F;
            float headerY = state.y - HEADER_HEIGHT - HEADER_GAP;
            float headerTextY = headerY + (HEADER_HEIGHT - Render2D.textHeight(TEXT_FONT, headerText, headerTextSize)) * 0.5F;
            float headerRounding = ThemeColors.splitHeaderRounding();
            float textX = headerX + headerPadding + iconPart;

            HudRenderCompat.splitHeader(headerX, headerY, headerWidth, HEADER_HEIGHT, headerRounding,
                    BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
            HudRenderCompat.background(state.x, state.y, state.width, state.height, 4.0F,
                    BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
            if (showIcon && alpha > 0.001F) {
                float iconX = headerX + headerPadding;
                float iconY = headerTextY + (Render2D.textHeight(TEXT_FONT, headerText, headerTextSize) - HEADER_ICON_SIZE) * 0.5F;
                Render2D.image(HEADER_ICON_TEXTURE, iconX, iconY, HEADER_ICON_SIZE, HEADER_ICON_SIZE, 0.0F, headerTextColor);
            }
            Render2D.text(TEXT_FONT, headerText, textX, headerTextY, headerTextSize, headerTextColor);
        } else {
            float headerY = state.y - HEADER_HEIGHT;
            float headerTextY = headerY + (HEADER_HEIGHT - Render2D.textHeight(TEXT_FONT, headerText, headerTextSize)) * 0.5F;
            float textX = state.x + HEADER_ICON_EDGE_PADDING;
            float iconX = state.x + state.width - HEADER_ICON_EDGE_PADDING - HEADER_ICON_SIZE;
            float iconY = headerTextY + (Render2D.textHeight(TEXT_FONT, headerText, headerTextSize) - HEADER_ICON_SIZE) * 0.5F;
            HudRenderCompat.background(state.x, headerY, state.width, HEADER_HEIGHT + state.height, 4.0F,
                    BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
            if (((headerTextColor >>> 24) & 0xFF) > 0) {
                Render2D.image(HEADER_ICON_TEXTURE, iconX, iconY, HEADER_ICON_SIZE, HEADER_ICON_SIZE, 0.0F, headerTextColor);
            }
            Render2D.text(TEXT_FONT, headerText, textX, headerTextY, headerTextSize, headerTextColor);
        }

        for (RowState row : state.rows) {
            float rowAlpha = alpha * row.alpha;
            float rowCenterY = state.y + BODY_PADDING_Y + ROW_HEIGHT * 0.5F + row.offset;
            float itemX = state.x + BODY_PADDING_X;
            float itemY = rowCenterY - ITEM_SIZE * 0.5F;
            float textY = rowCenterY - 3.15F;
            float nameX = itemX + ITEM_SIZE + ITEM_TEXT_GAP;
            float timerRightX = state.x + state.width - BODY_PADDING_X;
            float timerLeftX = timerRightX - timerWidth(row.time, row.timeSize);
            String visibleName = trimToWidth(row.name, TEXT_FONT, 6.0F,
                    Math.max(8.0F, timerLeftX - NAME_TIMER_GAP - nameX));

            RenderItem.item(row.stack, itemX, itemY, ITEM_SIZE, RenderItemOptions.noDecorations(rowAlpha));
            Render2D.text(TEXT_FONT, visibleName, nameX, textY, 6.0F,
                    hudTextColor(Math.round(245.0F * rowAlpha)));
            renderTimer(row.time, timerRightX, textY, row.timeSize, rowAlpha, row.secondsAnimation);
        }
    }

    private RowEntry row(String key, ItemStack stack, String name, String time, float timeSize, float targetY) {
        for (RowEntry entry : rowEntries) {
            if (entry.key.equals(key)) {
                entry.stack = stack == null ? ItemStack.EMPTY : stack.copy();
                entry.name = name;
                entry.time = time;
                entry.timeSize = timeSize;
                entry.updateTimer(time);
                return entry;
            }
        }

        RowEntry entry = new RowEntry(key, stack, name, time, timeSize);
        entry.alpha.set(0.0F);
        entry.y.set(targetY + 4.0F);
        rowEntries.add(entry);
        return entry;
    }

    private List<CooldownInfo> collectActive() {
        activeCooldowns.clear();
        if (mc.player == null) {
            infoByItem.clear();
            return activeCooldowns;
        }

        HolyWorldHealingCooldown.Snapshot healing = HolyWorldHealingCooldown.snapshot();
        Set<CooldownKey> seen = new LinkedHashSet<>();
        for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty() || !mc.player.getCooldowns().isOnCooldown(stack)) {
                continue;
            }
            if (healing != null && HolyWorldHealingCooldown.isHealingPotion(stack)) {

                continue;
            }

            CooldownKey key = CooldownKey.of(stack);
            if (!seen.add(key)) {
                continue;
            }

            Identifier group = mc.player.getCooldowns().getCooldownGroup(stack);
            int totalTicks = CooldownStateStorage.getDuration(group);
            float progress = clamp(mc.player.getCooldowns().getCooldownPercent(stack, 0.0F), 0.0F, 1.0F);
            CooldownInfo info = infoByItem.computeIfAbsent(key, ignored -> new CooldownInfo());
            info.key = key;
            info.stack = stack.copy();
            info.displayName = displayName(stack);
            info.update(progress, mc.player.tickCount, totalTicks);
            activeCooldowns.add(info);
        }

        infoByItem.entrySet().removeIf(entry -> !seen.contains(entry.getKey()));

        if (healing != null) {
            holyWorldHealingInfo.setFixed(
                    HOLYWORLD_HEALING_ROW_KEY,
                    healing.stack(),
                    healing.displayName(),
                    healing.remainingTicks(),
                    healing.remainingText()
            );
            activeCooldowns.add(holyWorldHealingInfo);
        }
        return activeCooldowns;
    }

    private String displayName(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return switch (id) {
            case "minecraft:ender_eye" -> "Disorientation";
            case "minecraft:sugar" -> "Sugar";
            case "minecraft:netherite_scrap" -> "Trap";
            case "minecraft:dried_kelp" -> "Plast";
            case "minecraft:trident" -> "Trident";
            case "minecraft:mace" -> "Mace";
            case "minecraft:wind_charge" -> "Wind Charge";
            case "minecraft:enchanted_golden_apple" -> "Ench. Gap";
            case "minecraft:golden_apple" -> "Golden Apple";
            default -> stack.getHoverName().getString();
        };
    }

    private float timerWidth(String text, float size) {
        if (text == null || text.isBlank()) {
            return Render2D.textWidth(TEXT_FONT, "**:**", size);
        }
        int separator = text.lastIndexOf(':');
        int seconds = parseTimerSeconds(text);
        if (separator < 0 || seconds < 0) {
            return Render2D.textWidth(TEXT_FONT, text, size);
        }
        String minutesText = text.substring(0, separator);
        return Render2D.textWidth(TEXT_FONT, minutesText + ":" + twoDigits(seconds), size);
    }

    private static float bodyHeight(int rowCount) {
        int rows = Math.max(1, rowCount);
        return BODY_PADDING_Y * 2.0F
                + rows * ROW_HEIGHT
                + Math.max(0, rows - 1) * ROW_GAP;
    }

    private void renderTimer(String text, float rightX, float y, float size, float alpha, SmoothAnimatedNumber secondsAnimation) {
        int color = hudTextColor(Math.round(245.0F * alpha));
        int separator = text == null ? -1 : text.lastIndexOf(':');
        int seconds = parseTimerSeconds(text);
        if (separator < 0 || seconds < 0 || secondsAnimation == null) {
            String value = text == null ? "**:**" : text;
            Render2D.text(TEXT_FONT, value,
                    rightX - Render2D.textWidth(TEXT_FONT, value, size), y, size, color);
            return;
        }

        String prefix = text.substring(0, separator + 1);
        secondsAnimation.update(seconds);
        float timerWidth = secondsAnimation.timerWidth(prefix);
        secondsAnimation.renderTimer(prefix, rightX - timerWidth, y, color);
    }

    private static int parseTimerSeconds(String text) {
        if (text == null) {
            return -1;
        }
        int separator = text.lastIndexOf(':');
        if (separator < 0 || separator + 1 >= text.length()) {
            return -1;
        }
        try {
            return Integer.parseInt(text.substring(separator + 1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String formatCooldownDurationTicks(int ticks) {
        if (ticks < 0) {
            return "**:**";
        }

        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = Math.min(99, totalSeconds / 60);
        int seconds = totalSeconds % 60;
        return twoDigits(minutes) + ":" + twoDigits(seconds);
    }

    private static final class RowEntry {
        private final String key;
        private final SmoothAnimation alpha = new SmoothAnimation();
        private final SmoothAnimation y = new SmoothAnimation();
        private ItemStack stack;
        private String name;
        private String time;
        private float timeSize;
        private final SmoothAnimatedNumber secondsAnimation;
        private boolean active;

        private RowEntry(String key, ItemStack stack, String name, String time, float timeSize) {
            this.key = key;
            this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
            this.name = name;
            this.time = time;
            this.timeSize = timeSize;
            this.secondsAnimation = new SmoothAnimatedNumber(TEXT_FONT, timeSize, 5.0F, 500L, Easings.BAKEK, true);
            updateTimer(time);
        }

        private void updateTimer(String value) {
            int seconds = parseTimerSeconds(value);
            if (seconds >= 0) {
                secondsAnimation.update(seconds);
            }
        }
    }

    private final class CooldownInfo {
        private CooldownKey key;
        private String customRowKey;
        private ItemStack stack = ItemStack.EMPTY;
        private String displayName = "";
        private String fixedRemainingText;
        private float lastProgress = -1.0F;
        private long lastTick = -1L;
        private int totalTicks = -1;
        private int remainingTicks;

        private void update(float nextProgress, long tick, int knownTotalTicks) {
            customRowKey = null;
            fixedRemainingText = null;
            float progress = clamp(nextProgress, 0.0F, 1.0F);

            if (knownTotalTicks > 0) {
                totalTicks = knownTotalTicks;
            } else if (lastTick >= 0L && tick > lastTick && lastProgress > progress) {
                float diff = lastProgress - progress;
                if (diff > 0.00001F) {
                    int estimate = Math.round((tick - lastTick) / diff);
                    if (estimate > 0 && estimate < 12000) {
                        totalTicks = totalTicks <= 0 ? estimate : Math.round(totalTicks * 0.75F + estimate * 0.25F);
                    }
                }
            }

            lastProgress = progress;
            lastTick = tick;
            remainingTicks = totalTicks <= 0
                    ? Math.max(1, Math.round(progress * 20.0F))
                    : Math.max(1, Math.round(progress * totalTicks));
        }

        private void setFixed(String rowKey, ItemStack stack, String displayName, int remainingTicks, String remainingText) {
            this.key = null;
            this.customRowKey = rowKey;
            this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
            this.displayName = displayName == null || displayName.isBlank()
                    ? "Healing Potion"
                    : displayName;
            this.remainingTicks = Math.max(1, remainingTicks);
            this.fixedRemainingText = remainingText;
            this.totalTicks = HolyWorldHealingCooldown.DURATION_SECONDS * 20;
            this.lastProgress = -1.0F;
            this.lastTick = -1L;
        }

        private String rowKey() {
            return customRowKey != null ? customRowKey : key.rowKey();
        }

        private String remainingText() {
            return fixedRemainingText != null
                    ? fixedRemainingText
                    : formatCooldownDurationTicks(remainingTicks);
        }
    }

    private record RowState(ItemStack stack, String name, String time, float timeSize, float offset, float alpha, SmoothAnimatedNumber secondsAnimation) {
    }

    private record CooldownsState(List<RowState> rows, float alpha, float x, float y, float width, float height) {
    }

    private record CooldownKey(Item item, DataComponentMap components, String name) {
        private static CooldownKey of(ItemStack stack) {
            return new CooldownKey(stack.getItem(), stack.immutableComponents(), stack.getHoverName().getString());
        }

        private String rowKey() {
            return BuiltInRegistries.ITEM.getKey(item) + "|" + name + "|" + components.hashCode();
        }
    }
}
