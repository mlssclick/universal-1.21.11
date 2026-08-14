package universalmod.api.module.impl.other;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CoinRateService {
    public static final String API_URL = "https://api.holyworld.me/v1/coins-trades?limit=5";
    public static final long UPDATE_INTERVAL_SECONDS = 60L;

    private static final Pattern RATE_PATTERN = Pattern.compile(
            "\\\"rate\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "CoinPrice-API");
                    thread.setDaemon(true);
                    return thread;
                }
            }
    );

    private static final AtomicBoolean REQUEST_RUNNING = new AtomicBoolean(false);
    private static volatile boolean started;
    private static volatile double averageRate;
    private static volatile long lastSuccessfulUpdate;

    private CoinRateService() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        EXECUTOR.scheduleAtFixedRate(
                CoinRateService::fetchAndUpdate,
                0L,
                UPDATE_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public static void refreshNow() {
        if (started) {
            EXECUTOR.execute(CoinRateService::fetchAndUpdate);
        }
    }

    public static double getAverageRate() {
        return averageRate;
    }

    public static long getLastSuccessfulUpdate() {
        return lastSuccessfulUpdate;
    }

    static void fetchAndUpdate() {
        if (!CoinPrice.isActive() || !REQUEST_RUNNING.compareAndSet(false, true)) {
            return;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "CoinPrice/1.0.0");

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("API returned HTTP " + status);
            }

            String response;
            try (InputStream stream = connection.getInputStream()) {
                response = readUtf8(stream);
            }

            averageRate = calculateAverageRate(response);
            lastSuccessfulUpdate = System.currentTimeMillis();
        } catch (Exception exception) {
            System.err.println("[CoinPrice] Rate update failed: " + exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            REQUEST_RUNNING.set(false);
        }
    }

    static double calculateAverageRate(String json) {
        Matcher matcher = RATE_PATTERN.matcher(json);
        double sum = 0.0D;
        int count = 0;

        while (matcher.find()) {
            double rate = Double.parseDouble(matcher.group(1));
            if (rate > 0.0D && !Double.isNaN(rate) && !Double.isInfinite(rate)) {
                sum += rate;
                count++;
            }
        }

        if (count == 0) {
            throw new IllegalArgumentException("API response contains no valid rate values");
        }
        return sum / count;
    }

    private static String readUtf8(InputStream stream) throws IOException {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[2048];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                result.append(buffer, 0, read);
            }
        }
        return result.toString();
    }
}
