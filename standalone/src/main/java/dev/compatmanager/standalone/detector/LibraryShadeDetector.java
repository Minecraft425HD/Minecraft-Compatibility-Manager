package dev.compatmanager.standalone.detector;

import dev.compatmanager.standalone.StandaloneIssue;
import dev.compatmanager.standalone.StandaloneIssue.Severity;
import dev.compatmanager.standalone.db.ModDatabase;
import dev.compatmanager.standalone.parser.UnifiedModMeta;
import dev.compatmanager.standalone.platform.StandaloneModPlatform;

import java.io.File;
import java.util.*;
import java.util.zip.ZipFile;

public class LibraryShadeDetector implements IssueDetector {

    @Override
    public List<StandaloneIssue> detect(StandaloneModPlatform platform, ScanContext ctx) {
        List<StandaloneIssue> issues = new ArrayList<>();

        List<UnifiedModMeta>           mods      = platform.getLoadedMods();
        List<File>                     jars      = platform.getJarFiles();
        List<ModDatabase.LibrarySingleton> libs  = ModDatabase.getLibrarySingletons();

        // Map: libraryName → list of mod IDs that shade it
        Map<String, List<String>> libToMods = new LinkedHashMap<>();
        Map<String, List<String>> libToJars = new LinkedHashMap<>();

        for (int i = 0; i < mods.size() && i < jars.size(); i++) {
            String modId = mods.get(i).id();
            File   jar   = jars.get(i);

            Set<String> foundLibs = findShadedLibraries(jar, libs);
            for (String libName : foundLibs) {
                libToMods.computeIfAbsent(libName, k -> new ArrayList<>()).add(modId);
                libToJars.computeIfAbsent(libName, k -> new ArrayList<>()).add(jar.getName());
            }
        }

        for (ModDatabase.LibrarySingleton lib : libs) {
            List<String> affectedMods = libToMods.getOrDefault(lib.libraryName(), List.of());
            if (affectedMods.size() < 2) continue;

            issues.add(StandaloneIssue.builder("Shaded Library Conflict", lib.severity())
                    .affectedMods(affectedMods)
                    .description(affectedMods.size() + " mods each bundle their own copy of "
                            + lib.libraryName() + " (" + lib.packagePrefix() + "). "
                            + "Class loading conflicts may occur at runtime.")
                    .solutions(
                            "Check each mod's configuration for a 'disable bundled library' option",
                            "Prefer mods that depend on a shared library mod instead of bundling",
                            "This issue often resolves itself if only one version wins the classloader race"
                    )
                    .build());
        }

        return issues;
    }

    private Set<String> findShadedLibraries(File jar, List<ModDatabase.LibrarySingleton> libs) {
        Set<String> found = new HashSet<>();
        try (ZipFile zf = new ZipFile(jar)) {
            zf.stream()
                    .map(e -> e.getName())
                    .filter(name -> name.endsWith(".class"))
                    .forEach(name -> {
                        for (ModDatabase.LibrarySingleton lib : libs) {
                            if (name.startsWith(lib.packagePrefix())) {
                                found.add(lib.libraryName());
                            }
                        }
                    });
        } catch (Exception ignored) {}
        return found;
    }
}
