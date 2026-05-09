package dev.compatmanager.api;

import java.util.Optional;
import java.util.function.Runnable;

public class Solution {

    public enum ActionType {
        MANUAL_INSTRUCTION,
        OPEN_CONFIG,
        OPEN_URL,
        COPY_COMMAND,
        AUTO_FIX
    }

    private final String descriptionKey;
    private final String[] descriptionArgs;
    private final ActionType actionType;
    private final String actionPayload;
    private final Runnable autoFixAction;
    private final boolean canAutoApply;

    private Solution(Builder builder) {
        this.descriptionKey = builder.descriptionKey;
        this.descriptionArgs = builder.descriptionArgs;
        this.actionType = builder.actionType;
        this.actionPayload = builder.actionPayload;
        this.autoFixAction = builder.autoFixAction;
        this.canAutoApply = builder.canAutoApply;
    }

    public String getDescriptionKey() { return descriptionKey; }
    public String[] getDescriptionArgs() { return descriptionArgs; }
    public ActionType getActionType() { return actionType; }
    public Optional<String> getActionPayload() { return Optional.ofNullable(actionPayload); }
    public boolean canAutoApply() { return canAutoApply; }

    public void apply() {
        if (autoFixAction != null) autoFixAction.run();
    }

    public static Builder builder(String descriptionKey, String... args) {
        return new Builder(descriptionKey, args);
    }

    public static class Builder {
        private final String descriptionKey;
        private final String[] descriptionArgs;
        private ActionType actionType = ActionType.MANUAL_INSTRUCTION;
        private String actionPayload;
        private Runnable autoFixAction;
        private boolean canAutoApply = false;

        private Builder(String descriptionKey, String[] args) {
            this.descriptionKey = descriptionKey;
            this.descriptionArgs = args;
        }

        public Builder openUrl(String url) {
            this.actionType = ActionType.OPEN_URL;
            this.actionPayload = url;
            return this;
        }

        public Builder copyCommand(String command) {
            this.actionType = ActionType.COPY_COMMAND;
            this.actionPayload = command;
            return this;
        }

        public Builder autoFix(Runnable action) {
            this.actionType = ActionType.AUTO_FIX;
            this.autoFixAction = action;
            this.canAutoApply = true;
            return this;
        }

        public Builder openConfig(String configPath) {
            this.actionType = ActionType.OPEN_CONFIG;
            this.actionPayload = configPath;
            return this;
        }

        public Solution build() {
            return new Solution(this);
        }
    }
}
