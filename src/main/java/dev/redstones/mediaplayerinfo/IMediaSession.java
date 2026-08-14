package dev.redstones.mediaplayerinfo;

public interface IMediaSession {
    String getOwner();

    MediaInfo getMedia();

    void play();

    void pause();

    void playPause();

    void stop();

    void next();

    void previous();

    void swapCycle();

    int getCycleType();

    default boolean setCycleType(int mode) {
        return false;
    }

    default String getSourceAppId() {
        return getOwner();
    }

    default String getSourceAppName() {
        return getOwner();
    }

    default String getSourceName() {
        return getSourceAppName();
    }

    default String getSourceType() {
        return "app";
    }

    default String getServiceName() {
        return "";
    }

    default boolean isRepeatSupported() {
        try {
            return getCycleType() >= 0;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
