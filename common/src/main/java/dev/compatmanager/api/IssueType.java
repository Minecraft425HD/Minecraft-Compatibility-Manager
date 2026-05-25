package dev.compatmanager.api;

public enum IssueType {
    VERSION_CONFLICT,
    MISSING_DEPENDENCY,
    INCOMPATIBLE_MODS,
    DUPLICATE_MOD,
    API_MISMATCH,
    CONFIG_CONFLICT,
    CLASS_CONFLICT;

    public String translationKey() {
        return "compatmanager.issue." + name().toLowerCase();
    }
}
