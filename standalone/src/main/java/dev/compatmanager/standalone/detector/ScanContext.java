package dev.compatmanager.standalone.detector;

import java.nio.file.Path;

public record ScanContext(
        String mcVersion,
        String loaderHint,
        Path configDir,
        boolean verbose,
        boolean dryRun,
        boolean offline,
        boolean updateDb,
        String curseForgeKey
) {
    public static ScanContext defaults() {
        return new ScanContext("", "", null, false, false, false, false, "");
    }
}
