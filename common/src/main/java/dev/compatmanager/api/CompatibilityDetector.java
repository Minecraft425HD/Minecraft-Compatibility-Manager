package dev.compatmanager.api;

import java.util.List;

/**
 * Implement this to add a custom detector.
 * Register via CompatManagerCommon.registerDetector().
 */
public interface CompatibilityDetector {
    String getId();
    String getName();
    List<CompatibilityIssue> detect();
}
