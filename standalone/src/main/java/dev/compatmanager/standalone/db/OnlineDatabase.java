package dev.compatmanager.standalone.db;

import com.google.gson.*;
import dev.compatmanager.standalone.api.ApiCache;
import dev.compatmanager.standalone.StandaloneIssue.Severity;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * Supplements ModDatabase with an auto-updating online JSON feed.
 * URL: hosted at the project's GitHub repo (raw content).
 * Falls back to the bundled database on any network error.
 *
 * Format of online-db.json:
 * {
 *   "version": 1,
 *   "pairs": [
 *     {"modA":"x","modB":"y","severity":"HIGH","category":"Rendering",
 *      "mcVersionMin":null,"mcVersionMax":null,
 *      "reason":"...","patchMod":null}
 *   ]
 * }
 */
public final class OnlineDatabase {

    public static final String DB_URL =
            "https://raw.githubusercontent.com/minecraft425hd/minecraft-compatibility-manager/main/online-db.json";
    private static final String CACHE_KEY = "online-db";
    private static final Gson   GSON      = new GsonBuilder().create();
    private static final HttpClient HTTP  = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static List<ModDatabase.IncompatPair> cachedExtra = null;

    private OnlineDatabase() {}

    /** Download a fresh copy and store it in the disk cache. */
    public static boolean update() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(DB_URL))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return false;
            ApiCache.invalidate(CACHE_KEY);
            ApiCache.write(CACHE_KEY, resp.body());
            cachedExtra = null;
            System.out.println("[DB] Online database updated successfully.");
            return true;
        } catch (Exception e) {
            System.err.println("[DB] Could not update online database: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns extra pairs from the online DB (loaded from disk cache if fresh).
     * Returns an empty list when offline or on any error.
     */
    public static List<ModDatabase.IncompatPair> getExtraPairs(boolean offline) {
        if (offline) return List.of();
        if (cachedExtra != null) return cachedExtra;

        String json = null;
        if (ApiCache.isFresh(CACHE_KEY)) {
            try { json = ApiCache.read(CACHE_KEY); } catch (Exception ignored) {}
        }

        if (json == null) {
            // Try to fetch fresh copy silently
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(DB_URL))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    json = resp.body();
                    try { ApiCache.write(CACHE_KEY, json); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        if (json == null) { cachedExtra = List.of(); return cachedExtra; }

        cachedExtra = parse(json);
        return cachedExtra;
    }

    private static List<ModDatabase.IncompatPair> parse(String json) {
        List<ModDatabase.IncompatPair> result = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (!root.has("pairs")) return List.of();
            for (JsonElement el : root.getAsJsonArray("pairs")) {
                JsonObject p = el.getAsJsonObject();
                String modA     = str(p, "modA", "");
                String modB     = str(p, "modB", "");
                String sevStr   = str(p, "severity", "MEDIUM");
                String cat      = str(p, "category", "Online");
                String minVer   = str(p, "mcVersionMin", null);
                String maxVer   = str(p, "mcVersionMax", null);
                String reason   = str(p, "reason", "Known incompatibility (online database).");
                String patch    = str(p, "patchMod", null);
                if (modA.isBlank() || modB.isBlank()) continue;
                Severity sev;
                try { sev = Severity.valueOf(sevStr); } catch (Exception e) { sev = Severity.MEDIUM; }
                result.add(new ModDatabase.IncompatPair(modA, modB, sev, cat, minVer, maxVer, reason, patch, null, null));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static String str(JsonObject o, String key, String def) {
        if (!o.has(key) || o.get(key).isJsonNull()) return def;
        return o.get(key).getAsString();
    }
}
