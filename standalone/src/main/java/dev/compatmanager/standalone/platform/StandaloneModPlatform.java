package dev.compatmanager.standalone.platform;

import dev.compatmanager.standalone.parser.ModMetaParser;
import dev.compatmanager.standalone.parser.UnifiedModMeta;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Builds a mod list purely from the file system (no Minecraft running).
 * This works for every Minecraft version ever released.
 */
public class StandaloneModPlatform {

    private final Path modsDir;
    private final ModMetaParser parser = new ModMetaParser();
    private List<UnifiedModMeta> cache;

    public StandaloneModPlatform(Path modsDir) {
        this.modsDir = modsDir;
    }

    public List<UnifiedModMeta> getLoadedMods() {
        if (cache != null) return cache;

        List<UnifiedModMeta> mods = new ArrayList<>();
        File[] jars = modsDir.toFile().listFiles(f ->
                f.isFile() && (f.getName().endsWith(".jar") || f.getName().endsWith(".litemod")));

        if (jars != null) {
            for (File jar : jars) {
                parser.parse(jar).ifPresent(mods::add);
            }
        }

        // Also scan one level of subdirectories (some packs use subfolders)
        File[] dirs = modsDir.toFile().listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                File[] subJars = dir.listFiles(f ->
                        f.isFile() && f.getName().endsWith(".jar"));
                if (subJars != null) {
                    for (File jar : subJars) {
                        parser.parse(jar).ifPresent(mods::add);
                    }
                }
            }
        }

        cache = Collections.unmodifiableList(mods);
        return cache;
    }

    public Optional<UnifiedModMeta> getMod(String modId) {
        return getLoadedMods().stream()
                .filter(m -> m.id().equalsIgnoreCase(modId))
                .findFirst();
    }

    public boolean isModLoaded(String modId) {
        return getMod(modId).isPresent();
    }

    public Map<String, Long> getLoaderSummary() {
        Map<String, Long> summary = new LinkedHashMap<>();
        getLoadedMods().forEach(m ->
                summary.merge(m.loaderType(), 1L, Long::sum));
        return summary;
    }
}
