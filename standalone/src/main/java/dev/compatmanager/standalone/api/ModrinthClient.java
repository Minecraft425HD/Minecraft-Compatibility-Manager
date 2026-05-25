package dev.compatmanager.standalone.api;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Modrinth API v2 client with 24-hour disk cache. */
public final class ModrinthClient {

    private static final String BASE = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "CompatManager-Standalone/1.0 (github.com/minecraft425hd/minecraft-compatibility-manager)";
    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ── Public data types ─────────────────────────────────────────────────

    public record ModrinthProject(
            String id, String slug, String title,
            List<String> loaders, List<String> gameVersions,
            String projectType, String clientSide, String serverSide
    ) {}

    public record ModrinthDependency(
            String projectId, String versionId, String dependencyType
    ) {}

    private ModrinthClient() {}

    // ── Batch project fetch ───────────────────────────────────────────────

    /**
     * Fetches metadata for up to 100 project slugs/IDs at once.
     * Returns an empty list on network failure (graceful degradation).
     */
    public static List<ModrinthProject> fetchProjects(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        String cacheKey = "modrinth-projects-" + Math.abs(ids.toString().hashCode());
        if (ApiCache.isFresh(cacheKey)) {
            try {
                return GSON.fromJson(ApiCache.read(cacheKey),
                        new TypeToken<List<ModrinthProject>>() {}.getType());
            } catch (Exception ignored) {}
        }

        try {
            String idsJson = GSON.toJson(ids);
            String encoded = URLEncoder.encode(idsJson, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/projects?ids=" + encoded))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return List.of();

            List<ModrinthProject> result = parseProjects(resp.body());
            ApiCache.write(cacheKey, GSON.toJson(result));
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Fetches declared incompatible dependencies for a single project. */
    public static List<ModrinthDependency> fetchDependencies(String projectId) {
        String cacheKey = "modrinth-deps-" + projectId;
        if (ApiCache.isFresh(cacheKey)) {
            try {
                return GSON.fromJson(ApiCache.read(cacheKey),
                        new TypeToken<List<ModrinthDependency>>() {}.getType());
            } catch (Exception ignored) {}
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/project/" + projectId + "/dependencies"))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return List.of();

            List<ModrinthDependency> deps = parseDependencies(resp.body());
            ApiCache.write(cacheKey, GSON.toJson(deps));
            return deps;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────

    private static List<ModrinthProject> parseProjects(String json) {
        List<ModrinthProject> result = new ArrayList<>();
        try {
            JsonArray arr = GSON.fromJson(json, JsonArray.class);
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                result.add(new ModrinthProject(
                        strOr(obj, "id", ""),
                        strOr(obj, "slug", ""),
                        strOr(obj, "title", ""),
                        strListOr(obj, "loaders"),
                        strListOr(obj, "game_versions"),
                        strOr(obj, "project_type", "mod"),
                        strOr(obj, "client_side", "unknown"),
                        strOr(obj, "server_side", "unknown")
                ));
            }
        } catch (Exception ignored) {}
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<ModrinthDependency> parseDependencies(String json) {
        List<ModrinthDependency> result = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            // Check version dependencies for "incompatible" type
            if (root.has("versions")) {
                for (JsonElement ver : root.getAsJsonArray("versions")) {
                    JsonObject vObj = ver.getAsJsonObject();
                    if (!vObj.has("dependencies")) continue;
                    for (JsonElement dep : vObj.getAsJsonArray("dependencies")) {
                        JsonObject d = dep.getAsJsonObject();
                        String type = strOr(d, "dependency_type", "");
                        if ("incompatible".equals(type)) {
                            result.add(new ModrinthDependency(
                                    strOr(d, "project_id", ""),
                                    strOr(d, "version_id", ""),
                                    type
                            ));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static String strOr(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    private static List<String> strListOr(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return List.of();
        List<String> list = new ArrayList<>();
        for (JsonElement el : o.getAsJsonArray(key)) list.add(el.getAsString());
        return list;
    }
}
