package universalmod.api.module.impl.render;

import universalmod.api.drag.core.ElementManager;
import universalmod.api.drag.core.ElementScreen;
import universalmod.api.drag.core.HudElement;
import universalmod.api.drag.impl.HudPanel;
import universalmod.api.drag.impl.HudElementStyleMenu;
import universalmod.api.drag.impl.ScoreboardStyleMenu;
import universalmod.api.drag.impl.BossbarPanel;
import universalmod.api.drag.impl.Cooldowns;
import universalmod.api.drag.impl.Potions;
import universalmod.api.drag.impl.CurrentEventsPanel;
import universalmod.api.drag.impl.Inventory;
import universalmod.api.drag.impl.Keystrokes;
import universalmod.api.drag.impl.MusicPlayerPanel;
import universalmod.api.drag.impl.LyricsPanel;
import universalmod.api.drag.impl.TntTimerPanel;
import universalmod.api.events.impl.DrawEvent;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.MultiModeSetting;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.utils.render.LoadingVisualGuard;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.item.RenderItem;
import universalmod.utils.theme.ThemeColors;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.Render2DCoordinateSpace;
import universalmod.utils.render.ui.blur.BuiltBlur;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.List;

public class Hud extends Module {
    private static final String EDIT_HINT = "\u041f\u041a\u041c \u0434\u043b\u044f \u0440\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u044f";

    private static final String BOSSBAR_ELEMENT = "Bossbar";
    private static final String MUSIC_PLAYER_ELEMENT = "Music Player";
    private static final String[] ELEMENTS = {
            "Inventory",
            "Cooldowns",
            "Potions",
            "Keystrokes",
            "TNT Timer",
            MUSIC_PLAYER_ELEMENT,
            BOSSBAR_ELEMENT,
            "Lyrics",
            "Event"
    };
    private static final String[] DEFAULT_ELEMENTS = {
            "Inventory",
            "Cooldowns",
            "Potions",
            "Keystrokes",
            "TNT Timer",
            MUSIC_PLAYER_ELEMENT,
            "Lyrics",
            "Event"
    };
    private static Hud instance;

    private final MultiModeSetting elements = register(new MultiModeSetting("Elements", "HUD elements to render.",
            ELEMENTS, DEFAULT_ELEMENTS));
    private final BooleanSetting keystrokesKeys = register(new BooleanSetting(
            "Keys", "Show W, A, S and D in the Keystrokes HUD.", true));
    private final BooleanSetting keystrokesMouseButtons = register(new BooleanSetting(
            "Mouse Buttons", "Show LMB and RMB in the Keystrokes HUD.", true));
    private final BooleanSetting keystrokesSpace = register(new BooleanSetting(
            "Space", "Show the Space bar in the Keystrokes HUD.", true));
    private final NumberSetting keystrokesOpacity = register(new NumberSetting(
            "Keystrokes Opacity", "Opacity of the Keystrokes HUD.", 82.0, 5.0, 100.0, 1.0));
    private final ColorSetting keystrokesNormalColor = register(new ColorSetting(
            "Keystrokes Color", "Normal key background color.", new Color(17, 18, 23, 220)));
    private final ColorSetting keystrokesPressedColor = register(new ColorSetting(
            "Keystrokes Pressed Color", "Color used while a key is pressed.", new Color(127, 242, 255, 255)));
    private final ColorSetting keystrokesLetterColor = register(new ColorSetting(
            "Keystrokes Letter Color", "Color of the letters in the Keystrokes HUD.", new Color(255, 255, 255, 255)));
    private final NumberSetting keystrokesPressedAlpha = register(new NumberSetting(
            "Keystrokes Pressed Alpha", "Opacity of the pressed key color.", 100.0, 0.0, 100.0, 1.0));
    private final ColorSetting potionsColor = register(new ColorSetting(
            "Potions Color", "Accent color for the Potions HUD.", new Color(244, 176, 101, 255)));
    private final ColorSetting cooldownsColor = register(new ColorSetting(
            "Cooldowns Color", "Accent color for the Cooldowns HUD.", new Color(244, 176, 101, 255)));

