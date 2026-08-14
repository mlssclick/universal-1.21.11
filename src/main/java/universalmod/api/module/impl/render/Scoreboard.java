package universalmod.api.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import universalmod.api.drag.core.ElementComponent;
import universalmod.api.drag.core.ElementManager;
import universalmod.api.drag.core.ElementScreen;
import universalmod.api.module.Module;
import universalmod.api.module.ModuleCategory;
import universalmod.api.module.impl.utils.CustomDonate;
import universalmod.api.settings.Setting;
import universalmod.api.settings.impl.BooleanSetting;
import universalmod.api.settings.impl.ColorSetting;
import universalmod.api.settings.impl.ModeSetting;
import universalmod.api.settings.impl.NumberSetting;
import universalmod.mixin.accessor.GuiScoreboardAccessor;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.liquidglass.LiquidGlassBlurChannel;
import universalmod.utils.render.ui.Render2DCoordinateSpace;
import universalmod.utils.render.ui.blur.BlurAlgorithm;
import universalmod.utils.render.ui.blur.BuiltBlur;
import universalmod.utils.render.ui.darkpanel.BuiltDarkPanel;
import universalmod.utils.render.ui.rectangle.rectdefault.BuiltRectangle;

import java.awt.Color;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class Scoreboard extends Module {
    private static final int VANILLA_LINE_HEIGHT = 9;
    private static final int VANILLA_LEFT_PADDING = 2;
    private static final int VANILLA_RIGHT_EXTENSION = 2;
    private static final int VANILLA_SCREEN_RIGHT_MARGIN = 1;
    private static final int VANILLA_MAX_LINES = 15;
    private static final String VANILLA_SPACER_FALLBACK = ": ";
    private static final int VANILLA_TEXT_COLOR = 0xFFFFFFFF;

    private static final Comparator<PlayerScoreEntry> FALLBACK_ORDER = Comparator
            .comparingInt(PlayerScoreEntry::value)
            .reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private static Scoreboard instance;

    private final BooleanSetting showNumbers = register(new BooleanSetting(
            "Show Numbers",
            "Shows the formatted score values on the right side.",
            true
    ));
    private final BooleanSetting textShadow = register(new BooleanSetting(
            "Text Shadow",
            "Draws Minecraft font shadows behind scoreboard text.",
            false
    ));
    private final NumberSetting scale = register(new NumberSetting(
            "Scale",
            "Scoreboard HUD scale.",
            1.0D,
            0.5D,
            2.0D,
            0.01D
    ));
    private final ModeSetting backgroundMode = register(new ModeSetting(
            "Background",
            "Vanilla, custom, blur, Liquid Glass, Dark shader, or no background.",
            "Vanilla",
            "Vanilla",
            "Custom",
            "Blur",
            "Liquid Glass",
            "Dark",
            "None"
    ));
    private final ColorSetting backgroundColor = register(new ColorSetting(
            "Background Color",
            "Custom color and opacity for scoreboard rows.",
            new Color(0, 0, 0, 77)
    ));
    private final ColorSetting titleBackgroundColor = register(new ColorSetting(
            "Title Background Color",
            "Custom color and opacity for the title row.",
            new Color(0, 0, 0, 102)
    ));
    private final NumberSetting blurRadius = register(new NumberSetting(
            "Blur Radius",
            "Strength of the scoreboard background blur shader.",
            12.0D,
            1.0D,
            32.0D,
            1.0D
    ));
    
    private final NumberSetting liquidGlassOpacity = register(new NumberSetting(
            "Glass Opacity", "Liquid Glass overlay opacity.", 20.0D, 0.0D, 100.0D, 1.0D));
    private final NumberSetting liquidGlassStrength = register(new NumberSetting(
            "Glass Strength", "Liquid Glass Fresnel power.", 25.0D, 0.0D, 100.0D, 1.0D));
    private final NumberSetting liquidGlassDistortion = register(new NumberSetting(
            "Glass Distortion", "Liquid Glass refraction strength.", 0.08D, -0.2D, 0.2D, 0.01D));
    private final NumberSetting liquidGlassBlur = register(new NumberSetting(
            "Glass Blur", "Kawase blur strength.", 0.5D, 0.0D, 8.0D, 0.25D));
    private final NumberSetting liquidGlassRounding = register(new NumberSetting(
            "Glass Rounding", "Liquid Glass rounding.", 7.0D, 0.0D, 8.0D, 1.0D));

    private final NumberSetting cornerRadius = register(new NumberSetting(
            "Corner Radius",
            "Rounds all four outer corners of the entire scoreboard background.",
            0.0D,
            0.0D,
            12.0D,
            0.5D
    ));
    private final ModeSetting textColorMode = register(new ModeSetting(
            "Text Colors",
            "Keeps server formatting or replaces it with custom colors.",
            "Vanilla",
            "Vanilla",
            "Custom"
    ));
    private final ColorSetting titleColor = register(new ColorSetting(
            "Title Color",
            "Custom title text color.",
            new Color(255, 255, 255, 255)
    ));
    private final ColorSetting lineColor = register(new ColorSetting(
            "Line Color",
            "Custom scoreboard line text color.",
            new Color(255, 255, 255, 255)
    ));
    private final ColorSetting numberColor = register(new ColorSetting(
            "Number Color",
            "Custom scoreboard number text color.",
            new Color(255, 85, 85, 255)
    ));

    private final ElementComponent drag;
    private final SmoothAnimation renderedScale = new SmoothAnimation();

    public Scoreboard() {
        super("Custom Scoreboard", "Replaces the vanilla scoreboard with a movable and configurable HUD element.", ModuleCategory.RENDER);
        instance = this;

        ElementScreen screen = ElementScreen.current();
        float defaultX = Math.max(0.0F, screen.width() - 154.0F);
        float defaultY = Math.max(0.0F, screen.height() * 0.5F - 70.0F);
        drag = ElementManager.getInstance()
                .register("hud.scoreboard", "Custom Scoreboard", defaultX, defaultY)
                .minimumSize(12.0F, 9.0F)
                .screenMargins(0.0F, 0.0F, 0.0F, 0.0F);
        drag.visible(false);
        renderedScale.set(scale.getFloat());

        backgroundColor.visibleWhen(() -> backgroundMode.is("Custom") || backgroundMode.is("Blur"));
        titleBackgroundColor.visibleWhen(() -> backgroundMode.is("Custom") || backgroundMode.is("Blur"));
        blurRadius.visibleWhen(() -> backgroundMode.is("Blur"));
        liquidGlassOpacity.visibleWhen(() -> backgroundMode.is("Liquid Glass"));
        liquidGlassStrength.visibleWhen(() -> backgroundMode.is("Liquid Glass"));
        liquidGlassDistortion.visibleWhen(() -> backgroundMode.is("Liquid Glass"));
        liquidGlassBlur.visibleWhen(() -> backgroundMode.is("Liquid Glass"));
        liquidGlassRounding.visibleWhen(() -> backgroundMode.is("Liquid Glass"));
        cornerRadius.visibleWhen(() -> !backgroundMode.is("None") && !backgroundMode.is("Liquid Glass"));
        titleColor.visibleWhen(() -> textColorMode.is("Custom"));
        lineColor.visibleWhen(() -> textColorMode.is("Custom"));
        numberColor.visibleWhen(() -> textColorMode.is("Custom") && showNumbers.getValue());
    }

    public static Scoreboard getInstance() {
        return instance;
    }

    public static boolean isActive() {
        Scoreboard module = instance;
        return module != null && module.isEnabled();
    }

    public boolean editorHit(float mouseX, float mouseY) {
        return isEnabled() && drag.visible() && drag.dragBounds().contains(mouseX, mouseY, 3.0F);
    }

    public float editorX() {
        return drag.x();
    }

    public float editorY() {
        return drag.y();
    }

    public float editorWidth() {
        return drag.width();
    }

    public float editorHeight() {
        return drag.height();
    }

    public String editorBackgroundMode() {
        return backgroundMode.getValue();
    }

    public void editorBackgroundMode(String value) {
        backgroundMode.setValue(value);
    }

    public List<Setting<?>> editorSettings() {
        return List.of(
                showNumbers,
                textShadow,
                backgroundMode,
                backgroundColor,
                titleBackgroundColor,
                blurRadius,
                liquidGlassOpacity,
                liquidGlassStrength,
                liquidGlassDistortion,
                liquidGlassBlur,
                liquidGlassRounding,
                cornerRadius,
                textColorMode,
                titleColor,
                lineColor,
                numberColor,
                scale
        );
    }

    public NumberSetting editorScaleSetting() {
        return scale;
    }

    public float editorScalePercent() {
        return safeScale() * 100.0F;
    }

    public void editorScalePercent(float percent) {
        scale.setValue((double) clamp(percent / 100.0F, 0.5F, 2.0F));
    }

    public void renderEditorPopupBackground(GuiGraphics graphics, float x, float y, float width, float height, float radius) {
        if (backgroundMode.is("Liquid Glass")) {
            drawLiquidGlassBackground(x, y, width, height, 1.0F);
            return;
        }
        drawDarkBackground(graphics, x, y, width, height, radius);
    }

    public void render(GuiGraphics graphics, boolean editorLayer) {
        Minecraft client = Minecraft.getInstance();
        if (!isEnabled() || graphics == null || client == null || client.getWindow() == null) {
            drag.visible(false);
            return;
        }

        if (NoRender.isActive("Scoreboard")) {
            drag.visible(false);
            return;
        }

        BoardData data = readBoardData(client);
        if (data == null && editorLayer && client.screen instanceof ChatScreen) {
            data = editorPlaceholder();
        }
        if (data == null) {
            drag.visible(false);
            return;
        }

        Layout layout = createLayout(client.font, data);
        if (layout.width() <= 0 || layout.height() <= 0) {
            drag.visible(false);
            return;
        }

        float targetScale = safeScale();
        renderedScale.run(targetScale, 0.11, Easings.CUBIC_OUT, true);
        renderedScale.update();
        float userScale = clamp(renderedScale.get(), 0.5F, 2.0F);
        float coordinateScale = Math.max(0.0001F, Render2DCoordinateSpace.guiIndependentScale());
        drag.visible(true);
        drag.size(
                layout.width() * userScale / coordinateScale,
                layout.height() * userScale / coordinateScale
        );
        resolveInitialPosition(graphics, layout, userScale, coordinateScale);
        drag.clamp(ElementScreen.current());

        if (editorLayer && drag.moving()) {
            drawVanillaPlacementGhost(graphics, layout, userScale, coordinateScale);
        }
        drawBackgrounds(graphics, client, layout, userScale, coordinateScale);

        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(
                    Render2DCoordinateSpace.toGui(drag.x()),
                    Render2DCoordinateSpace.toGui(drag.y())
            );
            graphics.pose().scale(userScale);
            drawBoard(graphics, client, data, layout);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    @Override
    protected void onDisable() {
        drag.visible(false);
    }

    private BoardData readBoardData(Minecraft client) {
        if (client.player == null || client.level == null) {
            return null;
        }

        net.minecraft.world.scores.Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = resolveObjective(scoreboard, client.player.getScoreboardName());
        if (objective == null) {
            return null;
        }

        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        Collection<PlayerScoreEntry> rawEntries = scoreboard.listPlayerScores(objective);
        if (rawEntries == null) {
            rawEntries = List.of();
        }

        Comparator<PlayerScoreEntry> comparator = vanillaOrder();
        List<LineData> lines = rawEntries.stream()
                .filter(entry -> entry != null && !entry.isHidden())
                .sorted(comparator)
                .limit(VANILLA_MAX_LINES)
                .map(entry -> createLine(client.font, scoreboard, entry, numberFormat))
                .toList();

        if (lines.isEmpty()) {
            return null;
        }

        Component title = objective.getDisplayName();
        return new BoardData(title == null ? Component.empty() : title, lines);
    }

    private Objective resolveObjective(net.minecraft.world.scores.Scoreboard scoreboard, String playerName) {
        if (scoreboard == null) {
            return null;
        }

        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        PlayerTeam team = playerName == null ? null : scoreboard.getPlayersTeam(playerName);
        if (team != null) {
            DisplaySlot teamSlot = DisplaySlot.teamColorToSlot(team.getColor());
            if (teamSlot != null) {
                Objective teamObjective = scoreboard.getDisplayObjective(teamSlot);
                if (teamObjective != null) {
                    objective = teamObjective;
                }
            }
        }
        return objective;
    }

    private LineData createLine(
            Font font,
            net.minecraft.world.scores.Scoreboard scoreboard,
            PlayerScoreEntry entry,
            NumberFormat numberFormat
    ) {
        Component ownerName = entry.ownerName();
        Component teamFormattedName = PlayerTeam.formatNameForTeam(
                scoreboard.getPlayersTeam(entry.owner()),
                ownerName == null ? Component.empty() : ownerName
        );
        Component name = CustomDonate.replaceScoreboardLine(teamFormattedName);
        if (name == null) {
            name = Component.empty();
        }

        Component score = showNumbers.getValue()
                ? entry.formatValue(numberFormat)
                : Component.empty();
        if (score == null) {
            score = Component.empty();
        }

        return new LineData(
                name,
                score,
                showNumbers.getValue() ? font.width(displayComponent(score)) : 0
        );
    }

    private BoardData editorPlaceholder() {
        Component title = Component.literal("Custom Scoreboard");
        Component hint = Component.literal("Drag to move");
        return new BoardData(title, List.of(new LineData(hint, Component.empty(), 0)));
    }

    private Layout createLayout(Font font, BoardData data) {
        int titleWidth = font.width(displayComponent(data.title()));
        int titleHeight = titleWidth > 0 ? VANILLA_LINE_HEIGHT : 0;
        int contentWidth = titleWidth;
        int spacerWidth = font.width(vanillaSpacer());

        for (LineData line : data.lines()) {
            int lineWidth = font.width(displayComponent(line.name()));
            if (showNumbers.getValue() && line.scoreWidth() > 0) {
                lineWidth += spacerWidth + line.scoreWidth();
            }
            contentWidth = Math.max(contentWidth, lineWidth);
        }

        if (contentWidth <= 0 && data.lines().isEmpty()) {
            return new Layout(0, 0, 0, 0, 0, 0);
        }

        int width = Math.max(1, contentWidth + VANILLA_LEFT_PADDING + VANILLA_RIGHT_EXTENSION);
        int bodyHeight = data.lines().size() * VANILLA_LINE_HEIGHT;
        int height = bodyHeight + titleHeight;
        if (height <= 0) {
            height = VANILLA_LINE_HEIGHT;
        }
        return new Layout(width, height, contentWidth, titleWidth, bodyHeight, titleHeight);
    }

    private void resolveInitialPosition(
            GuiGraphics graphics,
            Layout layout,
            float userScale,
            float coordinateScale
    ) {
        if (drag.positionCustomized()) {
            return;
        }

        VanillaPlacement vanilla = vanillaPlacement(graphics, layout, userScale, coordinateScale);
        drag.position(vanilla.x(), vanilla.y());
    }

    private VanillaPlacement vanillaPlacement(
            GuiGraphics graphics,
            Layout layout,
            float userScale,
            float coordinateScale
    ) {
        float nativeWidth = layout.width() * userScale;
        float nativeHeight = layout.height() * userScale;
        float nativeBodyHeight = layout.bodyHeight() * userScale;
        float nativeX = graphics.guiWidth() - nativeWidth - VANILLA_SCREEN_RIGHT_MARGIN;
        float nativeBottom = graphics.guiHeight() * 0.5F + nativeBodyHeight / 3.0F;
        float nativeY = nativeBottom - nativeHeight;
        return new VanillaPlacement(
                Math.max(0.0F, nativeX / coordinateScale),
                Math.max(0.0F, nativeY / coordinateScale)
        );
    }

    private void drawVanillaPlacementGhost(
            GuiGraphics graphics,
            Layout layout,
            float userScale,
            float coordinateScale
    ) {
        VanillaPlacement vanilla = vanillaPlacement(graphics, layout, userScale, coordinateScale);
        float width = layout.width() * userScale / coordinateScale;
        float height = layout.height() * userScale / coordinateScale;
        new BuiltRectangle(
                vanilla.x(),
                vanilla.y(),
                width,
                height,
                0.0F,
                ColorUtil.rgba(255, 255, 255, 72)
        ).render(graphics);
    }

    private void drawBoard(GuiGraphics graphics, Minecraft client, BoardData data, Layout layout) {
        Font font = client.font;
        int titleHeight = layout.titleHeight();
        boolean titleVisible = titleHeight > 0;
        int bodyTop = titleHeight;

        boolean shadow = textShadow.getValue();
        if (titleVisible) {
            int titleX = VANILLA_LEFT_PADDING + (layout.contentWidth() - layout.titleWidth()) / 2;
            graphics.drawString(
                    font,
                    displayComponent(data.title()),
                    titleX,
                    0,
                    displayColor(titleColor.getValue()),
                    shadow
            );
        }

        int scoreRight = layout.width();
        for (int index = 0; index < data.lines().size(); index++) {
            LineData line = data.lines().get(index);

            int y = bodyTop + index * VANILLA_LINE_HEIGHT;
            graphics.drawString(
                    font,
                    displayComponent(line.name()),
                    VANILLA_LEFT_PADDING,
                    y,
                    displayColor(lineColor.getValue()),
                    shadow
            );

            if (showNumbers.getValue() && line.scoreWidth() > 0) {
                graphics.drawString(
                        font,
                        displayComponent(line.score()),
                        scoreRight - line.scoreWidth(),
                        y,
                        displayColor(numberColor.getValue()),
                        shadow
                );
            }
        }
    }

    private void drawBackgrounds(
            GuiGraphics graphics,
            Minecraft client,
            Layout layout,
            float userScale,
            float coordinateScale
    ) {
        if (backgroundMode.is("None")) {
            return;
        }

        float inverseCoordinateScale = 1.0F / Math.max(0.0001F, coordinateScale);
        float x = drag.x();
        float y = drag.y();
        float width = layout.width() * userScale * inverseCoordinateScale;
        float height = layout.height() * userScale * inverseCoordinateScale;
        float titleHeight = layout.titleHeight() * userScale * inverseCoordinateScale;
        float radius = safeCornerRadius() * userScale * inverseCoordinateScale;
        boolean hasTitle = titleHeight > 0.0F && titleHeight < height;

        if (backgroundMode.is("Liquid Glass")) {
            drawLiquidGlassBackground(x, y, width, height, userScale * inverseCoordinateScale);
            return;
        }

        if (backgroundMode.is("Dark")) {
            drawDarkBackground(graphics, x, y, width, height, radius);
            return;
        }

        int bodyColor;
        int headerColor;
        if (backgroundMode.is("Custom") || backgroundMode.is("Blur")) {
            bodyColor = backgroundColor.getValue().getRGB();
            headerColor = titleBackgroundColor.getValue().getRGB();
        } else {
            bodyColor = client.options.getBackgroundColor(0.3F);
            headerColor = client.options.getBackgroundColor(0.4F);
        }

        if (backgroundMode.is("Blur")) {
            BuiltBlur blur = new BuiltBlur(
                    x,
                    y,
                    width,
                    height,
                    radius,
                    1.0F,
                    safeBlurRadius()
            )
                    .withAlgorithm(BlurAlgorithm.SCOREBOARD_SEPARABLE)
                    .withColor(blurTint(backgroundColor.getValue()));

            if (hasTitle) {
                blur = blur.withVerticalColorSplit(
                        titleHeight,
                        blurTint(titleBackgroundColor.getValue()),
                        blurTint(backgroundColor.getValue())
                );
            }
            blur.render(graphics);
            return;
        }

        BuiltRectangle rectangle = new BuiltRectangle(x, y, width, height, radius, bodyColor);
        if (hasTitle) {
            rectangle = rectangle.withVerticalColorSplit(titleHeight, headerColor, bodyColor);
        }
        rectangle.render(graphics);
    }

    private void drawDarkBackground(GuiGraphics graphics, float x, float y, float width, float height, float radius) {

        new BuiltBlur(x, y, width, height, radius, 1.0F, 2.0F)
                .withAlgorithm(BlurAlgorithm.SCOREBOARD_SEPARABLE)
                .withColor(ColorUtil.rgba(18, 18, 20, 86))
                .render(graphics);
        new BuiltDarkPanel(x, y, width, height, radius, 1.0F).render(graphics);
    }

    private void drawLiquidGlassBackground(float x, float y, float width, float height, float scaleFactor) {
        float squirt = 7.0F;
        float rounding = clamp(liquidGlassRounding.getFloat(), 0.0F, 8.0F) * Math.max(scaleFactor, 0.0001F);

        float liquidRadius = rounding * squirt / 2.0F;
        float strength = clamp(liquidGlassStrength.getFloat(), 0.0F, 100.0F);
        float distortion = clamp(liquidGlassDistortion.getFloat(), -0.2F, 0.2F);
        float blur = clamp(liquidGlassBlur.getFloat(), 0.0F, 8.0F);
        int white = ColorUtil.rgba(255, 255, 255, 255);

        Render2D.liquidGlass(
                x, y, width, height, liquidRadius, white, 1.0F,
                strength + (Math.abs(height - 240.0F) < 0.001F ? 2.0F : 1.0F),
                0xFFFFFFFF, 1.0F, true, 0.0F, distortion, squirt, blur,
                LiquidGlassBlurChannel.SCOREBOARD
        );

        float overlay = clamp(liquidGlassOpacity.getFloat() / 100.0F, 0.0F, 1.0F);
        Render2D.squircle(
                x, y, width, height, rounding, squirt,
                ColorUtil.rgba(12, 12, 12, Math.round(255.0F * overlay))
        );
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    private float safeBlurRadius() {
        float value = blurRadius.getFloat();
        return Float.isFinite(value) ? Math.max(1.0F, Math.min(32.0F, value)) : 12.0F;
    }

    private float safeCornerRadius() {
        float value = cornerRadius.getFloat();
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(12.0F, value)) : 0.0F;
    }

    private static int blurTint(Color color) {
        Color safe = color == null ? new Color(0, 0, 0, 77) : color;
        int alpha = Math.max(1, safe.getAlpha());
        return (alpha << 24) | (safe.getRed() << 16) | (safe.getGreen() << 8) | safe.getBlue();
    }

    private Component displayComponent(Component component) {
        Component safe = component == null ? Component.empty() : component;
        if (!textColorMode.is("Custom")) {
            return safe;
        }
        return Component.literal(safe.getString());
    }

    private int displayColor(Color customColor) {
        return textColorMode.is("Custom") && customColor != null
                ? customColor.getRGB()
                : VANILLA_TEXT_COLOR;
    }

    private float safeScale() {
        float value = scale.getFloat();
        return Float.isFinite(value) ? Math.max(0.5F, Math.min(2.0F, value)) : 1.0F;
    }

    private static Comparator<PlayerScoreEntry> vanillaOrder() {
        try {
            Comparator<PlayerScoreEntry> comparator = GuiScoreboardAccessor.universalmod$getScoreDisplayOrder();
            return comparator == null ? FALLBACK_ORDER : comparator;
        } catch (Throwable ignored) {
            return FALLBACK_ORDER;
        }
    }

    private static String vanillaSpacer() {
        try {
            String spacer = GuiScoreboardAccessor.universalmod$getScoreboardSpacer();
            return spacer == null ? VANILLA_SPACER_FALLBACK : spacer;
        } catch (Throwable ignored) {
            return VANILLA_SPACER_FALLBACK;
        }
    }

    private record BoardData(Component title, List<LineData> lines) {
    }

    private record LineData(Component name, Component score, int scoreWidth) {
    }

    private record Layout(int width, int height, int contentWidth, int titleWidth, int bodyHeight, int titleHeight) {
    }

    private record VanillaPlacement(float x, float y) {
    }
}
