package universalmod.api.drag.impl;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import universalmod.utils.media.MusicTracker;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.animation.SmoothAnimation;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.font.FontType;
import universalmod.utils.theme.HudStyleOverrides;

import java.awt.Color;
import java.util.Locale;

/** Compact album-art player used by the HUD. */
public final class MusicPlayerPanel extends HudPanel {
    // Values are authored in physical pixels, then converted to Render2D's fixed
    // coordinate space. At HUD Size = 100% this is a 220x64 pixel widget.
    private static final float BASE_SCALE = 0.50F;
    private static final float WIDTH = 220.0F * BASE_SCALE;
    private static final float HEIGHT = 64.0F * BASE_SCALE;
    private static final float COVER_SIZE = 52.0F * BASE_SCALE;
    private static final float COVER_RADIUS = 7.0F * BASE_SCALE;
    private static final float PANEL_RADIUS = 12.0F * BASE_SCALE;
    private static final float LEGACY_WIDTH = 210.0F * BASE_SCALE;
    private static final float LEGACY_HEIGHT = 85.0F * BASE_SCALE;
    private static final float LEGACY_COVER_SIZE = 44.0F * BASE_SCALE;
    private static final float LEGACY_COVER_RADIUS = 6.0F * BASE_SCALE;
    private static final float LEGACY_PANEL_RADIUS = 10.0F * BASE_SCALE;
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
        if (isLegacyView()) {
            renderLegacy();
            return;
        }
        renderCompact();
    }

    private void renderCompact() {
        sizeImmediate(WIDTH, HEIGHT);

        float x = drag.x();
        float y = drag.y();
        float mouseX = localMouseX(mouseX());
        float mouseY = localMouseY(mouseY());
        float previousX = x + WIDTH - s(48.0F);
        float playX = x + WIDTH - s(30.0F);
        float nextX = x + WIDTH - s(12.0F);
        float controlY = y + s(18.0F);
        float hitSize = s(16.0F);

        updateHover(previousHover, inside(mouseX, mouseY, previousX - hitSize * 0.5F, controlY - hitSize * 0.5F, hitSize, hitSize));
        updateHover(playHover, inside(mouseX, mouseY, playX - hitSize * 0.5F, controlY - hitSize * 0.5F, hitSize, hitSize));
        updateHover(nextHover, inside(mouseX, mouseY, nextX - hitSize * 0.5F, controlY - hitSize * 0.5F, hitSize, hitSize));
        updatePress(previousPress);
        updatePress(playPress);
        updatePress(nextPress);

        renderPanelBackground(x, y, WIDTH, HEIGHT, PANEL_RADIUS);

        float coverX = x + s(6.0F);
        float coverY = y + s(6.0F);
        Identifier cover = music.getImage();
        if (cover != null) {
            Render2D.image(cover.toString(), coverX, coverY, COVER_SIZE, COVER_SIZE, COVER_RADIUS, ColorUtil.WHITE);
        }

        float textX = coverX + COVER_SIZE + s(10.0F);
        float titleY = y + s(11.0F);
        float textWidth = previousX - s(11.0F) - textX;
        String title = safe(music.title());

        Render2D.pushScissor(Render2D.currentGraphics(), textX, y + s(4.0F), textWidth, s(22.0F));
        try {
            renderMovingTitle(title, textX, titleY, s(15.0F), opaque(hudTextColor(255)), textWidth);
        } finally {
            Render2D.popScissor(Render2D.currentGraphics());
        }

        int previousColor = iconColor(previousHover.get());
        int playColor = iconColor(playHover.get());
        int nextColor = iconColor(nextHover.get());
        playStateAnimation.run(music.isPlaying() ? 1.0D : 0.0D, 0.30D, Easings.EXPO_IN_OUT, true);
        playStateAnimation.update();
        renderControlIcon(IMAGE_PREFIX + "previous.png", previousX, controlY, previousColor, previousPress.get());
        renderAnimatedPlayPause(playX, controlY, playColor, playPress.get(), playStateAnimation.get());
        renderControlIcon(IMAGE_PREFIX + "next.png", nextX, controlY, nextColor, nextPress.get());

        float barX = textX;
        float barY = y + s(44.0F);
        float barWidth = x + WIDTH - s(10.0F) - barX;
        float barHeight = Math.max(1.0F, s(3.0F));
        Render2D.rect(barX, barY, barWidth, barHeight, s(1.5F), ColorUtil.rgba(255, 255, 255, 54));
        float fillWidth = barWidth * music.progress();
        if (fillWidth > 0.25F) {
            Render2D.rect(barX, barY, fillWidth, barHeight, s(1.5F), ColorUtil.rgba(192, 173, 255, 255));
        }
    }

    private void renderLegacy() {
        sizeImmediate(LEGACY_WIDTH, LEGACY_HEIGHT);

        float x = drag.x();
        float y = drag.y();
        float mouseX = localMouseX(mouseX());
        float mouseY = localMouseY(mouseY());
        float centerX = x + LEGACY_WIDTH * 0.5F;

        updateHover(previousHover, inside(mouseX, mouseY, centerX - s(40.0F), y + s(55.0F), s(24.0F), s(20.0F)));
        updateHover(playHover, inside(mouseX, mouseY, centerX - s(12.0F), y + s(55.0F), s(24.0F), s(20.0F)));
        updateHover(nextHover, inside(mouseX, mouseY, centerX + s(16.0F), y + s(55.0F), s(24.0F), s(20.0F)));
        updatePress(previousPress);
        updatePress(playPress);
        updatePress(nextPress);

        renderPanelBackground(x, y, LEGACY_WIDTH, LEGACY_HEIGHT, LEGACY_PANEL_RADIUS);
        Render2D.outline(x, y, LEGACY_WIDTH, LEGACY_HEIGHT, LEGACY_PANEL_RADIUS, Math.max(0.5F, s(1.0F)),
                ColorUtil.rgba(76, 78, 88, 255));
        Render2D.image(IMAGE_PREFIX + "stars.png", x, y, LEGACY_WIDTH, LEGACY_HEIGHT, LEGACY_PANEL_RADIUS, ColorUtil.WHITE);

        float coverX = x + s(12.0F);
        float coverY = y + s(12.0F);
        Identifier cover = music.getImage();
        if (cover != null) {
            Render2D.image(cover.toString(), coverX, coverY, LEGACY_COVER_SIZE, LEGACY_COVER_SIZE,
                    LEGACY_COVER_RADIUS, ColorUtil.WHITE);
        }
        renderSourceBadge(coverX, coverY, LEGACY_COVER_SIZE);

        float textX = coverX + LEGACY_COVER_SIZE + s(10.0F);
        float titleY = coverY + s(3.0F);
        float artistY = coverY + s(20.0F);
        float textWidth = (x + LEGACY_WIDTH - s(12.0F)) - textX;
        String title = safe(music.title()).toLowerCase(Locale.ROOT);
        String artist = safe(music.author()).toUpperCase(Locale.ROOT);
        Render2D.pushScissor(Render2D.currentGraphics(), textX, y, textWidth, LEGACY_HEIGHT);
        try {
            renderMovingTitle(title, textX, titleY, s(14.0F), opaque(hudTextColor(255)), textWidth);
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
        Render2D.text(FontType.BOLD, duration, x + LEGACY_WIDTH - s(12.0F) - durationWidth, rowY, s(11.0F), timeColor);

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
        float barWidth = LEGACY_WIDTH - s(24.0F);
        float barHeight = Math.max(1.0F, s(3.0F));
        Render2D.rect(barX, barY, barWidth, barHeight, s(1.25F), ColorUtil.rgba(255, 255, 255, 0x25));
        float fillWidth = barWidth * music.progress();
        if (fillWidth > 0.25F) {
            Render2D.rect(barX, barY, fillWidth, barHeight, s(1.25F), brightProgressColor(music.getMediaColor()));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event == null || event.button() != 0) {
            return false;
        }
        if (isLegacyView()) {
            return mouseClickedLegacy(event);
        }

        // ChatScreenMixin already converts mouse coordinates into Render2D's fixed
        // coordinate space. Convert once more into this panel's unscaled local space
        // so button hitboxes stay aligned with HUD Size at every percentage.
        float mouseX = localMouseX((float) event.x());
        float mouseY = localMouseY((float) event.y());
        float x = drag.x();
        float y = drag.y();
        float previousX = x + WIDTH - s(48.0F);
        float playX = x + WIDTH - s(30.0F);
        float nextX = x + WIDTH - s(12.0F);
        float controlY = y + s(18.0F);
        float hitSize = s(16.0F);

        if (inside(mouseX, mouseY, previousX - hitSize * 0.5F, controlY - hitSize * 0.5F, hitSize, hitSize)) {
            previousPress.set(1.0D);
            music.previousTrack();
            return true;
        }
        if (inside(mouseX, mouseY, playX - hitSize * 0.5F, controlY - hitSize * 0.5F, hitSize, hitSize)) {
            playPress.set(1.0D);
            music.togglePlay();
            return true;
        }
        if (inside(mouseX, mouseY, nextX - hitSize * 0.5F, controlY - hitSize * 0.5F, hitSize, hitSize)) {
            nextPress.set(1.0D);
            music.nextTrack();
            return true;
        }
        return false;
    }

    private boolean mouseClickedLegacy(MouseButtonEvent event) {
        float mouseX = localMouseX((float) event.x());
        float mouseY = localMouseY((float) event.y());
        float x = drag.x();
        float y = drag.y();
        float centerX = x + LEGACY_WIDTH * 0.5F;

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

    private void renderPanelBackground(float x, float y, float width, float height, float radius) {
        HudRenderCompat.background(x, y, width, height, radius, s(5.0F), 0.82F,
                ColorUtil.rgba(0, 0, 0, 255));
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

    private void renderSourceBadge(float coverX, float coverY, float coverSize) {
        String icon = resolveSourceIcon();
        if (icon.isBlank()) {
            return;
        }
        // 16 physical pixels at HUD Size = 100%. BASE_SCALE=0.5 and the fixed
        // Render2D coordinate space is 2 physical px/unit, so s(16) is exactly 16 px.
        // Center it on the right edge: half of the badge overlaps the artwork and
        // half sits outside it, while remaining at the top-right corner.
        float badgeSize = s(16.0F);
        float badgeX = coverX + coverSize - badgeSize * 0.5F;
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
        animation.run(hovered ? 1.0D : 0.0D, 0.08D, Easings.LINEAR, true);
        animation.update();
    }

    private static void updatePress(SmoothAnimation animation) {
        animation.run(0.0D, 0.16D, Easings.CUBIC_OUT, true);
        animation.update();
    }

    private static int iconColor(float hover) {
        // Resting controls are neutral grey; hover reaches pure white quickly.
        float value = 156.0F + clamp(hover, 0.0F, 1.0F) * 99.0F;
        return ColorUtil.rgba(Math.round(value), Math.round(value), Math.round(value), 255);
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

    private boolean isLegacyView() {
        return HudStyleOverrides.MUSIC_PLAYER_VIEW_1.equals(
                HudStyleOverrides.getInstance().getMusicPlayerView(elementId())
        );
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
