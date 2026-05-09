package dev.compatmanager.detector;

import dev.compatmanager.api.*;
import dev.compatmanager.platform.DependencyInfo;
import dev.compatmanager.platform.ModInfo;
import dev.compatmanager.platform.Services;

import java.util.ArrayList;
import java.util.List;

public class VersionConflictDetector implements CompatibilityDetector {

    @Override public String getId()   { return "compatmanager:version_conflict"; }
    @Override public String getName() { return "Version & Dependency Conflict Detector"; }

    @Override
    public List<CompatibilityIssue> detect() {
        List<CompatibilityIssue> issues = new ArrayList<>();

        for (ModInfo mod : Services.PLATFORM.getLoadedMods()) {
            for (DependencyInfo dep : mod.dependencies()) {
                switch (dep.kind()) {
                    case REQUIRED -> checkRequired(issues, mod, dep);
                    case INCOMPATIBLE -> checkIncompatible(issues, mod, dep);
                    default -> { /* OPTIONAL – skip */ }
                }
            }
        }
        return issues;
    }

    private void checkRequired(List<CompatibilityIssue> issues, ModInfo mod, DependencyInfo dep) {
        var depOpt = Services.PLATFORM.getMod(dep.modId());
        if (depOpt.isEmpty()) {
            issues.add(CompatibilityIssue.builder(IssueType.MISSING_DEPENDENCY, IssueSeverity.CRITICAL)
                    .description("compatmanager.issue.missing_dependency", mod.id(), dep.modId())
                    .affectedMod(mod.id())
                    .solution(Solution.manual("compatmanager.solution.add_dependency", dep.modId()))
                    .technicalDetail(mod.id() + " requires " + dep.modId()
                            + " " + dep.versionRange() + " which is not installed")
                    .build());
            return;
        }

        String installedVer = depOpt.get().version();
        if (!dep.versionRange().isBlank() && !versionSatisfies(installedVer, dep.versionRange())) {
            issues.add(CompatibilityIssue.builder(IssueType.VERSION_CONFLICT, IssueSeverity.CRITICAL)
                    .description("compatmanager.issue.version_conflict",
                            mod.id(), dep.modId(), installedVer, dep.versionRange())
                    .affectedMod(mod.id())
                    .affectedMod(dep.modId())
                    .solution(Solution.manual("compatmanager.solution.update_mod", dep.versionRange()))
                    .technicalDetail(mod.id() + " needs " + dep.modId()
                            + " " + dep.versionRange() + " but found " + installedVer)
                    .build());
        }
    }

    private void checkIncompatible(List<CompatibilityIssue> issues, ModInfo mod, DependencyInfo dep) {
        if (Services.PLATFORM.isModLoaded(dep.modId())) {
            issues.add(CompatibilityIssue.builder(IssueType.INCOMPATIBLE_MODS, IssueSeverity.CRITICAL)
                    .description("compatmanager.issue.incompatible_mods", mod.id(), dep.modId())
                    .affectedMod(mod.id())
                    .affectedMod(dep.modId())
                    .solution(Solution.manual("compatmanager.solution.remove_mod", dep.modId()))
                    .solution(Solution.manual("compatmanager.solution.remove_mod", mod.id()))
                    .technicalDetail(mod.id() + " declares " + dep.modId() + " as incompatible")
                    .build());
        }
    }

    /**
     * Very simple version range check that handles the most common patterns:
     * exact ("1.0.0"), prefix ("1.0.*"), range ("[1.0,2.0)"), minimum (">=1.0").
     */
    private boolean versionSatisfies(String installed, String range) {
        if (range.isBlank() || range.equals("*")) return true;
        try {
            if (range.startsWith(">=")) return cmp(installed, range.substring(2)) >= 0;
            if (range.startsWith(">"))  return cmp(installed, range.substring(1)) > 0;
            if (range.startsWith("<=")) return cmp(installed, range.substring(2)) <= 0;
            if (range.startsWith("<"))  return cmp(installed, range.substring(1)) < 0;
            if (range.startsWith("~"))  return installed.startsWith(range.substring(1, range.lastIndexOf('.')));
            if (range.contains(",")) {
                // Maven/Forge range [min,max)
                char lo = range.charAt(0), hi = range.charAt(range.length() - 1);
                String[] parts = range.substring(1, range.length() - 1).split(",", 2);
                int loC = cmp(installed, parts[0].trim());
                int hiC = parts[1].trim().isEmpty() ? -1 : cmp(installed, parts[1].trim());
                boolean loOk = lo == '[' ? loC >= 0 : loC > 0;
                boolean hiOk = parts[1].trim().isEmpty() || (hi == ']' ? hiC <= 0 : hiC < 0);
                return loOk && hiOk;
            }
            return installed.equals(range);
        } catch (Exception e) {
            return true; // unknown format — assume OK
        }
    }

    private int cmp(String a, String b) {
        String[] av = a.split("[.+\\-]");
        String[] bv = b.split("[.+\\-]");
        for (int i = 0; i < Math.min(av.length, bv.length); i++) {
            try {
                int d = Integer.compare(Integer.parseInt(av[i]), Integer.parseInt(bv[i]));
                if (d != 0) return d;
            } catch (NumberFormatException e) {
                int d = av[i].compareTo(bv[i]);
                if (d != 0) return d;
            }
        }
        return Integer.compare(av.length, bv.length);
    }
}
