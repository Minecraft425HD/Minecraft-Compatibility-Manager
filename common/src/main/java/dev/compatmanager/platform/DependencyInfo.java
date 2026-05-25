package dev.compatmanager.platform;

public record DependencyInfo(
        String modId,
        String versionRange,
        Kind kind
) {
    public enum Kind {
        REQUIRED,    // must be present (depends / required)
        OPTIONAL,    // should be present (recommends / optional)
        INCOMPATIBLE // must NOT be present (breaks / conflicts)
    }
}