    private final BooleanSetting useCustomBossbarSettings = register(new BooleanSetting(
            "Use Custom Bossbar Settings",
            "Uses the selected bossbar and text colors instead of the server or vanilla style.",
            false
    ));
    private final ColorSetting bossbarColor = register(new ColorSetting(
            "Bossbar Color",
            "Color of the custom bossbar progress and its dark background.",
            new Color(255, 85, 255, 255)
    ));
    private final ColorSetting bossbarTextColor = register(new ColorSetting(
            "Bossbar Text Color",
            "Color of bossbar titles while custom bossbar settings are enabled.",
            new Color(255, 255, 255, 255)
    ));

    private final MusicPlayerPanel musicPlayerPanel = new MusicPlayerPanel();
    private final BossbarPanel bossbarPanel = new BossbarPanel(this);
    private final SmoothAnimation hintAnimation = new SmoothAnimation();
    private HudPanel hintElement;
    private final List<HudElement> elementsList = List.of(
            new Inventory(),
            new Cooldowns(),
            new Potions(),
            new Keystrokes(this),
            new TntTimerPanel(),
            musicPlayerPanel,
            bossbarPanel,
            new LyricsPanel(),
            CurrentEventsPanel.getInstance()
    );

    public Hud() {
        super("HUD", "Renders draggable HUD elements.", ModuleCategory.RENDER);
        instance = this;
        elements.setSelected("TNT Timer", true);
        elements.setSelected("Cooldowns", true);
        elements.setSelected("Potions", true);
        elements.setSelected("Keystrokes", true);
        elements.setSelected(MUSIC_PLAYER_ELEMENT, true);
        elements.setSelected("Lyrics", true);
        elements.setSelected("Event", true);
        elements.setSelected(BOSSBAR_ELEMENT, false);

        keystrokesKeys.visibleWhen(this::isKeystrokesSelected);
        keystrokesMouseButtons.visibleWhen(this::isKeystrokesSelected);
        keystrokesSpace.visibleWhen(this::isKeystrokesSelected);
        keystrokesOpacity.visibleWhen(this::isKeystrokesSelected);
        keystrokesNormalColor.visibleWhen(this::isKeystrokesSelected);
        keystrokesPressedColor.visibleWhen(this::isKeystrokesSelected);
        keystrokesLetterColor.visibleWhen(this::isKeystrokesSelected);
        keystrokesPressedAlpha.visibleWhen(this::isKeystrokesSelected);
        potionsColor.visibleWhen(() -> elements.isSelected("Potions"));
        cooldownsColor.visibleWhen(() -> elements.isSelected("Cooldowns"));
        useCustomBossbarSettings.visibleWhen(this::isBossbarSelected);
        bossbarColor.visibleWhen(() -> isBossbarSelected() && useCustomBossbarSettings.getValue());
        bossbarTextColor.visibleWhen(() -> isBossbarSelected() && useCustomBossbarSettings.getValue());
        setEnabled(true);
    }

    public static Hud getInstance() {
        return instance;
    }

    public boolean isBossbarSelected() {
        return elements.isSelected(BOSSBAR_ELEMENT);
    }

    public boolean isMusicPlayerSelected() {
        return elements.isSelected(MUSIC_PLAYER_ELEMENT);
    }

    public boolean isKeystrokesSelected() {
        return elements.isSelected("Keystrokes");
    }

    public boolean keystrokesShowKeys() {
        return keystrokesKeys.getValue();
    }

    public boolean keystrokesShowMouseButtons() {
        return keystrokesMouseButtons.getValue();
    }

    public boolean keystrokesShowSpace() {
        return keystrokesSpace.getValue();
    }

    public float keystrokesOpacity() {
        return Math.max(0.05F, Math.min(1.0F, keystrokesOpacity.getFloat() / 100.0F));
    }

    public Color keystrokesNormalColor() {
        return keystrokesNormalColor.getValue();
    }

    public Color keystrokesPressedColor() {
        return keystrokesPressedColor.getValue();
    }

    public Color keystrokesLetterColor() {
        return keystrokesLetterColor.getValue();
    }

    public ColorSetting keystrokesNormalColorSetting() {
        return keystrokesNormalColor;
    }

    public ColorSetting keystrokesPressedColorSetting() {
        return keystrokesPressedColor;
    }

    public ColorSetting keystrokesLetterColorSetting() {
        return keystrokesLetterColor;
    }

    public float keystrokesPressedAlpha() {
        return Math.max(0.0F, Math.min(1.0F, keystrokesPressedAlpha.getFloat() / 100.0F));
    }

