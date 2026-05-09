package dev.compatmanager.api;

public enum IssueType {
    VERSION_CONFLICT,
    CLASS_CONFLICT,
    REGISTRY_CONFLICT,
    MISSING_DEPENDENCY,
    CONFIG_CONFLICT,
    API_MISMATCH,
    DUPLICATE_MOD,
    INCOMPATIBLE_MODS;

    public String translationKey() {
        return "compatmanager.issue." + name().toLowerCase();
    }
}
