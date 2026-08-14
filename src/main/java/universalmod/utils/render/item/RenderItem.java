package universalmod.utils.render.item;

import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.render.color.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class RenderItem {
    private RenderItem() {
    }

    public static void beginFrame(GuiGraphics graphics) {
        renderer().beginFrame(graphics);
    }

    public static void flush() {
        renderer().flush();
    }

    public static void item(ItemStack stack, float x, float y, float size) {
        item(stack, x, y, size, RenderItemOptions.defaults());
    }

    public static void item(ItemStack stack, float x, float y, float size, float alpha) {
        item(stack, x, y, size, RenderItemOptions.decorated(alpha));
    }

    public static void item(ItemStack stack, float x, float y, float size, RenderItemOptions options) {
        BuiltRenderItem item = new BuiltRenderItem(stack, x, y, size, options, 0);
        renderer().enqueue(item);
        drawDecorations(item);
    }

    private static void drawDecorations(BuiltRenderItem item) {
        if (item == null || !item.visible()) {
            return;
        }

        ItemStack stack = item.stack();
        RenderItemOptions options = item.options();
        if (options.showDurability() && stack.isBarVisible()) {
            drawDurabilityBar(stack, item.x(), item.y(), item.size(), options.alpha());
        }
        if (options.showCount() && stack.getCount() > 1) {
            drawCount(stack.getCount(), item.x(), item.y(), item.size(), options.alpha());
        }
    }

    private static void drawDurabilityBar(ItemStack stack, float x, float y, float size, float alpha) {
        float barWidth = Math.max(8.0f, size * 0.78f);
        float barHeight = Math.max(1.5f, size * 0.07f);
        float barX = x + (size - barWidth) * 0.5f;
        float barY = y + size - barHeight - Math.max(1.0f, size * 0.08f);
        float fill = Math.max(0.0f, Math.min(1.0f, stack.getBarWidth() / 13.0f));
        int barColor = stack.getBarColor();

        Render2D.rect(barX, barY, barWidth, barHeight, barHeight * 0.5f, ColorUtil.rgba(0, 0, 0, Math.round(150.0f * alpha)));
        Render2D.rect(
                barX,
                barY,
                Math.max(1.0f, barWidth * fill),
                barHeight,
                barHeight * 0.5f,
                ColorUtil.rgba((barColor >>> 16) & 0xFF, (barColor >>> 8) & 0xFF, barColor & 0xFF, Math.round(240.0f * alpha))
        );
    }

    private static void drawCount(int count, float x, float y, float size, float alpha) {
        String text = Integer.toString(count);
        float textSize = Math.max(4.0f, Math.min(6.0f, size * 0.5f));
        float textWidth = Render2D.textWidth(FontType.SEMIBOLD, text, textSize);
        float textX = x + size - textWidth + size * 0.08f;
        float textY = y + size - textSize + size * 0.04f;
        float shadowOffset = Math.max(0.35f, Math.min(0.75f, size * 0.07f));
        int shadowAlpha = Math.round(170.0f * alpha);
        int textAlpha = Math.round(255.0f * alpha);

        Render2D.text(FontType.SEMIBOLD, text, textX + shadowOffset, textY + shadowOffset, textSize, ColorUtil.rgba(0, 0, 0, shadowAlpha));
        Render2D.text(FontType.SEMIBOLD, text, textX, textY, textSize, ColorUtil.rgba(255, 255, 255, textAlpha));
    }

    private static CustomItemRenderer renderer() {
        return CustomItemRenderer.getInstance();
    }
}
