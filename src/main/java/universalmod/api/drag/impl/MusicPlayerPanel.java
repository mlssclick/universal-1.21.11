package universalmod.api.drag.impl;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import universalmod.utils.media.MusicTracker;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.blur.BuiltBlur;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.theme.ThemeColors;

import java.awt.Color;
import java.util.Locale;

/** Music player layout ported from the supplied reference widget. */
public final class MusicPlayerPanel extends HudPanel {
    // The reference widget uses a 210x85 layout. Render2D's fixed coordinate space is
    // two physical pixels per unit, so a 0.5 base factor reproduces the reference's
    // compact on-screen size at HUD Size = 100%.
    private static final float BASE_SCALE = 0.50F;
    private static final float WIDTH = 210.0F * BASE_SCALE;
    private static final float HEIGHT = 85.0F * BASE_SCALE;
    private static final float COVER_SIZE = 44.0F * BASE_SCALE;
    private static final float COVER_RADIUS = 6.0F * BASE_SCALE;
    private static final float PANEL_RADIUS = 10.0F * BASE_SCALE;
    private static final String IMAGE_PREFIX = "music_player/";
    private static final String SOURCE_ICON_PREFIX = IMAGE_PREFIX + "source_icons/";
    private static final float MARQUEE_GAP = 18.0F;
    private static final float MARQUEE_SPEED = 18.0F;

    private final MusicTracker music = MusicTracker.getInstance();
    private final SmoothAnimation previousHover = new SmoothAnimation();
    private final SmoothAnimation playHover = new SmoothAnimation();
    private final SmoothAnimation nextHover = new SmoothAnimation();
    private final SmoothAnimation previousPress = new SmoothAnimation();
    private final SmoothAnimation playPress = new SmoothAnimation();
    private final SmoothAnimation nextPress = new SmoothAnimation();
    private final SmoothAnimation playStateAnimation = new SmoothAnimation();

    public MusicPlayerPanel() {
        super("music_player", "Music Player", 10.0F, 80.0F, WIDTH, HEIGHT);
        sizeImmediate(WIDTH, HEIGHT);
        previousHover.set(0.0D);
        playHover.set(0.0D);
        nextHover.set(0.0D);
        previousPress.set(0.0D);
        playPress.set(0.0D);
        nextPress.set(0.0D);
        playStateAnimation.set(0.0D);
    }

