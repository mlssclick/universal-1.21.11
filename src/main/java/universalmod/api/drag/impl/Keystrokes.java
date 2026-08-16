package universalmod.api.drag.impl;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import universalmod.api.module.impl.render.Hud;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

import java.awt.Color;

public final class Keystrokes extends HudPanel {
    private static final float KEY_SIZE = 24.0F;
    private static final float GAP = 3.0F;
    private static final float TOTAL_WIDTH = KEY_SIZE * 3.0F + GAP * 2.0F;
    private static final float MOUSE_HEIGHT = 20.0F;
    private static final float SPACE_HEIGHT = 18.0F;
    private static final float RADIUS = 5.0F;
    private static final float BLUR_RADIUS = 4.0F;
    private static final float BLUR_SMOOTHNESS = 0.55F;
    private static final float KEY_TEXT_SIZE = 8.2F;
    private static final float MOUSE_TEXT_SIZE = 7.0F;
    private static final float PRESS_ANIM = 0.18F;

    private final Hud hud;
    private final SmoothAnimation w = animation();
    private final SmoothAnimation a = animation();
    private final SmoothAnimation s = animation();
    private final SmoothAnimation d = animation();
    private final SmoothAnimation lmb = animation();
    private final SmoothAnimation rmb = animation();
    private final SmoothAnimation space = animation();

    public Keystrokes(Hud hud) {
        super("keystrokes", "Keystrokes", 12.0F, 145.0F, TOTAL_WIDTH, fullHeight(true, true, true));
        this.hud = hud;
    }

    @Override
    public void render() {
        if (mc.getWindow() == null || hud == null) {
            return;
        }

        boolean showKeys = hud.keystrokesShowKeys();
        boolean showMouse = hud.keystrokesShowMouseButtons();
        boolean showSpace = hud.keystrokesShowSpace();
        boolean visible = showKeys || showMouse || showSpace;
        contentVisible(visible);
        if (!visible) {
            return;
        }

        update(w, isDown(mc.options.keyUp));
        update(a, isDown(mc.options.keyLeft));
        update(s, isDown(mc.options.keyDown));
        update(d, isDown(mc.options.keyRight));
        update(lmb, mouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT));
        update(rmb, mouseDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        update(space, isDown(mc.options.keyJump));
        float height = fullHeight(showKeys, showMouse, showSpace);
        size(TOTAL_WIDTH, height);

        float x = drag.x();
        float y = drag.y();
        float cursorY = y;

        if (showKeys) {
            float wX = x + KEY_SIZE + GAP;
            renderButton("W", wX, cursorY, KEY_SIZE, KEY_SIZE, w.get(), KEY_TEXT_SIZE, false);
            cursorY += KEY_SIZE + GAP;
            renderButton("A", x, cursorY, KEY_SIZE, KEY_SIZE, a.get(), KEY_TEXT_SIZE, false);
            renderButton("S", x + KEY_SIZE + GAP, cursorY, KEY_SIZE, KEY_SIZE, s.get(), KEY_TEXT_SIZE, false);
            renderButton("D", x + (KEY_SIZE + GAP) * 2.0F, cursorY, KEY_SIZE, KEY_SIZE, d.get(), KEY_TEXT_SIZE, false);
            cursorY += KEY_SIZE;
            if (showMouse || showSpace) {
                cursorY += GAP;
            }
        }

        if (showMouse) {
            float mouseWidth = (TOTAL_WIDTH - GAP) * 0.5F;
            renderButton("LMB", x, cursorY, mouseWidth, MOUSE_HEIGHT, lmb.get(), MOUSE_TEXT_SIZE, false);
            renderButton("RMB", x + mouseWidth + GAP, cursorY, mouseWidth, MOUSE_HEIGHT, rmb.get(), MOUSE_TEXT_SIZE, false);
            cursorY += MOUSE_HEIGHT;
            if (showSpace) {
                cursorY += GAP;
            }
        }

        if (showSpace) {
            renderButton("", x, cursorY, TOTAL_WIDTH, SPACE_HEIGHT, space.get(), KEY_TEXT_SIZE, true);
        }
    }

