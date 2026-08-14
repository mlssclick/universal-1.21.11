package universalmod.screens.clickgui.impl.module;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import universalmod.api.module.Module;
import universalmod.api.settings.Setting;
import universalmod.api.settings.bind.KeyBind;
import universalmod.api.settings.impl.BindSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.MultiModeSetting;
import universalmod.screens.clickgui.impl.options.ClickGuiOption;
import universalmod.utils.lang.LanguageManager;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;

import java.util.List;

public class ModuleOption {
    // The complete expanded-settings UI is intentionally 30% smaller than the previous build.
    private static final float OPTION_GAP = 2.1f;
    private static final float SETTINGS_PADDING_X = 5.6f;
    private static final float SETTINGS_PADDING_Y = 4.9f;
    private static final float OPTION_SCALE = 0.70f;

    private final Module module;
    private List<ClickGuiOption> settings = List.of();
    private final boolean hasSupportedSettings;
    private boolean settingsInitialized;
    private final BindSetting bindSetting;
    private final SmoothAnimation expandedAnimation = new SmoothAnimation();
    private final SmoothAnimation enabledAnimation = new SmoothAnimation();
    private final SmoothAnimation bindAnimation = new SmoothAnimation();

    private boolean expanded;
    private boolean binding;
    private float cardX;
    private float cardY;
    private float cardWidth;
    private float cardHeight;
    private float cardScale = 1.0f;
    private boolean cardInteractive;

    public ModuleOption(Module module) {
        this.module = module;
        this.hasSupportedSettings = ModuleOptionFactory.hasSupportedSettings(module);
        this.bindSetting = module.getBindSetting();
        expandedAnimation.set(0.0);
        enabledAnimation.set(module.isEnabled() ? 1.0 : 0.0);
        bindAnimation.set(0.0);
    }

    public void updateAnimation() {
        expandedAnimation.run(expanded ? 1.0 : 0.0, 0.24, Easings.CUBIC_OUT, true);
        expandedAnimation.update();
        enabledAnimation.run(module.isEnabled() ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT, true);
        enabledAnimation.update();
        bindAnimation.run(binding ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT, true);
        bindAnimation.update();
        if (settingsInitialized) {
            for (ClickGuiOption option : settings) {
                option.updateVisibility();
            }
        }
    }

    public void renderCard(
            GuiGraphics graphics,
            float x,
            float y,
            float width,
            float height,
            float scale,
            float alpha,
            float clipX,
            float clipY,
            float clipWidth,
            float clipHeight
    ) {
        beginInteractionFrame();
        cardX = x;
        cardY = y;
        cardWidth = width * scale;
        cardHeight = height * scale;
        cardScale = scale;
        cardInteractive = alpha > 0.01f && intersects(x, y, cardWidth, cardHeight, clipX, clipY, clipWidth, clipHeight);

        float enabled = enabledAnimation.get();
        float bind = bindAnimation.get();
        if (hasSettings() && (expanded || expandedAnimation.get() > 0.01f)) {
            ensureSettings();
        }
        float settingsProgress = hasSettings() ? Math.max(0.0f, Math.min(1.0f, expandedAnimation.get())) : 0.0f;
        float settingsVisibleHeight = hasSettings() ? computeSettingsHeight() * scale * settingsProgress : 0.0f;
        float shellHeight = cardHeight + settingsVisibleHeight;
        float radius = 6.0f * scale;

        // The ClickGUI root already owns the background blur. A second blur for every module card
        // multiplied the cold-start GPU work by the number of visible modules. The card still keeps
        // the same translucent fill and outline, but no longer submits a redundant blur region.

        float cardBottomRadius = settingsProgress > 0.01f ? 0.0f : radius;
        int cardFill = ColorUtil.rgba(7, 10, 21, Math.round((54.0f + 26.0f * enabled) * alpha));
        Render2D.rect(x, y, cardWidth, cardHeight,
                radius, radius, cardBottomRadius, cardBottomRadius, cardFill);

        if (settingsVisibleHeight > 0.25f) {
            int settingsFill = ColorUtil.rgba(7, 10, 21,
                    Math.round((38.0f + 12.0f * enabled) * alpha * settingsProgress));
            Render2D.rect(x, y + cardHeight, cardWidth, settingsVisibleHeight,
                    0.0f, 0.0f, radius, radius, settingsFill);
        }

        Render2D.outline(x, y, cardWidth, shellHeight, radius, 0.35f * scale,
                ColorUtil.rgba(133, 119, 198, Math.round((18.0f + 18.0f * enabled) * alpha)));

        // Binding feedback stays inside the bind badge; the module title never morphs into bind text.
        String title = module.getDisplayName();
        int titleColor = ColorUtil.rgba(
                Math.round(232.0f + 20.0f * enabled),
                Math.round(234.0f + 18.0f * enabled),
                Math.round(244.0f + 11.0f * enabled),
                Math.round(255.0f * alpha)
        );

        float badgeHeight = 13.0f * scale;
        float badgeY = y + (cardHeight - badgeHeight) * 0.5f;
        float bindBadgeWidth = renderBindBadge(graphics, x + 6.5f * scale, badgeY, scale, alpha, bind);

        float switchWidth = 22.0f * scale;
        float switchHeight = 12.0f * scale;
        float switchX = x + cardWidth - switchWidth - 6.5f * scale;
        float switchY = y + (cardHeight - switchHeight) * 0.5f;

        float textSize = 7.7f * scale;
        float textX = x + 6.5f * scale + bindBadgeWidth + 6.5f * scale;
        float availableTextWidth = Math.max(1.0f, switchX - 7.0f * scale - textX);
        float textY = y + (cardHeight - textSize) * 0.5f - 0.80f * scale;
        Render2D.text(FontType.BOLD, fitText(title, textSize, availableTextWidth), textX, textY, textSize, titleColor);
        renderToggle(switchX, switchY, scale, alpha, enabled);

        renderSettings(graphics, x, y + cardHeight, width, scale, alpha, clipX, clipY, clipWidth, clipHeight);
    }

