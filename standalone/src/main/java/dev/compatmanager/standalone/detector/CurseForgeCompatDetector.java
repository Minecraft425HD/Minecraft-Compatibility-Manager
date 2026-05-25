package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.api.CurseForgeClient;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.util.*;

/**
 * Fetches incompatibility data from the CurseForge API.
 *
 * Skipped when:
 * - ctx.offline() is true
 * - No CurseForge API key (neither --curseforge-key nor CF_API_KEY env var)
 *
 * For each loaded mod it:
 * 1. Searches CurseForge by mod slug/id
 * 2. Reads relationType==5 (Incompatible) dependencies from the latest file
 * 3. Fetches those incompatible mods and checks if they're also loaded
 */
public class CurseForgeCompatDetector implements IssueDetector {

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        if (ctx.offline()) return List.of();

        String apiKey = CurseForgeClient.resolveKey(ctx.curseForgeKey());
        if (apiKey.isBlank()) return List.of();

        List<UnifiedModMeta> mods = platform.getLoadedMods();
        if (mods.isEmpty()) return List.of();

        List<StandaloneIssue> issues = new ArrayList<>();
        Set<String> loadedIds = new HashSet<>();
        mods.forEach(m -> loadedIds.add(m.id().toLowerCase()));

        // Cache CurseForge mod lookups to avoid redundant API calls
        Map<Integer, CurseForgeClient.CurseForgeMod> cfById = new HashMap<>();

        for (UnifiedModMeta mod : mods) {
            Optional<CurseForgeClient.CurseForgeMod> cfModOpt =
                    CurseForgeClient.searchMod(mod.id(), apiKey);
            if (cfModOpt.isEmpty()) continue;

            CurseForgeClient.CurseForgeMod cfMod = cfModOpt.get();

            for (CurseForgeClient.CurseForgeModDependency dep : cfMod.latestDeps()) {
                if (!dep.isIncompatible()) continue;

                // Fetch the incompatible mod to get its slug
                CurseForgeClient.CurseForgeMod other = cfById.computeIfAbsent(dep.modId(),
                        id -> CurseForgeClient.fetchMod(id, apiKey).orElse(null));
                if (other == null) continue;

                String otherSlug = other.slug().toLowerCase();
                if (!loadedIds.contains(otherSlug)) continue;

                issues.add(StandaloneIssue.builder("CurseForge-Declared Incompatibility", Severity.CRITICAL)
                        .affectedMods(mod.id(), otherSlug)
                        .description("CurseForge reports that '" + cfMod.name()
                                + "' declares '" + other.name() + "' as incompatible "
                                + "in its latest file dependency metadata.")
                        .solutions(
                                "Remove '" + mod.id() + "' or '" + otherSlug + "'",
                                "Check both mods' CurseForge pages for updated compatibility notes"
                        )
                        .jarPath(platform.getJarForMod(mod.id())
                                .map(f -> f.getAbsolutePath()).orElse(""))
                        .build());
            }
        }

        return issues;
    }
}