    @Override
    public void render() {
        music.ensureStarted();
        sizeImmediate(WIDTH, HEIGHT);

        float x = drag.x();
        float y = drag.y();
        float mouseX = localMouseX(mouseX());
        float mouseY = localMouseY(mouseY());
        float centerX = x + WIDTH / 2.0F;

        updateHover(previousHover, inside(mouseX, mouseY, centerX - s(40.0F), y + s(55.0F), s(24.0F), s(20.0F)));
        updateHover(playHover, inside(mouseX, mouseY, centerX - s(12.0F), y + s(55.0F), s(24.0F), s(20.0F)));
        updateHover(nextHover, inside(mouseX, mouseY, centerX + s(16.0F), y + s(55.0F), s(24.0F), s(20.0F)));
        updatePress(previousPress);
        updatePress(playPress);
        updatePress(nextPress);

        renderPanelBackground(x, y);
        Render2D.outline(x, y, WIDTH, HEIGHT, PANEL_RADIUS, Math.max(0.5F, s(1.0F)), ColorUtil.rgba(76, 78, 88, 255));
        Render2D.image(IMAGE_PREFIX + "stars.png", x, y, WIDTH, HEIGHT, PANEL_RADIUS, ColorUtil.WHITE);

        float coverX = x + s(12.0F);
        float coverY = y + s(12.0F);
        Identifier cover = music.getImage();
        if (cover != null) {
            Render2D.image(cover.toString(), coverX, coverY, COVER_SIZE, COVER_SIZE, COVER_RADIUS, ColorUtil.WHITE);
        }
        renderSourceBadge(coverX, coverY);

        float textX = coverX + COVER_SIZE + s(10.0F);
        float titleY = coverY + s(3.0F);
        float artistY = coverY + s(20.0F);
        float textWidth = (x + WIDTH - s(12.0F)) - textX;
        String title = safe(music.title()).toLowerCase(Locale.ROOT);
        String artist = safe(music.author()).toUpperCase(Locale.ROOT);

        Render2D.pushScissor(Render2D.currentGraphics(), textX, y, textWidth, HEIGHT);
        try {
            renderMovingTitle(title, textX, titleY, s(14.0F), opaque(hudTextColor(255)), textWidth);
            // Match the reference widget exactly: 0xFF888899.
            Render2D.text(FontType.SEMIBOLD, artist, textX, artistY, s(11.0F), ColorUtil.rgba(136, 136, 153, 255));
        } finally {
            Render2D.popScissor(Render2D.currentGraphics());
        }

        float rowY = y + s(62.0F);
        float iconY = y + s(61.0F);
        String position = formatTime(music.positionSeconds());
        String duration = formatTime(music.durationSeconds());
        int timeColor = ColorUtil.rgba(221, 221, 221, 255);
        Render2D.text(FontType.BOLD, position, x + s(12.0F), rowY, s(11.0F), timeColor);
        float durationWidth = Render2D.textWidth(FontType.BOLD, duration, s(11.0F));
        Render2D.text(FontType.BOLD, duration, x + WIDTH - s(12.0F) - durationWidth, rowY, s(11.0F), timeColor);

        int previousColor = iconColor(previousHover.get());
        int playColor = iconColor(playHover.get());
        int nextColor = iconColor(nextHover.get());
        playStateAnimation.run(music.isPlaying() ? 1.0D : 0.0D, 0.30D, Easings.EXPO_IN_OUT, true);
        playStateAnimation.update();
        renderControlIcon(IMAGE_PREFIX + "previous.png", centerX - s(24.0F), iconY + s(6.0F), previousColor, previousPress.get());
        renderAnimatedPlayPause(centerX, iconY + s(6.0F), playColor, playPress.get(), playStateAnimation.get());
        renderControlIcon(IMAGE_PREFIX + "next.png", centerX + s(24.0F), iconY + s(6.0F), nextColor, nextPress.get());

        float barX = x + s(12.0F);
        float barY = y + s(77.0F);
        float barWidth = WIDTH - s(24.0F);
        float barHeight = Math.max(1.0F, s(3.0F));
        // Reference track: 0x25FFFFFF. Only the filled progress remains fully opaque
        // and uses the average artwork color as requested.
        Render2D.rect(barX, barY, barWidth, barHeight, s(1.25F), ColorUtil.rgba(255, 255, 255, 0x25));
        float fillWidth = barWidth * music.progress();
        if (fillWidth > 0.25F) {
            int bright = brightProgressColor(music.getMediaColor());
            Render2D.rect(barX, barY, fillWidth, barHeight, s(1.25F), bright);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event == null || event.button() != 0) {
            return false;
        }

        // ChatScreenMixin already converts mouse coordinates into Render2D's fixed
        // coordinate space. Convert once more into this panel's unscaled local space
        // so button hitboxes stay aligned with HUD Size at every percentage.
        float mouseX = localMouseX((float) event.x());
        float mouseY = localMouseY((float) event.y());
        float x = drag.x();
        float y = drag.y();
        float centerX = x + WIDTH / 2.0F;

        if (inside(mouseX, mouseY, centerX - s(40.0F), y + s(55.0F), s(24.0F), s(20.0F))) {
            previousPress.set(1.0D);
            music.previousTrack();
            return true;
        }
        if (inside(mouseX, mouseY, centerX - s(12.0F), y + s(55.0F), s(24.0F), s(20.0F))) {
            playPress.set(1.0D);
            music.togglePlay();
            return true;
        }
        if (inside(mouseX, mouseY, centerX + s(16.0F), y + s(55.0F), s(24.0F), s(20.0F))) {
            nextPress.set(1.0D);
            music.nextTrack();
            return true;
        }
        return false;
    }

    private void renderPanelBackground(float x, float y) {
        if (ThemeColors.isHudDarkDesignEnabled()) {
            // Music Player intentionally ignores the global Dark opacity control: in
            // Dark design this widget must remain fully opaque for readability.
            Render2D.darkPanel(x, y, WIDTH, HEIGHT, PANEL_RADIUS, 1.0F,
                    ThemeColors.darkGradientStrength(), false, opaque(ThemeColors.darkColor()));
            return;
        }

        if (ThemeColors.isHudLiquidGlassDesignEnabled()) {
            HudRenderCompat.background(new BuiltBlur(x, y, WIDTH, HEIGHT, PANEL_RADIUS, 0.62F, s(4.0F))
                    .withColor(ColorUtil.rgba(8, 9, 13, 255)));
            return;
        }

        // Reference/default design: opaque panel so artwork, controls and text never
        // disappear against a bright world background.
        int base = opaque(ThemeColors.hudBlurColor(ColorUtil.rgba(8, 9, 13, 255)));
        Render2D.rect(x, y, WIDTH, HEIGHT, PANEL_RADIUS, base);
    }

