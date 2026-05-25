package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.api.ModrinthClient;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fetches live incompatibility data from the Modrinth API.
 *
 * Checks three things:
 * 1. Loader mismatch — Modrinth says mod X supports only Fabric, but we detect Forge
 * 2. MC version mismatch — mod doesn't list the given --mc-version as supported
 * 3. Author-declared incompatibilities in version dependency metadata
 *
 * All network calls are skipped with --offline. Results are disk-cached for 24h.
 */
public class ModrinthCompatDetector implements IssueDetector {

    private static final int BATCH_SIZE = 50; // Modrinth limit per request

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        if (ctx.offline()) return List.of();

        List<UnifiedModMeta> mods = platform.getLoadedMods();
        if (mods.isEmpty()) return List.of();

        List<StandaloneIssue> issues = new ArrayList<>();

        // Batch mod IDs for Modrinth lookup
        List<String> modIds = mods.stream().map(UnifiedModMeta::id).toList();
        Map<String, ModrinthClient.ModrinthProject> projectMap = new HashMap<>();

        for (int i = 0; i < modIds.size(); i += BATCH_SIZE) {
            List<String> batch = modIds.subList(i, Math.min(i + BATCH_SIZE, modIds.size()));
            List<ModrinthClient.ModrinthProject> projects = ModrinthClient.fetchProjects(batch);
            for (ModrinthClient.ModrinthProject p : projects) {
                projectMap.put(p.slug().toLowerCase(), p);
                projectMap.put(p.id().toLowerCase(), p);
            }
        }

        if (projectMap.isEmpty()) return List.of(); // Offline or all mods unknown to Modrinth

        String mcVer     = ctx.mcVersion();
        String loaderHint = ctx.loaderHint().toLowerCase();

        for (UnifiedModMeta mod : mods) {
            ModrinthClient.ModrinthProject proj =
                    projectMap.get(mod.id().toLowerCase());
            if (proj == null) continue;

            // ── MC version compatibility check ───────────────────────────
            if (!mcVer.isBlank() && !proj.gameVersions().isEmpty()) {
                boolean supportsVersion = proj.gameVersions().stream()
                        .anyMatch(v -> v.equals(mcVer) || v.startsWith(mcVer));
                if (!supportsVersion) {
                    issues.add(StandaloneIssue.builder("Unsupported MC Version", Severity.HIGH)
                            .affectedMods(mod.id())
                            .description("Modrinth reports that '" + mod.id()
                                    + "' does not officially support Minecraft " + mcVer
                                    + ". Supported versions: "
                                    + String.join(", ", latestVersions(proj.gameVersions())))
                            .solutions(
                                    "Update to the version of '" + mod.id() + "' that supports MC " + mcVer,
                                    "Check the mod's Modrinth page for the correct version",
                                    "Accept potential breakage if using an unsupported version"
                            )
                            .build());
                }
            }

            // ── Loader compatibility check ────────────────────────────────
            if (!loaderHint.isBlank() && !proj.loaders().isEmpty()) {
                boolean supportsLoader = proj.loaders().stream()
                        .anyMatch(l -> l.equalsIgnoreCase(loaderHint)
                                || (loaderHint.equals("quilt") && l.equals("fabric")));
                if (!supportsLoader) {
                    issues.add(StandaloneIssue.builder("Wrong Loader", Severity.CRITICAL)
                            .affectedMods(mod.id())
                            .description("Modrinth reports that '" + mod.id()
                                    + "' supports: " + String.join(", ", proj.loaders())
                                    + " — but you are using: " + loaderHint + ". "
                                    + "This mod will not load on your current modloader.")
                            .solutions(
                                    "Remove '" + mod.id() + "' from your mods folder",
                                    "Find a " + loaderHint + "-compatible alternative",
                                    "Switch your modloader to one of: " + String.join(", ", proj.loaders())
                            )
                            .jarPath(platform.getJarForMod(mod.id())
                                    .map(f -> f.getAbsolutePath()).orElse(""))
                            .build());
                }
            }

            // ── Author-declared incompatibilities from version deps ───────
            List<ModrinthClient.ModrinthDependency> deps =
                    ModrinthClient.fetchDependencies(proj.id());
            for (ModrinthClient.ModrinthDependency dep : deps) {
                if (!"incompatible".equals(dep.dependencyType())) continue;
                if (!platform.isModLoaded(dep.projectId())) continue;

                String otherMod = dep.projectId();
                ModrinthClient.ModrinthProject otherProj = projectMap.get(otherMod.toLowerCase());
                String otherName = otherProj != null ? otherProj.title() : otherMod;

                issues.add(StandaloneIssue.builder("Modrinth-Declared Incompatibility", Severity.CRITICAL)
                        .affectedMods(mod.id(), otherMod)
                        .description("The author of '" + mod.id()
                                + "' has declared '" + otherName + "' as incompatible "
                                + "in the Modrinth version metadata.")
                        .solutions(
                                "Remove '" + mod.id() + "' or '" + otherName + "'",
                                "Check both mods' Modrinth pages for updated compatibility notes"
                        )
                        .jarPath(platform.getJarForMod(mod.id())
                                .map(f -> f.getAbsolutePath()).orElse(""))
                        .build());
            }
        }

        return issues;
    }

    private List<String> latestVersions(List<String> versions) {
        return versions.stream().sorted(Comparator.reverseOrder()).limit(5).toList();
    }
}
