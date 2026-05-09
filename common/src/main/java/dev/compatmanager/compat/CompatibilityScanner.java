package dev.compatmanager.compat;

import dev.compatmanager.api.CompatibilityDetector;
import dev.compatmanager.api.CompatibilityIssue;
import dev.compatmanager.api.IssueSeverity;
import dev.compatmanager.detector.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CompatibilityScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("CompatManager/Scanner");
    private static final CompatibilityScanner INSTANCE = new CompatibilityScanner();

    private final List<CompatibilityDetector> detectors = new CopyOnWriteArrayList<>();
    private volatile List<CompatibilityIssue> lastResults = List.of();
    private volatile boolean complete = false;

    private CompatibilityScanner() {
        detectors.add(new VersionConflictDetector());
        detectors.add(new DuplicateModDetector());
        detectors.add(new KnownIncompatibilityDetector());
        detectors.add(new ApiVersionDetector());
    }

    public static CompatibilityScanner getInstance() { return INSTANCE; }

    /** Called by third-party mods to register a custom detector. */
    public void registerDetector(CompatibilityDetector detector) {
        detectors.add(detector);
        LOGGER.info("Registered external detector: {}", detector.getId());
    }

    /** Runs all detectors synchronously and caches the results. Thread-safe. */
    public synchronized List<CompatibilityIssue> scan() {
        LOGGER.info("Starting compatibility scan ({} detectors)...", detectors.size());
        List<CompatibilityIssue> all = new ArrayList<>();

        for (CompatibilityDetector d : detectors) {
            try {
                List<CompatibilityIssue> found = d.detect();
                LOGGER.info("[{}] found {} issue(s)", d.getId(), found.size());
                all.addAll(found);
            } catch (Exception e) {
                LOGGER.error("[{}] threw an exception: {}", d.getId(), e.getMessage(), e);
            }
        }

        // Deduplicate: same type + same affected mods = same issue
        all = deduplicate(all);

        // Sort: severity first, then affected mod count descending
        all.sort(Comparator
                .comparingInt((CompatibilityIssue i) -> i.getSeverity().priority)
                .thenComparingInt(i -> -i.getAffectedMods().size()));

        lastResults = Collections.unmodifiableList(all);
        complete    = true;

        long critical = all.stream().filter(i -> i.getSeverity() == IssueSeverity.CRITICAL).count();
        LOGGER.info("Scan complete: {} total, {} critical.", all.size(), critical);
        return lastResults;
    }

    private List<CompatibilityIssue> deduplicate(List<CompatibilityIssue> issues) {
        Set<String> seen = new LinkedHashSet<>();
        List<CompatibilityIssue> unique = new ArrayList<>();
        for (CompatibilityIssue issue : issues) {
            String key = issue.getType() + "|" + issue.getAffectedMods().stream().sorted().toList();
            if (seen.add(key)) unique.add(issue);
        }
        return unique;
    }

    public List<CompatibilityIssue> getLastResults() { return lastResults; }
    public boolean isScanComplete()                  { return complete; }
    public int getCriticalCount() {
        return (int) lastResults.stream().filter(i -> i.getSeverity() == IssueSeverity.CRITICAL).count();
    }
}