    private static void renderMovingTitle(String text, float x, float y, float size, int color, float width) {
        if (text == null || text.isBlank() || width <= 0.0F) {
            return;
        }
        float textWidth = Render2D.textWidth(FontType.BOLD, text, size);
        if (textWidth <= width) {
            Render2D.text(FontType.BOLD, text, x, y, size, color);
            return;
        }

        // Same marquee behavior the old Dynamic Island used: the title moves
        // continuously and the duplicate enters after a fixed gap.
        float gap = s(MARQUEE_GAP);
        float speed = s(MARQUEE_SPEED);
        float travel = textWidth + gap;
        long periodMs = Math.max(1L, (long) ((travel / Math.max(0.001F, speed)) * 1000.0F));
        float offset = (System.currentTimeMillis() % periodMs) / 1000.0F * speed;
        Render2D.text(FontType.BOLD, text, x - offset, y, size, color);
        Render2D.text(FontType.BOLD, text, x - offset + travel, y, size, color);
    }

    private static void renderControlIcon(String texture, float centerX, float centerY, int color, float press) {
        float p = clamp(press, 0.0F, 1.0F);
        float size = s(12.0F) * (1.0F - 0.14F * p);
        Render2D.image(texture, centerX - size * 0.5F, centerY - size * 0.5F, size, size, 0.0F, opaque(color));
    }

    private static void renderAnimatedPlayPause(float centerX, float centerY, int color, float press, float state) {
        float p = clamp(press, 0.0F, 1.0F);
        float value = clamp(state, 0.0F, 1.0F);
        float baseSize = s(12.0F) * (1.0F - 0.14F * p);
        int baseColor = opaque(color);

        // Restored from the old Dynamic Island: Play shrinks and rotates out while
        // Pause grows and rotates in, both using the same 300 ms EXPO_IN_OUT state.
        float playScale = 1.0F - value;
        if (playScale > 0.01F) {
            float playSize = baseSize * playScale;
            Render2D.image(
                    IMAGE_PREFIX + "play.png",
                    centerX - playSize * 0.5F,
                    centerY - playSize * 0.5F,
                    playSize,
                    0.0F,
                    90.0F * value,
                    centerX,
                    centerY,
                    ColorUtil.multAlpha(baseColor, playScale)
            );
        }

        float pauseScale = value;
        if (pauseScale > 0.01F) {
            float pauseSize = baseSize * pauseScale;
            Render2D.image(
                    IMAGE_PREFIX + "pause.png",
                    centerX - pauseSize * 0.5F,
                    centerY - pauseSize * 0.5F,
                    pauseSize,
                    0.0F,
                    -90.0F + 90.0F * value,
                    centerX,
                    centerY,
                    ColorUtil.multAlpha(baseColor, pauseScale)
            );
        }
    }

    private void renderSourceBadge(float coverX, float coverY) {
        String icon = resolveSourceIcon();
        if (icon.isBlank()) {
            return;
        }
        // 16 physical pixels at HUD Size = 100%. BASE_SCALE=0.5 and the fixed
        // Render2D coordinate space is 2 physical px/unit, so s(16) is exactly 16 px.
        // Center it on the right edge: half of the badge overlaps the artwork and
        // half sits outside it, while remaining at the top-right corner.
        float badgeSize = s(16.0F);
        float badgeX = coverX + COVER_SIZE - badgeSize * 0.5F;
        float badgeY = coverY - badgeSize * 0.5F;
        float radius = s(3.5F);
        Render2D.rect(badgeX - s(0.75F), badgeY - s(0.75F), badgeSize + s(1.5F), badgeSize + s(1.5F), radius,
                ColorUtil.rgba(10, 12, 16, 225));
        Render2D.outline(badgeX - s(0.75F), badgeY - s(0.75F), badgeSize + s(1.5F), badgeSize + s(1.5F), radius,
                Math.max(0.5F, s(0.8F)), ColorUtil.rgba(255, 255, 255, 52));
        Render2D.image(SOURCE_ICON_PREFIX + icon + ".png", badgeX, badgeY, badgeSize, badgeSize, s(3.0F), ColorUtil.WHITE);
    }

    private String resolveSourceIcon() {
        String service = normalizeSourceKey(music.getServiceName());
        String source = normalizeSourceKey(music.getSourceName());
        String app = normalizeSourceKey(music.getSourceAppName());
        String appId = normalizeSourceKey(music.getSourceAppId());
        String sourceType = normalizeSourceKey(music.getSourceType());

        String key = sourceIconFor(service, true);
        if (!key.isBlank()) return key;
        key = sourceIconFor(source, true);
        if (!key.isBlank()) return key;
        key = sourceIconFor(app, true);
        if (!key.isBlank()) return key;
        key = sourceIconFor(appId, true);
        if (!key.isBlank()) return key;

        key = sourceIconFor(source, false);
        if (!key.isBlank()) return key;
        key = sourceIconFor(app, false);
        if (!key.isBlank()) return key;
        key = sourceIconFor(appId, false);
        if (!key.isBlank()) return key;

        if (sourceType.contains("browser")) {
            return browserIconFor(source + ' ' + app + ' ' + appId);
        }
        if (sourceType.contains("player")) {
            return playerIconFor(source + ' ' + app + ' ' + appId);
        }
        return "";
    }

