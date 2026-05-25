package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.io.File;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * Detects mod namespace conflicts.
 *
 * Each mod should own a unique namespace under assets/ and data/.
 * If two mods both populate assets/examplemod/ they will silently override
 * each other's textures, models, and sounds.
 */
public class NamespaceConflictDetector implements IssueDetector {

    private static final Set<String> VANILLA_NAMESPACES = Set.of(
            "minecraft", "realms", "brigadier", "fabric", "forge", "neoforge"
    );

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        List<StandaloneIssue> issues = new ArrayList<>();

        List<UnifiedModMeta> mods = platform.getLoadedMods();
        List<File>           jars = platform.getJarFiles();

        // namespace → list of modIds claiming it
        Map<String, List<String>> nsToMods = new LinkedHashMap<>();

        for (int i = 0; i < mods.size() && i < jars.size(); i++) {
            String modId = mods.get(i).id();
            File   jar   = jars.get(i);

            for (String ns : extractNamespaces(jar)) {
                if (VANILLA_NAMESPACES.contains(ns)) continue;
                nsToMods.computeIfAbsent(ns, k -> new ArrayList<>()).add(modId);
            }
        }

        for (Map.Entry<String, List<String>> entry : nsToMods.entrySet()) {
            List<String> claimants = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
            if (claimants.size() < 2) continue;

            String ns = entry.getKey();
            issues.add(StandaloneIssue.builder("Namespace Conflict", Severity.HIGH)
                    .affectedMods(claimants)
                    .description("Multiple mods claim the resource namespace '" + ns + "': "
                            + String.join(", ", claimants) + ". "
                            + "One mod's textures, models, or sounds will silently override the other's.")
                    .solutions(
                            "Check if one of these mods is a resource pack add-on for the other",
                            "Report the namespace conflict to the mod authors",
                            "Only load one of: " + String.join(", ", claimants)
                    )
                    .build());
        }

        return issues;
    }

    private Set<String> extractNamespaces(File jar) {
        Set<String> namespaces = new HashSet<>();
        try (ZipFile zf = new ZipFile(jar)) {
            zf.stream()
                    .map(e -> e.getName())
                    .filter(n -> n.startsWith("assets/") || n.startsWith("data/"))
                    .forEach(name -> {
                        String[] parts = name.split("/");
                        if (parts.length >= 2 && !parts[1].isBlank()) {
                            namespaces.add(parts[1]);
                        }
                    });
        } catch (Exception ignored) {}
        return namespaces;
    }
}
