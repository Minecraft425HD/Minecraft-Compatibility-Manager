package dev.compatmanager.standalone.api;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;

/** Persistent on-disk cache for API responses with TTL. */
public final class ApiCache {

    public static final Path CACHE_DIR =
            Path.of(System.getProperty("user.home"), ".compatmanager");
    private static final long TTL_SECONDS = 24L * 60 * 60; // 24 h

    private ApiCache() {}

    public static boolean isFresh(String key) {
        Path ts = CACHE_DIR.resolve(key + ".ts");
        if (!ts.toFile().exists()) return false;
        try {
            long saved = Long.parseLong(Files.readString(ts).trim());
            return Instant.now().getEpochSecond() - saved < TTL_SECONDS;
        } catch (Exception e) {
            return false;
        }
    }

    public static String read(String key) throws IOException {
        return Files.readString(CACHE_DIR.resolve(key + ".json"));
    }

    public static void write(String key, String json) throws IOException {
        Files.createDirectories(CACHE_DIR);
        Files.writeString(CACHE_DIR.resolve(key + ".json"), json);
        Files.writeString(CACHE_DIR.resolve(key + ".ts"),
                String.valueOf(Instant.now().getEpochSecond()));
    }

    public static void invalidate(String key) {
        try {
            Files.deleteIfExists(CACHE_DIR.resolve(key + ".json"));
            Files.deleteIfExists(CACHE_DIR.resolve(key + ".ts"));
        } catch (IOException ignored) {}
    }
}