    private static String sourceIconFor(String value, boolean serviceOnly) {
        if (value.isBlank()) {
            return "";
        }
        String key = serviceIconFor(value);
        if (!key.isBlank()) {
            return key;
        }
        return serviceOnly ? "" : fallbackSourceIconFor(value);
    }

    private static String serviceIconFor(String value) {
        if (containsAny(value, "youtube music", "music.youtube", "youtubemusic")) return "youtubemusic";
        if (containsAny(value, "youtube", "youtu.be")) return "youtube";
        if (containsAny(value, "spotify")) return "spotify";
        if (containsAny(value, "yandex music", "яндекс музыка", "music.yandex", "yandexmusic")) return "yandexmusic";
        if (containsAny(value, "soundcloud")) return "soundcloud";
        if (containsAny(value, "apple music", "applemusic", "itunes")) return "applemusic";
        if (containsAny(value, "deezer")) return "deezer";
        if (containsAny(value, "tidal")) return "tidal";
        if (containsAny(value, "bandcamp")) return "bandcamp";
        if (containsAny(value, "mixcloud")) return "mixcloud";
        if (containsAny(value, "twitch")) return "twitch";
        if (containsAny(value, "vk music", "вк музыка", "vkmusic", "vk.com", "boom")) return "vkmusic";
        return "";
    }

    private static String fallbackSourceIconFor(String value) {
        String browser = browserIconFor(value);
        if (!browser.isBlank()) {
            return browser;
        }
        String player = playerIconFor(value);
        if (!player.isBlank()) {
            return player;
        }
        return "";
    }

    private static String browserIconFor(String value) {
        if (containsAny(value, "msedge", "microsoft edge", "microsoftedge", "edge")) return "edge";
        if (containsAny(value, "google chrome", "chrome")) return "chrome";
        if (containsAny(value, "mozilla firefox", "firefox")) return "firefox";
        if (containsAny(value, "brave")) return "brave";
        if (containsAny(value, "vivaldi")) return "vivaldi";
        if (containsAny(value, "yandex browser", "yandexbrowser", "browser.exe")) return "yandexbrowser";
        if (containsAny(value, "opera")) return "opera";
        if (containsAny(value, "arc")) return "arc";
        if (containsAny(value, "floorp")) return "floorp";
        if (containsAny(value, "zen browser", "zenbrowser", "zen")) return "zenbrowser";
        return "";
    }

    private static String playerIconFor(String value) {
        if (containsAny(value, "vlc")) return "vlc";
        if (containsAny(value, "foobar2000", "foobar")) return "foobar2000";
        if (containsAny(value, "winamp")) return "winamp";
        return "";
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeSourceKey(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private static void updateHover(SmoothAnimation animation, boolean hovered) {
        animation.run(hovered ? 1.0D : 0.0D, 0.15D, Easings.LINEAR, true);
        animation.update();
    }

    private static void updatePress(SmoothAnimation animation) {
        animation.run(0.0D, 0.16D, Easings.CUBIC_OUT, true);
        animation.update();
    }

    private static int iconColor(float hover) {
        // Reference textures are already pure white; tinting them to 225 made the
        // controls look grey. Keep them fully white and let hover/press animations
        // provide the interaction feedback instead of reducing contrast.
        return ColorUtil.rgba(255, 255, 255, 255);
    }

    private static int brightProgressColor(int color) {
        float[] hsb = Color.RGBtoHSB(
                ColorUtil.getRed(color),
                ColorUtil.getGreen(color),
                ColorUtil.getBlue(color),
                null);
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], 1.0F);
        return ColorUtil.rgba((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF, 255);
    }

    private static float s(float value) {
        return value * BASE_SCALE;
    }

    private static int opaque(int color) {
        return (color & 0x00FFFFFF) | 0xFF000000;
    }

    private float mouseX() {
        return mc.mouseHandler == null || mc.getWindow() == null
                ? Float.NaN
                : (float) Render2D.guiToFixed(mc.mouseHandler.getScaledXPos(mc.getWindow()));
    }

    private float mouseY() {
        return mc.mouseHandler == null || mc.getWindow() == null
                ? Float.NaN
                : (float) Render2D.guiToFixed(mc.mouseHandler.getScaledYPos(mc.getWindow()));
    }

    private static boolean inside(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
    }

    private static String formatTime(double seconds) {
        if (seconds < 0.0D || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            seconds = 0.0D;
        }
        int total = Math.max(0, (int) seconds);
        return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
