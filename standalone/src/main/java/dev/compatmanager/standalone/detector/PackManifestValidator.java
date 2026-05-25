package dev.compatmanager.standalone.detector;

import com.google.gson.*;
import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Validates installed mods against a modpack manifest.
 *
 * Supported formats:
 * - CurseForge:  manifest.json  (fields: minecraft.version, minecraft.modLoaders, files[])
 * - Modrinth:    modrinth.index.json  (fields: dependencies, files[])
 *
 * Looks for the manifest in:
 *   1. The mods folder itself
 *   2. The mods folder's parent (most common — instance root)
 *   3. The current working directory
 *
 * Reports:
 * - Mods in the folder that aren't in the manifest (unmanaged mods)
 * - MC version mismatch between manifest and --mc-version
 * - Loader mismatch between manifest and --loader
 */
public class PackManifestValidator implements IssueDetector {

    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        List<StandaloneIssue> issues = new ArrayList<>();

        Path manifestPath = findManifest(platform.getModsDir());
        if (manifestPath == null) return List.of();

        String filename = manifestPath.getFileName().toString().toLowerCase();
        try {
            String json = Files.readString(manifestPath);
            if (filename.equals("manifest.json")) {
                issues.addAll(validateCurseForge(json, platform, ctx, manifestPath));
            } else if (filename.equals("modrinth.index.json")) {
                issues.addAll(validateModrinth(json, platform, ctx, manifestPath));
            }
        } catch (IOException e) {
            /* Skip on read error */
        }

