package dev.compatmanager.compat;

import dev.compatmanager.api.CompatibilityDetector;
import dev.compatmanager.api.CompatibilityIssue;
import dev.compatmanager.api.IssueSeverity;
import dev.compatmanager.detector.ApiVersionDetector;
import dev.compatmanager.detector.DuplicateModDetector;
import dev.compatmanager.detector.KnownIncompatibilityDetector;
import dev.compatmanager.detector.VersionConflictDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CompatibilityScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("CompatManager/Scanner");
    private static final CompatibilityScanner INSTANCE = new CompatibilityScanner();

    private final List<CompatibilityDetector> detectors = new CopyOnWriteArrayList<>();
    private List<CompatibilityIssue> lastResults = List.of();
    private boolean scanComplete = false;

    private CompatibilityScanner() {
        registerBuiltins();
    }

    public static CompatibilityScanner getInstance() { return INSTANCE; }

    private void registerBuiltins() {
        detectors.add(new VersionConflictDetector());
        detectors.add(new DuplicateModDetector());
        detectors.add(new KnownIncompatibilityDetector());
        detectors.add(new ApiVersionDetector());
    }

    public void registerDetector(CompatibilityDetector detector) {
        detectors.add(detector);
        LOGGER.info("Registered external detector: {}", detector.getId());
    }

    /** Runs all detectors synchronously and caches results. */
    public List<CompatibilityIssue> scan() {
        LOGGER.info("Starting compatibility scan with {} detectors...", detectors.size());
        List<CompatibilityIssue> all = new ArrayList<>();

        for (CompatibilityDetector detector : detectors) {
            try {
                List<CompatibilityIssue> found = detector.detect();
                LOGGER.info("Detector [{}] found {} issue(s)", detector.getId(), found.size());
                all.addAll(found);
            } catch (Exception e) {
                LOGGER.error("Detector [{}] threw an exception: {}", detector.getId(), e.getMessage(), e);
            }
        }

        // Sort: critical first, then by affected mod count (more = higher priority)
        all.sort(Comparator.comparingInt((CompatibilityIssue i) -> i.getSeverity().priority)
                .thenComparingInt(i -> -i.getAffectedMods().size()));

        lastResults = List.copyOf(all);
        scanComplete = true;

        int critical = (int) all.stream().filter(i -> i.getSeverity() == IssueSeverity.CRITICAL).count();
        LOGGER.info("Scan complete. {} total issue(s), {} critical.", all.size(), critical);
        return lastResults;
    }

    public List<CompatibilityIssue> getLastResults() { return lastResults; }
    public boolean isScanComplete() { return scanComplete; }
    public int getCriticalCount() {
        return (int) lastResults.stream().filter(i -> i.getSeverity() == IssueSeverity.CRITICAL).count();
    }
}
