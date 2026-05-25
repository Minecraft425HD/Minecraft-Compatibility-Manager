package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.util.*;

/**
 * Resolves the full transitive dependency graph and reports missing mods
 * that aren't direct dependencies of any loaded mod (i.e. depth ≥ 2).
 *
 * Example: ModA requires LibX, LibX requires LibY.
 * If LibY is missing, ModA crashes but only LibX's error is obvious.
 * This detector surfaces LibY as the root cause.
 */
public class TransitiveDependencyDetector implements IssueDetector {

    // System IDs that are always "installed" (loader, java, minecraft itself)
    private static final Set<String> SYSTEM_IDS = Set.of(
            "minecraft", "java", "fabricloader", "forge", "neoforge",
            "quilt_loader", "quilt_base", "fabric-api", "fabric",
            "mod_menu", "mixinextras"
    );

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        List<StandaloneIssue> issues = new ArrayList<>();

        List<UnifiedModMeta> mods = platform.getLoadedMods();
        Set<String> loadedIds = new HashSet<>();
        mods.forEach(m -> loadedIds.add(m.id().toLowerCase()));

        // Build full dep graph: modId → required dep IDs
        Map<String, List<String>> graph = new HashMap<>();
        for (UnifiedModMeta mod : mods) {
            List<String> required = mod.dependencies().stream()
                    .filter(d -> d.kind() == UnifiedModMeta.DepEntry.Kind.REQUIRED)
                    .map(d -> d.modId().toLowerCase())
                    .filter(id -> !SYSTEM_IDS.contains(id))
                    .toList();
            graph.put(mod.id().toLowerCase(), required);
        }

        // BFS from every loaded mod — collect all transitively required IDs
        // Track which mod introduced each transitive dep
        Map<String, String> transitiveRequiredBy = new LinkedHashMap<>(); // depId → root mod
        Map<String, Integer> depDepth = new HashMap<>();

        for (UnifiedModMeta root : mods) {
            String rootId = root.id().toLowerCase();
            Queue<String> queue = new ArrayDeque<>(graph.getOrDefault(rootId, List.of()));
            Set<String>   visited = new HashSet<>();
            visited.add(rootId);
            int depth = 1;

            while (!queue.isEmpty()) {
                int levelSize = queue.size();
                depth++;
                for (int i = 0; i < levelSize; i++) {
                    String current = queue.poll();
                    if (current == null || !visited.add(current)) continue;
                    if (!loadedIds.contains(current) && !SYSTEM_IDS.contains(current)) {
                        // Missing transitive dep — only report if depth > 1 (direct deps are
                        // already caught by detectVersionConflicts in StandaloneDetector)
                        if (depth > 2 && !transitiveRequiredBy.containsKey(current)) {
                            transitiveRequiredBy.put(current, rootId);
                            depDepth.put(current, depth);
                        }
                    }
                    // Recurse into this dep's own deps (if we know them)
                    queue.addAll(graph.getOrDefault(current, List.of()));
                }
            }
        }

        for (Map.Entry<String, String> entry : transitiveRequiredBy.entrySet()) {
            String missingDep = entry.getKey();
            String rootMod    = entry.getValue();
            int depth         = depDepth.getOrDefault(missingDep, 2);

            issues.add(new StandaloneIssue(
                    "Missing Transitive Dependency",
                    Severity.HIGH,
                    List.of(rootMod, missingDep),
                    rootMod + " indirectly requires '" + missingDep
                            + "' (dependency depth: " + depth + ") which is not installed. "
                            + "This will cause a crash when " + rootMod + " tries to load.",
                    List.of(
                            "Install the missing library: " + missingDep,
                            "Check the mod page for " + rootMod + " for its full dependency list"
                    )
            ));
        }

        return issues;
    }
}
