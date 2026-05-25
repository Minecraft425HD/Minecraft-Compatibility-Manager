package dev.compatmanager.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Loader-agnostic access to the running mod platform.
 * Implemented separately for Fabric, Forge, NeoForge and Quilt.
 */
public interface ModPlatform {

    /** Returns "Fabric", "Forge", "NeoForge", or "Quilt". */
    String getName();

    /** Returns ALL loaded mods, including the loader itself and sub-mods. */
    List<ModInfo> getLoadedMods();

    boolean isModLoaded(String modId);

    Optional<ModInfo> getMod(String modId);

    /** The game root directory (contains saves/, mods/, …). */
    Path getGameDir();

    /** The config directory. */
    Path getConfigDir();

    /** Minecraft version string, e.g. "1.20.1". */
    String getMinecraftVersion();

    /** Loader version string, e.g. "0.15.11" for Fabric. */
    String getLoaderVersion();
}
