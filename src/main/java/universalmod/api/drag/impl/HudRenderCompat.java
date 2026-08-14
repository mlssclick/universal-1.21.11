package universalmod.api.drag.impl;

import universalmod.api.module.impl.render.Hud;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.blur.BuiltBlur;

final class HudRenderCompat {
    private HudRenderCompat() {
    }

    static void background(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        Hud.renderHudBackground(x, y, width, height, radius, blurRadius, smoothness, color);
    }

    static void splitHeader(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        Hud.renderSplitHudHeader(x, y, width, height, radius, blurRadius, smoothness, color);
    }

    static void background(BuiltBlur blur) {
        if (blur == null) {
            return;
        }
        Hud.renderHudBackground(blur);
    }

    static void glow(String texture, float x, float y, float width, float height, float radius, int color) {
        Hud.renderHudGlow(texture, x, y, width, height, radius, color);
    }
}
