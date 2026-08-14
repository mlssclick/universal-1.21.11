package universalmod.utils.theme;

import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;

public final class ThemeRender {
    private ThemeRender() {
    }

    public static boolean clickGuiGlass(float x, float y, float width, float height, float rounding, float alpha) {
        return clickGuiGlass(x, y, width, height, rounding, 7.0f, alpha);
    }

    public static boolean clickGuiGlass(
            float x, float y, float width, float height,
            float rounding, float squirt, float alpha
    ) {
        if (ThemeColors.isClickGuiDarkDesignEnabled()) {
            drawDark(x, y, width, height, rounding, alpha);
            return true;
        }
        float glass = ThemeColors.clickGuiLiquidGlassProgress();
        if (glass <= 0.0001f) {
            return false;
        }
        drawLiquidGlass(x, y, width, height, rounding, squirt, alpha, glass);
        return true;
    }

    public static boolean hudGlass(float x, float y, float width, float height, float alpha) {
        return hudGlass(x, y, width, height, ThemeColors.glassRounding(), alpha);
    }

    public static boolean hudGlass(float x, float y, float width, float height, float rounding, float alpha) {
        if (ThemeColors.isHudDarkDesignEnabled()) {
            drawDark(x, y, width, height, Math.max(0.0f, rounding), alpha);
            return true;
        }
        float glass = ThemeColors.hudLiquidGlassProgress();
        if (glass <= 0.0001f) {
            return false;
        }
        drawLiquidGlass(x, y, width, height, Math.max(0.0f, rounding), 7.0f, alpha, glass);
        return true;
    }

    public static float defaultAlpha(float alpha) {
        if (ThemeColors.isClickGuiDarkDesignEnabled()) {
            return 0.0f;
        }
        return clamp01(alpha) * ThemeColors.clickGuiMinimalismProgress();
    }

    public static float hudDefaultAlpha(float alpha) {
        if (ThemeColors.isHudDarkDesignEnabled()) {
            return 0.0f;
        }
        return clamp01(alpha) * ThemeColors.hudMinimalismProgress();
    }

    private static void drawDark(float x, float y, float width, float height, float rounding, float alpha) {
        float a = clamp01(alpha) * ThemeColors.darkOpacity();
        if (a <= 0.001f) {
            return;
        }
        int darkColor = ThemeColors.darkColor();
        Render2D.blur(x, y, width, height, Math.max(0.0f, rounding), 2.0f, 0.8f,
                ColorUtil.rgba(
                        ColorUtil.getRed(darkColor),
                        ColorUtil.getGreen(darkColor),
                        ColorUtil.getBlue(darkColor),
                        Math.round(72.0f * a)
                ));
        Render2D.darkPanel(
                x, y, width, height, Math.max(0.0f, rounding), a,
                ThemeColors.darkGradientStrength(), false, darkColor
        );
    }

    private static void drawLiquidGlass(
            float x, float y, float width, float height,
            float rounding, float squirt, float alpha, float glass
    ) {
        float a = clamp01(alpha);
        float minimalism = 1.0f - glass;
        float safeSquirt = Math.max(0.001f, squirt);
        float radius = Math.max(0.0f, rounding) * safeSquirt / 2.0f;
        float gx = x - 5.0f * minimalism;
        float gy = y - 5.0f * minimalism;
        float gw = width + 10.0f * minimalism;
        float gh = height + 10.0f * minimalism;
        int white = ColorUtil.rgba(255, 255, 255, Math.round(255.0f * a * glass));
        float globalAlpha = a * glass * glass;
        float fresnelPower = (ThemeColors.glassStrength() + (Math.abs(height - 240.0f) < 0.001f ? 2.0f : 1.0f)) * glass;
        float distortion = ThemeColors.glassDistortion() * glass;

        Render2D.liquidGlass(
                gx, gy, gw, gh, radius,
                white, globalAlpha, fresnelPower,
                0xFFFFFFFF, 1.0f, true, 0.0f,
                distortion, safeSquirt, ThemeColors.glassBlur()
        );

        float glassOverlay = clamp01(ThemeColors.glassOpacity() / 100.0f);
        float fillAlpha = 0.8f - (0.8f - glassOverlay) * glass;
        Render2D.squircle(
                x, y, width, height, radius, safeSquirt,
                ColorUtil.rgba(12, 12, 12, Math.round(255.0f * fillAlpha * a))
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
