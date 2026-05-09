package dev.compatmanager.detector;

import dev.compatmanager.api.CompatibilityDetector;
import dev.compatmanager.api.CompatibilityIssue;
import dev.compatmanager.api.IssueType;
import dev.compatmanager.api.IssueSeverity;
import dev.compatmanager.api.Solution;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;

import java.util.ArrayList;
import java.util.List;

public class VersionConflictDetector implements CompatibilityDetector {

    @Override
    public String getId() { return "compatmanager:version_conflict"; }

    @Override
    public String getName() { return "Version Conflict Detector"; }

    @Override
    public List<CompatibilityIssue> detect() {
        List<CompatibilityIssue> issues = new ArrayList<>();
        FabricLoader loader = FabricLoader.getInstance();

        for (ModContainer mod : loader.getAllMods()) {
            String modId = mod.getMetadata().getId();

            for (ModDependency dep : mod.getMetadata().getDependencies()) {
                if (dep.getKind() == ModDependency.Kind.DEPENDS || dep.getKind() == ModDependency.Kind.RECOMMENDS) {
                    String depId = dep.getModId();

                    loader.getModContainer(depId).ifPresentOrElse(depMod -> {
                        String installedVersion = depMod.getMetadata().getVersion().getFriendlyString();
                        boolean satisfied = dep.getVersionRequirements().stream()
                                .allMatch(vr -> vr.test(depMod.getMetadata().getVersion()));

                        if (!satisfied) {
                            IssueSeverity severity = dep.getKind() == ModDependency.Kind.DEPENDS
                                    ? IssueSeverity.CRITICAL : IssueSeverity.MEDIUM;

                            String requiredRange = dep.getVersionRequirements().stream()
                                    .map(Object::toString)
                                    .reduce((a, b) -> a + " || " + b)
                                    .orElse("unknown");

                            Solution updateSolution = Solution.builder(
                                    "compatmanager.solution.update_mod", requiredRange)
                                    .build();

                            issues.add(CompatibilityIssue.builder(IssueType.VERSION_CONFLICT, severity)
                                    .description("compatmanager.issue.version_conflict",
                                            modId, depId, installedVersion, requiredRange)
                                    .affectedMod(modId)
                                    .affectedMod(depId)
                                    .solution(updateSolution)
                                    .technicalDetail(String.format(
                                            "%s requires %s %s but found %s",
                                            modId, depId, requiredRange, installedVersion))
                                    .build());
                        }
                    }, () -> {
                        if (dep.getKind() == ModDependency.Kind.DEPENDS) {
                            Solution addSolution = Solution.builder(
                                    "compatmanager.solution.add_dependency", depId).build();

                            issues.add(CompatibilityIssue.builder(IssueType.MISSING_DEPENDENCY, IssueSeverity.CRITICAL)
                                    .description("compatmanager.issue.missing_dependency", modId, depId)
                                    .affectedMod(modId)
                                    .solution(addSolution)
                                    .technicalDetail(String.format(
                                            "%s requires %s which is not installed", modId, depId))
                                    .build());
                        }
                    });
                }

                if (dep.getKind() == ModDependency.Kind.BREAKS || dep.getKind() == ModDependency.Kind.CONFLICTS) {
                    String depId = dep.getModId();
                    loader.getModContainer(depId).ifPresent(depMod -> {
                        boolean conflicting = dep.getVersionRequirements().isEmpty() ||
                                dep.getVersionRequirements().stream()
                                        .anyMatch(vr -> vr.test(depMod.getMetadata().getVersion()));

                        if (conflicting) {
                            IssueSeverity severity = dep.getKind() == ModDependency.Kind.BREAKS
                                    ? IssueSeverity.CRITICAL : IssueSeverity.HIGH;

                            Solution removeSolution = Solution.builder(
                                    "compatmanager.solution.remove_mod", depId).build();

                            issues.add(CompatibilityIssue.builder(IssueType.INCOMPATIBLE_MODS, severity)
                                    .description("compatmanager.issue.incompatible_mods", modId, depId)
                                    .affectedMod(modId)
                                    .affectedMod(depId)
                                    .solution(removeSolution)
                                    .technicalDetail(String.format(
                                            "%s declares %s as incompatible (%s)",
                                            modId, depId, dep.getKind().name().toLowerCase()))
                                    .build());
                        }
                    });
                }
            }
        }

        return issues;
    }
}
