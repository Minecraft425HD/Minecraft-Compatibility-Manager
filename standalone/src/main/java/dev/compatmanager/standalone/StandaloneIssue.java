package dev.compatmanager.standalone;

import dev.compatmanager.standalone.fix.AutoFixer;

import java.util.List;

public final class StandaloneIssue {

    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW, INFO;

        public String ansiColor() {
            return switch (this) {
                case CRITICAL -> "[1;31m";
                case HIGH     -> "[0;31m";
                case MEDIUM   -> "[0;33m";
                case LOW      -> "[0;93m";
                case INFO     -> "[0;36m";
            };
        }
    }

    public static final String ANSI_RESET = "[0m";
    public static final String ANSI_BOLD  = "[1m";
    public static final String ANSI_GREEN = "[0;32m";
    public static final String ANSI_GRAY  = "[0;90m";

    private final String type;
    private final Severity severity;
    private final List<String> affectedMods;
    private final String description;
    private final List<String> solutions;
    private final String jarPath;
    private boolean isFixed;

    /** Preserves the original 5-argument call sites. */
    public StandaloneIssue(String type, Severity severity, List<String> affectedMods,
                           String description, List<String> solutions) {
        this(type, severity, affectedMods, description, solutions, "");
    }

    private StandaloneIssue(String type, Severity severity, List<String> affectedMods,
                            String description, List<String> solutions, String jarPath) {
        this.type = type;
        this.severity = severity;
        this.affectedMods = List.copyOf(affectedMods);
        this.description = description;
        this.solutions = List.copyOf(solutions);
        this.jarPath = jarPath == null ? "" : jarPath;
        this.isFixed = false;
    }

    // ── Accessors (record-style) ──────────────────────────────────────────

    public String type()              { return type; }
    public Severity severity()        { return severity; }
    public List<String> affectedMods(){ return affectedMods; }
    public String description()       { return description; }
    public List<String> solutions()   { return solutions; }
    public String jarPath()           { return jarPath; }
    public boolean isFixed()          { return isFixed; }

    // ── Auto-fix support ─────────────────────────────────────────────────

    public boolean canAutoFix() {
        return !jarPath.isBlank();
    }

    public void applyFix(AutoFixer fixer) {
        if (!canAutoFix() || isFixed) return;
        String modId = affectedMods.isEmpty() ? "unknown" : affectedMods.get(0);
        boolean ok = fixer.disableMod(modId, jarPath, description);
        if (ok) isFixed = true;
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder(String type, Severity severity) {
        return new Builder(type, severity);
    }

    public static final class Builder {
        private final String type;
        private final Severity severity;
        private List<String> affectedMods = List.of();
        private String description = "";
        private List<String> solutions = List.of();
        private String jarPath = "";

        private Builder(String type, Severity severity) {
            this.type = type;
            this.severity = severity;
        }

        public Builder affectedMods(List<String> mods) { this.affectedMods = mods; return this; }
        public Builder affectedMods(String... mods)    { this.affectedMods = List.of(mods); return this; }
        public Builder description(String d)           { this.description = d; return this; }
        public Builder solutions(List<String> s)       { this.solutions = s; return this; }
        public Builder solutions(String... s)          { this.solutions = List.of(s); return this; }
        public Builder jarPath(String p)               { this.jarPath = p; return this; }

        public StandaloneIssue build() {
            return new StandaloneIssue(type, severity, affectedMods, description, solutions, jarPath);
        }
    }
}
