package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.io.File;
import java.util.*;
import java.util.zip.ZipFile;

public class ClassConflictDetector implements IssueDetector {

    private static final Set<String> SKIP_PREFIXES = Set.of(
            "java/", "javax/", "net/minecraft/", "com/mojang/", "META-INF/",
            "net/minecraftforge/", "net/neoforged/", "net/fabricmc/loader/",
            "org/jetbrains/annotations/", "javax/annotation/",
            "com/google/errorprone/", "org/checkerframework/"
    );

    private static final int CLASS_THRESHOLD = 5;

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        List<StandaloneIssue> issues = new ArrayList<>();

        List<UnifiedModMeta> mods = platform.getLoadedMods();
        List<File>           jars = platform.getJarFiles();

        // className → list of (modId, jarName) that contain it
        Map<String, List<String>> classOwners = new HashMap<>();

        for (int i = 0; i < mods.size() && i < jars.size(); i++) {
            String modId = mods.get(i).id();
            File   jar   = jars.get(i);
            collectClasses(jar, modId, classOwners);
        }

        // Find classes present in 2+ different mods
        Map<String, List<String>> conflicts = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : classOwners.entrySet()) {
            if (entry.getValue().size() >= 2) {
                Set<String> distinctMods = new LinkedHashSet<>(entry.getValue());
                if (distinctMods.size() >= 2) {
                    conflicts.put(entry.getKey(), new ArrayList<>(distinctMods));
                }
            }
        }

        if (conflicts.isEmpty()) return issues;

        // Group conflicts by the pair of mods involved
        Map<String, List<String>> pairToClasses = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : conflicts.entrySet()) {
            List<String> owners = entry.getValue();
            // Create a canonical key from the sorted mod list
            List<String> sorted = new ArrayList<>(new LinkedHashSet<>(owners));
            Collections.sort(sorted);
            String key = String.join("|", sorted);
            pairToClasses.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.getKey());
        }

        for (Map.Entry<String, List<String>> entry : pairToClasses.entrySet()) {
            List<String> affectedMods = List.of(entry.getKey().split("\\|"));
            List<String> classes      = entry.getValue();
            int count = classes.size();
            Severity sev = count >= CLASS_THRESHOLD ? Severity.HIGH : Severity.MEDIUM;

            List<String> top3 = classes.stream().limit(3).toList();
            String sample = String.join(", ", top3) + (count > 3 ? " …and " + (count - 3) + " more" : "");

            issues.add(StandaloneIssue.builder("Class File Conflict", sev)
                    .affectedMods(affectedMods)
                    .description(count + " class name(s) appear in multiple mods ("
                            + String.join(" + ", affectedMods) + "). "
                            + "Sample conflicts: " + sample + ". "
                            + "One mod's classes will shadow the other's, causing unexpected behavior.")
                    .solutions(
                            "Check if one of these mods should be replaced by the other",
                            "Look for updated versions that no longer share class names",
                            "Report the conflict to both mod authors"
                    )
                    .build());
        }

        return issues;
    }

    private void collectClasses(File jar, String modId, Map<String, List<String>> classOwners) {
        try (ZipFile zf = new ZipFile(jar)) {
            zf.stream()
                    .map(e -> e.getName())
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> !name.endsWith("module-info.class"))
                    .filter(name -> !name.endsWith("package-info.class"))
                    .filter(name -> SKIP_PREFIXES.stream().noneMatch(name::startsWith))
                    .forEach(name -> classOwners
                            .computeIfAbsent(name, k -> new ArrayList<>())
                            .add(modId));
        } catch (Exception ignored) {}
    }
}
