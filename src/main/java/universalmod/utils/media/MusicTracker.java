package universalmod.utils.media;

import com.mojang.blaze3d.platform.NativeImage;
import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import universalmod.utils.render.color.ColorUtil;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Windows media-session tracker used by the Music Player and Lyrics HUDs.
 * The polling/control flow intentionally mirrors the reference SMTC player:
 * one-second polling, optimistic play/pause and asynchronous media controls.
 */
public final class MusicTracker {
    private static final MusicTracker INSTANCE = new MusicTracker();
    private static final Identifier COVER_ID = Identifier.fromNamespaceAndPath("universalmod", "music_player/current_artwork");

    private final Minecraft mc = Minecraft.getInstance();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MusicService-Action");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MusicService-Artwork");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final AtomicLong artworkGeneration = new AtomicLong();

    private volatile boolean running = true;
    private volatile Thread worker;
    private volatile IMediaSession session;
    private volatile boolean playing;
    private volatile long positionMs;
    private volatile double durationSeconds;
    private volatile long lastKnownSessionPosition = -1L;
    private volatile long lastSyncTime;
    private volatile long lastInteractionTime;
    private volatile String timingTrackKey = "";
    private volatile String currentTitle = "No Session";
    private volatile String currentAuthor = "";
    private volatile Identifier coverIdentifier;
    private volatile DynamicTexture coverTexture;
    private volatile byte[] lastArtworkBytes;
    private volatile int mediaColor = ColorUtil.WHITE;
    private volatile String currentSourceAppId = "";
    private volatile String currentSourceAppName = "";
    private volatile String currentSourceName = "";
    private volatile String currentSourceType = "";
    private volatile String currentServiceName = "";
    private volatile boolean currentRepeatSupported;
    private volatile int currentRepeatMode = -1;

    // Lyrics stay a separate HUD/feature; only its existing data feed is retained here.
    private volatile String lyrics = "";
    private volatile String lastTrack = "";
    private volatile long nextLyricsRetryAt;

    private MusicTracker() {
    }

    public static MusicTracker getInstance() {
        return INSTANCE;
    }

    public void ensureStarted() {
        if (!running || !started.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(this::pollSessions, "SMTC-JNI-Poller");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    private void pollSessions() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                List<IMediaSession> sessions = MediaPlayerInfo.INSTANCE.getMediaSessions();
                if (sessions != null && !sessions.isEmpty()) {
                    IMediaSession current = sessions.get(0);
                    MediaInfo info = current == null ? null : current.getMedia();
                    session = current;
                    if (info != null) {
                        updateFromMediaInfo(current, info);
                    } else {
                        clearSessionState();
                    }
                } else {
                    clearSessionState();
                }
            } catch (Throwable throwable) {
                clearSessionState();
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void updateFromMediaInfo(IMediaSession current, MediaInfo info) {
        updateSourceState(current);

        String newTitle = safe(info.getTitle());
        String newAuthor = safe(info.getArtist());
        String newTrackKey = newAuthor + "\u0000" + newTitle;
        boolean trackChanged = !Objects.equals(timingTrackKey, newTrackKey);

        currentTitle = newTitle;
        currentAuthor = newAuthor;

        // The native bridge uses seconds, exactly like the reference MediaPlayerInfo.
        // Do not guess units, debounce duration or predict seeks here: the SMTC layer is
        // responsible for returning the current position and duration.
        long nativePositionSeconds = Math.max(0L, info.getPosition());
        double nativeDurationSeconds = Math.max(0.0D, (double) info.getDuration());
        long now = System.currentTimeMillis();

        durationSeconds = nativeDurationSeconds;

        if (trackChanged) {
            // A new title is a new timeline. Never carry the previous song's base
            // position into it, even when both songs happen to report the same second.
            timingTrackKey = newTrackKey;
            lastKnownSessionPosition = nativePositionSeconds;
            positionMs = nativePositionSeconds * 1000L;
            lastSyncTime = now;
            playing = info.isPlaying();
            lastInteractionTime = 0L;
        } else {
            boolean interactionPending = now - lastInteractionTime <= 2500L;
            if (!interactionPending) {
                playing = info.isPlaying();
            }

            // This is the same timeline model as main/MusicService: whenever SMTC
            // publishes a new current position, use it as the base and interpolate
            // locally until the next poll. No seek thresholds or correction windows.
            if (nativePositionSeconds != lastKnownSessionPosition) {
                lastKnownSessionPosition = nativePositionSeconds;
                positionMs = nativePositionSeconds * 1000L;
                lastSyncTime = now;
            }
        }

        byte[] artwork = info.getArtworkPng();
        if (artwork != null && artwork.length > 0) {
            if (lastArtworkBytes == null || !Arrays.equals(lastArtworkBytes, artwork)) {
                lastArtworkBytes = artwork.clone();
                updateArtwork(lastArtworkBytes);
            }
        } else if (lastArtworkBytes != null) {
            lastArtworkBytes = null;
            updateArtwork(null);
        }

        updateLyrics(current, info);
    }

    private void updateLyrics(IMediaSession current, MediaInfo info) {
        String track = safe(info.getArtist()) + " - " + safe(info.getTitle());
        if (!Objects.equals(track, lastTrack)) {
            lastTrack = track;
            lyrics = "";
            nextLyricsRetryAt = 0L;
        }
        fetchLyricsIfNeeded(current);
    }

    private void fetchLyricsIfNeeded(IMediaSession current) {
        if (!running || current == null || current.getMedia() == null || notBlank(lyrics)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextLyricsRetryAt) {
            return;
        }
        nextLyricsRetryAt = now + 30_000L;
        String artist = current.getMedia().getArtist();
        String title = current.getMedia().getTitle();
        String requestedTrack = safe(artist) + " - " + safe(title);
        Thread thread = new Thread(() -> {
            String fetched = LyricsFetcher.fetchFromGenius(artist, title);
            if (running && Objects.equals(requestedTrack, lastTrack) && notBlank(fetched)) {
                lyrics = fetched;
            }
        }, "UniversalMod-LyricsFetcher");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateSourceState(IMediaSession current) {
        if (current == null) {
            return;
        }
        try { currentSourceAppId = safe(current.getSourceAppId()); } catch (Throwable ignored) { currentSourceAppId = safe(current.getOwner()); }
        try { currentSourceAppName = safe(current.getSourceAppName()); } catch (Throwable ignored) { currentSourceAppName = currentSourceAppId; }
        try { currentSourceName = safe(current.getSourceName()); } catch (Throwable ignored) { currentSourceName = currentSourceAppName; }
        try { currentSourceType = safe(current.getSourceType()); } catch (Throwable ignored) { currentSourceType = "app"; }
        try { currentServiceName = safe(current.getServiceName()); } catch (Throwable ignored) { currentServiceName = ""; }
        try {
            currentRepeatSupported = current.isRepeatSupported();
            currentRepeatMode = currentRepeatSupported ? current.getCycleType() : -1;
        } catch (Throwable ignored) {
            currentRepeatSupported = false;
            currentRepeatMode = -1;
        }
    }

    private void clearSessionState() {
        session = null;
        playing = false;
        currentTitle = "No Session";
        currentAuthor = "";
        lastKnownSessionPosition = -1L;
        positionMs = 0L;
        durationSeconds = 0.0D;
        timingTrackKey = "";
        lastSyncTime = 0L;
        currentSourceAppId = "";
        currentSourceAppName = "";
        currentSourceName = "";
        currentSourceType = "";
        currentServiceName = "";
        currentRepeatSupported = false;
        currentRepeatMode = -1;

        // A vanished SMTC session must also invalidate its artwork. Previously the
        // tracker only cleared text/timeline state, so the last cover stayed visible
        // indefinitely after the player/browser session disappeared.
        boolean hadArtwork = lastArtworkBytes != null || coverIdentifier != null || coverTexture != null;
        lastArtworkBytes = null;
        if (hadArtwork) {
            updateArtwork(null);
        }
    }

    private void updateArtwork(byte[] imageBytes) {
        long generation = artworkGeneration.incrementAndGet();

        if (imageBytes == null) {
            mediaColor = ColorUtil.WHITE;
            mc.execute(() -> {
                if (generation != artworkGeneration.get()) {
                    return;
                }
                DynamicTexture old = coverTexture;
                coverTexture = null;
                coverIdentifier = null;
                if (old != null) {
                    try {
                        old.close();
                    } catch (Throwable ignored) {
                    }
                }
            });
            return;
        }

        // Never decode/resize artwork on the SMTC poller or render thread. Some
        // players expose 1000-3000px covers; ImageIO + NativeImage conversion and
        // uploading those full-size textures caused a visible frame-time spike on
        // every track change. Keep only a compact GPU texture: the HUD displays the
        // cover at ~44 physical pixels at 100%, so 128px leaves ample headroom.
        byte[] bytes = imageBytes.clone();
        try {
            artworkExecutor.execute(() -> prepareArtwork(bytes, generation));
        } catch (RuntimeException ignored) {
        }
    }

    private void prepareArtwork(byte[] imageBytes, long generation) {
        if (generation != artworkGeneration.get() || !running) {
            return;
        }
        NativeImage nativeImage = null;
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (decoded == null || generation != artworkGeneration.get() || !running) {
                return;
            }

            int average = averageColor(decoded);
            BufferedImage uploadImage = scaleArtworkForGpu(decoded, 128);
            nativeImage = toNativeImage(uploadImage);
            NativeImage preparedImage = nativeImage;
            nativeImage = null;

            if (generation != artworkGeneration.get() || !running) {
                preparedImage.close();
                return;
            }

            mc.execute(() -> applyPreparedArtwork(preparedImage, average, generation));
        } catch (Throwable ignored) {
            if (nativeImage != null) {
                try {
                    nativeImage.close();
                } catch (Throwable ignoredClose) {
                }
            }
        }
    }

    private void applyPreparedArtwork(NativeImage nativeImage, int average, long generation) {
        if (generation != artworkGeneration.get() || !running) {
            try {
                nativeImage.close();
            } catch (Throwable ignored) {
            }
            return;
        }

        try {
            DynamicTexture old = coverTexture;
            if (old != null) {
                try {
                    old.close();
                } catch (Throwable ignored) {
                }
            }
            DynamicTexture replacement = new DynamicTexture(() -> "UNIVERSALMOD_music_player_artwork", nativeImage);
            coverTexture = replacement;
            coverIdentifier = COVER_ID;
            mediaColor = average;
            mc.getTextureManager().register(COVER_ID, replacement);
        } catch (Throwable throwable) {
            try {
                nativeImage.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static BufferedImage scaleArtworkForGpu(BufferedImage source, int maxSize) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxSize && height <= maxSize) {
            return source;
        }

        double scale = Math.min((double) maxSize / width, (double) maxSize / height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    public void togglePlay() {
        IMediaSession current = session;
        if (current == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long visiblePositionMs = interpolatedPositionMs(now);
        boolean newState = !playing;

        // Preserve exactly what the HUD was showing before changing state and restart
        // interpolation from now. Reusing an old lastSyncTime caused the +/- several
        // second jump when Play/Pause was pressed.
        positionMs = visiblePositionMs;
        lastSyncTime = now;
        playing = newState;
        lastInteractionTime = now;
        runAction(() -> {
            try {
                current.playPause();
            } catch (Throwable first) {
                try {
                    if (newState) {
                        current.play();
                    } else {
                        current.pause();
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    public void previousTrack() {
        IMediaSession current = session;
        if (current == null) {
            return;
        }
        lastInteractionTime = System.currentTimeMillis();
        runAction(() -> {
            try {
                current.previous();
            } catch (Throwable ignored) {
            }
        });
    }

    public void nextTrack() {
        IMediaSession current = session;
        if (current == null) {
            return;
        }
        lastInteractionTime = System.currentTimeMillis();
        runAction(() -> {
            try {
                current.next();
            } catch (Throwable ignored) {
            }
        });
    }

    private void runAction(Runnable action) {
        if (!running || actionExecutor.isShutdown()) {
            return;
        }
        try {
            actionExecutor.execute(action);
        } catch (RuntimeException ignored) {
        }
    }

    public boolean haveActiveSession() {
        ensureStarted();
        return running && session != null && session.getMedia() != null;
    }

    public IMediaSession getSession() {
        ensureStarted();
        return session;
    }

    public boolean isPlaying() {
        ensureStarted();
        return playing;
    }

    public String title() {
        ensureStarted();
        return currentTitle;
    }

    public String author() {
        ensureStarted();
        return currentAuthor;
    }

    public double durationSeconds() {
        ensureStarted();
        return durationSeconds;
    }

    public double positionSeconds() {
        ensureStarted();
        return interpolatedPositionMs(System.currentTimeMillis()) / 1000.0D;
    }

    private long interpolatedPositionMs(long now) {
        long current = Math.max(0L, positionMs);
        if (playing && lastSyncTime > 0L && now > lastSyncTime) {
            current += now - lastSyncTime;
        }
        if (durationSeconds > 0.0D) {
            current = Math.min(current, Math.max(0L, Math.round(durationSeconds * 1000.0D)));
        }
        return current;
    }

    public float progress() {
        double duration = durationSeconds();
        if (duration <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, positionSeconds() / duration));
    }

    public Identifier getImage() {
        ensureStarted();
        return coverIdentifier;
    }

    public int getMediaColor() {
        return mediaColor;
    }

    public String getSourceAppId() {
        ensureStarted();
        return currentSourceAppId;
    }

    public String getSourceAppName() {
        ensureStarted();
        return currentSourceAppName;
    }

    public String getSourceName() {
        ensureStarted();
        return currentSourceName;
    }

    public String getSourceType() {
        ensureStarted();
        return currentSourceType;
    }

    public String getServiceName() {
        ensureStarted();
        return currentServiceName;
    }

    public boolean isRepeatSupported() {
        ensureStarted();
        return currentRepeatSupported;
    }

    /** -1 unsupported/unknown, 0 off, 1 track, 2 list. */
    public int getRepeatMode() {
        ensureStarted();
        return currentRepeatMode;
    }

    public void cycleRepeat() {
        IMediaSession current = session;
        if (current == null || !currentRepeatSupported) {
            return;
        }
        runAction(() -> {
            try {
                current.swapCycle();
                currentRepeatMode = current.getCycleType();
            } catch (Throwable ignored) {
            }
        });
    }

    /** Sets repeat directly: 0 = off, 1 = track, 2 = list. */
    public void setRepeatMode(int mode) {
        IMediaSession current = session;
        if (current == null || !currentRepeatSupported || mode < 0 || mode > 2) {
            return;
        }
        runAction(() -> {
            try {
                if (current.setCycleType(mode)) {
                    currentRepeatMode = mode;
                } else {
                    currentRepeatMode = current.getCycleType();
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public String getLyrics() {
        ensureStarted();
        return lyrics == null ? "" : lyrics;
    }

    public double getSyncedPositionSeconds() {
        return positionSeconds();
    }

    public void shutdown() {
        running = false;
        artworkGeneration.incrementAndGet();
        actionExecutor.shutdownNow();
        artworkExecutor.shutdownNow();

        Thread currentWorker = worker;
        if (currentWorker != null) {
            currentWorker.interrupt();
        }

        // Close the JNI bridge first. The new DLL owns no process-global SMTC
        // manager/session objects, so this is safe even if a daemon call is finishing.
        try {
            MediaPlayerInfo.INSTANCE.cleanup();
        } catch (Throwable ignored) {
        }

        if (currentWorker != null && currentWorker.isAlive()) {
            try {
                currentWorker.join(250L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            actionExecutor.awaitTermination(100L, TimeUnit.MILLISECONDS);
            artworkExecutor.awaitTermination(100L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        DynamicTexture texture = coverTexture;
        coverTexture = null;
        coverIdentifier = null;
        if (texture != null) {
            try {
                texture.close();
            } catch (Throwable ignored) {
            }
        }
        session = null;
    }

    private static NativeImage toNativeImage(BufferedImage source) {
        NativeImage image = new NativeImage(source.getWidth(), source.getHeight(), true);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                image.setPixelABGR(x, y, argbToAbgr(source.getRGB(x, y)));
            }
        }
        return image;
    }

    private static int averageColor(BufferedImage image) {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        long alpha = 0L;
        int pixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                alpha += a;
                red += (argb >>> 16) & 0xFF;
                green += (argb >>> 8) & 0xFF;
                blue += argb & 0xFF;
                pixels++;
            }
        }
        if (pixels == 0) {
            return ColorUtil.WHITE;
        }
        return ColorUtil.rgba(
                (int) (red / pixels),
                (int) (green / pixels),
                (int) (blue / pixels),
                (int) (alpha / pixels)
        );
    }

    private static int argbToAbgr(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
