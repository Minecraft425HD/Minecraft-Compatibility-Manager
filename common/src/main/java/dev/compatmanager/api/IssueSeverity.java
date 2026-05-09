package dev.compatmanager.api;

public enum IssueSeverity {
    CRITICAL(0xAA0000, 0),
    HIGH    (0xFF5555, 1),
    MEDIUM  (0xFFAA00, 2),
    LOW     (0xFFFF55, 3),
    INFO    (0x55FFFF, 4);

    /** Raw ARGB colour for rendering (alpha 0xFF). */
    public final int argb;
    /** Lower = displayed first. */
    public final int priority;

    IssueSeverity(int rgb, int priority) {
        this.argb = 0xFF000000 | rgb;
        this.priority = priority;
    }

    public String translationKey() {
        return "compatmanager.severity." + name().toLowerCase();
    }
}
