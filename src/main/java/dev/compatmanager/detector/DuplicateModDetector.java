package dev.compatmanager.detector;

import dev.compatmanager.api.CompatibilityDetector;
import dev.compatmanager.api.CompatibilityIssue;
import dev.compatmanager.api.IssueType;
import dev.compatmanager.api.IssueSeverity;
import dev.compatmanager.api.Solution;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.*;

public class DuplicateModDetector implements CompatibilityDetector {

    // Known mod pairs that provide the same functionality
    private static final List<Set<String>> KNOWN_DUPLICATES = List.of(
            Set.of("optifine", "sodium"),
            Set.of("optifine", "iris"),
            Set.of("optifabric", "sodium"),
            Set.of("lithium", "canary"),
            Set.of("phosphor", "starlight"),
            Set.of("ferritecore", "memoryleakfix"),
            Set.of("entityculling", "cullleaves"),
            Set.of("appleskin", "betterfoods"),
            Set.of("journeymap", "voxelmap", "xaeros_minimap", "xaeros_worldmap")
    );

    @Override
    public String getId() { return "compatmanager:duplicate_mod"; }

    @Override
    public String getName() { return "Duplicate Mod Detector"; }

    @Override
    public List<CompatibilityIssue> detect() {
        List<CompatibilityIssue> issues = new ArrayList<>();
        FabricLoader loader = FabricLoader.getInstance();
        Set<String> installedIds = new HashSet<>();

        for (ModContainer mod : loader.getAllMods()) {
            installedIds.add(mod.getMetadata().getId());
        }

        // Check known duplicate groups
        for (Set<String> group : KNOWN_DUPLICATES) {
            List<String> installedFromGroup = group.stream()
                    .filter(installedIds::contains)
                    .toList();

            if (installedFromGroup.size() > 1) {
                CompatibilityIssue.Builder builder = CompatibilityIssue
                        .builder(IssueType.DUPLICATE_MOD, IssueSeverity.HIGH)
                        .description("compatmanager.issue.duplicate_mod",
                                String.join(", ", installedFromGroup))
                        .technicalDetail("These mods provide overlapping functionality: "
                                + String.join(", ", installedFromGroup));

                for (String modId : installedFromGroup) {
                    builder.affectedMod(modId);
                    builder.solution(Solution.builder("compatmanager.solution.remove_mod", modId).build());
                }

                issues.add(builder.build());
            }
        }

        // Detect duplicate mod IDs (same mod installed twice with different file names)
        Map<String, Long> idCount = new HashMap<>();
        for (ModContainer mod : loader.getAllMods()) {
            String id = mod.getMetadata().getId();
            idCount.merge(id, 1L, Long::sum);
        }

        idCount.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .forEach(e -> {
                    issues.add(CompatibilityIssue.builder(IssueType.DUPLICATE_MOD, IssueSeverity.CRITICAL)
                            .description("compatmanager.issue.duplicate_mod", e.getKey())
                            .affectedMod(e.getKey())
                            .solution(Solution.builder("compatmanager.solution.remove_mod", e.getKey()).build())
                            .technicalDetail("Mod ID '" + e.getKey() + "' is present " + e.getValue() + " times")
                            .build());
                });

        return issues;
    }
}