        return issues;
    }

    // ── CurseForge manifest ───────────────────────────────────────────────

    private List<StandaloneIssue> validateCurseForge(String json, StandaloneModPlatform platform,
                                                       ScanContext ctx, Path manifestPath) {
        List<StandaloneIssue> issues = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (!root.has("manifestType")) return List.of(); // Not a CF manifest

            // MC version check
            if (root.has("minecraft") && !ctx.mcVersion().isBlank()) {
                JsonObject mc = root.getAsJsonObject("minecraft");
                String manifestMc = mc.has("version") ? mc.get("version").getAsString() : "";
                if (!manifestMc.isBlank() && !manifestMc.equals(ctx.mcVersion())) {
                    issues.add(new StandaloneIssue("Pack MC Version Mismatch", Severity.CRITICAL,
                            List.of("manifest.json"),
                            "The CurseForge pack manifest requires Minecraft " + manifestMc
                                    + " but you specified --mc-version " + ctx.mcVersion() + ". "
                                    + "Using a different MC version than the pack was built for will cause crashes.",
                            List.of("Use Minecraft " + manifestMc + " as the pack requires",
                                    "Or update the pack manifest to match your MC version")));
                }

                // Loader check
                if (mc.has("modLoaders") && !ctx.loaderHint().isBlank()) {
                    StringBuilder loaders = new StringBuilder();
                    for (JsonElement el : mc.getAsJsonArray("modLoaders")) {
                        String lid = el.getAsJsonObject().get("id").getAsString();
                        loaders.append(lid).append(", ");
                    }
                    String loadersStr = loaders.toString().replaceAll(", $", "");
                    String hint = ctx.loaderHint().toLowerCase();
                    if (!loadersStr.toLowerCase().contains(hint)) {
                        issues.add(new StandaloneIssue("Pack Loader Mismatch", Severity.HIGH,
                                List.of("manifest.json"),
                                "The CurseForge pack manifest specifies loader: " + loadersStr
                                        + " but you specified --loader " + ctx.loaderHint() + ".",
                                List.of("Use the loader specified in the pack manifest: " + loadersStr)));
                    }
                }
            }

            // Count pack-managed files
            int packFileCount = 0;
            if (root.has("files")) {
                for (JsonElement el : root.getAsJsonArray("files")) {
                    if (el.getAsJsonObject().has("projectID")) packFileCount++;
                }
            }

            int installedCount = platform.getLoadedMods().size();
            if (installedCount > packFileCount + 5) {
                issues.add(new StandaloneIssue("Unmanaged Mods Detected", Severity.MEDIUM,
                        List.of("manifest.json"),
                        "The CurseForge pack manifest lists " + packFileCount + " files, "
                                + "but " + installedCount + " mods are installed. "
                                + (installedCount - packFileCount) + " mod(s) are not managed by the pack. "
                                + "Manifest: " + manifestPath.toAbsolutePath(),
                        List.of("Remove manually added mods that are not in the pack manifest",
                                "Or add them to the manifest if they are intentional additions")));
            }

        } catch (Exception e) { /* Skip on parse error */ }
        return issues;
    }

    // ── Modrinth manifest ─────────────────────────────────────────────────

    private List<StandaloneIssue> validateModrinth(String json, StandaloneModPlatform platform,
                                                     ScanContext ctx, Path manifestPath) {
        List<StandaloneIssue> issues = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (!root.has("formatVersion")) return List.of();

            if (root.has("dependencies") && !ctx.mcVersion().isBlank()) {
                JsonObject deps = root.getAsJsonObject("dependencies");
                String manifestMc = deps.has("minecraft") ? deps.get("minecraft").getAsString() : "";
                if (!manifestMc.isBlank() && !manifestMc.equals(ctx.mcVersion())) {
                    issues.add(new StandaloneIssue("Pack MC Version Mismatch", Severity.CRITICAL,
                            List.of("modrinth.index.json"),
                            "The Modrinth pack requires Minecraft " + manifestMc
                                    + " but you specified --mc-version " + ctx.mcVersion() + ".",
                            List.of("Use Minecraft " + manifestMc + " as the pack requires")));
                }

                // Loader check from dependencies
                if (!ctx.loaderHint().isBlank()) {
                    String hint = ctx.loaderHint().toLowerCase();
                    boolean loaderFound = deps.keySet().stream()
                            .anyMatch(k -> k.toLowerCase().contains(hint)
                                    || (hint.equals("quilt") && k.contains("quilt"))
                                    || (hint.equals("fabric") && k.contains("fabric-loader")));
                    if (!loaderFound) {
                        issues.add(new StandaloneIssue("Pack Loader Mismatch", Severity.HIGH,
                                List.of("modrinth.index.json"),
                                "The Modrinth pack dependencies don't include '" + ctx.loaderHint()
                                        + "'. Available loaders in manifest: "
                                        + String.join(", ", deps.keySet()),
                                List.of("Verify you are using the correct modloader for this pack")));
                    }
                }
            }

            // Count pack files vs installed
            int packFiles = root.has("files") ? root.getAsJsonArray("files").size() : 0;
            int installed = platform.getLoadedMods().size();
            if (installed > packFiles + 5) {
                issues.add(new StandaloneIssue("Unmanaged Mods Detected", Severity.MEDIUM,
                        List.of("modrinth.index.json"),
                        "The Modrinth pack lists " + packFiles + " mods, "
                                + "but " + installed + " are installed. "
                                + (installed - packFiles) + " mod(s) are not in the pack.",
                        List.of("Remove manually added mods not in the pack",
                                "Or use a pack manager to add them properly")));
            }

        } catch (Exception e) { /* Skip on parse error */ }
        return issues;
    }

    // ── Manifest discovery ────────────────────────────────────────────────

    private Path findManifest(Path modsDir) {
        List<Path> searchDirs = new ArrayList<>();
        searchDirs.add(modsDir);
        if (modsDir.getParent() != null) searchDirs.add(modsDir.getParent());
        searchDirs.add(Path.of("."));

        for (Path dir : searchDirs) {
            for (String name : List.of("manifest.json", "modrinth.index.json")) {
                Path candidate = dir.resolve(name);
                if (candidate.toFile().isFile()) {
                    try {
                        // Verify it's actually a pack manifest
                        String content = Files.readString(candidate);
                        if (name.equals("manifest.json") && content.contains("manifestType")) return candidate;
                        if (name.equals("modrinth.index.json") && content.contains("formatVersion")) return candidate;
                    } catch (IOException ignored) {}
                }
            }
        }
        return null;
    }
}