    public Color potionsColor() {
        return potionsColor.getValue();
    }

    public Color cooldownsColor() {
        return cooldownsColor.getValue();
    }

    public boolean useCustomBossbarSettings() {
        return useCustomBossbarSettings.getValue();
    }

    public Color bossbarColor() {
        return bossbarColor.getValue();
    }

    public Color bossbarTextColor() {
        return bossbarTextColor.getValue();
    }

    public static boolean shouldReplaceVanillaBossbar() {
        Hud hud = instance;
        return hud != null && hud.isEnabled() && hud.bossbarPanel.shouldReplaceVanilla();
    }

    public static boolean isBlurEnabled() {
        return true;
    }

    public static boolean isGlowEnabled() {
        return true;
    }

    public static void renderHudBackground(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0f;
        if (alpha <= 0.0001f || width <= 0.0f || height <= 0.0f) {
            return;
        }
        if (isBlurEnabled()) {
            Render2D.blur(x, y, width, height, radius, Math.max(6.0f, blurRadius * 1.75f),
                    Math.max(0.70f, smoothness), ThemeColors.hudBlurColor(ColorUtil.rgba(5, 7, 11, Math.round(212.0f * alpha))));
        } else {
            Render2D.rect(x, y, width, height, radius, ThemeColors.hudBlurColor(ColorUtil.rgba(5, 7, 11, Math.round(238.0f * alpha))));
        }
        Render2D.hudChrome(x, y, width, height, radius, 0.78f * alpha, 0.88f, 0.90f);
    }

    public static void renderHudHeader(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
        renderHudBackground(x, y, width, height, radius, blurRadius, smoothness, color);
    }

    public static void renderHudBackground(BuiltBlur blur) {
        if (blur == null) {
            return;
        }
        float alpha = ((blur.color() >>> 24) & 0xFF) / 255.0f;
        if (alpha <= 0.0001f) {
            return;
        }
        BuiltBlur darkBlur = new BuiltBlur(
                blur.x(), blur.y(), blur.width(), blur.height(),
                blur.radiusTopLeft(), blur.radiusTopRight(), blur.radiusBottomRight(), blur.radiusBottomLeft(),
                Math.max(0.70f, blur.smoothness()), Math.max(6.0f, blur.blurRadius() * 1.75f),
                ThemeColors.hudBlurColor(ColorUtil.rgba(5, 7, 11, Math.round(212.0f * alpha)))
        );
        if (isBlurEnabled()) {
            Render2D.blur(darkBlur);
        } else {
            Render2D.rect(blur.x(), blur.y(), blur.width(), blur.height(), blur.radiusTopLeft(), blur.radiusTopRight(),
                    blur.radiusBottomRight(), blur.radiusBottomLeft(), ThemeColors.hudBlurColor(ColorUtil.rgba(5, 7, 11, Math.round(238.0f * alpha))));
        }
        Render2D.hudChrome(blur.x(), blur.y(), blur.width(), blur.height(), blur.radiusTopLeft(), 0.78f * alpha, 0.88f, 0.90f);
    }

    public static void renderHudGlow(String texture, float x, float y, float width, float height, float radius, int color) {
        if (isGlowEnabled()) {
            Render2D.image(texture, x, y, width, height, radius, color);
        }
    }

    public void renderHudLayer(DrawEvent event) {
        if (mc.player == null || mc.level == null || mc.getWindow() == null || LoadingVisualGuard.shouldSuppressHud(mc)) {
            return;
        }

        ElementScreen screen = ElementScreen.current();
        ElementManager elementManager = ElementManager.getInstance();
        elementManager.frame(screen);
        if (event.getLayer() == DrawEvent.Layer.CHAT_OVERLAY) {
            elementManager.updateActiveElementFromMouse();
            HudElementStyleMenu.getInstance().updateBeforeHudRender();
        }
        RenderItem.beginFrame(event.getGraphics());
        try {
            for (HudElement element : elementsList) {
                boolean selected = elements.isSelected(elementName(element));
                if (element instanceof BossbarPanel panel) {
                    panel.setHudSelected(selected);
                    boolean render = selected || panel.shouldRenderDynamicFallback();
                    panel.setHudVisible(render);
                    if (render) {

                        renderHudPanel(panel, selected);
                    }
                    continue;
                }

                setElementVisible(element, selected);
                if (selected) {
                    if (element instanceof HudPanel panel) {
                        renderHudPanel(panel, true);
                    } else {
                        element.render();
                    }
                }
            }
        } finally {
            RenderItem.flush();
        }

        boolean editLayer = event.getLayer() == DrawEvent.Layer.CHAT_OVERLAY && elementManager.canEditCurrentScreen();
        if (editLayer) {
            elementManager.renderEditorOverlay(event.getGraphics(), screen);
            HudElementStyleMenu.getInstance().render(event.getGraphics());
        }
    }

