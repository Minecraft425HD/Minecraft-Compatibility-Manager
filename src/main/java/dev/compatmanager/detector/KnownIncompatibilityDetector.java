package dev.compatmanager.detector;

import dev.compatmanager.api.CompatibilityDetector;
import dev.compatmanager.api.CompatibilityIssue;
import dev.compatmanager.api.IssueType;
import dev.compatmanager.api.IssueSeverity;
import dev.compatmanager.api.Solution;
import net.fabricmc.loader.api.FabricLoader;

import java.util.*;

public class KnownIncompatibilityDetector implements CompatibilityDetector {

    record IncompatPair(String modA, String modB, IssueSeverity severity, String reason, String patch) {}

    private static final List<IncompatPair> KNOWN_INCOMPATIBILITIES = List.of(
            new IncompatPair("sodium", "optifabric", IssueSeverity.CRITICAL,
                    "Sodium and OptiFabric use conflicting rendering pipelines",
                    null),
            new IncompatPair("iris", "optifabric", IssueSeverity.CRITICAL,
                    "Iris replaces OptiFabric's shader pipeline — use Iris instead of OptiFabric",
                    null),
            new IncompatPair("rubidium", "sodium", IssueSeverity.HIGH,
                    "Rubidium (Forge port of Sodium) conflicts with Fabric Sodium",
                    null),
            new IncompatPair("phosphor", "starlight", IssueSeverity.HIGH,
                    "Phosphor and Starlight both replace the light engine — pick one",
                    null),
            new IncompatPair("smoothboot", "lazydfu", IssueSeverity.MEDIUM,
                    "SmoothBoot and LazyDFU both modify startup — may cause ordering issues",
                    null),
            new IncompatPair("create", "immersive_engineering", IssueSeverity.LOW,
                    "Minor recipe conflicts between Create and Immersive Engineering — use a compat mod",
                    "create_ie_compat"),
            new IncompatPair("applied_energistics2", "refined_storage", IssueSeverity.MEDIUM,
                    "AE2 and Refined Storage provide overlapping storage networks",
                    null),
            new IncompatPair("betteranimals", "animatica", IssueSeverity.MEDIUM,
                    "BetterAnimals and Animatica both modify entity animations",
                    null)
    );

    @Override
    public String getId() { return "compatmanager:known_incompatibility"; }

    @Override
    public String getName() { return "Known Incompatibility Detector"; }

    @Override
    public List<CompatibilityIssue> detect() {
        List<CompatibilityIssue> issues = new ArrayList<>();
        FabricLoader loader = FabricLoader.getInstance();

        for (IncompatPair pair : KNOWN_INCOMPATIBILITIES) {
            if (loader.isModLoaded(pair.modA()) && loader.isModLoaded(pair.modB())) {
                CompatibilityIssue.Builder builder = CompatibilityIssue
                        .builder(IssueType.INCOMPATIBLE_MODS, pair.severity())
                        .description("compatmanager.issue.incompatible_mods", pair.modA(), pair.modB())
                        .affectedMod(pair.modA())
                        .affectedMod(pair.modB())
                        .technicalDetail(pair.reason());

                builder.solution(Solution.builder("compatmanager.solution.remove_mod", pair.modA()).build());
                builder.solution(Solution.builder("compatmanager.solution.remove_mod", pair.modB()).build());

                if (pair.patch() != null) {
                    builder.solution(Solution.builder(
                            "compatmanager.solution.use_compat_mod", pair.patch()).build());
                }

                issues.add(builder.build());
            }
        }

        return issues;
    }
}
