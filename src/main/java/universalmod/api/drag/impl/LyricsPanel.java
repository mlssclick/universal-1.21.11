package universalmod.api.drag.impl;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import universalmod.utils.media.LyricsTimeline;
import universalmod.utils.media.MusicTracker;
import universalmod.utils.render.animation.Easings;
import universalmod.utils.render.color.ColorUtil;
import universalmod.utils.render.ui.Render2D;
import universalmod.utils.render.ui.blur.BuiltBlur;
import universalmod.utils.render.ui.font.FontType;

import java.util.List;

public final class LyricsPanel extends HudPanel {
    private static final float WIDTH = 230.0F;
    private static final float HEIGHT = 76.0F;
    private static final float PADDING_X = 9.0F;
    private static final float CENTER_Y = 37.0F;
    private static final float LINE_HEIGHT = 10.0F;
    private static final float TEXT_SIZE = 6.6F;
    private static final float TITLE_SIZE = 6.0F;
    private static final float BLUR_RADIUS = 4.0F;
    private static final float BLUR_SMOOTHNESS = 0.55F;
    private static final double BACKWARD_TOLERANCE = 0.45D;
    private static final double LYRICS_LEAD_SECONDS = 0.24D;

    private final MusicTracker musicTracker = MusicTracker.getInstance();
    private String lastLyrics = "";
    private List<LyricsTimeline.Line> cachedLines = List.of();
    private float scrollY;
    private float activeVisualIndex;
    private int displayedActive;
    private int targetActive;
    private long activeSwitchMs;
    private double lastRenderPosition;

    public LyricsPanel() {
        super("lyrics", "Lyrics", 10.0F, 90.0F, WIDTH, HEIGHT);
    }

    @Override
    public void render() {
        IMediaSession session = musicTracker.getSession();
        MediaInfo media = session == null ? null : session.getMedia();
        String lyrics = musicTracker.getLyrics();
        boolean preview = editPreview();
        boolean visible = preview || (musicTracker.haveActiveSession() && media != null && lyrics != null && !lyrics.isBlank());
        float alpha = contentAlpha(visible);
        if (alpha <= 0.0F) {
            return;
        }

        size(WIDTH, HEIGHT);
        float x = drag.x();
        float y = drag.y();
        float width = logicalWidth();
        float height = logicalHeight();

        HudRenderCompat.background(new BuiltBlur(x, y, width, height, 6.0F, BLUR_SMOOTHNESS, BLUR_RADIUS)
                .withColor(ColorUtil.rgba(0, 0, 0, Math.round(alpha * 255.0F))));

        if (preview && (lyrics == null || lyrics.isBlank())) {
            renderPreview(x, y, alpha);
            return;
        }

        List<LyricsTimeline.Line> lines = linesFor(lyrics, media);
        if (lines.isEmpty()) {
            renderEmpty(x, y, alpha);
            return;
        }

        double syncedPosition = musicTracker.getSyncedPositionSeconds();
        double visualPosition = Math.max(0.0D, syncedPosition + LYRICS_LEAD_SECONDS);
        int active = stabilizedActiveIndex(lines, visualPosition, media);
        updateActiveAnimation(active);
        float targetScroll = activeVisualIndex * LINE_HEIGHT;
        scrollY = smooth(scrollY, targetScroll, 1.0F / 60.0F, 9.0F);

        String title = media == null ? "Lyrics" : trimToWidth(trackTitle(media), FontType.SEMIBOLD, TITLE_SIZE, width - PADDING_X * 2.0F);
        Render2D.text(FontType.SEMIBOLD, title, x + PADDING_X, y + 5.0F, TITLE_SIZE, withAlpha(hudTextColor(220), alpha));

        float listX = x + PADDING_X;
        float listY = y + 19.0F;
        float listWidth = width - PADDING_X * 2.0F;
        float listHeight = height - 24.0F;
        Render2D.pushScissor(Render2D.currentGraphics(), listX, listY, listWidth, listHeight);
        try {
            float centerLineY = listY + CENTER_Y - 19.0F;
            for (int i = 0; i < lines.size(); i++) {
                float lineY = centerLineY + i * LINE_HEIGHT - scrollY;
                if (lineY < listY - LINE_HEIGHT || lineY > listY + listHeight) {
                    continue;
                }
                float visualDistance = Math.abs(i - activeVisualIndex);
                float lineAlpha = alpha * clamp(1.0F - visualDistance * 0.24F, 0.28F, 1.0F);
                float focus = clamp(1.0F - visualDistance, 0.0F, 1.0F);
                float size = TEXT_SIZE + 0.55F * focus;
                String lineText = lines.get(i).text();
                int mutedColor = withAlpha(hudMutedColor(210), lineAlpha);

                int lineColor = i == active
                        ? withAlpha(hudTextColor(255), lineAlpha)
                        : mutedColor;
                Render2D.textFade(FontType.SEMIBOLD, lineText, listX, lineY, size, lineColor,
                        listX, listX + listWidth, 12.0F, 0.0F, 1.0F);
            }
        } finally {
            Render2D.popScissor(Render2D.currentGraphics());
        }
    }