    public boolean handleMouseClicked(MouseButtonEvent event, boolean doubled) {
        if (!isEnabled() || event == null) {
            return false;
        }
        if (HudElementStyleMenu.getInstance().mouseClicked(event)) {
            return true;
        }
        if (event.button() == 1) {
            for (int i = elementsList.size() - 1; i >= 0; i--) {
                HudElement element = elementsList.get(i);
                if (!elements.isSelected(elementName(element)) || !(element instanceof HudPanel panel)) {
                    continue;
                }
                if (panel.hit((float) event.x(), (float) event.y())) {
                    ScoreboardStyleMenu.getInstance().close();
                    HudElementStyleMenu.getInstance().open(panel, (float) event.x(), (float) event.y());
                    return true;
                }
            }
            return false;
        }
        if (event.button() != 0) {
            return false;
        }
        for (int i = elementsList.size() - 1; i >= 0; i--) {
            HudElement element = elementsList.get(i);
            if (!elements.isSelected(elementName(element))) {
                continue;
            }
            if (element instanceof HudPanel panel) {
                if (element.mouseClicked(event, doubled)) {
                    return true;
                }
            } else if (element.mouseClicked(event, doubled)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isEnabled()) {
            return false;
        }
        for (int i = elementsList.size() - 1; i >= 0; i--) {
            HudElement element = elementsList.get(i);
            if (!elements.isSelected(elementName(element))) {
                continue;
            }
            if (element instanceof HudPanel panel) {
                if (element.mouseScrolled(mouseX, mouseY, scrollY)) {
                    return true;
                }
            } else if (element.mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }
        return false;
    }

    public void renderHudHoverHint(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isEnabled() || mc.player == null || mc.level == null || !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            fadeOutHint();
            return;
        }

        ElementScreen screen = ElementScreen.current();
        ElementManager.getInstance().frame(screen);
        float coordinateScale = Math.max(0.0001F, screen.coordinateScale());
        float designMouseX = mouseX / coordinateScale;
        float designMouseY = mouseY / coordinateScale;
        HudPanel hovered = hoveredPanelAt(designMouseX, designMouseY);
        if (hovered == null && Math.abs(coordinateScale - 1.0F) > 0.0001F) {
            hovered = hoveredPanelAt(mouseX, mouseY);
        }
        if (hovered != null && hovered.moving()) {
            hovered = null;
        }

        if (hovered != null && hovered != hintElement) {
            hintElement = hovered;
            hintAnimation.set(0.0);
        }
        hintAnimation.run(hovered != null ? 1.0 : 0.0, 0.16, Easings.CUBIC_OUT, true);
        hintAnimation.update();

        float progress = Math.clamp(hintAnimation.get(), 0.0F, 1.0F);
        if (progress <= 0.01F || hintElement == null) {
            if (progress <= 0.01F) {
                hintElement = null;
            }
            return;
        }

        Render2D.beginFrame(graphics);
        try {
            float textSize = 7.4F;
            float gap = 4.0F;
            float textWidth = Render2D.textWidth(universalmod.utils.render.ui.font.FontType.SEMIBOLD, EDIT_HINT, textSize);
            float textHeight = textSize;
            float elementX = hintElement.x();
            float elementY = hintElement.y();
            float elementWidth = hintElement.width();
            float elementHeight = hintElement.height();
            float centerX = elementX + elementWidth * 0.5F;
            boolean below = elementY + elementHeight + gap + textHeight <= screen.height();
            float baseY = below ? elementY + elementHeight + gap : elementY - textHeight - gap;
            float offset = (1.0F - progress) * (below ? 3.0F : -3.0F);
            float drawX = clamp(centerX - textWidth * 0.5F, 2.0F, screen.width() - textWidth - 2.0F);
            float drawY = clamp(baseY + offset, 2.0F, screen.height() - textHeight - 2.0F);
            Render2D.text(universalmod.utils.render.ui.font.FontType.SEMIBOLD, EDIT_HINT, drawX, drawY, textSize,
                    ColorUtil.rgba(255, 255, 255, Math.round(255.0F * progress)));
        } finally {
            Render2D.flush();
            graphics.nextStratum();
        }
    }

    private void fadeOutHint() {
        hintAnimation.run(0.0, 0.16, Easings.CUBIC_OUT, true);
        hintAnimation.update();
        if (hintAnimation.get() <= 0.01F) {
            hintElement = null;
        }
    }

    private HudPanel hoveredPanelAt(float mouseX, float mouseY) {
        for (int i = elementsList.size() - 1; i >= 0; i--) {
            HudElement element = elementsList.get(i);
            if (!elements.isSelected(elementName(element)) || !(element instanceof HudPanel panel)) {
                continue;
            }
            if (panel.hit(mouseX, mouseY)) {
                return panel;
            }
        }
        return null;
    }

    private void renderHudPanel(HudPanel panel, boolean useConfiguredScale) {
        if (panel == null) {
            return;
        }

        float scale = useConfiguredScale ? panel.configuredHudScale() : 1.0F;
        panel.prepareHudScale(scale);

        GuiGraphics graphics = Render2D.currentGraphics();
        float appliedScale = panel.hudScale();
        float dragScale = panel.dragScale();
        float tiltDegrees = panel.dragTiltDegrees();
        float anchorX = panel.x();
        float anchorY = panel.y();
        float centerX = anchorX + panel.width() * 0.5F;
        float centerY = anchorY + panel.height() * 0.5F;
        boolean transform = graphics != null
                && (Math.abs(appliedScale - 1.0F) > 0.0001F
                || Math.abs(dragScale - 1.0F) > 0.0001F
                || Math.abs(tiltDegrees) > 0.0001F);

        try {
            if (!transform) {
                panel.render();
                return;
            }

            float guiAnchorX = Render2DCoordinateSpace.toGui(anchorX);
            float guiAnchorY = Render2DCoordinateSpace.toGui(anchorY);
            float guiCenterX = Render2DCoordinateSpace.toGui(centerX);
            float guiCenterY = Render2DCoordinateSpace.toGui(centerY);
            graphics.pose().pushMatrix();
            try {

                graphics.pose().translate(guiCenterX, guiCenterY);
                graphics.pose().rotate((float) Math.toRadians(tiltDegrees));
                graphics.pose().scale(dragScale);
                graphics.pose().translate(-guiCenterX, -guiCenterY);

                graphics.pose().translate(guiAnchorX, guiAnchorY);
                graphics.pose().scale(appliedScale);
                graphics.pose().translate(-guiAnchorX, -guiAnchorY);

                Render2D.withProjection(
                        new Matrix4f(),
                        (px, py) -> projectHudPoint(px, py, anchorX, anchorY, centerX, centerY, appliedScale, dragScale, tiltDegrees),
                        panel::render
                );
            } finally {
                graphics.pose().popMatrix();
            }
        } finally {
            panel.finishHudScale();
        }
    }

    private Render2D.ProjectedPoint projectHudPoint(
            float px,
            float py,
            float anchorX,
            float anchorY,
            float centerX,
            float centerY,
            float appliedScale,
            float dragScale,
            float tiltDegrees
    ) {
        float scaledX = anchorX + (px - anchorX) * appliedScale;
        float scaledY = anchorY + (py - anchorY) * appliedScale;
        float dx = (scaledX - centerX) * dragScale;
        float dy = (scaledY - centerY) * dragScale;
        float radians = (float) Math.toRadians(tiltDegrees);
        float sin = (float) Math.sin(radians);
        float cos = (float) Math.cos(radians);
        return new Render2D.ProjectedPoint(
                centerX + dx * cos - dy * sin,
                centerY + dx * sin + dy * cos
        );
    }

    private String elementName(HudElement element) {
        if (element instanceof HudPanel panel) {
            return panel.elementName();
        }
        return element.getClass().getSimpleName();
    }

    private void setElementVisible(HudElement element, boolean visible) {
        if (element instanceof HudPanel panel) {
            panel.setHudVisible(visible);
        }
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
