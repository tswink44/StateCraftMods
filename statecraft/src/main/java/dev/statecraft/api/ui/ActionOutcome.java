package dev.statecraft.api.ui;

public enum ActionOutcome {
    COMPLETED, REJECTED, UNCERTAIN, REVIEW_REQUIRED, READY, UNKNOWN;

    public boolean success() { return this == COMPLETED; }

    public boolean uncertain() { return this == UNCERTAIN || this == UNKNOWN; }
}
