package dev.compatmanager;

import dev.compatmanager.api.CompatibilityDetector;
import dev.compatmanager.compat.CompatibilityScanner;
import dev.compatmanager.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompatManagerCommon {

    public static final String MOD_ID = "compatmanager";
    public static final Logger LOGGER = LoggerFactory.getLogger("CompatManager");

    public static void init() {
        LOGGER.info("Minecraft Compatibility Manager initializing on {} {}.",
                Services.PLATFORM.getName(),
                Services.PLATFORM.getLoaderVersion());

        // Fire off background scan immediately so results are ready when the player opens the UI
        Thread t = new Thread(() -> {
            try {
                var issues = CompatibilityScanner.getInstance().scan();
                if (!issues.isEmpty()) {
                    LOGGER.warn("{} compatibility issue(s) found. Press F8 in-game for details.",
                            issues.size());
                } else {
                    LOGGER.info("No compatibility issues detected.");
                }
            } catch (Exception e) {
                LOGGER.error("Background scan failed: {}", e.getMessage(), e);
            }
        }, "CompatManager-InitScan");
        t.setDaemon(true);
        t.start();
    }

    /** Public API for third-party mods to register a custom detector. */
    public static void registerDetector(CompatibilityDetector detector) {
        CompatibilityScanner.getInstance().registerDetector(detector);
    }
}
