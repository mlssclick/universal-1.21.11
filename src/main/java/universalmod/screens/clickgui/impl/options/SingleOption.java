package universalmod.screens.clickgui.impl.options;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

import java.util.ArrayList;
import java.util.List;

public class SingleOption extends ClickGuiOption {
    // Reference components are drawn at panel scale. Our settings container applies an extra 0.70
    // scale, so divide the measurements by it. Selection chips are intentionally 20% larger.
    private static final float OPTION_SCALE = 0.70f;
    private static final float CHIP_SCALE = 1.20f;
    private static final double SELECTION_ANIMATION_DURATION = 0.16;
    private static final float LAYOUT_WIDTH = 163.0f;
    private static final float TITLE_SIZE = 6.5f / OPTION_SCALE;
    private static final float CHIP_TEXT_SIZE = 6.25f * CHIP_SCALE / OPTION_SCALE;
    private static final float CHIP_HEIGHT = 9.0f * CHIP_SCALE / OPTION_SCALE;
    private static final float CHIP_GAP_X = 3.0f * CHIP_SCALE / OPTION_SCALE;
    private static final float CHIP_GAP_Y = 3.0f * CHIP_SCALE / OPTION_SCALE;
    private static final float CHIP_PADDING_X = 3.0f * CHIP_SCALE / OPTION_SCALE;
    private static final float CHIPS_TOP = 11.5f / OPTION_SCALE;
    private static final float BOTTOM_PADDING = 1.9f / OPTION_SCALE;
    private static final float CHIP_RADIUS = 2.0f * CHIP_SCALE / OPTION_SCALE;
    private static final float OUTLINE_THICKNESS = 0.5f * CHIP_SCALE / OPTION_SCALE;

    private final ModeSetting setting;
    private final String[] values;
    private final float cachedHeight;
    private final List<PillHit> pillHits = new ArrayList<>();
    private final SmoothAnimation[] selectionAnimations;

    public SingleOption(ModeSetting setting) {
        super(setting.getName());
        this.setting = setting;
        this.values = setting.getModes().toArray(String[]::new);
        this.selectionAnimations = new SmoothAnimation[this.values.length];
        for (int i = 0; i < this.values.length; i++) {
            this.selectionAnimations[i] = new SmoothAnimation();
            this.selectionAnimations[i].set(setting.isSelected(this.values[i]) ? 1.0 : 0.0);
        }
        int rows = rowsForWidth(LAYOUT_WIDTH);
        this.cachedHeight = CHIPS_TOP + rows * CHIP_HEIGHT
                + Math.max(0, rows - 1) * CHIP_GAP_Y + BOTTOM_PADDING;
    }

    @Override
    public float getHeight() {
        return cachedHeight;
    }

    @Override
    protected void renderControl(GuiGraphics graphics, float x, float y, float width, float height, float scale, float alpha) {
        pillHits.clear();

        float titleSize = TITLE_SIZE * scale;
        renderScrollingText(graphics, "single:title", displayName(), x, y, titleSize,
                color(236, 237, 247, 250, alpha),
                x, y, width, CHIPS_TOP * scale,
                x, y, width, CHIPS_TOP * scale);

        float cursorX = x;
        float cursorY = y + CHIPS_TOP * scale;
        float rowRight = x + width;
        for (int i = 0; i < values.length; i++) {
            String display = setting.getDisplayMode(values[i]);
            float textSize = CHIP_TEXT_SIZE * scale;
            float textWidth = Render2D.textWidth(FontType.SEMIBOLD, display, textSize);
            float chipWidth = Math.min(width, textWidth + CHIP_PADDING_X * 2.0f * scale);
            if (cursorX > x && cursorX + chipWidth > rowRight + 0.01f) {
                cursorX = x;
                cursorY += (CHIP_HEIGHT + CHIP_GAP_Y) * scale;
            }

            boolean selected = setting.isSelected(values[i]);
            SmoothAnimation selectionAnimation = selectionAnimations[i];
            selectionAnimation.run(selected ? 1.0 : 0.0, SELECTION_ANIMATION_DURATION, Easings.CIRC_IN, true);
            selectionAnimation.update();
            float selection = clamp(selectionAnimation.get(), 0.0f, 1.0f);

            float chipHeight = CHIP_HEIGHT * scale;
            int fill = ColorUtil.lerpColor(
                    ColorUtil.rgba(86, 87, 91, Math.round(155.0f * alpha)),
                    ColorUtil.rgba(137, 117, 199, Math.round(205.0f * alpha)),
                    selection
            );
            int outline = ColorUtil.rgba(255, 255, 255,
                    Math.round((70.0f + 32.0f * selection) * alpha));
            Render2D.rect(cursorX, cursorY, chipWidth, chipHeight, CHIP_RADIUS * scale, fill);
            Render2D.outline(cursorX, cursorY, chipWidth, chipHeight, CHIP_RADIUS * scale,
                    OUTLINE_THICKNESS * scale, outline);

            Render2D.TextVisualBounds textBounds = Render2D.textVisualBounds(FontType.SEMIBOLD, display, textSize);
            float textX = textBounds.empty()
                    ? cursorX + (chipWidth - textWidth) * 0.5f
                    : cursorX + (chipWidth - textBounds.width()) * 0.5f - textBounds.minX();
            float textY = textBounds.empty()
                    ? cursorY + (chipHeight - textSize) * 0.5f
                    : cursorY + (chipHeight - textBounds.height()) * 0.5f - textBounds.minY();
            int textColor = ColorUtil.lerpColor(
                    ColorUtil.rgba(185, 187, 205, Math.round(230.0f * alpha)),
                    ColorUtil.rgba(255, 255, 255, Math.round(245.0f * alpha)),
                    selection
            );
            Render2D.text(FontType.SEMIBOLD, display, textX, textY, textSize, textColor);

            pillHits.add(new PillHit(i, cursorX, cursorY, chipWidth, chipHeight));
            cursorX += chipWidth + CHIP_GAP_X * scale;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 2 && hovered(event.x(), event.y(), x, y, width,
                Math.max(0.0f, height - BOTTOM_PADDING * scale))) {
            setting.reset();
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        for (PillHit hit : pillHits) {
            if (hovered(event.x(), event.y(), hit.x, hit.y, hit.width, hit.height)) {
                setting.setValue(values[hit.index]);
                return true;
            }
        }
        return false;
    }

    private int rowsForWidth(float availableWidth) {
        if (values.length == 0) {
            return 1;
        }
        int rows = 1;
        float cursor = 0.0f;
        for (String value : values) {
            String display = setting.getDisplayMode(value);
            float textWidth = Render2D.textWidth(FontType.SEMIBOLD, display, CHIP_TEXT_SIZE);
            float chipWidth = Math.min(availableWidth, textWidth + CHIP_PADDING_X * 2.0f);
            if (cursor > 0.0f && cursor + chipWidth > availableWidth + 0.01f) {
                rows++;
                cursor = 0.0f;
            }
            cursor += chipWidth + CHIP_GAP_X;
        }
        return rows;
    }

    private record PillHit(int index, float x, float y, float width, float height) {
    }
}
