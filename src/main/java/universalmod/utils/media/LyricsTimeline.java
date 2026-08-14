package universalmod.utils.media;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsTimeline {
    private static final double UNSYNCED_START_PADDING = 8.0D;
    private static final double UNSYNCED_END_PADDING = 8.0D;
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private LyricsTimeline() {
    }

    public static List<Line> parse(String lyrics, long duration) {
        String normalized = stripHeader(lyrics);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<Line> timed = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        for (String rawLine : normalized.lines().toList()) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }

            Matcher matcher = TIMESTAMP.matcher(line);
            List<Double> timestamps = new ArrayList<>();
            while (matcher.find()) {
                timestamps.add(toSeconds(matcher));
            }

            String text = matcher.replaceAll("").trim();
            if (text.isBlank()) {
                continue;
            }
            if (timestamps.isEmpty()) {
                plain.add(text);
                continue;
            }
            for (Double time : timestamps) {
                timed.add(new Line(time, text));
            }
        }

        if (!timed.isEmpty()) {
            timed.sort(Comparator.comparingDouble(Line::time));
            return timed;
        }

        double safeDuration = Math.max(1.0D, duration);
        double usableDuration = Math.max(1.0D, safeDuration - UNSYNCED_START_PADDING - UNSYNCED_END_PADDING);
        List<Line> approximated = new ArrayList<>();
        for (int i = 0; i < plain.size(); i++) {
            double time = plain.size() <= 1 ? 0.0D : UNSYNCED_START_PADDING + (usableDuration * i) / Math.max(1, plain.size() - 1);
            approximated.add(new Line(time, plain.get(i)));
        }
        return approximated;
    }

    public static int activeIndex(List<Line> lines, double position) {
        if (lines == null || lines.isEmpty()) {
            return -1;
        }
        int active = 0;
        double safePosition = Math.max(0.0D, position);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).time() <= safePosition) {
                active = i;
            } else {
                break;
            }
        }
        return active;
    }

    public static String activeText(List<Line> lines, double position) {
        int index = activeIndex(lines, position);
        return index < 0 ? "" : lines.get(index).text();
    }

    private static String stripHeader(String lyrics) {
        if (lyrics == null || lyrics.isBlank()) {
            return "";
        }
        String normalized = lyrics.replace("\r\n", "\n").replace('\r', '\n');
        int contributors = normalized.indexOf("butors\n\n");
        if (contributors >= 0) {
            normalized = normalized.substring(contributors + "butors\n\n".length());
        }
        return normalized.trim();
    }

    private static double toSeconds(Matcher matcher) {
        double minutes = Double.parseDouble(matcher.group(1));
        double seconds = Double.parseDouble(matcher.group(2));
        String fraction = matcher.group(3);
        double fractional = 0.0D;
        if (fraction != null && !fraction.isBlank()) {
            fractional = Double.parseDouble("0." + fraction);
        }
        return minutes * 60.0D + seconds + fractional;
    }

    public record Line(double time, String text) {
    }
}
