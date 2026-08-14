package universalmod.screens.emotions;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import universalmod.api.module.impl.render.Emotions;
import universalmod.utils.render.ui.emotionwheel.BuiltEmotionWheelArc;
import universalmod.utils.render.ui.emotionwheel.EmotionWheelArcRenderer;

public final class EmotionWheelScreen extends Screen {
    private static final int SECTOR_COUNT = 6;
    private static final float SECTOR_DEGREE = 60.8F;
    private static final float SECTOR_STEP = 60.0F;
    private static final float OUTER_RADIUS_FACTOR = 0.205F;
    private static final float INNER_RADIUS_FACTOR = 0.60F;
    private static final float BLUR_RADIUS = 8.0F;
    // 30% more opaque than the source wheel (0xB8 -> 0xEF, hover clamps to 0xFF).
    private static final int SECTOR_COLOR = 0xEF110F1B;
    private static final int SECTOR_HOVER_COLOR = 0xFFEFB600;
    private static final int TEXT_COLOR = 0xFFE4E1EA;
    private static final int TEXT_HOVER_COLOR = 0xFFFFFFFF;
    private static final int RESET_COLOR = 0xFFFF7676;
    private static final String RESET_LABEL = "Сброс";

    private static final String[] LABELS = {
            Emotions.GREETING,
            Emotions.DANCE,
            Emotions.MASTURBATION,
            Emotions.ALPHA_WALK,
            Emotions.ALPHA_MALE,
            RESET_LABEL
    };

    private final Emotions owner;
    private int hoveredSector = -1;
    private int lastTouchedSector = -1;
    private boolean committed;

    public EmotionWheelScreen(Emotions owner) {
        super(Component.literal("Emotions"));
        this.owner = owner;
    }

    public boolean belongsTo(Emotions module) {
        return owner == module;
    }

    public void commitSelection() {
        if (committed) {
            return;
        }
        committed = true;
        int selected = lastTouchedSector;
        if (selected >= 0 && selected < Emotions.EMOTIONS.length) {
            owner.selectEmotion(Emotions.EMOTIONS[selected]);
        } else {
            owner.clearEmotion();
        }
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        float centerX = width * 0.5F;
        float centerY = height * 0.5F;
        float outerRadius = Math.min(width, height) * OUTER_RADIUS_FACTOR;
        float innerRadius = outerRadius * INNER_RADIUS_FACTOR;
        // Place every label on the same visual radius. Keeping the anchor slightly
        // toward the inner half leaves enough horizontal room for side sectors.
        float labelRadius = innerRadius + (outerRadius - innerRadius) * 0.50F;

        boolean cancelCenter = insideCenter(mouseX, mouseY, centerX, centerY, innerRadius);
        hoveredSector = cancelCenter ? -1 : computeHoveredSector(mouseX, mouseY, centerX, centerY, innerRadius, outerRadius);

        if (cancelCenter) {
            lastTouchedSector = -1;
        } else if (hoveredSector >= 0) {
            lastTouchedSector = hoveredSector;
        }

        EmotionWheelArcRenderer arcs = EmotionWheelArcRenderer.getInstance();
        arcs.beginFrame(graphics);
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float centerAngle = -90.0F + i * SECTOR_STEP;
            boolean active = i == activeVisualSector();
            int color = active ? SECTOR_HOVER_COLOR : SECTOR_COLOR;
            float size = outerRadius * 2.0F;
            arcs.enqueue(new BuiltEmotionWheelArc(
                    centerX - outerRadius,
                    centerY - outerRadius,
                    size,
                    outerRadius - innerRadius,
                    SECTOR_DEGREE,
                    centerAngle,
                    BLUR_RADIUS,
                    color
            ));
        }
        arcs.flush();

        for (int i = 0; i < SECTOR_COUNT; i++) {
            float centerAngle = -90.0F + i * SECTOR_STEP;
            double radians = Math.toRadians(centerAngle);
            float x = centerX + (float) Math.cos(radians) * labelRadius;
            float y = centerY + (float) Math.sin(radians) * labelRadius;
            boolean active = i == activeVisualSector();
            int color = i == 5 ? RESET_COLOR : (active ? TEXT_HOVER_COLOR : TEXT_COLOR);
            drawSectorLabel(graphics, LABELS[i], x, y, outerRadius * 0.52F, color);
        }

    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubled);
        }

        float centerX = width * 0.5F;
        float centerY = height * 0.5F;
        float outerRadius = Math.min(width, height) * OUTER_RADIUS_FACTOR;
        float innerRadius = outerRadius * INNER_RADIUS_FACTOR;
        double mouseX = event.x();
        double mouseY = event.y();

        if (insideCenter(mouseX, mouseY, centerX, centerY, innerRadius)) {
            lastTouchedSector = -1;
            commitSelection();
            return true;
        }

        int clickedSector = computeHoveredSector(mouseX, mouseY, centerX, centerY, innerRadius, outerRadius);
        if (clickedSector >= 0) {
            hoveredSector = clickedSector;
            lastTouchedSector = clickedSector;
            commitSelection();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (minecraft != null) {
                minecraft.setScreen(null);
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }


    private void drawSectorLabel(GuiGraphics graphics, String label, float x, float y, float maxWidth, int color) {
        String[] lines = switch (label) {
            case Emotions.ALPHA_WALK -> new String[]{"Альфа", "ходьба"};
            case Emotions.ALPHA_MALE -> new String[]{"Альфа", "Мужик"};
            default -> new String[]{label};
        };

        int widest = 1;
        for (String line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        float scale = Math.min(1.0F, Math.max(0.45F, maxWidth / widest));
        float lineHeight = font.lineHeight;
        float blockHeight = lines.length * lineHeight;

        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale);
            float firstY = -blockHeight * 0.5F;
            for (int i = 0; i < lines.length; i++) {
                int drawY = Math.round(firstY + i * lineHeight);
                graphics.drawCenteredString(font, lines[i], 0, drawY, color);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private int activeVisualSector() {
        return hoveredSector >= 0 ? hoveredSector : lastTouchedSector;
    }

    private static boolean insideCenter(double mouseX, double mouseY, float centerX, float centerY, float innerRadius) {
        float dx = (float) mouseX - centerX;
        float dy = (float) mouseY - centerY;
        return dx * dx + dy * dy < innerRadius * innerRadius;
    }

    private static int computeHoveredSector(double mouseX, double mouseY, float centerX, float centerY, float innerRadius, float outerRadius) {
        float dx = (float) mouseX - centerX;
        float dy = (float) mouseY - centerY;
        float distanceSq = dx * dx + dy * dy;
        float maxRadius = outerRadius + 8.0F;
        if (distanceSq < innerRadius * innerRadius || distanceSq > maxRadius * maxRadius) {
            return -1;
        }
        float angle = normalize((float) Math.toDegrees(Math.atan2(dy, dx)));
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float center = normalize(-90.0F + i * SECTOR_STEP);
            float start = normalize(center - SECTOR_DEGREE * 0.5F);
            float end = normalize(center + SECTOR_DEGREE * 0.5F);
            if (angleInside(angle, start, end)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean angleInside(float angle, float start, float end) {
        return start <= end ? angle >= start && angle <= end : angle >= start || angle <= end;
    }

    private static float normalize(float angle) {
        float value = angle % 360.0F;
        return value < 0.0F ? value + 360.0F : value;
    }
}