    private float renderBindBadge(GuiGraphics graphics, float x, float y, float scale, float alpha, float bindProgress) {
        String bind = displayBind();
        boolean unbound = "n/a".equalsIgnoreCase(bind);
        float badgeHeight = 13.0f * scale;
        float textSize = (unbound ? 5.1f : 5.6f) * scale;
        float textWidth = Render2D.textWidth(FontType.BOLD, bind, textSize);
        float badgeWidth = Math.max(22.0f * scale, Math.min(44.0f * scale, textWidth + 9.0f * scale));
        float radius = 4.5f * scale;
        float pulse = Math.max(0.0f, Math.min(1.0f, bindProgress));

        // Only the panel behind the bind text animates. The text itself stays stable and centered.
        int fill = ColorUtil.lerpColor(
                ColorUtil.rgba(31, 34, 50, Math.round((unbound ? 190.0f : 230.0f) * alpha)),
                ColorUtil.rgba(91, 73, 139, Math.round(242.0f * alpha)),
                pulse
        );
        int outline = ColorUtil.lerpColor(
                ColorUtil.rgba(151, 137, 216, Math.round((unbound ? 24.0f : 46.0f) * alpha)),
                ColorUtil.rgba(188, 162, 241, Math.round(108.0f * alpha)),
                pulse
        );
        Render2D.rect(x, y, badgeWidth, badgeHeight, radius, fill);
        Render2D.outline(x, y, badgeWidth, badgeHeight, radius, 0.35f * scale, outline);

        float clipPad = 3.0f * scale;
        float clipWidth = badgeWidth - clipPad * 2.0f;
        float textX = x + (badgeWidth - textWidth) * 0.5f;
        float textY = y + (badgeHeight - textSize) * 0.5f - 0.75f * scale;
        Render2D.pushScissor(graphics, x + clipPad, y, clipWidth, badgeHeight);
        try {
            Render2D.text(FontType.BOLD, bind, textX, textY, textSize,
                    ColorUtil.rgba(unbound ? 170 : 215, unbound ? 172 : 215, unbound ? 192 : 231,
                            Math.round((unbound ? 218.0f : 248.0f) * alpha)));
        } finally {
            Render2D.popScissor(graphics);
        }
        return badgeWidth;
    }

    private String displayBind() {
        if (binding) {
            return "...";
        }
        if (bindSetting == null || bindSetting.getValue() == null || !bindSetting.getValue().isBound()) {
            return "n/a";
        }
        String display = bindSetting.getValue().getDisplayName();
        if (display == null || display.isBlank()) {
            return "n/a";
        }
        return display
                .replace("LEFT ", "L")
                .replace("RIGHT ", "R")
                .replace("MOUSE ", "M");
    }

    private void renderToggle(float x, float y, float scale, float alpha, float progress) {
        // Match the HUD switch geometry exactly: 22x12, 5px track radius, 8x8 knob at y+2.
        // Keeping the radius below half-height avoids the pointed/capsule-looking ends seen before.
        float width = 22.0f * scale;
        float height = 12.0f * scale;
        float radius = 5.0f * scale;
        renderSwitchGlass(x, y, width, height, radius, alpha, progress);
        float knobSize = 8.0f * scale;
        float knobX = x + (2.0f + 10.0f * progress) * scale;
        Render2D.rect(knobX, y + 2.0f * scale, knobSize, knobSize, 3.0f * scale,
                ColorUtil.rgba(255, 255, 255, Math.round(245.0f * alpha)));
    }

