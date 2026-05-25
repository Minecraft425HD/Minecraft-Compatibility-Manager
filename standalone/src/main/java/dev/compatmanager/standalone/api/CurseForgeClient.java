package dev.compatmanager.standalone.api;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * CurseForge API v1 client.
 * Requires an API key from https://console.curseforge.com/
 * Pass via --curseforge-key or env var CF_API_KEY.
 */
public final class CurseForgeClient {

    private static final String BASE      = "https://api.curseforge.com/v1";
    private static final int    GAME_MC   = 432;
    private static final Gson   GSON      = new GsonBuilder().create();
    private static final HttpClient HTTP  = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record CurseForgeModDependency(int modId, int relationType) {
        // relationType: 1=EmbeddedLibrary, 2=OptionalDependency, 3=RequiredDependency,
        //               4=Tool, 5=Incompatible, 6=Include
        public boolean isIncompatible() { return relationType == 5; }
        public boolean isRequired()     { return relationType == 3; }
    }

    public record CurseForgeMod(int id, String slug, String name,
                                List<CurseForgeModDependency> latestDeps) {}

    private CurseForgeClient() {}

    public static String resolveKey(String cliKey) {
        if (cliKey != null && !cliKey.isBlank()) return cliKey;
        String env = System.getenv("CF_API_KEY");
        return env != null ? env : "";
    }

    /** Search for a mod by slug and return its latest dependency list. */
    public static Optional<CurseForgeMod> searchMod(String slug, String apiKey) {
        if (apiKey.isBlank()) return Optional.empty();
        String cacheKey = "cf-mod-" + slug;
        if (ApiCache.isFresh(cacheKey)) {
            try {
                return Optional.ofNullable(GSON.fromJson(ApiCache.read(cacheKey), CurseForgeMod.class));
            } catch (Exception ignored) {}
        }
        try {
            String url = BASE + "/mods/search?gameId=" + GAME_MC
                    + "&searchFilter=" + slug + "&pageSize=3";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-api-key", apiKey)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return Optional.empty();

            Optional<CurseForgeMod> mod = parseFirstMod(resp.body(), slug);
            mod.ifPresent(m -> {
                try { ApiCache.write(cacheKey, GSON.toJson(m)); } catch (Exception ignored) {}
            });
            return mod;
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Fetch mod by CurseForge numeric ID. */
    public static Optional<CurseForgeMod> fetchMod(int modId, String apiKey) {
        if (apiKey.isBlank()) return Optional.empty();
        String cacheKey = "cf-mod-id-" + modId;
        if (ApiCache.isFresh(cacheKey)) {
            try {
                return Optional.ofNullable(GSON.fromJson(ApiCache.read(cacheKey), CurseForgeMod.class));
            } catch (Exception ignored) {}
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/mods/" + modId))
                    .header("x-api-key", apiKey)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return Optional.empty();

            Optional<CurseForgeMod> mod = parseModFromData(resp.body());
            mod.ifPresent(m -> {
                try { ApiCache.write(cacheKey, GSON.toJson(m)); } catch (Exception ignored) {}
            });
            return mod;
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<CurseForgeMod> parseFirstMod(String json, String expectedSlug) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (!root.has("data")) return Optional.empty();
            JsonArray data = root.getAsJsonArray("data");
            if (data.isEmpty()) return Optional.empty();
            for (JsonElement el : data) {
                JsonObject obj = el.getAsJsonObject();
                String slug = obj.has("slug") ? obj.get("slug").getAsString() : "";
                if (!slug.equalsIgnoreCase(expectedSlug)) continue;
                return Optional.of(parseMod(obj));
            }
            return Optional.of(parseMod(data.get(0).getAsJsonObject()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<CurseForgeMod> parseModFromData(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (!root.has("data")) return Optional.empty();
            return Optional.of(parseMod(root.getAsJsonObject("data")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static CurseForgeMod parseMod(JsonObject obj) {
        int id = obj.has("id") ? obj.get("id").getAsInt() : 0;
        String slug = obj.has("slug") ? obj.get("slug").getAsString() : "";
        String name = obj.has("name") ? obj.get("name").getAsString() : slug;

        List<CurseForgeModDependency> deps = new ArrayList<>();
        if (obj.has("latestFiles") && !obj.get("latestFiles").isJsonNull()) {
            JsonArray files = obj.getAsJsonArray("latestFiles");
            if (!files.isEmpty()) {
                JsonObject latest = files.get(0).getAsJsonObject();
                if (latest.has("dependencies")) {
                    for (JsonElement dep : latest.getAsJsonArray("dependencies")) {
                        JsonObject d = dep.getAsJsonObject();
                        int depId  = d.has("modId")        ? d.get("modId").getAsInt()        : 0;
                        int relType = d.has("relationType") ? d.get("relationType").getAsInt() : 0;
                        deps.add(new CurseForgeModDependency(depId, relType));
                    }
                }
            }
        }
        return new CurseForgeMod(id, slug, name, deps);
    }
}
