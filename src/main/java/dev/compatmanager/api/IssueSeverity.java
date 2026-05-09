package dev.compatmanager.api;

import net.minecraft.util.Formatting;

public enum IssueSeverity {
    CRITICAL(Formatting.DARK_RED, 0),
    HIGH(Formatting.RED, 1),
    MEDIUM(Formatting.GOLD, 2),
    LOW(Formatting.YELLOW, 3),
    INFO(Formatting.AQUA, 4);

    public final Formatting color;
    public final int priority;

    IssueSeverity(Formatting color, int priority) {
        this.color = color;
        this.priority = priority;
    }

    public String translationKey() {
        return "compatmanager.severity." + name().toLowerCase();
    }
}
