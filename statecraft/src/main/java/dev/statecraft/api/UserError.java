package dev.statecraft.api;

public final class UserError extends RuntimeException {
    public UserError(String message) {
        super(message);
    }
}
