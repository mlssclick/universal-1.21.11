package universalmod.api.drag.impl;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.item.RenderItem;
import universalmod.utils.render.item.RenderItemOptions;

public final class Inventory extends HudPanel {
    private static final float HUD_SCALE = 1.08F;
    private static final float BASE_WIDTH = 140.0F;
    private static final float BASE_HEIGHT = 64.0F;
    private static final float BLUR_RADIUS = 4.0F;
    private static final float BLUR_SMOOTHNESS = 0.55F;
    private static final ItemStack[] PREVIEW = {
            Items.ENDER_PEARL.getDefaultInstance(),
            Items.GOLDEN_APPLE.getDefaultInstance(),
            Items.TOTEM_OF_UNDYING.getDefaultInstance(),
            Items.SUGAR.getDefaultInstance()
    };
    private final ItemStack[] inventoryStacks = new ItemStack[27];

    public Inventory() {
        super("inventory", "Inventory", 10.0F, 260.0F, BASE_WIDTH * HUD_SCALE, BASE_HEIGHT * HUD_SCALE);
        for (int i = 0; i < inventoryStacks.length; i++) {
            inventoryStacks[i] = ItemStack.EMPTY;
        }
    }

    @Override
    public void render() {
        InventoryState state = logics();
        if (state == null) {
            return;
        }
        renderInventory(state);
    }

    private InventoryState logics() {
        boolean hasItems = inventoryItems();
        boolean preview = !hasItems && editPreview();
        boolean visible = hasItems || preview;
        float alpha = contentAlpha(visible);
        if (alpha <= 0.0F) {
            return null;
        }

        size(BASE_WIDTH * HUD_SCALE, BASE_HEIGHT * HUD_SCALE);

        return new InventoryState(preview ? PREVIEW : inventoryStacks, preview ? PREVIEW.length : inventoryStacks.length, alpha, drag.x(), drag.y(), logicalWidth(), logicalHeight());
    }

    private void renderInventory(InventoryState state) {
        float paddingX = Math.max(3.5F, state.width * 0.025F);
        float paddingY = Math.max(4.0F, state.height * 0.055F);
        float cellWidth = (state.width - paddingX * 2.0F) / 9.0F;
        float cellHeight = (state.height - paddingY * 2.0F) / 3.0F;
        float itemSize = Math.min(cellWidth, cellHeight) * 0.98F;
        float startX = state.x + paddingX + (cellWidth - itemSize) * 0.5F;
        float startY = state.y + paddingY + (cellHeight - itemSize) * 0.5F;
        float stepX = cellWidth;
        float stepY = cellHeight;

        HudRenderCompat.background(state.x, state.y, state.width, state.height, 4.0F, BLUR_RADIUS, BLUR_SMOOTHNESS, ColorUtil.rgba(0, 0, 0, Math.round(255.0F * state.alpha)));

        for (int i = 0; i < state.count; i++) {
            ItemStack stack = state.items[i];
            int column = i % 9;
            int row = i / 9;
            float itemX = Math.round(startX + column * stepX);
            float itemY = Math.round(startY + row * stepY);
            RenderItem.item(
                    stack,
                    itemX,
                    itemY,
                    itemSize,
                    RenderItemOptions.countNoDurability(state.alpha)
            );
        }
    }

    private boolean inventoryItems() {
        if (mc.player == null) {
            clearInventoryCache();
            return false;
        }
        boolean hasItems = false;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack;
            inventoryStacks[slot - 9] = safeStack;
            if (!safeStack.isEmpty()) {
                hasItems = true;
            }
        }
        return hasItems;
    }

    private void clearInventoryCache() {
        for (int i = 0; i < inventoryStacks.length; i++) {
            inventoryStacks[i] = ItemStack.EMPTY;
        }
    }

    private record InventoryState(ItemStack[] items, int count, float alpha, float x, float y, float width, float height) {
    }
}
