package dev.compatmanager.api;

import java.util.Optional;
import java.util.function.Runnable;

public final class Solution {

    public enum ActionType {
        MANUAL_INSTRUCTION,
        OPEN_CONFIG,
        OPEN_URL,
        COPY_TEXT,
        AUTO_FIX
    }

    private final String descriptionKey;
    private final String[] descriptionArgs;
    private final ActionType actionType;
    private final String actionPayload;
    private final Runnable autoFixAction;

    private Solution(Builder b) {
        this.descriptionKey  = b.descriptionKey;
        this.descriptionArgs = b.descriptionArgs;
        this.actionType      = b.actionType;
        this.actionPayload   = b.actionPayload;
        this.autoFixAction   = b.autoFixAction;
    }

    public String getDescriptionKey()   { return descriptionKey; }
    public String[] getDescriptionArgs(){ return descriptionArgs; }
    public ActionType getActionType()   { return actionType; }
    public Optional<String> getActionPayload() { return Optional.ofNullable(actionPayload); }
    public boolean canAutoApply()       { return actionType == ActionType.AUTO_FIX; }

    public void apply() { if (autoFixAction != null) autoFixAction.run(); }

    // ── Factory helpers ──────────────────────────────────────────────────────

    public static Solution manual(String key, String... args) {
        return new Builder(key, args).build();
    }

    public static Solution withUrl(String key, String url, String... args) {
        return new Builder(key, args).openUrl(url).build();
    }

    public static Solution withCopy(String key, String text, String... args) {
        return new Builder(key, args).copyText(text).build();
    }

    public static Solution autoFix(String key, Runnable fix, String... args) {
        return new Builder(key, args).autoFix(fix).build();
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static Builder builder(String key, String... args) { return new Builder(key, args); }

    public static final class Builder {
        private final String   descriptionKey;
        private final String[] descriptionArgs;
        private ActionType actionType   = ActionType.MANUAL_INSTRUCTION;
        private String     actionPayload;
        private Runnable   autoFixAction;

        private Builder(String key, String[] args) {
            this.descriptionKey  = key;
            this.descriptionArgs = args;
        }

        public Builder openUrl(String url) {
            actionType    = ActionType.OPEN_URL;
            actionPayload = url;
            return this;
        }

        public Builder copyText(String text) {
            actionType    = ActionType.COPY_TEXT;
            actionPayload = text;
            return this;
        }

        public Builder autoFix(Runnable action) {
            actionType    = ActionType.AUTO_FIX;
            autoFixAction = action;
            return this;
        }

        public Builder openConfig(String configPath) {
            actionType    = ActionType.OPEN_CONFIG;
            actionPayload = configPath;
            return this;
        }

        public Solution build() { return new Solution(this); }
    }
}
