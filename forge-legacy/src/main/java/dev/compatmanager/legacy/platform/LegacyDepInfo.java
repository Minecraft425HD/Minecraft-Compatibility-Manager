package dev.compatmanager.legacy.platform;

public record LegacyDepInfo(String modId, String versionRange, Kind kind) {
    public enum Kind { REQUIRED, OPTIONAL, INCOMPATIBLE }
}
