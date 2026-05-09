package dev.compatmanager.detector;

import dev.compatmanager.api.CompatibilityDetector;
import dev.compatmanager.api.CompatibilityIssue;
import dev.compatmanager.api.IssueType;
import dev.compatmanager.api.IssueSeverity;
import dev.compatmanager.api.Solution;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.*;

public class ApiVersionDetector implements CompatibilityDetector {

    // Minimum required Fabric API module versions for common mods
    private static final Map<String, String> FABRIC_API_MODULE_REQUIREMENTS = new LinkedHashMap<>();

    static {
        // mod id -> minimum fabric-api version
        FABRIC_API_MODULE_REQUIREMENTS.put("sodium", "0.80.0");
        FABRIC_API_MODULE_REQUIREMENTS.put("iris", "0.83.0");
        FABRIC_API_MODULE_REQUIREMENTS.put("create", "0.76.0");
    }

    @Override
    public String getId() { return "compatmanager:api_mismatch"; }

    @Override
    public String getName() { return "API Version Detector"; }

    @Override
    public List<CompatibilityIssue> detect() {
        List<CompatibilityIssue> issues = new ArrayList<>();
        FabricLoader loader = FabricLoader.getInstance();

        Optional<ModContainer> fabricApiOpt = loader.getModContainer("fabric-api");
        if (fabricApiOpt.isEmpty()) {
            // Fabric API itself missing — critical
            issues.add(CompatibilityIssue.builder(IssueType.MISSING_DEPENDENCY, IssueSeverity.CRITICAL)
                    .description("compatmanager.issue.missing_dependency", "fabric-api", "fabric-api")
                    .affectedMod("fabric-api")
                    .solution(Solution.builder("compatmanager.solution.add_dependency", "Fabric API").build())
                    .technicalDetail("Fabric API is not installed but is required by multiple mods")
                    .build());
            return issues;
        }

        String fabricApiVersion = fabricApiOpt.get().getMetadata().getVersion().getFriendlyString();

        for (Map.Entry<String, String> entry : FABRIC_API_MODULE_REQUIREMENTS.entrySet()) {
            String modId = entry.getKey();
            String minApiVersion = entry.getValue();

            if (loader.isModLoaded(modId)) {
                if (isVersionBefore(fabricApiVersion, minApiVersion)) {
                    issues.add(CompatibilityIssue.builder(IssueType.API_MISMATCH, IssueSeverity.HIGH)
                            .description("compatmanager.issue.api_mismatch", modId, "fabric-api", minApiVersion)
                            .affectedMod(modId)
                            .affectedMod("fabric-api")
                            .solution(Solution.builder("compatmanager.solution.update_mod", minApiVersion).build())
                            .technicalDetail(String.format(
                                    "%s recommends fabric-api >= %s but found %s",
                                    modId, minApiVersion, fabricApiVersion))
                            .build());
                }
            }
        }

        // Check for Java version compatibility
        int javaVersion = Runtime.version().feature();
        for (ModContainer mod : loader.getAllMods()) {
            mod.getMetadata().getCustomValue("java_version").ifPresent(jvStr -> {
                try {
                    int required = Integer.parseInt(jvStr.getAsString());
                    if (javaVersion < required) {
                        issues.add(CompatibilityIssue.builder(IssueType.API_MISMATCH, IssueSeverity.CRITICAL)
                                .description("compatmanager.issue.api_mismatch",
                                        mod.getMetadata().getId(), "Java", String.valueOf(required))
                                .affectedMod(mod.getMetadata().getId())
                                .solution(Solution.builder("compatmanager.solution.update_mod",
                                        "Java " + required).build())
                                .technicalDetail(String.format(
                                        "%s requires Java %d but running Java %d",
                                        mod.getMetadata().getId(), required, javaVersion))
                                .build());
                    }
                } catch (NumberFormatException ignored) {}
            });
        }

        return issues;
    }

    private boolean isVersionBefore(String installed, String required) {
        try {
            String[] iv = installed.split("[.+\\-]");
            String[] rv = required.split("[.+\\-]");
            int len = Math.min(iv.length, rv.length);
            for (int i = 0; i < len; i++) {
                int a = Integer.parseInt(iv[i]);
                int b = Integer.parseInt(rv[i]);
                if (a != b) return a < b;
            }
            return iv.length < rv.length;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
