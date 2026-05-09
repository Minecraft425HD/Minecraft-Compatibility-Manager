package dev.compatmanager.standalone.detector;

import java.nio.file.Path;

public record ScanContext(
        String mcVersion,
        String loaderHint,
        Path configDir,
        boolean verbose,
        boolean dryRun
) {
    public static ScanContext defaults() {
        return new ScanContext("", "", null, false, false);
    }
}
