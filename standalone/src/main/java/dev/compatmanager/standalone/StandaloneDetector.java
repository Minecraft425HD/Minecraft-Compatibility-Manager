package dev.compatmanager.standalone;

import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.util.*;

/**
 * All detection logic operating purely on UnifiedModMeta — no MC runtime needed.
 */
public class StandaloneDetector {

    private final StandaloneModPlatform platform;

    public StandaloneDetector(StandaloneModPlatform platform) {
        this.platform = platform;
    }

    public List<StandaloneIssue> detectAll() {
        List<StandaloneIssue> issues = new ArrayList<>();
        issues.addAll(detectVersionConflicts());
        issues.addAll(detectDuplicates());
        issues.addAll(detectKnownIncompatibilities());
        issues.addAll(detectMixedLoaders());

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
                            if (!dep.versionRange().isBlank()
                                    && !dep.versionRange().equals("*")
                                    && !versionSatisfied(installed, dep.versionRange())) {
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
                            issues.add(new StandaloneIssue(
                                    "Incompatible Mods",
                                    StandaloneIssue.Severity.CRITICAL,
                                    List.of(mod.id(), dep.modId()),
                                    mod.id() + " declares " + dep.modId() + " as incompatible.",
                                    List.of("Remove " + dep.modId(), "Remove " + mod.id())
                            ));
                        }
                    }
                    default -> {}
                }
            }
        }
        return issues;
    }

    // ── Duplicate detection ───────────────────────────────────────────────

    private static final List<Set<String>> FUNCTIONAL_GROUPS = List.of(
            Set.of("sodium", "optifabric", "rubidium", "embeddium", "replaymod"),
            Set.of("iris", "optifabric"),
            Set.of("phosphor", "starlight", "moonrise"),
            Set.of("journeymap", "voxelmap", "xaeros_minimap", "xaerosminimap"),
            Set.of("applied_energistics2", "refined_storage"),
            Set.of("lazydfu", "datafixer_slayer"),
            Set.of("terraforged", "terralith"),
            Set.of("oh_the_biomes_youll_go", "biomes_o_plenty")
    );

    private List<StandaloneIssue> detectDuplicates() {
        List<StandaloneIssue> issues = new ArrayList<>();
        Set<String> allIds = new HashSet<>();
        Map<String, Long> idCount = new HashMap<>();

        for (UnifiedModMeta mod : platform.getLoadedMods()) {
            allIds.add(mod.id());
            idCount.merge(mod.id(), 1L, Long::sum);
        }

        // Same ID loaded more than once
        idCount.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .forEach(e -> issues.add(new StandaloneIssue(
                        "Duplicate Mod",
                        StandaloneIssue.Severity.CRITICAL,
                        List.of(e.getKey()),
                        "'" + e.getKey() + "' is present " + e.getValue()
                                + " times in the mods folder (multiple JAR files).",
                        List.of("Remove duplicate JAR files for " + e.getKey())
                )));

        // Functional overlaps
        for (Set<String> group : FUNCTIONAL_GROUPS) {
            List<String> present = group.stream().filter(allIds::contains).sorted().toList();
            if (present.size() < 2) continue;

            List<String> solutions = present.stream()
                    .map(id -> "Remove " + id + " and keep one of the alternatives")
                    .toList();

            issues.add(new StandaloneIssue(
                    "Functional Duplicate",
                    StandaloneIssue.Severity.HIGH,
                    present,
                    "These mods provide overlapping functionality: " + String.join(", ", present),
                    solutions
            ));
        }
        return issues;
    }

    // ── Known incompatibility database ────────────────────────────────────

    record KnownPair(String a, String b, StandaloneIssue.Severity sev, String reason, String patch) {}

    private static final List<KnownPair> KNOWN = List.of(
            new KnownPair("sodium",        "optifabric",        StandaloneIssue.Severity.CRITICAL,
                    "Sodium and OptiFabric use conflicting rendering pipelines.", null),
            new KnownPair("iris",          "optifabric",        StandaloneIssue.Severity.CRITICAL,
                    "Iris replaces OptiFabric's shader system entirely.", null),
            new KnownPair("rubidium",      "embeddium",         StandaloneIssue.Severity.HIGH,
                    "Rubidium and Embeddium are both Forge ports of Sodium — keep one.", null),
            new KnownPair("phosphor",      "starlight",         StandaloneIssue.Severity.HIGH,
                    "Both replace the Minecraft light engine.", null),
            new KnownPair("starlight",     "moonrise",          StandaloneIssue.Severity.HIGH,
                    "Both replace the Minecraft light engine.", null),
            new KnownPair("terraforged",   "terralith",         StandaloneIssue.Severity.MEDIUM,
                    "Both overhaul world generation.", null),
            new KnownPair("create",        "immersive_engineering", StandaloneIssue.Severity.LOW,
                    "Minor recipe conflicts.", "create_crafts_additions"),
            new KnownPair("applied_energistics2", "refined_storage", StandaloneIssue.Severity.MEDIUM,
                    "Both provide storage networks — recipe conflicts.", null),
            // Legacy 1.7-1.12 known pairs
            new KnownPair("optifine",      "fastcraft",         StandaloneIssue.Severity.HIGH,
                    "OptiFine and FastCraft conflict on chunk rendering (1.7-1.12).", null),
            new KnownPair("optifine",      "betterfoliage",     StandaloneIssue.Severity.MEDIUM,
                    "OptiFine and BetterFoliage can conflict on shader rendering.", null),
            new KnownPair("codechickencore", "codechickenlib",  StandaloneIssue.Severity.HIGH,
                    "CodeChickenCore and CodeChickenLib are different versions of the same lib.", null),
            new KnownPair("nei",           "jei",               StandaloneIssue.Severity.HIGH,
                    "NEI (Not Enough Items) and JEI (Just Enough Items) conflict on recipe display.", null),
            new KnownPair("inventorytweaks", "inventorysorter",  StandaloneIssue.Severity.MEDIUM,
                    "Both modify inventory sorting behavior.", null)
    );

    private List<StandaloneIssue> detectKnownIncompatibilities() {
        List<StandaloneIssue> issues = new ArrayList<>();
        for (KnownPair p : KNOWN) {
            if (platform.isModLoaded(p.a()) && platform.isModLoaded(p.b())) {
                List<String> solutions = new ArrayList<>(List.of(
                        "Remove " + p.a(), "Remove " + p.b()));
                if (p.patch() != null)
                    solutions.add("Or install compatibility patch: " + p.patch());

                issues.add(new StandaloneIssue(
                        "Known Incompatibility",
                        p.sev(),
                        List.of(p.a(), p.b()),
                        p.reason(),
                        solutions
                ));
            }
        }
        return issues;
    }

    // ── Mixed loader detection ────────────────────────────────────────────

    private List<StandaloneIssue> detectMixedLoaders() {
        List<StandaloneIssue> issues = new ArrayList<>();
        Set<String> loaderTypes = new HashSet<>();

        for (UnifiedModMeta mod : platform.getLoadedMods()) {
            if (!mod.loaderType().equals("unknown")) {
                loaderTypes.add(mod.loaderType());
            }
        }

        // Forge mods in a Fabric environment (or vice versa)
        boolean hasFabric  = loaderTypes.contains("fabric") || loaderTypes.contains("quilt");
        boolean hasForge   = loaderTypes.contains("forge")  || loaderTypes.contains("forge-legacy");
        boolean hasNeoForge = loaderTypes.contains("neoforge");

        if (hasFabric && (hasForge || hasNeoForge)) {
            issues.add(new StandaloneIssue(
                    "Mixed Loader Types",
                    StandaloneIssue.Severity.CRITICAL,
                    List.of("fabric/quilt mods", "forge/neoforge mods"),
                    "Your mods folder contains a mix of Fabric and Forge mods. "
                            + "These are NOT compatible — each mod only runs on its specific loader.",
                    List.of(
                            "Separate your mods into different mods folders for each loader",
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
                    "Forge mods (1.20.1 and earlier) and NeoForge mods may not be binary-compatible.",
                    List.of(
                            "Verify each mod supports your specific loader version",
                            "Check if NeoForge-specific mods have Forge equivalents"
                    )
            ));
        }

        return issues;
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private boolean versionSatisfied(String installed, String range) {
        try {
            if (range.startsWith(">=")) return cmp(installed, range.substring(2)) >= 0;
            if (range.startsWith(">"))  return cmp(installed, range.substring(1)) >  0;
            if (range.startsWith("<=")) return cmp(installed, range.substring(2)) <= 0;
            if (range.startsWith("<"))  return cmp(installed, range.substring(1)) <  0;
            if (range.startsWith("~"))  {
                String prefix = range.substring(1, range.lastIndexOf('.'));
                return installed.startsWith(prefix);
            }
            if (range.contains(",")) {
                char lo = range.charAt(0), hi = range.charAt(range.length() - 1);
                String[] parts = range.substring(1, range.length() - 1).split(",", 2);
                int loC = cmp(installed, parts[0].trim());
                boolean loOk = lo == '[' ? loC >= 0 : loC > 0;
                boolean hiOk = parts[1].trim().isEmpty() ||
                        (hi == ']'
                                ? cmp(installed, parts[1].trim()) <= 0
                                : cmp(installed, parts[1].trim()) < 0);
                return loOk && hiOk;
            }
            return installed.equals(range);
        } catch (Exception e) { return true; }
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
