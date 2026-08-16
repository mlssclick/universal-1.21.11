package universalmod.api.drag.impl;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import universalmod.api.module.impl.render.Hud;
import universalmod.utils.cooldown.CooldownStateStorage;
import universalmod.utils.cooldown.HolyWorldHealingCooldown;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.animation.SmoothAnimatedNumber;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.item.RenderItem;
import universalmod.utils.render.item.RenderItemOptions;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.awt.Color;

public final class Cooldowns extends HudPanel {
    private static final float HEADER_HEIGHT = 18.0F;
    private static final float HEADER_GAP = 3.0F;
    private static final float BODY_PADDING_Y = 5.5F;
    private static final float BODY_PADDING_X = 7.0F;
    private static final float ROW_HEIGHT = 12.0F;
    private static final float ROW_GAP = 2.0F;
    private static final float ROW_STEP = ROW_HEIGHT + ROW_GAP;
    private static final float ICON_SIZE = 7.0F;
    private static final float ICON_SEPARATOR_GAP = 2.4F;
    private static final float SEPARATOR_WIDTH = 0.6F;
    private static final float SEPARATOR_TEXT_GAP = 3.6F;
    private static final float NAME_TIMER_GAP = 7.0F;
    private static final float TIMER_BOX_HEIGHT = 8.6F;
    private static final float TEXT_SIZE = 6.6F;
    private static final float HEADER_TEXT_SIZE = 7.7F;
    private static final float PANEL_ANIM = 0.24F;
    private static final float ROW_ANIM = 0.22F;
    private static final float PANEL_WIDTH = 94.0F;
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

        hitExpansion(0.0F, HEADER_HEIGHT + HEADER_GAP, 0.0F, 0.0F);
        panelAnimation.update();
        panelAnimation.run(targetVisible ? 1.0F : 0.0F, PANEL_ANIM, targetVisible ? Easings.EXPO_OUT : Easings.EXPO_IN, true);

        for (RowEntry entry : rowEntries) {
            entry.active = false;
            entry.alpha.update();
            entry.y.update();
        }

        int targetRows = 0;
        if (preview) {
            RowEntry entry = row("__preview", PREVIEW_STACK, "Sugar", "**:**", TEXT_SIZE, 0.0F);
            entry.active = true;
            entry.alpha.run(1.0F, ROW_ANIM, Easings.EXPO_OUT, true);
            entry.y.run(0.0F, ROW_ANIM, Easings.EXPO_OUT, true);
            targetRows = 1;
        } else {
            for (CooldownInfo info : active) {
                float targetY = targetRows * ROW_STEP;
                RowEntry entry = row(info.rowKey(), info.stack, info.displayName, info.remainingText(), TEXT_SIZE, targetY);
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
                        BODY_PADDING_X + ICON_SIZE + ICON_SEPARATOR_GAP + SEPARATOR_WIDTH + SEPARATOR_TEXT_GAP
                                + Render2D.textWidth(TEXT_FONT, entry.name, TEXT_SIZE)
                                + NAME_TIMER_GAP + timerBoxWidth(entry.time, entry.timeSize) + BODY_PADDING_X);
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
        float headerTextSize = HEADER_TEXT_SIZE;
        int headerTextColor = ColorUtil.rgba(255, 255, 255, Math.round(255.0F * alpha));
        int backgroundColor = ColorUtil.rgba(0, 0, 0, Math.round(255.0F * alpha));
        float headerY = state.y - HEADER_HEIGHT - HEADER_GAP;
        float titleY = headerY + (HEADER_HEIGHT - Render2D.textHeight(TEXT_FONT, headerText, headerTextSize)) * 0.5F;
        HudRenderCompat.background(state.x, headerY, state.width, HEADER_HEIGHT, 5.0F,
                BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
        HudRenderCompat.background(state.x, state.y, state.width, state.height, 5.0F,
                BODY_BLUR_RADIUS, BODY_BLUR_SMOOTHNESS, backgroundColor);
        float markerHeight = 6.0F;
        float markerY = headerY + (HEADER_HEIGHT - markerHeight) * 0.5F;
        Render2D.rect(state.x + 6.0F, markerY, 1.4F, markerHeight, 0.7F, accentColor(214.0F * alpha));
        Render2D.text(TEXT_FONT, headerText, state.x + 11.0F, titleY, headerTextSize, headerTextColor);
        renderHeaderIcon("D", state.x + state.width - 7.0F, headerY, alpha);

        for (RowState row : state.rows) {
            float rowAlpha = alpha * row.alpha;
            float rowCenterY = state.y + BODY_PADDING_Y + ROW_HEIGHT * 0.5F + row.offset;
            float iconX = state.x + BODY_PADDING_X;
            float iconY = rowCenterY - ICON_SIZE * 0.5F;
            float separatorX = iconX + ICON_SIZE + ICON_SEPARATOR_GAP;
            float nameX = separatorX + SEPARATOR_WIDTH + SEPARATOR_TEXT_GAP;
            float timerRightX = state.x + state.width - BODY_PADDING_X;
            float timerLeftX = timerRightX - timerBoxWidth(row.time, row.timeSize);
            String visibleName = trimToWidth(row.name, TEXT_FONT, TEXT_SIZE,
                    Math.max(8.0F, timerLeftX - NAME_TIMER_GAP - nameX));
            float textY = rowCenterY - Render2D.textHeight(TEXT_FONT, visibleName, TEXT_SIZE) * 0.5F;

            renderCooldownIcon(row.stack, iconX, iconY, rowAlpha);
            Render2D.rect(separatorX, rowCenterY - 2.3F, SEPARATOR_WIDTH, 4.6F, 0.3F,
                    ColorUtil.rgba(255, 255, 255, Math.round(40.0F * rowAlpha)));
            Render2D.text(TEXT_FONT, visibleName, nameX, textY, TEXT_SIZE,
                    ColorUtil.rgba(255, 255, 255, Math.round(238.0F * rowAlpha)));
            renderTimer(row.time, timerRightX, rowCenterY, row.timeSize, rowAlpha, row.secondsAnimation);
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
            return Render2D.textWidth(TEXT_FONT, "88:88", size);
        }
        StringBuilder stable = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            stable.append(Character.isDigit(character) || character == '*' ? '8' : character);
        }
        return Render2D.textWidth(TEXT_FONT, stable.toString(), size);
    }

