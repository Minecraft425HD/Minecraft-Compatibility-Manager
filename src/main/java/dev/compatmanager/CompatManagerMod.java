package dev.compatmanager;

import dev.compatmanager.api.CompatibilityDetector;
import dev.compatmanager.compat.CompatibilityScanner;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompatManagerMod implements ModInitializer {

    public static final String MOD_ID = "compatmanager";
    public static final Logger LOGGER = LoggerFactory.getLogger("CompatManager");

    @Override
    public void onInitialize() {
        LOGGER.info("Minecraft Compatibility Manager loaded.");
        // Scan runs eagerly at startup so results are ready when the player opens the UI
        new Thread(() -> {
            try {
                var issues = CompatibilityScanner.getInstance().scan();
                if (!issues.isEmpty()) {
                    LOGGER.warn("Compatibility scan found {} issue(s). Open the Compat Manager for details.", issues.size());
                }
            } catch (Exception e) {
                LOGGER.error("Compatibility scan failed: {}", e.getMessage(), e);
            }
        }, "CompatManager-InitScan").start();
    }

    /** Third-party mods can register custom detectors via this API. */
    public static void registerDetector(CompatibilityDetector detector) {
        CompatibilityScanner.getInstance().registerDetector(detector);
    }
}
