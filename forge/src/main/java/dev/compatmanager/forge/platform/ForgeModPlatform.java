package dev.compatmanager.forge.platform;

import dev.compatmanager.platform.DependencyInfo;
import dev.compatmanager.platform.ModInfo;
import dev.compatmanager.platform.ModPlatform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class ForgeModPlatform implements ModPlatform {

    @Override public String getName() { return "Forge"; }

    @Override
    public List<ModInfo> getLoadedMods() {
        return ModList.get().getMods().stream()
                .map(this::toModInfo)
                .toList();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Optional<ModInfo> getMod(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(c -> toModInfo(c.getModInfo()));
    }

    @Override public Path getGameDir()   { return FMLPaths.GAMEDIR.get(); }
    @Override public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }

    @Override
    public String getMinecraftVersion() {
        return FMLLoader.versionInfo().mcVersion();
    }

    @Override
    public String getLoaderVersion() {
        return FMLLoader.versionInfo().forgeVersion();
    }

    // ── Conversion helpers ────────────────────────────────────────────────

    private ModInfo toModInfo(IModInfo info) {
        List<DependencyInfo> deps = info.getDependencies().stream()
                .map(this::toDependencyInfo)
                .toList();
        return new ModInfo(
                info.getModId(),
                info.getVersion().toString(),
                info.getDisplayName(),
                info.getDescription(),
                deps
        );
    }

    private DependencyInfo toDependencyInfo(IModInfo.ModVersion dep) {
        String range = dep.getVersionRange() != null
                ? dep.getVersionRange().toString() : "*";

        DependencyInfo.Kind kind = switch (dep.getType()) {
            case REQUIRED  -> DependencyInfo.Kind.REQUIRED;
            case OPTIONAL  -> DependencyInfo.Kind.OPTIONAL;
            case INCOMPATIBLE, DISCOURAGED -> DependencyInfo.Kind.INCOMPATIBLE;
        };
        return new DependencyInfo(dep.getModId(), range, kind);
    }
}
