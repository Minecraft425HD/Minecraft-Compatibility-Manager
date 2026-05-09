package dev.compatmanager.api;

import java.util.List;

/**
 * Implement this interface to add a custom compatibility detector.
 * Register via CompatManagerMod.registerDetector().
 */
public interface CompatibilityDetector {

    /** Unique identifier for this detector. */
    String getId();

    /** Human-readable name shown in the UI. */
    String getName();

    /** Run detection and return all found issues. */
    List<CompatibilityIssue> detect();
}