    private void renderSwitchGlass(float x, float y, float width, float height, float radius, float alpha, float progress) {
        int trackColor = ColorUtil.lerpColor(
                ColorUtil.rgba(86, 87, 91, Math.round(155.0f * alpha)),
                ColorUtil.rgba(137, 117, 199, Math.round(205.0f * alpha)),
                Math.max(0.0f, Math.min(1.0f, progress))
        );
        Render2D.rect(x, y, width, height, radius, trackColor);
        Render2D.outline(x, y, width, height, radius, 0.55f * cardScale,
                ColorUtil.rgba(255, 255, 255, Math.round((70.0f + 32.0f * progress) * alpha)));
    }

    private String fitText(String text, float size, float maxWidth) {
        if (text == null || text.isEmpty() || Render2D.textWidth(FontType.BOLD, text, size) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        float ellipsisWidth = Render2D.textWidth(FontType.BOLD, ellipsis, size);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        int end = text.length();
        while (end > 0 && Render2D.textWidth(FontType.BOLD, text.substring(0, end), size) + ellipsisWidth > maxWidth) {
            end--;
        }
        return end <= 0 ? "" : text.substring(0, end) + ellipsis;
    }

    private void renderSettings(GuiGraphics graphics, float x, float settingsY, float width, float scale, float alpha,
                                float clipX, float clipY, float clipWidth, float clipHeight) {
        float progress = expandedAnimation.get();
        if (!hasSettings() || progress <= 0.01f) {
            return;
        }
        ensureSettings();

        float rawHeight = computeSettingsHeight();
        float fullHeight = rawHeight * scale;
        float visibleHeight = fullHeight * progress;
        if (visibleHeight <= 0.5f) {
            return;
        }

        float panelX = x;
        float panelY = settingsY;
        float panelWidth = width * scale;

        Render2D.pushScissor(graphics, panelX, panelY, panelWidth, visibleHeight);
        try {
            float optionScale = scale * OPTION_SCALE;
            float optionY = panelY + SETTINGS_PADDING_Y * scale;
            for (ClickGuiOption option : settings) {
                float visibility = option.visibilityProgress();
                if (visibility <= 0.01f) {
                    continue;
                }
                float slotHeight = (option.getHeight() * OPTION_SCALE + OPTION_GAP) * scale;
                option.setFadeMultiplier(progress * visibility);
                float optionWidth = Math.max(1.0f, (panelWidth - SETTINGS_PADDING_X * 2.0f * scale) / Math.max(0.0001f, optionScale));
                option.render(graphics,
                        panelX + SETTINGS_PADDING_X * scale,
                        optionY - (1.0f - visibility) * 5.0f * scale,
                        optionWidth,
                        optionScale,
                        alpha,
                        clipX,
                        clipY,
                        clipWidth,
                        clipHeight);
                optionY += slotHeight * visibility;
            }
        } finally {
            Render2D.popScissor(graphics);
        }
    }

    public void renderOverlay(GuiGraphics graphics) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return;
        }
        ensureSettings();
        for (ClickGuiOption option : settings) {
            if (option.isVisibleForRender()) {
                option.renderOverlay(graphics);
            }
        }
    }

    public void renderRow(float panelX, float rowY, float panelWidth, float rowHeight, float scale, float alpha) {
        beginInteractionFrame();
        cardX = panelX + 5.0f * scale;
        cardY = rowY;
        cardWidth = Math.max(0.0f, panelWidth - 10.0f * scale);
        cardHeight = rowHeight * scale;
        cardScale = scale;
        cardInteractive = alpha > 0.01f;

        float enabled = enabledAnimation.get();
        float switchWidth = 22.0f * scale;
        float switchX = cardX + cardWidth - switchWidth - 2.0f * scale;
        float switchY = cardY + (cardHeight - 12.0f * scale) * 0.5f;

        Render2D.text(FontType.BOLD, module.getDisplayName(), cardX + 4.0f * scale, cardY + 2.5f * scale, 4.0f * scale,
                ColorUtil.rgba(230, 230, 230, Math.round(245.0f * alpha)));
        renderToggle(switchX, switchY, scale, alpha, enabled);
    }

    public float renderSettingsBackground(float panelX, float settingsTop, float width, float scale, float alpha, float clipBottom) {
        float progress = expandedAnimation.get();
        if (!hasSettings() || progress <= 0.01f) {
            return 0.0f;
        }
        float height = computeSettingsHeight() * scale * progress;
        float clipped = Math.max(0.0f, Math.min(height, clipBottom - settingsTop));
        if (clipped > 0.0f) {
            float boxX = panelX + 2.5f * scale;
            float boxY = settingsTop;
            float boxWidth = Math.max(0.0f, width - 5.0f * scale);
            float radius = 6.0f * scale;
            int extensionFill = ColorUtil.rgba(7, 10, 21, Math.round((38.0f + 12.0f * enabledAnimation.get()) * alpha * progress));
            Render2D.rect(boxX, boxY, boxWidth, clipped, 0.0f, 0.0f, radius, radius, extensionFill);
        }
        return height;
    }

    public void renderSettingsContent(GuiGraphics graphics, float panelX, float settingsTop, float width, float scale, float alpha, float clipBottom) {
        renderSettings(graphics, panelX + 2.5f * scale, settingsTop - 25.0f * scale, Math.max(1.0f, width / Math.max(0.0001f, scale) - 5.0f), scale, alpha,
                panelX, settingsTop, width, Math.max(0.0f, clipBottom - settingsTop));
    }

    public void beginInteractionFrame() {
        cardInteractive = false;
        for (ClickGuiOption option : settings) {
            option.resetInteractionFrame();
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (mouseClickedPopup(event, doubled)) {
            return true;
        }
        if (mouseClickedSettings(event, doubled)) {
            return true;
        }
        if (binding) {
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                setBind(KeyBind.mouse(event.button()));
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight)) {
            startBinding();
            return true;
        }
        if (event.button() == 0 && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight)) {
            module.toggle();
            return true;
        }
        if (event.button() == 1 && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight) && hasSettings()) {
            if (!expanded) {
                ensureSettings();
            }
            expanded = !expanded;
            expandedAnimation.run(expanded ? 1.0 : 0.0, 0.24, Easings.CUBIC_OUT);
            closePopupsExcept(null);
            return true;
        }
        return false;
    }

    public boolean mouseClickedSettings(MouseButtonEvent event, boolean doubled) {
        if (!hasSettings() || expandedAnimation.get() <= 0.85f) {
            return false;
        }
        ClickGuiOption handled = null;
        for (ClickGuiOption option : settings) {
            if (!option.isVisibleForInteraction()) {
                continue;
            }
            if (option.wantsMousePriority(event) && option.mouseClicked(event, doubled)) {
                handled = option;
                break;
            }
        }
        if (handled == null) {
            for (int i = settings.size() - 1; i >= 0; i--) {
                ClickGuiOption option = settings.get(i);
                if (!option.isVisibleForInteraction()) {
                    continue;
                }
                if (option.mouseClicked(event, doubled)) {
                    handled = option;
                    break;
                }
            }
        }
        if (handled != null) {
            closePopupsExcept(handled);
            return true;
        }
        closePopupsExcept(null);
        return false;
    }

    public boolean mouseClickedPopup(MouseButtonEvent event, boolean doubled) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        boolean hasOpenPopup = false;
        ClickGuiOption handled = null;
        for (int i = settings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = settings.get(i);
            if (!option.isVisibleForRender() || !option.hasOpenPopup()) {
                continue;
            }
            hasOpenPopup = true;
            if (option.wantsMousePriority(event) && option.mouseClicked(event, doubled)) {
                handled = option;
                break;
            }
        }
        if (handled != null) {
            closePopupsExcept(handled);
            return true;
        }
        if (hasOpenPopup) {
            closePopupsExcept(null);
            return true;
        }
        return false;
    }

    public boolean mouseClickedRow(MouseButtonEvent event, boolean doubled) {
        if (binding) {
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                setBind(KeyBind.mouse(event.button()));
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight)) {
            startBinding();
            return true;
        }
        if (event.button() == 0 && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight)) {
            module.toggle();
            return true;
        }
        if (event.button() == 1 && hovered(event.x(), event.y(), cardX, cardY, cardWidth, cardHeight) && hasSettings()) {
            if (!expanded) {
                ensureSettings();
            }
            expanded = !expanded;
            expandedAnimation.run(expanded ? 1.0 : 0.0, 0.24, Easings.CUBIC_OUT);
            closePopupsExcept(null);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        for (int i = settings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = settings.get(i);
            if (option.isVisibleForInteraction() && option.mouseReleased(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        for (int i = settings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = settings.get(i);
            if (option.isVisibleForInteraction() && option.mouseDragged(event, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        for (int i = settings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = settings.get(i);
            if (!option.isVisibleForRender() || !option.hasOpenPopup()) {
                continue;
            }
            if (option.mouseScrolled(mouseX, mouseY, scrollY)) {
                closePopupsExcept(option);
                return true;
            }
        }
        for (int i = settings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = settings.get(i);
            if (!option.isVisibleForInteraction() || option.hasOpenPopup()) {
                continue;
            }
            if (option.mouseScrolled(mouseX, mouseY, scrollY)) {
                closePopupsExcept(option);
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (binding) {
            if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_ESCAPE) {
                setBind(KeyBind.NONE);
            } else {
                setBind(KeyBind.keyboard(event.key()));
            }
            return true;
        }
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        for (int i = settings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = settings.get(i);
            if (option.isVisibleForInteraction() && option.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!hasSettings() || expandedAnimation.get() <= 0.01f) {
            return false;
        }
        for (int i = settings.size() - 1; i >= 0; i--) {
            ClickGuiOption option = settings.get(i);
            if (option.isVisibleForInteraction() && option.charTyped(event)) {
                return true;
            }
        }
        return false;
    }

    public float extraHeight() {
        if (!hasSettings()) {
            return 0.0f;
        }
        return Math.max(0.0f, computeSettingsHeight()) * expandedAnimation.get();
    }

    public float getAnimatedExtraHeight() {
        return extraHeight();
    }

    public float totalHeight() {
        return 27.0f + extraHeight();
    }

    public boolean hasSettings() {
        return hasSupportedSettings;
    }

    public void revealSettings() {
        if (!hasSettings()) {
            return;
        }
        ensureSettings();
        expanded = true;
        expandedAnimation.run(1.0, 0.18, Easings.CUBIC_OUT);
    }

    public void warmupText() {
        Render2D.warmupText(FontType.BOLD, module.getDisplayName(), 7.7f);
        for (Setting<?> setting : module.getSettings()) {
            Render2D.warmupText(FontType.SEMIBOLD, setting.getDisplayName(), 9.0f);
            warmupSettingValue(setting);
        }
    }

    private void ensureSettings() {
        if (settingsInitialized) {
            return;
        }
        settings = ModuleOptionFactory.createSettings(module);
        settingsInitialized = true;
    }

    private float computeSettingsHeight() {
        if (!settingsInitialized) {
            return 0.0f;
        }
        float height = SETTINGS_PADDING_Y * 2.0f;
        for (ClickGuiOption option : settings) {
            float visibility = option.visibilityProgress();
            if (visibility > 0.01f) {
                height += (option.getHeight() * OPTION_SCALE + OPTION_GAP) * visibility;
            }
        }
        return Math.max(0.0f, height);
    }

    private void startBinding() {
        binding = true;
        bindAnimation.run(1.0, 0.18, Easings.CUBIC_OUT);
        closePopupsExcept(null);
    }

    private void setBind(KeyBind bind) {
        bindSetting.setValue(bind);
        binding = false;
        bindAnimation.run(0.0, 0.18, Easings.CUBIC_OUT);
    }

    private void closePopupsExcept(ClickGuiOption keepOpen) {
        for (ClickGuiOption option : settings) {
            if (option != keepOpen) {
                option.closePopup();
            }
        }
    }

    private boolean hovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return cardInteractive && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean intersects(float x, float y, float width, float height, float clipX, float clipY, float clipWidth, float clipHeight) {
        return width > 0.0f
                && height > 0.0f
                && clipWidth > 0.0f
                && clipHeight > 0.0f
                && x + width >= clipX
                && x <= clipX + clipWidth
                && y + height >= clipY
                && y <= clipY + clipHeight;
    }

    private static void warmupSettingValue(Setting<?> setting) {
        if (setting instanceof ModeSetting modeSetting) {
            Render2D.warmupText(FontType.SEMIBOLD, modeSetting.getDisplayValue(), 9.0f);
            for (String mode : modeSetting.getDisplayModes()) {
                Render2D.warmupText(FontType.SEMIBOLD, mode, 9.0f);
            }
            return;
        }
        if (setting instanceof MultiModeSetting multiModeSetting) {
            Render2D.warmupText(FontType.SEMIBOLD, LanguageManager.translateFormat("selected.count", multiModeSetting.selectedCount()), 9.0f);
            for (String mode : multiModeSetting.getDisplayModes()) {
                Render2D.warmupText(FontType.SEMIBOLD, mode, 9.0f);
            }
            return;
        }
        Object value = setting.getValue();
        if (value != null) {
            Render2D.warmupText(FontType.SEMIBOLD, String.valueOf(value), 9.0f);
        }
    }
}
