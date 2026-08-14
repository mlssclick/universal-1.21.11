package universalmod.utils.media;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsFetcher {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String GENIUS_TOKEN = "batnaM4ixvdL448SIofj6I6aqLsRZ2RuLowRA8tXoWYUAse55DoAX7Xf7MT0vjy5";

    private LyricsFetcher() {
    }

    public static String fetchFromGenius(String artist, String title) {
        if (artist == null || title == null || artist.isBlank() || title.isBlank()) {
            return null;
        }
        String lyrics = fetchSyncedFromLrcLib(artist, title);
        if (!isBlank(lyrics)) {
            return normalizeLyrics(lyrics);
        }

        lyrics = null;
        try {
            SearchResult result = search(artist, title);
            if (result != null) {
                lyrics = fetchBySongId(result.id());
                if (lyrics == null && result.url() != null) {
                    lyrics = fetchFromPage(result.url());
                }
            }
        } catch (IOException exception) {
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
        }
        if (!isBlank(lyrics)) {
            return normalizeLyrics(lyrics);
        }

        lyrics = fetchPlainFromLrcLib(artist, title);
        if (!isBlank(lyrics)) {
            return normalizeLyrics(lyrics);
        }

        lyrics = fetchFromLyricsOvh(artist, title);
        return isBlank(lyrics) ? null : normalizeLyrics(lyrics);
    }

    private static SearchResult search(String artist, String title) throws IOException, InterruptedException {
        HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder()
                .uri(URI.create("https://api.genius.com/search?q=" + URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + GENIUS_TOKEN)
                .header("Accept", "application/json")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject meta = root.getAsJsonObject("meta");
        JsonObject responseObject = root.getAsJsonObject("response");
        if (meta != null && meta.has("status") && meta.get("status").getAsInt() != 200) {
            return null;
        }
        if (responseObject == null || !responseObject.has("hits")) {
            return null;
        }

        Iterator<JsonElement> iterator = responseObject.getAsJsonArray("hits").iterator();
        while (iterator.hasNext()) {
            JsonObject result = iterator.next().getAsJsonObject().getAsJsonObject("result");
            if (result != null && result.has("url") && result.has("id")) {
                return new SearchResult(result.get("url").getAsString(), result.get("id").getAsInt());
            }
        }
        return null;
    }

    private static String fetchBySongId(int id) throws IOException, InterruptedException {
        HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder()
                .uri(URI.create("https://api.genius.com/songs/" + id + "?text_format=plain"))
                .header("Authorization", "Bearer " + GENIUS_TOKEN)
                .header("Accept", "application/json")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }

        JsonObject song = Optional.ofNullable(JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("response"))
                .map(object -> object.getAsJsonObject("song"))
                .orElse(null);
        if (song != null && song.has("lyrics")) {
            JsonObject lyrics = song.getAsJsonObject("lyrics");
            if (lyrics.has("plain")) {
                return lyrics.get("plain").getAsString();
            }
        }
        if (song == null || !song.has("lyrics_body")) {
            return null;
        }
        return song.get("lyrics_body").getAsString();
    }

    private static String fetchFromPage(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        Matcher matcher = Pattern.compile("(<div[^>]*class=\"[^\"]*Lyrics__Container[^\"]*\"[^>]*>.*?</div>)", Pattern.DOTALL)
                .matcher(response.body());
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String text = matcher.group(1)
                    .replaceAll("<br\\s*/?>", "\n")
                    .replaceAll("<.*?>", "")
                    .replaceAll("&quot;", "\"")
                    .trim();
            if (!text.isEmpty()) {
                builder.append(text).append("\n\n");
            }
        }
        return builder.isEmpty() ? null : builder.toString().trim();
    }

    private static String fetchSyncedFromLrcLib(String artist, String title) {
        return fetchFromLrcLib(artist, title, true);
    }

    private static String fetchPlainFromLrcLib(String artist, String title) {
        return fetchFromLrcLib(artist, title, false);
    }

    private static String fetchFromLrcLib(String artist, String title, boolean syncedOnly) {
        try {
            HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder()
                    .uri(URI.create("https://lrclib.net/api/search?artist_name=" + URLEncoder.encode(artist, StandardCharsets.UTF_8)
                            + "&track_name=" + URLEncoder.encode(title, StandardCharsets.UTF_8)))
                    .header("Accept", "application/json")
                    .header("User-Agent", "UniversalMod/1.0")
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            JsonArray results = JsonParser.parseString(response.body()).getAsJsonArray();
            for (JsonElement element : results) {
                JsonObject object = element.getAsJsonObject();
                if (object == null || object.has("instrumental") && object.get("instrumental").getAsBoolean()) {
                    continue;
                }
                String synced = stringValue(object, "syncedLyrics");
                if (!isBlank(synced)) {
                    return synced;
                }
                if (syncedOnly) {
                    continue;
                }
                String plain = stringValue(object, "plainLyrics");
                if (!isBlank(plain)) {
                    return plain;
                }
            }
        } catch (IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
            return null;
        }
        return null;
    }

    private static String fetchFromLyricsOvh(String artist, String title) {
        try {
            HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder()
                    .uri(URI.create("https://api.lyrics.ovh/v1/" + URLEncoder.encode(artist, StandardCharsets.UTF_8)
                            + "/" + URLEncoder.encode(title, StandardCharsets.UTF_8)))
                    .header("Accept", "application/json")
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            return stringValue(root, "lyrics");
        } catch (IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static String normalizeLyrics(String lyrics) {
        return lyrics
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?m)^\\s*$\\n?", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String stringValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SearchResult(String url, int id) {
    }
}
