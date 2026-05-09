package dev.compatmanager.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompatibilityIssue {

    private final IssueType       type;
    private final IssueSeverity   severity;
    private final String          descriptionKey;
    private final String[]        descriptionArgs;
    private final List<String>    affectedMods;
    private final List<Solution>  solutions;
    private final String          technicalDetail;

    private CompatibilityIssue(Builder b) {
        this.type            = b.type;
        this.severity        = b.severity;
        this.descriptionKey  = b.descriptionKey;
        this.descriptionArgs = b.descriptionArgs;
        this.affectedMods    = Collections.unmodifiableList(b.affectedMods);
        this.solutions       = Collections.unmodifiableList(b.solutions);
        this.technicalDetail = b.technicalDetail;
    }

    public IssueType       getType()            { return type; }
    public IssueSeverity   getSeverity()         { return severity; }
    public String          getDescriptionKey()   { return descriptionKey; }
    public String[]        getDescriptionArgs()  { return descriptionArgs; }
    public List<String>    getAffectedMods()     { return affectedMods; }
    public List<Solution>  getSolutions()        { return solutions; }
    public String          getTechnicalDetail()  { return technicalDetail; }

    public static Builder builder(IssueType type, IssueSeverity severity) {
        return new Builder(type, severity);
    }

    public static final class Builder {
        private final IssueType     type;
        private final IssueSeverity severity;
        private String          descriptionKey  = "";
        private String[]        descriptionArgs = {};
        private final List<String>   affectedMods  = new ArrayList<>();
        private final List<Solution> solutions     = new ArrayList<>();
        private String          technicalDetail = "";

        private Builder(IssueType type, IssueSeverity severity) {
            this.type     = type;
            this.severity = severity;
        }

        public Builder description(String key, String... args) {
            descriptionKey  = key;
            descriptionArgs = args;
            return this;
        }

        public Builder affectedMod(String modId) { affectedMods.add(modId); return this; }

        public Builder solution(Solution s) { solutions.add(s); return this; }

        public Builder technicalDetail(String d) { technicalDetail = d; return this; }

        public CompatibilityIssue build() { return new CompatibilityIssue(this); }
    }
}
