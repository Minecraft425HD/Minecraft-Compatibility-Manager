package dev.compatmanager.standalone;

import dev.compatmanager.standalone.db.ModDatabase;
import dev.compatmanager.standalone.db.OnlineDatabase;
import dev.compatmanager.standalone.db.VersionUtils;
import dev.compatmanager.standalone.detector.*;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.util.*;

public class StandaloneDetector {

    private final StandaloneModPlatform platform;
    private final ScanContext           ctx;

    private static final List<IssueDetector> EXTRA_DETECTORS = List.of(
            new ClassConflictDetector(),
            new JavaBytecodeDetector(),
            new LibraryShadeDetector(),
            new ConfigConflictDetector(),
            new MixinConflictDetector(),
            new NamespaceConflictDetector(),
            new ResourceConflictDetector(),
            new CoremodConflictDetector(),
            new TransitiveDependencyDetector(),
            new ChangelogConflictDetector(),
            new PackManifestValidator(),
            new ModrinthCompatDetector(),
            new CurseForgeCompatDetector()
    );

    public StandaloneDetector(StandaloneModPlatform platform) {
        this(platform, ScanContext.defaults());
    }

    public StandaloneDetector(StandaloneModPlatform platform, ScanContext ctx) {
        this.platform = platform;
        this.ctx      = ctx;
    }

    public List<StandaloneIssue> detectAll() {
        List<StandaloneIssue> issues = new ArrayList<>();

        issues.addAll(detectVersionConflicts());
        issues.addAll(detectDuplicates());
        issues.addAll(detectKnownIncompatibilities());
        issues.addAll(detectFunctionalGroups());
        issues.addAll(detectRequiredCompanions());
        issues.addAll(detectMixedLoaders());

        for (IssueDetector detector : EXTRA_DETECTORS) {
            try {
                issues.addAll(detector.detect(platform, ctx));
            } catch (Exception e) {
                // Never crash the whole scan due to one detector failing
            }
        }

        issues.sort(Comparator.comparingInt(i -> i.severity().ordinal()));
        return deduplicate(issues);
    }

    // ── Version / Dependency conflicts ────────────────────────────────────

    private List<StandaloneIssue> detectVersionConflicts() {
        List<StandaloneIssue> issues = new ArrayList<>();
        for (UnifiedModMeta mod : platform.getLoadedMods()) {
            for (UnifiedModMeta.DepEntry dep : mod.dependencies()) {
                switch (dep.kind()) {
                    case REQUIRED -> {
                        Optional<UnifiedModMeta> found = platform.getMod(dep.modId());
                        if (found.isEmpty()) {
                            issues.add(new StandaloneIssue(
                                    "Missing Dependency",
                                    StandaloneIssue.Severity.CRITICAL,
                                    List.of(mod.id(), dep.modId()),
                                    mod.id() + " requires '" + dep.modId() + "' "
                                            + dep.versionRange() + " which is not installed.",
                                    List.of("Install the missing mod: " + dep.modId()
                                            + " " + dep.versionRange())
                            ));
                        } else {
                            String installed = found.get().version();
                            if (!dep.versionRange().isBlank() && !dep.versionRange().equals("*")
                                    && !VersionUtils.versionInRange(installed, dep.versionRange())) {
                                issues.add(new StandaloneIssue(
                                        "Version Conflict",
                                        StandaloneIssue.Severity.CRITICAL,
                                        List.of(mod.id(), dep.modId()),
                                        mod.id() + " needs " + dep.modId()
                                                + " " + dep.versionRange()
                                                + " but " + installed + " is installed.",
                                        List.of("Update " + dep.modId() + " to " + dep.versionRange())
                                ));
                            }
                        }
                    }
                    case INCOMPATIBLE -> {
                        if (platform.isModLoaded(dep.modId())) {
                            Optional<String> jar = platform.getJarForMod(dep.modId())
                                    .map(f -> f.getAbsolutePath());
                            issues.add(StandaloneIssue.builder("Incompatible Mods",
                                            StandaloneIssue.Severity.CRITICAL)
                                    .affectedMods(mod.id(), dep.modId())
                                    .description(mod.id() + " declares " + dep.modId() + " as incompatible.")
                                    .solutions("Remove " + dep.modId(), "Remove " + mod.id())
                                    .jarPath(jar.orElse(""))
                                    .build());
                        }
                    }
                    default -> {}
                }
            }
        }
        return issues;
    }

    // ── Duplicate mod IDs ─────────────────────────────────────────────────

    private List<StandaloneIssue> detectDuplicates() {
        List<StandaloneIssue> issues = new ArrayList<>();
        Map<String, Long> idCount = new HashMap<>();
        platform.getLoadedMods().forEach(m -> idCount.merge(m.id(), 1L, Long::sum));
        idCount.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .forEach(e -> issues.add(new StandaloneIssue(
                        "Duplicate Mod",
                        StandaloneIssue.Severity.CRITICAL,
                        List.of(e.getKey()),
                        "'" + e.getKey() + "' is present " + e.getValue()
                                + " times in the mods folder.",
                        List.of("Remove duplicate JAR files for " + e.getKey())
                )));
        return issues;
    }

    // ── Known incompatibility database ────────────────────────────────────