    private float timerBoxWidth(String text, float size) {
        return timerWidth(text, size) + 7.0F;
    }

    private static float bodyHeight(int rowCount) {
        int rows = Math.max(1, rowCount);
        return BODY_PADDING_Y * 2.0F
                + rows * ROW_HEIGHT
                + Math.max(0, rows - 1) * ROW_GAP;
    }

    private void renderTimer(String text, float rightX, float centerY, float size, float alpha, SmoothAnimatedNumber secondsAnimation) {
        int color = accentColor(250.0F * alpha);
        float boxWidth = timerBoxWidth(text, size);
        float boxLeftX = rightX - boxWidth;
        Render2D.rect(boxLeftX, centerY - TIMER_BOX_HEIGHT * 0.5F, boxWidth, TIMER_BOX_HEIGHT, 2.0F,
                accentBackgroundColor(112.0F * alpha));
        int separator = text == null ? -1 : text.lastIndexOf(':');
        int seconds = parseTimerSeconds(text);
        if (separator < 0 || seconds < 0 || secondsAnimation == null) {
            String value = text == null ? "**:**" : text;
            Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(TEXT_FONT, value, size);
            float textWidth = Render2D.textWidth(TEXT_FONT, value, size);
            float textX = bounds.empty()
                    ? boxLeftX + (boxWidth - textWidth) * 0.5F
                    : boxLeftX + boxWidth * 0.5F - (bounds.minX() + bounds.maxX()) * 0.5F;
            float textY = bounds.empty() ? centerY - size * 0.5F : centerY - bounds.centerY();
            Render2D.text(TEXT_FONT, value, textX, textY, size, color);
            return;
        }

        String prefix = text.substring(0, separator + 1);
        secondsAnimation.update(seconds);
        String timerText = prefix + twoDigits(seconds);
        Render2D.TextVisualBounds bounds = Render2D.textVisualBounds(TEXT_FONT, timerText, size);
        float timerWidth = secondsAnimation.timerWidth(prefix);
        float timerX = bounds.empty()
                ? boxLeftX + (boxWidth - timerWidth) * 0.5F
                : boxLeftX + boxWidth * 0.5F - (bounds.minX() + bounds.maxX()) * 0.5F;
        float timerY = bounds.empty() ? centerY - size * 0.5F : centerY - bounds.centerY();
        secondsAnimation.renderTimer(prefix, timerX, timerY, color);
    }

    private void renderHeaderIcon(String glyph, float rightX, float headerY, float alpha) {
        float size = 6.0F;
        float x = rightX - Render2D.textWidth(FontType.VIREX_WONDERFUL, glyph, size);
        float y = headerY + (HEADER_HEIGHT - Render2D.textHeight(FontType.VIREX_WONDERFUL, glyph, size)) * 0.5F;
        Render2D.text(FontType.VIREX_WONDERFUL, glyph, x, y, size,
                accentColor(255.0F * alpha));
    }

    private void renderCooldownIcon(ItemStack stack, float x, float y, float alpha) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        RenderItem.item(stack, x, y, ICON_SIZE, RenderItemOptions.noDecorations(alpha));
    }

    private static int accentColor(float alpha) {
        Color color = Hud.getInstance() == null ? new Color(244, 176, 101) : Hud.getInstance().cooldownsColor();
        return ColorUtil.rgba(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * alpha / 255.0F));
    }

    private static int accentBackgroundColor(float alpha) {
        Color color = Hud.getInstance() == null ? new Color(126, 72, 39) : Hud.getInstance().cooldownsColor();
        return ColorUtil.rgba(Math.round(color.getRed() * 0.52F), Math.round(color.getGreen() * 0.52F),
                Math.round(color.getBlue() * 0.52F), Math.round(color.getAlpha() * alpha / 255.0F));
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
