package dev.statecraft.api.ui;

public enum ActionIntent {
    QUERY, MUTATION, NAVIGATION, RAW;

    public boolean requiresReview() {
        return this == MUTATION || this == RAW;
    }
}
