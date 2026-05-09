package dev.compatmanager.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompatibilityIssue {

    private final IssueType type;
    private final IssueSeverity severity;
    private final String descriptionKey;
    private final String[] descriptionArgs;
    private final List<String> affectedMods;
    private final List<Solution> solutions;
    private final String technicalDetail;

    private CompatibilityIssue(Builder builder) {
        this.type = builder.type;
        this.severity = builder.severity;
        this.descriptionKey = builder.descriptionKey;
        this.descriptionArgs = builder.descriptionArgs;
        this.affectedMods = Collections.unmodifiableList(builder.affectedMods);
        this.solutions = Collections.unmodifiableList(builder.solutions);
        this.technicalDetail = builder.technicalDetail;
    }

    public IssueType getType() { return type; }
    public IssueSeverity getSeverity() { return severity; }
    public String getDescriptionKey() { return descriptionKey; }
    public String[] getDescriptionArgs() { return descriptionArgs; }
    public List<String> getAffectedMods() { return affectedMods; }
    public List<Solution> getSolutions() { return solutions; }
    public String getTechnicalDetail() { return technicalDetail; }

    public static Builder builder(IssueType type, IssueSeverity severity) {
        return new Builder(type, severity);
    }

    public static class Builder {
        private final IssueType type;
        private final IssueSeverity severity;
        private String descriptionKey = "";
        private String[] descriptionArgs = new String[0];
        private final List<String> affectedMods = new ArrayList<>();
        private final List<Solution> solutions = new ArrayList<>();
        private String technicalDetail = "";

        private Builder(IssueType type, IssueSeverity severity) {
            this.type = type;
            this.severity = severity;
        }

        public Builder description(String key, String... args) {
            this.descriptionKey = key;
            this.descriptionArgs = args;
            return this;
        }

        public Builder affectedMod(String modId) {
            this.affectedMods.add(modId);
            return this;
        }

        public Builder solution(Solution solution) {
            this.solutions.add(solution);
            return this;
        }

        public Builder technicalDetail(String detail) {
            this.technicalDetail = detail;
            return this;
        }

        public CompatibilityIssue build() {
            return new CompatibilityIssue(this);
        }
    }
}
