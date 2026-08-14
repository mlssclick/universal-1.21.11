package dev.redstones.mediaplayerinfo.impl.win;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaSourceResolver;

public class WindowsMediaSession implements IMediaSession {
    private final MediaInfo media;
    private final String owner;
    private final int index;
    private static int cycle = -1;

    // Filled by the new native DLL through JNI. Defaults keep old DLLs ABI-compatible.
    private String sourceAppId;
    private String sourceAppName;
    private String sourceName;
    private String sourceType;
    private String serviceName;
    private boolean repeatSupported;

    public WindowsMediaSession(MediaInfo media, String owner, int index) {
        this.media = media;
        this.owner = owner == null ? "" : owner;
        this.index = index;
        MediaSourceResolver.SourceDetails source = MediaSourceResolver.resolve(this.owner, media);
        this.sourceAppId = source.appId();
        this.sourceAppName = source.appName();
        this.sourceName = source.sourceName();
        this.sourceType = source.sourceType();
        this.serviceName = source.serviceName();
    }

    @Override
    public native void play();

    @Override
    public native void pause();

    @Override
    public native void playPause();

    @Override
    public native void stop();

    @Override
    public native void next();

    @Override
    public native void previous();

    @Override
    public native void swapCycle();

    @Override
    public native int getCycleType();

    @Override
    public native boolean setCycleType(int mode);

    @Override
    public MediaInfo getMedia() {
        return this.media;
    }

    @Override
    public String getOwner() {
        return this.owner;
    }

    @Override
    public String getSourceAppId() {
        return nonBlank(sourceAppId, owner);
    }

    @Override
    public String getSourceAppName() {
        return nonBlank(sourceAppName, getSourceAppId());
    }

    @Override
    public String getSourceName() {
        return nonBlank(sourceName, getSourceAppName());
    }

    @Override
    public String getSourceType() {
        return nonBlank(sourceType, "app");
    }

    @Override
    public String getServiceName() {
        return serviceName == null ? "" : serviceName;
    }

    @Override
    public boolean isRepeatSupported() {
        if (repeatSupported) {
            return true;
        }
        try {
            return getCycleType() >= 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public int getIndex() {
        return index;
    }

    public static int getCycle() {
        return cycle;
    }

    public static void setCycle(int cycle) {
        WindowsMediaSession.cycle = cycle;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? (fallback == null ? "" : fallback) : value;
    }
}
