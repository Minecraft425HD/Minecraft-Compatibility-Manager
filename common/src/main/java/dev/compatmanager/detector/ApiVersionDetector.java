package dev.compatmanager.detector;

import dev.compatmanager.api.*;
import dev.compatmanager.platform.Services;

import java.util.ArrayList;
import java.util.List;

public class ApiVersionDetector implements CompatibilityDetector {

    @Override public String getId()   { return "compatmanager:api_version"; }
    @Override public String getName() { return "API & Java Version Detector"; }

    @Override
    public List<CompatibilityIssue> detect() {
        List<CompatibilityIssue> issues = new ArrayList<>();

        checkJavaVersion(issues);
        checkMinecraftVersion(issues);

        return issues;
    }

    private void checkJavaVersion(List<CompatibilityIssue> issues) {
        int running = Runtime.version().feature();
        // Mods that declare minimum Java via custom metadata key
        for (var mod : Services.PLATFORM.getLoadedMods()) {
            // Convention: custom metadata "required_java" = "21"
            // Since reading custom keys is loader-specific, we skip that here.
            // We DO check the universal rule: MC 1.17+ needs Java 17, MC 1.21+ needs Java 21.
        }

        String mcVer = Services.PLATFORM.getMinecraftVersion();
        int needed = javaFloor(mcVer);
        if (running < needed) {
            issues.add(CompatibilityIssue.builder(IssueType.API_MISMATCH, IssueSeverity.CRITICAL)
                    .description("compatmanager.issue.api_mismatch",
                            "Java", String.valueOf(needed), mcVer)
                    .affectedMod("java")
                    .solution(Solution.manual("compatmanager.solution.update_mod", "Java " + needed))
                    .technicalDetail("Minecraft " + mcVer + " requires Java " + needed
                            + " but running Java " + running)
                    .build());
        }
    }

    private void checkMinecraftVersion(List<CompatibilityIssue> issues) {
        // Detect if any mod's declared MC dependency is unsatisfied.
        // For Forge/NeoForge this is handled by the loader, so we emit INFO only.
        String platform = Services.PLATFORM.getName();
        String loader   = Services.PLATFORM.getLoaderVersion();
        String mc       = Services.PLATFORM.getMinecraftVersion();

        // NeoForge only supports 1.20.2+
        if ("NeoForge".equals(platform) && cmpMc(mc, "1.20.2") < 0) {
            issues.add(CompatibilityIssue.builder(IssueType.API_MISMATCH, IssueSeverity.CRITICAL)
                    .description("compatmanager.issue.api_mismatch",
                            "NeoForge", "1.20.2+", mc)
                    .affectedMod("neoforge")
                    .solution(Solution.manual("compatmanager.solution.update_mod", "Minecraft 1.20.2+"))
                    .technicalDetail("NeoForge requires Minecraft 1.20.2 or newer (found " + mc + ")")
                    .build());
        }
    }

    private int javaFloor(String mc) {
        if (cmpMc(mc, "1.21") >= 0) return 21;
        if (cmpMc(mc, "1.17") >= 0) return 17;
        if (cmpMc(mc, "1.12") >= 0) return 8;
        return 8;
    }

    private int cmpMc(String a, String b) {
        String[] av = a.split("\\.");
        String[] bv = b.split("\\.");
        for (int i = 0; i < Math.min(av.length, bv.length); i++) {
            try {
                int d = Integer.compare(Integer.parseInt(av[i]), Integer.parseInt(bv[i]));
                if (d != 0) return d;
            } catch (NumberFormatException e) { return 0; }
        }
        return Integer.compare(av.length, bv.length);
    }
}
