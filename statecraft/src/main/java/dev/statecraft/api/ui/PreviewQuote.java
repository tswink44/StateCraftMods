package dev.statecraft.api.ui;

import java.util.Objects;

public record PreviewQuote(OperationRef operation, long expiresAt, ActionPreview preview) {
    public PreviewQuote {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(preview);
        if (!operation.present() || expiresAt < 0) throw new IllegalArgumentException("Invalid preview quote.");
    }
}