    private void renderButton(String label, float x, float y, float width, float height,
                              float progress, float textSize, boolean spaceButton) {
        float alpha = hud.keystrokesOpacity();
        Color normal = hud.keystrokesNormalColor();
        Color pressed = hud.keystrokesPressedColor();
        Color letters = hud.keystrokesLetterColor();
        float pressedAlpha = hud.keystrokesPressedAlpha();
        float p = clamp01(progress);
        int baseColor = ColorUtil.rgba(normal.getRed(), normal.getGreen(), normal.getBlue(),
                Math.round(normal.getAlpha() * alpha));
        HudRenderCompat.background(x, y, width, height, RADIUS, BLUR_RADIUS, BLUR_SMOOTHNESS, baseColor);
        Render2D.rect(x, y, width, height, RADIUS, ColorUtil.rgba(
                normal.getRed(), normal.getGreen(), normal.getBlue(),
                Math.round(normal.getAlpha() * alpha * 0.42F)
        ));
        renderCenterFill(x, y, width, height, p, alpha, pressedAlpha, pressed);

        if (spaceButton) {
            float lineWidth = Math.min(width - 14.0F, 40.0F);
            float lineHeight = 2.1F;
            float lineX = x + (width - lineWidth) * 0.5F;
            float lineY = y + (height - lineHeight) * 0.5F;
            int lineColor = ColorUtil.rgba(255, 255, 255, Math.round(245.0F * alpha));
            Render2D.rect(lineX, lineY, lineWidth, lineHeight, lineHeight * 0.5F, lineColor);
            return;
        }

        int textColor = ColorUtil.rgba(255, 255, 255, Math.round(letters.getAlpha() * alpha));

        float textWidth = Render2D.textWidth(FontType.BOLD, label, textSize);
        float textHeight = Render2D.textHeight(FontType.BOLD, label, textSize);
        Render2D.text(FontType.BOLD, label,
                x + (width - textWidth) * 0.5F,
                y + (height - textHeight) * 0.5F - 0.15F,
                textSize, textColor);
    }

    private void renderCenterFill(float x, float y, float width, float height, float progress, float alpha, float pressedAlpha, Color pressed) {
        if (progress <= 0.001F) {
            return;
        }
        float eased = easedFill(progress);
        float scale = 0.08F + 0.92F * eased;
        float fillWidth = width * scale;
        float fillHeight = height * scale;
        float fillX = x + (width - fillWidth) * 0.5F;
        float fillY = y + (height - fillHeight) * 0.5F;
        float fillRadius = Math.min(fillWidth, fillHeight) * (RADIUS / Math.max(1.0F, Math.min(width, height)));
        int fillColor = ColorUtil.rgba(
                pressed.getRed(), pressed.getGreen(), pressed.getBlue(),
                Math.round(255.0F * alpha * pressedAlpha * progress)
        );

        Render2D.pushScissor(Render2D.currentGraphics(), x, y, width, height);
        try {
            Render2D.rect(fillX, fillY, fillWidth, fillHeight, fillRadius, fillColor);
        } finally {
            Render2D.popScissor(Render2D.currentGraphics());
        }
    }

    private void update(SmoothAnimation animation, boolean down) {
        animation.run(down ? 1.0F : 0.0F, PRESS_ANIM, Easings.CUBIC_OUT, true);
        animation.update();
    }

    private boolean mouseDown(int button) {
        return GLFW.glfwGetMouseButton(mc.getWindow().handle(), button) == GLFW.GLFW_PRESS;
    }

    private static boolean isDown(KeyMapping mapping) {
        return mapping != null && mapping.isDown();
    }

    private static SmoothAnimation animation() {
        SmoothAnimation animation = new SmoothAnimation();
        animation.set(0.0F);
        return animation;
    }

    private static float fullHeight(boolean keys, boolean mouse, boolean space) {
        float height = 0.0F;
        int groups = 0;
        if (keys) {
            height += KEY_SIZE * 2.0F + GAP;
            groups++;
        }
        if (mouse) {
            height += MOUSE_HEIGHT;
            groups++;
        }
        if (space) {
            height += SPACE_HEIGHT;
            groups++;
        }
        if (groups > 1) {
            height += GAP * (groups - 1);
        }
        return Math.max(1.0F, height);
    }

    private static float easedFill(float progress) {
        float p = clamp01(progress);
        return 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