    private void renderPreview(float x, float y, float alpha) {
        Render2D.text(FontType.SEMIBOLD, "Lyrics", x + PADDING_X, y + 5.0F, TITLE_SIZE, withAlpha(hudTextColor(220), alpha));
    }

    private void renderEmpty(float x, float y, float alpha) {
        Render2D.text(FontType.SEMIBOLD, "Lyrics", x + PADDING_X, y + 5.0F, TITLE_SIZE, withAlpha(hudTextColor(220), alpha));
        Render2D.text(FontType.DEFAULT, "No lyrics for this track", x + PADDING_X, y + 34.0F, TEXT_SIZE, withAlpha(hudMutedColor(210), alpha));
    }

    private List<LyricsTimeline.Line> linesFor(String lyrics, MediaInfo media) {
        String safeLyrics = lyrics == null ? "" : lyrics;
        if (!safeLyrics.equals(lastLyrics)) {
            lastLyrics = safeLyrics;
            cachedLines = LyricsTimeline.parse(safeLyrics, media == null ? 0L : media.getDuration());
            scrollY = 0.0F;
            activeVisualIndex = 0.0F;
            displayedActive = 0;
            targetActive = 0;
            activeSwitchMs = 0L;
            lastRenderPosition = 0.0D;
        }
        return cachedLines;
    }

    private int stabilizedActiveIndex(List<LyricsTimeline.Line> lines, double position, MediaInfo media) {
        double safePosition = Math.max(0.0D, position);
        boolean playing = media == null || media.isPlaying();
        if (playing && safePosition + BACKWARD_TOLERANCE < lastRenderPosition) {
            safePosition = lastRenderPosition;
        } else {
            lastRenderPosition = safePosition;
        }

        int active = LyricsTimeline.activeIndex(lines, safePosition);
        if (active < displayedActive && playing) {
            double previousLineTime = lines.get(active).time();
            boolean realSeekBack = lastRenderPosition - safePosition > 1.5D || safePosition + 1.0D < previousLineTime;
            if (!realSeekBack) {
                return displayedActive;
            }
        }
        return active;
    }

    private void updateActiveAnimation(int active) {
        int safeActive = Math.max(0, active);
        long now = System.currentTimeMillis();
        if (safeActive != targetActive) {
            targetActive = safeActive;
            activeSwitchMs = now;
        }

        double part = activeSwitchMs <= 0L ? 1.0D : Math.min(1.0D, Math.max(0.0D, (now - activeSwitchMs) / 460.0D));
        float eased = (float) Easings.EXPO_OUT.ease(part);
        activeVisualIndex = activeVisualIndex + (targetActive - activeVisualIndex) * Math.min(0.35F, eased);
        if (Math.abs(activeVisualIndex - targetActive) < 0.015F) {
            activeVisualIndex = targetActive;
            displayedActive = targetActive;
        } else {
            displayedActive = Math.round(activeVisualIndex);
        }
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String trackTitle(MediaInfo media) {
        String artist = safe(media.getArtist()).trim();
        String title = safe(media.getTitle()).trim();
        if (artist.isBlank()) {
            return title.isBlank() ? "Lyrics" : title;
        }
        if (title.isBlank()) {
            return artist;
        }
        return artist + " - " + title;
    }
}
