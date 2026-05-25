package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.io.File;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * Detects vanilla resource overrides shared by multiple mods.
 *
 * When two mods both replace assets/minecraft/textures/block/stone.png,
 * only one wins — the other's change is silently discarded. This causes
 * visual glitches that are hard to track down.
 */
public class ResourceConflictDetector implements IssueDetector {

    // Only flag actual content files, not .mcmeta sidecars or model arrays
    private static final Set<String> CHECKED_EXTENSIONS = Set.of(
            ".png", ".ogg", ".json", ".fsh", ".vsh", ".glsl"
    );

    // Directories inside assets/minecraft/ that matter
    private static final Set<String> CHECKED_PATHS = Set.of(
            "assets/minecraft/textures/",
            "assets/minecraft/models/",
            "assets/minecraft/sounds/",
            "assets/minecraft/shaders/",
            "assets/minecraft/blockstates/",
            "data/minecraft/recipes/",
            "data/minecraft/loot_tables/",
            "data/minecraft/tags/"
    );

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        List<StandaloneIssue> issues = new ArrayList<>();

        List<UnifiedModMeta> mods = platform.getLoadedMods();
        List<File>           jars = platform.getJarFiles();

        // resource path → list of modIds that provide it
        Map<String, List<String>> pathToMods = new LinkedHashMap<>();

        for (int i = 0; i < mods.size() && i < jars.size(); i++) {
            String modId = mods.get(i).id();
            File   jar   = jars.get(i);

            for (String path : collectVanillaOverrides(jar)) {
                pathToMods.computeIfAbsent(path, k -> new ArrayList<>()).add(modId);
            }
        }

        // Group conflicts by mod pair to avoid flooding output
        Map<String, List<String>> pairToFiles = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : pathToMods.entrySet()) {
            List<String> providers = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
            if (providers.size() < 2) continue;

            List<String> sorted = new ArrayList<>(providers);
            Collections.sort(sorted);
            String key = String.join("|", sorted);
            pairToFiles.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.getKey());
        }

        for (Map.Entry<String, List<String>> entry : pairToFiles.entrySet()) {
            List<String> affectedMods = List.of(entry.getKey().split("\\|"));
            List<String> files        = entry.getValue();
            int count = files.size();
            List<String> sample = files.stream().limit(3).toList();
            String sampleStr = String.join(", ", sample) + (count > 3 ? " …+" + (count - 3) + " more" : "");

            issues.add(StandaloneIssue.builder("Vanilla Resource Override Conflict",
                            count >= 10 ? Severity.HIGH : Severity.MEDIUM)
                    .affectedMods(affectedMods)
                    .description(count + " vanilla resource(s) are overridden by multiple mods ("
                            + String.join(" + ", affectedMods) + "). "
                            + "Only the last-loaded mod's version will appear. Sample: " + sampleStr)
                    .solutions(
                            "Use a resource pack to control which mod's assets take priority",
                            "Check if one mod is designed as a companion/override for the other",
                            "Report the overlap to both mod authors"
                    )
                    .build());
        }

        return issues;
    }

    private Set<String> collectVanillaOverrides(File jar) {
        Set<String> overrides = new HashSet<>();
        try (ZipFile zf = new ZipFile(jar)) {
            zf.stream()
                    .map(e -> e.getName())
                    .filter(name -> !name.endsWith("/"))
                    .filter(this::isCheckedPath)
                    .filter(this::isCheckedExtension)
                    .forEach(overrides::add);
        } catch (Exception ignored) {}
        return overrides;
    }

    private boolean isCheckedPath(String name) {
        for (String prefix : CHECKED_PATHS) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isCheckedExtension(String name) {
        for (String ext : CHECKED_EXTENSIONS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }
}
