package dev.compatmanager.standalone.parser;

import com.google.gson.*;
import com.moandjiezana.toml.Toml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Reads a mod JAR file and extracts its metadata in a unified format.
 * Supports every mod metadata format:
 *   fabric.mod.json      (Fabric 1.14+)
 *   quilt.mod.json       (Quilt 1.18+)
 *   META-INF/mods.toml   (Forge 1.13+)
 *   META-INF/neoforge.mods.toml (NeoForge 1.20.2+)
 *   mcmod.info           (Forge 1.7 – 1.12.2)
 *   litemod.json         (LiteLoader)
 */
public class ModMetaParser {

    private final Gson gson = new GsonBuilder().setLenient().create();

    public Optional<UnifiedModMeta> parse(File jarFile) {
        try (ZipFile zip = new ZipFile(jarFile)) {
            // Priority order: try each format
            UnifiedModMeta meta = tryFabric(zip);
            if (meta == null) meta = tryQuilt(zip);
            if (meta == null) meta = tryNeoForge(zip);
            if (meta == null) meta = tryForgeToml(zip);
            if (meta == null) meta = tryForgeLegacy(zip);
            if (meta == null) meta = tryLiteLoader(zip);
            if (meta == null) meta = tryJarManifest(zip, jarFile.getName());
            return Optional.ofNullable(meta);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ── fabric.mod.json ───────────────────────────────────────────────────

    private UnifiedModMeta tryFabric(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("fabric.mod.json");
        if (entry == null) return null;

        JsonObject obj = gson.fromJson(read(zip, entry), JsonObject.class);
        String id      = getStr(obj, "id", "unknown");
        String version = getStr(obj, "version", "0.0.0");
        String name    = getStr(obj, "name", id);
        String desc    = getStr(obj, "description", "");

        List<UnifiedModMeta.DepEntry> deps = new ArrayList<>();
        parseFabricDeps(obj, "depends",    UnifiedModMeta.DepEntry.Kind.REQUIRED,    deps);
        parseFabricDeps(obj, "recommends", UnifiedModMeta.DepEntry.Kind.OPTIONAL,    deps);
        parseFabricDeps(obj, "breaks",     UnifiedModMeta.DepEntry.Kind.INCOMPATIBLE, deps);
        parseFabricDeps(obj, "conflicts",  UnifiedModMeta.DepEntry.Kind.INCOMPATIBLE, deps);

        String mcRange = extractFabricMcRange(obj);
        return new UnifiedModMeta(id, version, name, desc, "fabric", mcRange, deps);
    }

    private void parseFabricDeps(JsonObject obj, String key,
                                  UnifiedModMeta.DepEntry.Kind kind,
                                  List<UnifiedModMeta.DepEntry> out) {
        if (!obj.has(key)) return;
        JsonElement el = obj.get(key);
        if (el.isJsonObject()) {
            for (var e : el.getAsJsonObject().entrySet()) {
                out.add(new UnifiedModMeta.DepEntry(e.getKey(),
                        e.getValue().getAsString(), kind));
            }
        }
    }

    private String extractFabricMcRange(JsonObject obj) {
        if (obj.has("depends")) {
            JsonObject deps = obj.getAsJsonObject("depends");
            if (deps.has("minecraft")) return deps.get("minecraft").getAsString();
        }
        return "*";
    }

    // ── quilt.mod.json ────────────────────────────────────────────────────

    private UnifiedModMeta tryQuilt(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("quilt.mod.json");
        if (entry == null) return null;

        JsonObject root   = gson.fromJson(read(zip, entry), JsonObject.class);
        JsonObject loader = root.has("quilt_loader") ? root.getAsJsonObject("quilt_loader") : root;
        JsonObject meta   = loader.has("metadata")   ? loader.getAsJsonObject("metadata")   : new JsonObject();

        String id      = getStr(loader, "id", "unknown");
        String version = getStr(loader, "version", "0.0.0");
        String name    = getStr(meta,   "name", id);
        String desc    = getStr(meta,   "description", "");

        List<UnifiedModMeta.DepEntry> deps = new ArrayList<>();
        if (loader.has("depends")) {
            for (JsonElement dep : loader.getAsJsonArray("depends")) {
                parseQuiltDep(dep, UnifiedModMeta.DepEntry.Kind.REQUIRED, deps);
            }
        }
        if (loader.has("breaks")) {
            for (JsonElement dep : loader.getAsJsonArray("breaks")) {
                parseQuiltDep(dep, UnifiedModMeta.DepEntry.Kind.INCOMPATIBLE, deps);
            }
        }
        return new UnifiedModMeta(id, version, name, desc, "quilt", "*", deps);
    }

    private void parseQuiltDep(JsonElement el, UnifiedModMeta.DepEntry.Kind kind,
                                List<UnifiedModMeta.DepEntry> out) {
        if (el.isJsonObject()) {
            JsonObject o  = el.getAsJsonObject();
            String depId  = getStr(o, "id", "");
            String ver    = o.has("versions") ? o.get("versions").toString() : "*";
            if (!depId.isEmpty()) out.add(new UnifiedModMeta.DepEntry(depId, ver, kind));
        } else if (el.isJsonPrimitive()) {
            out.add(new UnifiedModMeta.DepEntry(el.getAsString(), "*", kind));
        }
    }

    // ── META-INF/neoforge.mods.toml ───────────────────────────────────────

    private UnifiedModMeta tryNeoForge(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/neoforge.mods.toml");
        if (entry == null) return null;
        return parseForgeToml(zip, entry, "neoforge");
    }

    // ── META-INF/mods.toml (Forge 1.13+) ─────────────────────────────────

    private UnifiedModMeta tryForgeToml(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/mods.toml");
        if (entry == null) return null;
        return parseForgeToml(zip, entry, "forge");
    }

    @SuppressWarnings("unchecked")
    private UnifiedModMeta parseForgeToml(ZipFile zip, ZipEntry entry, String loaderType) throws IOException {
        Toml toml = new Toml().read(read(zip, entry));

        List<Map<String, Object>> mods = (List<Map<String, Object>>) toml.getList("mods");
        if (mods == null || mods.isEmpty()) return null;

        Map<String, Object> mod = mods.get(0);
        String id      = str(mod, "modId",      "unknown");
        String version = str(mod, "version",    "0.0.0").replace("${file.jarVersion}", "?");
        String name    = str(mod, "displayName", id);
        String desc    = str(mod, "description", "");

        List<UnifiedModMeta.DepEntry> deps = new ArrayList<>();
        List<Map<String, Object>> depList  = (List<Map<String, Object>>) toml.getList("dependencies." + id);
        if (depList != null) {
            for (Map<String, Object> dep : depList) {
                String depId  = str(dep, "modId", "");
                String range  = str(dep, "versionRange", "*");
                String type   = str(dep, "type", str(dep, "mandatory", "false").equals("true")
                        ? "required" : "optional");
                UnifiedModMeta.DepEntry.Kind kind = switch (type.toLowerCase()) {
                    case "required"     -> UnifiedModMeta.DepEntry.Kind.REQUIRED;
                    case "incompatible" -> UnifiedModMeta.DepEntry.Kind.INCOMPATIBLE;
                    default             -> UnifiedModMeta.DepEntry.Kind.OPTIONAL;
                };
                if (!depId.isEmpty()) deps.add(new UnifiedModMeta.DepEntry(depId, range, kind));
            }
        }

        String mcRange = deps.stream()
                .filter(d -> d.modId().equals("minecraft") || d.modId().equals("neoforge") || d.modId().equals("forge"))
                .map(UnifiedModMeta.DepEntry::versionRange)
                .findFirst().orElse("*");

        return new UnifiedModMeta(id, version, name, desc, loaderType, mcRange, deps);
    }

    // ── mcmod.info (Forge 1.7 – 1.12.2) ──────────────────────────────────

    @SuppressWarnings("unchecked")
    private UnifiedModMeta tryForgeLegacy(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("mcmod.info");
        if (entry == null) return null;

        String raw = read(zip, entry).trim();
        // mcmod.info is either an array or wrapped in {"modList": [...]}
        JsonElement el = gson.fromJson(raw, JsonElement.class);
        JsonArray arr = null;
        if (el.isJsonArray()) {
            arr = el.getAsJsonArray();
        } else if (el.isJsonObject() && el.getAsJsonObject().has("modList")) {
            arr = el.getAsJsonObject().getAsJsonArray("modList");
        }
        if (arr == null || arr.size() == 0) return null;

        JsonObject mod = arr.get(0).getAsJsonObject();
        String id      = getStr(mod, "modid",       "unknown");
        String version = getStr(mod, "version",     "0.0.0");
        String name    = getStr(mod, "name",        id);
        String desc    = getStr(mod, "description", "");
        String mcVer   = getStr(mod, "mcversion",   "*");

        List<UnifiedModMeta.DepEntry> deps = new ArrayList<>();
        if (mod.has("requiredMods")) {
            for (JsonElement dep : mod.getAsJsonArray("requiredMods")) {
                String s = dep.getAsString(); // format: "modid" or "modid@version"
                String[] parts = s.split("@", 2);
                deps.add(new UnifiedModMeta.DepEntry(parts[0],
                        parts.length > 1 ? parts[1] : "*",
                        UnifiedModMeta.DepEntry.Kind.REQUIRED));
            }
        }
        if (mod.has("dependencies")) {
            for (JsonElement dep : mod.getAsJsonArray("dependencies")) {
                String s = dep.getAsString();
                String[] parts = s.split("@", 2);
                String depId = parts[0].replaceAll("^(required-after:|after:)", "").trim();
                UnifiedModMeta.DepEntry.Kind kind = s.startsWith("required")
                        ? UnifiedModMeta.DepEntry.Kind.REQUIRED
                        : UnifiedModMeta.DepEntry.Kind.OPTIONAL;
                deps.add(new UnifiedModMeta.DepEntry(depId,
                        parts.length > 1 ? parts[1] : "*", kind));
            }
        }
        return new UnifiedModMeta(id, version, name, desc, "forge-legacy", mcVer, deps);
    }

    // ── litemod.json (LiteLoader) ─────────────────────────────────────────

    private UnifiedModMeta tryLiteLoader(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("litemod.json");
        if (entry == null) return null;

        JsonObject obj = gson.fromJson(read(zip, entry), JsonObject.class);
        String id      = getStr(obj, "name",    "unknown");
        String version = getStr(obj, "version", "0.0.0");
        String mcVer   = getStr(obj, "mcversion", "*");

        return new UnifiedModMeta(id, version, id, "", "liteloader", mcVer, List.of());
    }

    // ── MANIFEST.MF fallback ──────────────────────────────────────────────

    private UnifiedModMeta tryJarManifest(ZipFile zip, String fileName) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
        if (entry == null) return null;

        Manifest manifest = new Manifest(zip.getInputStream(entry));
        Attributes attrs  = manifest.getMainAttributes();
        String id = attrs.getValue("Implementation-Title");
        if (id == null || id.isBlank()) {
            // Strip .jar extension, use filename as ID
            id = fileName.replaceAll("(-\\d.*)?\\.jar$", "").toLowerCase();
        }
        String version = attrs.getValue("Implementation-Version");
        if (version == null) version = "0.0.0";

        return new UnifiedModMeta(id, version, id, "", "unknown", "*", List.of());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String read(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream is = zip.getInputStream(entry)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String getStr(JsonObject obj, String key, String def) {
        return obj.has(key) && obj.get(key).isJsonPrimitive()
                ? obj.get(key).getAsString() : def;
    }

    private String str(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? v.toString() : def;
    }
}
