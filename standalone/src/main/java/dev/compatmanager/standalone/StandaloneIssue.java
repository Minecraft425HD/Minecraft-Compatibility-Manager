package dev.compatmanager.standalone;

import java.util.List;

public record StandaloneIssue(
        String type,
        Severity severity,
        List<String> affectedMods,
        String description,
        List<String> solutions
) {
    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW, INFO;

        public String ansiColor() {
            return switch (this) {
                case CRITICAL -> "[1;31m"; // bold red
                case HIGH     -> "[0;31m"; // red
                case MEDIUM   -> "[0;33m"; // yellow
                case LOW      -> "[0;93m"; // bright yellow
                case INFO     -> "[0;36m"; // cyan
            };
        }
    }

    public static final String ANSI_RESET  = "[0m";
    public static final String ANSI_BOLD   = "[1m";
    public static final String ANSI_GREEN  = "[0;32m";
    public static final String ANSI_GRAY   = "[0;90m";
}
