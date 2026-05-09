package dev.compatmanager.standalone.platform;

import dev.compatmanager.standalone.parser.ModMetaParser;
import dev.compatmanager.standalone.parser.UnifiedModMeta;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class StandaloneModPlatform {

    private final Path modsDir;
    private final ModMetaParser parser = new ModMetaParser();

    private List<UnifiedModMeta> cache;
    private List<File>           jarCache;
    private Map<String, File>    jarMap;

    public StandaloneModPlatform(Path modsDir) {
        this.modsDir = modsDir;
    }

    public Path getModsDir() { return modsDir; }

    // ── Mod metadata ──────────────────────────────────────────────────────

    public List<UnifiedModMeta> getLoadedMods() {
        if (cache != null) return cache;
        buildCache();
        return cache;
    }

    public List<File> getJarFiles() {
        if (jarCache != null) return jarCache;
        buildCache();
        return jarCache;
    }

    public Map<String, File> getModJarMap() {
        if (jarMap != null) return jarMap;
        buildCache();
        return jarMap;
    }

    public Optional<File> getJarForMod(String modId) {
        return Optional.ofNullable(getModJarMap().get(modId.toLowerCase(Locale.ROOT)));
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
        getLoadedMods().forEach(m -> summary.merge(m.loaderType(), 1L, Long::sum));
        return summary;
    }

    // ── Cache build ───────────────────────────────────────────────────────

    private void buildCache() {
        List<UnifiedModMeta> mods = new ArrayList<>();
        List<File>           jars = new ArrayList<>();
        Map<String, File>    jmap = new LinkedHashMap<>();

        scanDir(modsDir.toFile(), mods, jars, jmap);

        // One level of subdirectories (some launchers use pack subfolders)
        File[] dirs = modsDir.toFile().listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir.getName().equals("disabled-by-compatmanager")) continue;
                scanDir(dir, mods, jars, jmap);
            }
        }

        cache    = Collections.unmodifiableList(mods);
        jarCache = Collections.unmodifiableList(jars);
        jarMap   = Collections.unmodifiableMap(jmap);
    }

    private void scanDir(File dir, List<UnifiedModMeta> mods, List<File> jars, Map<String, File> jmap) {
        File[] files = dir.listFiles(f ->
                f.isFile() && (f.getName().endsWith(".jar") || f.getName().endsWith(".litemod")));
        if (files == null) return;
        for (File jar : files) {
            parser.parse(jar).ifPresent(meta -> {
                mods.add(meta);
                jars.add(jar);
                jmap.putIfAbsent(meta.id().toLowerCase(Locale.ROOT), jar);
            });
        }
    }
}
