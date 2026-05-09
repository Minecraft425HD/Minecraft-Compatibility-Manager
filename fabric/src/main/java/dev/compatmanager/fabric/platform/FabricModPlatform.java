package dev.compatmanager.fabric.platform;

import dev.compatmanager.platform.DependencyInfo;
import dev.compatmanager.platform.ModInfo;
import dev.compatmanager.platform.ModPlatform;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class FabricModPlatform implements ModPlatform {

    @Override public String getName() { return "Fabric"; }

    @Override
    public List<ModInfo> getLoadedMods() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(this::toModInfo)
                .toList();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Optional<ModInfo> getMod(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(this::toModInfo);
    }

    @Override public Path getGameDir()   { return FabricLoader.getInstance().getGameDir(); }
    @Override public Path getConfigDir() { return FabricLoader.getInstance().getConfigDir(); }

    @Override
    public String getMinecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public String getLoaderVersion() {
        return FabricLoader.getInstance().getModContainer("fabricloader")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    // ── Conversion helpers ────────────────────────────────────────────────

    private ModInfo toModInfo(ModContainer mc) {
        var meta = mc.getMetadata();
        List<DependencyInfo> deps = meta.getDependencies().stream()
                .map(this::toDependencyInfo)
                .toList();
        return new ModInfo(
                meta.getId(),
                meta.getVersion().getFriendlyString(),
                meta.getName(),
                meta.getDescription(),
                deps
        );
    }

    private DependencyInfo toDependencyInfo(ModDependency dep) {
        String range = dep.getVersionRequirements().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + " || " + b)
                .orElse("*");

        DependencyInfo.Kind kind = switch (dep.getKind()) {
            case DEPENDS   -> DependencyInfo.Kind.REQUIRED;
            case RECOMMENDS, SUGGESTS -> DependencyInfo.Kind.OPTIONAL;
            case BREAKS, CONFLICTS    -> DependencyInfo.Kind.INCOMPATIBLE;
        };
        return new DependencyInfo(dep.getModId(), range, kind);
    }
}