    private List<StandaloneIssue> detectKnownIncompatibilities() {
        List<StandaloneIssue> issues = new ArrayList<>();
        List<ModDatabase.IncompatPair> pairs = new ArrayList<>(ModDatabase.getIncompatPairsForMcVersion(ctx.mcVersion()));
        // Merge online database pairs (auto-updating community list)
        try {
            pairs.addAll(OnlineDatabase.getExtraPairs(ctx.offline()));
        } catch (Exception ignored) {}

        for (ModDatabase.IncompatPair p : pairs) {
            if (!platform.isModLoaded(p.modA()) || !platform.isModLoaded(p.modB())) continue;

            List<String> solutions = new ArrayList<>(List.of(
                    "Remove " + p.modA(), "Remove " + p.modB()));
            if (p.patchMod() != null)
                solutions.add("Or install compatibility patch: " + p.patchMod());

            // Use modB as the auto-fix target (disable the second/newer one)
            String jarPath = platform.getJarForMod(p.modB())
                    .map(f -> f.getAbsolutePath()).orElse("");

            issues.add(StandaloneIssue.builder("Known Incompatibility", p.severity())
                    .affectedMods(p.modA(), p.modB())
                    .description("[" + p.category() + "] " + p.reason())
                    .solutions(solutions)
                    .jarPath(jarPath)
                    .build());
        }
        return issues;
    }

    // ── Functional groups ─────────────────────────────────────────────────

    private List<StandaloneIssue> detectFunctionalGroups() {
        List<StandaloneIssue> issues = new ArrayList<>();
        Set<String> allIds = new HashSet<>();
        platform.getLoadedMods().forEach(m -> allIds.add(m.id().toLowerCase()));

        for (ModDatabase.FunctionalGroup group : ModDatabase.getFunctionalGroups()) {
            List<String> present = group.modIds().stream()
                    .filter(allIds::contains)
                    .sorted()
                    .toList();
            if (present.size() < 2) continue;

            // Auto-fix: disable all except the first alphabetically
            String keepMod  = present.get(0);
            String disableMod = present.get(1);
            String jarPath  = platform.getJarForMod(disableMod)
                    .map(f -> f.getAbsolutePath()).orElse("");

            List<String> solutions = present.stream()
                    .map(id -> "Keep " + id + " and remove the others")
                    .toList();

            issues.add(StandaloneIssue.builder("Functional Duplicate", group.severity())
                    .affectedMods(present)
                    .description("[" + group.category() + "] " + group.groupName()
                            + " — these mods provide overlapping functionality: "
                            + String.join(", ", present))
                    .solutions(solutions)
                    .jarPath(jarPath)
                    .build());
        }
        return issues;
    }

    // ── Required companions ───────────────────────────────────────────────

    private List<StandaloneIssue> detectRequiredCompanions() {
        List<StandaloneIssue> issues = new ArrayList<>();
        for (ModDatabase.RequiredCompanion companion : ModDatabase.getRequiredCompanions()) {
            if (!platform.isModLoaded(companion.primaryMod())) continue;
            if (platform.isModLoaded(companion.requiredMod()))  continue;

            List<String> solutions = new ArrayList<>();
            solutions.add("Install " + companion.requiredMod());
            if (companion.modrinthUrl() != null)
                solutions.add("Download from Modrinth: " + companion.modrinthUrl());

            issues.add(new StandaloneIssue(
                    "Missing Required Companion",
                    StandaloneIssue.Severity.HIGH,
                    List.of(companion.primaryMod(), companion.requiredMod()),
                    companion.reason(),
                    solutions
            ));
        }
        return issues;
    }

    // ── Mixed loader detection ────────────────────────────────────────────

    private List<StandaloneIssue> detectMixedLoaders() {
        List<StandaloneIssue> issues = new ArrayList<>();
        Set<String> loaderTypes = new HashSet<>();
        platform.getLoadedMods().forEach(m -> {
            if (!m.loaderType().equals("unknown")) loaderTypes.add(m.loaderType());
        });

        boolean hasFabric   = loaderTypes.contains("fabric") || loaderTypes.contains("quilt");
        boolean hasForge    = loaderTypes.contains("forge")  || loaderTypes.contains("forge-legacy");
        boolean hasNeoForge = loaderTypes.contains("neoforge");

        if (hasFabric && (hasForge || hasNeoForge)) {
            issues.add(new StandaloneIssue(
                    "Mixed Loader Types",
                    StandaloneIssue.Severity.CRITICAL,
                    List.of("fabric/quilt mods", "forge/neoforge mods"),
                    "Your mods folder contains a mix of Fabric and Forge mods. "
                            + "These are NOT compatible — each mod only runs on its specific loader.",
                    List.of(
                            "Separate your mods into different folders for each loader",
                            "Use Sinytra Connector (Fabric) to run some Forge mods on Fabric",
                            "Check each mod's page to confirm it supports your loader"
                    )
            ));
        }
        if (hasForge && hasNeoForge) {
            issues.add(new StandaloneIssue(
                    "Forge + NeoForge Mix",
                    StandaloneIssue.Severity.HIGH,
                    List.of("forge mods", "neoforge mods"),
                    "Forge mods (≤1.20.1) and NeoForge mods may not be binary-compatible.",
                    List.of(
                            "Verify each mod supports your specific loader version",
                            "Check if NeoForge mods have Forge equivalents"
                    )
            ));
        }
        return issues;
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private List<StandaloneIssue> deduplicate(List<StandaloneIssue> in) {
        Set<String> seen = new LinkedHashSet<>();
        List<StandaloneIssue> out = new ArrayList<>();
        for (StandaloneIssue issue : in) {
            String key = issue.type() + "|" + issue.affectedMods().stream().sorted().toList();
            if (seen.add(key)) out.add(issue);
        }
        return out;
    }
}
