package dev.redstones.mediaplayerinfo;

import dev.redstones.mediaplayerinfo.impl.DummyMediaPlayerInfo;
import dev.redstones.mediaplayerinfo.impl.win.WindowsMediaPlayerInfo;

import java.util.List;

public interface MediaPlayerInfo {
    MediaPlayerInfo INSTANCE = SystemMediaPlayerInfo.getInstance();

    List<IMediaSession> getMediaSessions();

    default void cleanup() {
    }

    final class SystemMediaPlayerInfo {
        private static final MediaPlayerInfo INSTANCE = create();

        private SystemMediaPlayerInfo() {
        }

        public static MediaPlayerInfo getInstance() {
            return INSTANCE;
        }

        private static MediaPlayerInfo create() {
            if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
                return new WindowsMediaPlayerInfo();
            }
            return new DummyMediaPlayerInfo();
        }
    }
}
