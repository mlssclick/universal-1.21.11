package universalmod.api.drag.impl;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;
import universalmod.api.drag.core.ElementManager;
import universalmod.api.drag.core.ElementScreen;
import universalmod.api.module.impl.render.Hud;
import universalmod.api.module.impl.render.NoRender;
import universalmod.mixin.accessor.BossHealthOverlayAccessor;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.Render2DCoordinateSpace;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BossbarPanel extends HudPanel {
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int VANILLA_BAR_Y = 12;
    private static final int VANILLA_TEXT_Y = 3;
    private static final int ENTRY_SPACING = 19;
    private static final int VANILLA_SNAP_DISTANCE_GUI = 12;
    private static final int VANILLA_TEXT_COLOR = 0xFFFFFFFF;
    private static final int PREVIEW_PROGRESS_COLOR = 0xFFFF55FF;
    private static final UUID PREVIEW_ID = UUID.fromString("32e7247d-5f69-4e06-bdfa-bebd3d8e9490");

    private final Hud hud;
    private final LerpingBossEvent previewEvent = new LerpingBossEvent(
            PREVIEW_ID,
            Component.literal("Bossbar"),
            0.72F,
            BossEvent.BossBarColor.PINK,
            BossEvent.BossBarOverlay.PROGRESS,
            false,
            false,
            false
    );

    private boolean hudSelected;
    private boolean wasMoving;
    private boolean snapToVanillaOnRelease;

    public BossbarPanel(Hud hud) {
        super("bossbar", "Bossbar", defaultX(), defaultY(), BAR_WIDTH, 14.0F);
        this.hud = hud;
        drag.minimumSize(BAR_WIDTH * 0.5F, 7.0F)
                .screenMargins(0.0F, 0.0F, 0.0F, 0.0F)
                .visible(false);
    }

    public void setHudSelected(boolean selected) {
        hudSelected = selected;
    }

    public boolean isHudSelected() {
        return hudSelected;
    }

    public boolean shouldReplaceVanilla() {
        if (hud == null || !hud.isEnabled() || NoRender.isActive("Bossbar")) {
            return false;
        }
        return hud.isBossbarSelected();
    }

    public boolean shouldRenderDynamicFallback() {
        return false;
    }

    @Override
    public void render() {
        hudSelected = hud != null && hud.isBossbarSelected();
        GuiGraphics graphics = Render2D.currentGraphics();
        if (graphics == null || mc == null || mc.getWindow() == null || mc.player == null || NoRender.isActive("Bossbar")) {
            drag.visible(false).clearVisualOffset();
            wasMoving = false;
            snapToVanillaOnRelease = false;
            return;
        }

        List<LerpingBossEvent> events = visibleEvents(graphics.guiHeight());
        boolean editorPreview = hudSelected && events.isEmpty() && mc.screen instanceof ChatScreen;
        if (editorPreview) {
            events = List.of(previewEvent);
        }
        if (events.isEmpty()) {
            drag.visible(false).clearVisualOffset();
            wasMoving = false;
            snapToVanillaOnRelease = false;
            return;
        }

        BossLayout layout = layout(events, mc.font);
        float coordinateScale = Math.max(0.0001F, Render2DCoordinateSpace.guiIndependentScale());
        float fixedWidth = layout.widthGui() / coordinateScale;
        float fixedHeight = layout.heightGui() / coordinateScale;
        float physicalWidth = fixedWidth * hudScale();
        float physicalHeight = fixedHeight * hudScale();
        VanillaPlacement vanilla = vanillaPlacement(graphics, layout, coordinateScale);
        VanillaPlacement visibleVanilla = vanilla;

        boolean movingNow = hudSelected && drag.moving();
        if (wasMoving && !movingNow && snapToVanillaOnRelease) {

            drag.resetPosition().position(vanilla.xFixed(), vanilla.yFixed());
            ElementManager.getInstance().save();
            snapToVanillaOnRelease = false;
        }

        float baseX;
        float baseY;
        if (hudSelected) {
            drag.visible(true);
            sizeImmediate(fixedWidth, fixedHeight);
            if (!drag.positionCustomized()) {
                drag.position(vanilla.xFixed(), vanilla.yFixed());
            }
            drag.clamp(ElementScreen.current());
            baseX = drag.baseX();
            baseY = drag.baseY();
        } else {
            drag.visible(false).clearVisualOffset();
            baseX = vanilla.xFixed();
            baseY = vanilla.yFixed();
        }

        float renderX;
        float renderY;
        if (hudSelected) {
            drag.clearVisualOffset();
            renderX = drag.x();
            renderY = drag.y();
        } else {
            renderX = baseX;
            renderY = baseY;
        }

        if (movingNow) {
            renderVanillaPlacementGhost(visibleVanilla, fixedWidth, fixedHeight);
            snapToVanillaOnRelease = isNearVanillaPlacement(
                    drag.x(),
                    drag.y(),
                    visibleVanilla,
                    physicalWidth,
                    physicalHeight
            );
        } else if (!wasMoving) {
            snapToVanillaOnRelease = false;
        }
        wasMoving = movingNow;

        drawEvents(graphics, events, layout, renderX, renderY, coordinateScale);
    }

    private List<LerpingBossEvent> visibleEvents(int guiHeight) {
        BossHealthOverlay overlay = mc.gui == null ? null : mc.gui.getBossOverlay();
        BossHealthOverlayAccessor accessor = accessor(overlay);
        if (accessor == null) {
            return List.of();
        }

        Map<UUID, LerpingBossEvent> map = accessor.universalmod$getEvents();
        if (map == null || map.isEmpty()) {
            return List.of();
        }

        List<LerpingBossEvent> result = new ArrayList<>();
        int barY = VANILLA_BAR_Y;
        for (LerpingBossEvent event : map.values()) {
            if (event == null) {
                continue;
            }
            result.add(event);
            barY += ENTRY_SPACING;
            if (barY >= guiHeight / 3) {
                break;
            }
        }
        return result;
    }

    private BossLayout layout(List<LerpingBossEvent> events, Font font) {
        int width = BAR_WIDTH;
        for (LerpingBossEvent event : events) {
            width = Math.max(width, font.width(event.getName()));
        }
        int height = 14 + Math.max(0, events.size() - 1) * ENTRY_SPACING;
        return new BossLayout(width, height, (width - BAR_WIDTH) / 2);
    }

    private VanillaPlacement vanillaPlacement(GuiGraphics graphics, BossLayout layout, float coordinateScale) {
        float xGui = (graphics.guiWidth() - layout.widthGui()) * 0.5F;
        float yGui = VANILLA_TEXT_Y;
        return new VanillaPlacement(xGui / coordinateScale, yGui / coordinateScale);
    }

    private void drawEvents(
            GuiGraphics graphics,
            List<LerpingBossEvent> events,
            BossLayout layout,
            float renderXFixed,
            float renderYFixed,
            float coordinateScale
    ) {
        int leftGui = Math.round(renderXFixed * coordinateScale);
        int topGui = Math.round(renderYFixed * coordinateScale);
        int barLeft = leftGui + layout.barOffsetXGui();
        int centerX = leftGui + layout.widthGui() / 2;
        boolean customStyle = hud.isBossbarSelected() && hud.useCustomBossbarSettings();

        BossHealthOverlay overlay = mc.gui == null ? null : mc.gui.getBossOverlay();
        BossHealthOverlayAccessor accessor = accessor(overlay);

        for (int index = 0; index < events.size(); index++) {
            LerpingBossEvent event = events.get(index);
            int barY = topGui + (VANILLA_BAR_Y - VANILLA_TEXT_Y) + index * ENTRY_SPACING;
            int textY = barY - 9;

            if (customStyle) {
                drawTintedVanillaBar(graphics, barLeft, barY, event, hud.bossbarColor());
                Component plainName = Component.literal(event.getName().getString());
                int nameWidth = mc.font.width(plainName);
                graphics.drawString(
                        mc.font,
                        plainName,
                        centerX - nameWidth / 2,
                        textY,
                        hud.bossbarTextColor().getRGB(),
                        true
                );
            } else {
                if (accessor != null) {
                    accessor.universalmod$drawBar(graphics, barLeft, barY, event);
                } else {
                    drawTintedVanillaBar(graphics, barLeft, barY, event, new Color(PREVIEW_PROGRESS_COLOR, true));
                }
                graphics.drawCenteredString(mc.font, event.getName(), centerX, textY, VANILLA_TEXT_COLOR);
            }
        }
    }

    private void drawTintedVanillaBar(
            GuiGraphics graphics,
            int x,
            int y,
            BossEvent event,
            Color selectedColor
    ) {
        Color safe = selectedColor == null ? new Color(PREVIEW_PROGRESS_COLOR, true) : selectedColor;
        int progressTint = safe.getRGB();
        int backgroundTint = new Color(
                Math.max(0, Math.round(safe.getRed() * 0.35F)),
                Math.max(0, Math.round(safe.getGreen() * 0.35F)),
                Math.max(0, Math.round(safe.getBlue() * 0.35F)),
                safe.getAlpha()
        ).getRGB();

        Identifier[] barBackgrounds = BossHealthOverlayAccessor.universalmod$getBarBackgroundSprites();
        Identifier[] barProgress = BossHealthOverlayAccessor.universalmod$getBarProgressSprites();
        drawTintedSpritePair(
                graphics,
                x,
                y,
                event,
                BossEvent.BossBarColor.WHITE.ordinal(),
                barBackgrounds,
                barProgress,
                backgroundTint,
                progressTint
        );

        if (event.getOverlay() != BossEvent.BossBarOverlay.PROGRESS) {
            Identifier[] overlayBackgrounds = BossHealthOverlayAccessor.universalmod$getOverlayBackgroundSprites();
            Identifier[] overlayProgress = BossHealthOverlayAccessor.universalmod$getOverlayProgressSprites();
            drawTintedSpritePair(
                    graphics,
                    x,
                    y,
                    event,
                    event.getOverlay().ordinal() - 1,
                    overlayBackgrounds,
                    overlayProgress,
                    0xFFFFFFFF,
                    0xFFFFFFFF
            );
        }
    }

    private void drawTintedSpritePair(
            GuiGraphics graphics,
            int x,
            int y,
            BossEvent event,
            int spriteIndex,
            Identifier[] backgroundSprites,
            Identifier[] progressSprites,
            int backgroundTint,
            int progressTint
    ) {
        if (!validSpriteIndex(spriteIndex, backgroundSprites, progressSprites)) {
            return;
        }

        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                backgroundSprites[spriteIndex],
                x,
                y,
                BAR_WIDTH,
                BAR_HEIGHT,
                backgroundTint
        );

        int progressWidth = Math.round(clamp01(event.getProgress()) * BAR_WIDTH);
        if (progressWidth <= 0) {
            return;
        }

        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                progressSprites[spriteIndex],
                BAR_WIDTH,
                BAR_HEIGHT,
                0,
                0,
                x,
                y,
                progressWidth,
                BAR_HEIGHT,
                progressTint
        );
    }

    private static boolean validSpriteIndex(
            int index,
            Identifier[] backgrounds,
            Identifier[] progress
    ) {
        return index >= 0
                && backgrounds != null
                && progress != null
                && index < backgrounds.length
                && index < progress.length
                && backgrounds[index] != null
                && progress[index] != null;
    }

    private void renderVanillaPlacementGhost(VanillaPlacement vanilla, float width, float height) {
        Render2D.rect(
                vanilla.xFixed(),
                vanilla.yFixed(),
                width,
                height,
                0.0F,
                ColorUtil.rgba(255, 255, 255, 72)
        );
    }

    private boolean isNearVanillaPlacement(
            float x,
            float y,
            VanillaPlacement vanilla,
            float width,
            float height
    ) {
        float snapDistance = Math.max(1.0F, Render2D.guiToFixed(VANILLA_SNAP_DISTANCE_GUI));
        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        float vanillaCenterX = vanilla.xFixed() + width * 0.5F;
        float vanillaCenterY = vanilla.yFixed() + height * 0.5F;
        return Math.abs(centerX - vanillaCenterX) <= snapDistance
                && Math.abs(centerY - vanillaCenterY) <= snapDistance;
    }

    private static BossHealthOverlayAccessor accessor(BossHealthOverlay overlay) {
        if (overlay == null) {
            return null;
        }
        try {
            return (BossHealthOverlayAccessor) (Object) overlay;
        } catch (ClassCastException ignored) {
            return null;
        }
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float defaultX() {
        ElementScreen screen = ElementScreen.current();
        return Math.max(0.0F, (screen.width() - BAR_WIDTH) * 0.5F);
    }

    private static float defaultY() {
        return Math.max(0.0F, Render2D.guiToFixed(VANILLA_TEXT_Y));
    }

    private record BossLayout(int widthGui, int heightGui, int barOffsetXGui) {
    }

    private record VanillaPlacement(float xFixed, float yFixed) {
        private VanillaPlacement offsetY(float offset) {
            return new VanillaPlacement(xFixed, yFixed + offset);
        }
    }
}
