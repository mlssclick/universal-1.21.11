package universalmod.utils.render.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import universalmod.utils.render.ui.Render2DCoordinateSpace;

public final class CustomItemRenderer {
    private static volatile CustomItemRenderer instance;

    private GuiGraphics activeGraphics;

    private CustomItemRenderer() {
    }

    public static CustomItemRenderer getInstance() {
        CustomItemRenderer local = instance;
        if (local == null) {
            synchronized (CustomItemRenderer.class) {
                local = instance;
                if (local == null) {
                    local = new CustomItemRenderer();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static void closeInstance() {
        CustomItemRenderer local = instance;
        if (local != null) {
            local.close();
            instance = null;
        }
    }

    public void beginFrame(GuiGraphics graphics) {
        activeGraphics = graphics;
    }

    public void enqueue(BuiltRenderItem item) {
        submit(activeGraphics, item);
    }

    public void flush() {
        activeGraphics = null;
    }

    private void submit(GuiGraphics graphics, BuiltRenderItem item) {
        if (graphics == null || item == null || !item.visible()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getItemModelResolver() == null) {
            return;
        }

        try {
            submitVanillaItem(graphics, item);
        } catch (RuntimeException ignored) {
        }
    }

    private void submitVanillaItem(GuiGraphics graphics, BuiltRenderItem item) {
        float scale = item.size() / 16.0f;
        if (scale <= 0.0f) {
            return;
        }

        graphics.pose().pushMatrix();
        try {
            Render2DCoordinateSpace.applyGuiScaleIndependence(graphics.pose());
            graphics.pose().translate(item.x(), item.y());
            graphics.pose().scale(scale);
            graphics.renderItem(item.stack(), 0, 0, item.seed());
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public void close() {
        activeGraphics = null;
    }
}
