package dev.redstones.mediaplayerinfo;

import java.util.Locale;

/**
 * Java-side fallback for older native DLLs. The new DLL fills richer source fields itself,
 * including album/subtitle based detection; this fallback only has owner/title/artist.
 */
public final class MediaSourceResolver {
    private MediaSourceResolver() {
    }

    public static SourceDetails resolve(String owner, MediaInfo media) {
        String rawOwner = safe(owner);
        String ownerLower = rawOwner.toLowerCase(Locale.ROOT);
        String title = media == null ? "" : safe(media.getTitle());
        String artist = media == null ? "" : safe(media.getArtist());
        String metadata = (title + " | " + artist).toLowerCase(Locale.ROOT);

        String directService = directService(ownerLower);
        if (!directService.isEmpty()) {
            return new SourceDetails(rawOwner, directService, directService, "service", directService);
        }

        String browser = browser(ownerLower);
        if (!browser.isEmpty()) {
            String service = metadataService(metadata);
            String display = service.isEmpty() ? browser : service + " · " + browser;
            return new SourceDetails(rawOwner, browser, display, service.isEmpty() ? "browser" : "browser-site", service);
        }

        String player = localPlayer(ownerLower);
        if (!player.isEmpty()) {
            return new SourceDetails(rawOwner, player, player, "player", "");
        }

        String appName = stripExecutable(rawOwner);
        if (appName.isBlank()) {
            appName = "Unknown media source";
        }
        return new SourceDetails(rawOwner, appName, appName, "app", "");
    }

    private static String browser(String owner) {
        if (containsAny(owner, "msedge", "microsoftedge")) return "Microsoft Edge";
        if (containsAny(owner, "chrome", "googlechrome")) return "Google Chrome";
        if (owner.contains("firefox")) return "Mozilla Firefox";
        if (owner.contains("brave")) return "Brave";
        if (owner.contains("vivaldi")) return "Vivaldi";
        if (containsAny(owner, "yandexbrowser", "yandex.browser", "browser.exe")) return "Yandex Browser";
        if (owner.contains("opera")) return "Opera";
        if (containsAny(owner, "arc.exe", "thebrowsercompany")) return "Arc";
        if (owner.contains("floorp")) return "Floorp";
        if (containsAny(owner, "zen.exe", "zen-browser")) return "Zen Browser";
        return "";
    }

    private static String directService(String owner) {
        if (owner.contains("spotify")) return "Spotify";
        if (containsAny(owner, "yandexmusic", "yandex.music", "music.yandex")) return "Yandex Music";
        if (owner.contains("deezer")) return "Deezer";
        if (owner.contains("tidal")) return "TIDAL";
        if (containsAny(owner, "applemusic", "apple.music", "itunes")) return "Apple Music";
        if (owner.contains("amazonmusic")) return "Amazon Music";
        if (owner.contains("soundcloud")) return "SoundCloud";
        if (containsAny(owner, "vkmusic", "vk.music", "boom.exe")) return "VK Music";
        if (owner.contains("youtube")) return "YouTube";
        return "";
    }

    private static String localPlayer(String owner) {
        if (owner.contains("vlc")) return "VLC";
        if (owner.contains("foobar")) return "foobar2000";
        if (owner.contains("aimp")) return "AIMP";
        if (owner.contains("winamp")) return "Winamp";
        if (owner.contains("musicbee")) return "MusicBee";
        if (containsAny(owner, "zunemusic", "microsoft.zunemusic", "media player")) return "Windows Media Player";
        return "";
    }

    private static String metadataService(String metadata) {
        if (containsAny(metadata, "music.youtube.com", "youtube music")) return "YouTube Music";
        if (containsAny(metadata, "youtube.com", "youtu.be", "youtube")) return "YouTube";
        if (containsAny(metadata, "music.yandex.ru", "music.yandex.com", "яндекс музыка", "yandex music")) return "Yandex Music";
        if (containsAny(metadata, "open.spotify.com", "spotify")) return "Spotify";
        if (containsAny(metadata, "soundcloud.com", "soundcloud")) return "SoundCloud";
        if (containsAny(metadata, "vk.com", "vk music", "вк музыка")) return "VK Music";
        if (containsAny(metadata, "music.apple.com", "apple music")) return "Apple Music";
        if (containsAny(metadata, "deezer.com", "deezer")) return "Deezer";
        if (containsAny(metadata, "tidal.com", "tidal")) return "TIDAL";
        if (containsAny(metadata, "bandcamp.com", "bandcamp")) return "Bandcamp";
        if (containsAny(metadata, "twitch.tv", "twitch")) return "Twitch";
        if (containsAny(metadata, "mixcloud.com", "mixcloud")) return "Mixcloud";
        if (containsAny(metadata, "amazon music", "music.amazon.")) return "Amazon Music";
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

    private static String stripExecutable(String value) {
        String result = safe(value).trim();
        int slash = Math.max(result.lastIndexOf('/'), result.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < result.length()) {
            result = result.substring(slash + 1);
        }
        if (result.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            result = result.substring(0, result.length() - 4);
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record SourceDetails(
            String appId,
            String appName,
            String sourceName,
            String sourceType,
            String serviceName) {
    }
}
