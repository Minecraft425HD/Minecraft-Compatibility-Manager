package dev.compatmanager.detector;

import dev.compatmanager.api.*;
import dev.compatmanager.platform.Services;

import java.util.*;

public class DuplicateModDetector implements CompatibilityDetector {

    /** Groups of mods that provide overlapping/conflicting functionality. */
    private static final List<Set<String>> FUNCTIONAL_DUPLICATES = List.of(
            // Renderers / shaders
            Set.of("sodium", "optifabric", "rubidium", "embeddium"),
            Set.of("iris", "optifabric"),
            // Light engines
            Set.of("phosphor", "starlight", "moonrise"),
            // Mini-maps
            Set.of("journeymap", "voxelmap", "xaeros_minimap"),
            // Storage networks
            Set.of("applied_energistics2", "refined_storage"),
            // Performance: chunk loading
            Set.of("smoothchunk", "betterchunkloading"),
            // Boot optimisers
            Set.of("lazydfu", "datafixer_slayer"),
            // Entity culling
            Set.of("entityculling", "entitycullingfabric")
    );

    @Override public String getId()   { return "compatmanager:duplicate_mod"; }
    @Override public String getName() { return "Duplicate / Conflicting Mod Detector"; }

    @Override
    public List<CompatibilityIssue> detect() {
        List<CompatibilityIssue> issues = new ArrayList<>();
        Set<String> loaded = new HashSet<>();
        Map<String, Long> idCount = new HashMap<>();

        for (var mod : Services.PLATFORM.getLoadedMods()) {
            loaded.add(mod.id());
            idCount.merge(mod.id(), 1L, Long::sum);
        }

        // Detect identical mod ID loaded more than once (two JAR files)
        idCount.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .forEach(e -> issues.add(
                        CompatibilityIssue.builder(IssueType.DUPLICATE_MOD, IssueSeverity.CRITICAL)
                                .description("compatmanager.issue.duplicate_mod", e.getKey())
                                .affectedMod(e.getKey())
                                .solution(Solution.manual("compatmanager.solution.remove_mod", e.getKey()))
                                .technicalDetail("'" + e.getKey() + "' is present "
                                        + e.getValue() + " times in the mods folder")
                                .build()));

        // Detect functional overlaps
        for (Set<String> group : FUNCTIONAL_DUPLICATES) {
            List<String> present = group.stream().filter(loaded::contains).sorted().toList();
            if (present.size() < 2) continue;

            var builder = CompatibilityIssue.builder(IssueType.DUPLICATE_MOD, IssueSeverity.HIGH)
                    .description("compatmanager.issue.duplicate_mod", String.join(", ", present))
                    .technicalDetail("These mods provide overlapping functionality: "
                            + String.join(", ", present));

            for (String id : present) {
                builder.affectedMod(id);
                builder.solution(Solution.manual("compatmanager.solution.remove_mod", id));
            }
            issues.add(builder.build());
        }

        return issues;
    }
}
