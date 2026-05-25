package dev.compatmanager.standalone.parser;

import java.util.List;

/** Loader-agnostic representation of a mod's metadata, parsed from its JAR. */
public record UnifiedModMeta(
        String id,
        String version,
        String name,
        String description,
        String loaderType,      // "fabric", "forge", "neoforge", "quilt", "liteloader", "unknown"
        String mcVersionRange,
        List<DepEntry> dependencies
) {
    public record DepEntry(String modId, String versionRange, Kind kind) {
        public enum Kind { REQUIRED, OPTIONAL, INCOMPATIBLE }
    }
}
