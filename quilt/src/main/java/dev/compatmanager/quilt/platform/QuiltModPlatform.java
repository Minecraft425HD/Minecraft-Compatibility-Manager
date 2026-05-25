package dev.compatmanager.quilt.platform;

import dev.compatmanager.platform.DependencyInfo;
import dev.compatmanager.platform.ModInfo;
import dev.compatmanager.platform.ModPlatform;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.ModDependency;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class QuiltModPlatform implements ModPlatform {

    @Override public String getName() { return "Quilt"; }

    @Override
    public List<ModInfo> getLoadedMods() {
        return QuiltLoader.getAllMods().stream()
                .map(this::toModInfo)
                .toList();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return QuiltLoader.isModLoaded(modId);
    }

    @Override
    public Optional<ModInfo> getMod(String modId) {
        return QuiltLoader.getAllMods().stream()
                .filter(m -> m.metadata().id().equals(modId))
                .findFirst()
                .map(this::toModInfo);
    }

    @Override public Path getGameDir()   { return QuiltLoader.getGameDir(); }
    @Override public Path getConfigDir() { return QuiltLoader.getConfigDir(); }

    @Override
    public String getMinecraftVersion() {
        return QuiltLoader.getAllMods().stream()
                .filter(m -> m.metadata().id().equals("minecraft"))
                .findFirst()
                .map(m -> m.metadata().version().raw())
                .orElse("unknown");
    }

    @Override
    public String getLoaderVersion() {
        return QuiltLoader.getAllMods().stream()
                .filter(m -> m.metadata().id().equals("quilt_loader"))
                .findFirst()
                .map(m -> m.metadata().version().raw())
                .orElse("unknown");
    }

    // ── Conversion helpers ────────────────────────────────────────────────

    private ModInfo toModInfo(ModContainer mc) {
        var meta = mc.metadata();
        List<DependencyInfo> deps = meta.depends().stream()
                .map(this::toDependencyInfo)
                .toList();
        return new ModInfo(
                meta.id(),
                meta.version().raw(),
                meta.name().orElse(meta.id()),
                meta.description().orElse(""),
                deps
        );
    }

    private DependencyInfo toDependencyInfo(ModDependency dep) {
        String range = dep.versions().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + " || " + b)
                .orElse("*");

        DependencyInfo.Kind kind = dep.shouldExclude()
                ? DependencyInfo.Kind.INCOMPATIBLE
                : DependencyInfo.Kind.REQUIRED;

        return new DependencyInfo(dep.id().id(), range, kind);
    }
}
