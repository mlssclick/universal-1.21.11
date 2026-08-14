package dev.redstones.mediaplayerinfo.impl.win;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class WindowsMediaPlayerInfo implements MediaPlayerInfo {
    @Override
    public native List<IMediaSession> getMediaSessions();

    @Override
    public native void cleanup();

    static {
        try {
            byte[] dllBytes;
            try (InputStream stream = WindowsMediaPlayerInfo.class.getResourceAsStream(
                    "/mediaplayerinfo/natives/win/MediaPlayerInfo.dll")) {
                if (stream == null) {
                    throw new IOException("Resource not found: /mediaplayerinfo/natives/win/MediaPlayerInfo.dll");
                }
                dllBytes = stream.readAllBytes();
            }

            String fingerprint = sha256(dllBytes).substring(0, 16);
            Path cacheDirectory = Path.of(System.getProperty("java.io.tmpdir"), "universalmod-mediaplayerinfo");
            Files.createDirectories(cacheDirectory);
            Path dllPath = cacheDirectory.resolve("MediaPlayerInfo-" + fingerprint + ".dll");

            if (!Files.exists(dllPath)) {
                Files.write(dllPath, dllBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            System.load(dllPath.toAbsolutePath().toString());

            // Old versions are best-effort cleanup only. A DLL used by another running
            // Minecraft instance is locked by Windows and is intentionally left alone.
            try (var files = Files.list(cacheDirectory)) {
                files.filter(path -> !path.equals(dllPath))
                        .filter(path -> path.getFileName().toString().startsWith("MediaPlayerInfo-"))
                        .filter(path -> path.getFileName().toString().endsWith(".dll"))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            } catch (IOException ignored) {
            }
        } catch (IOException exception) {
            throw new RuntimeException("Failed to load MediaPlayerInfo.dll", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
