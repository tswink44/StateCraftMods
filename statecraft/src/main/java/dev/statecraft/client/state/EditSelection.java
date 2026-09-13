package dev.statecraft.client.state;

public record EditSelection(int cursor, int anchor, boolean focused) {
    public static EditSelection end(String value) { return new EditSelection(value.length(), value.length(), false); }
    public EditSelection bounded(String value) {
        return new EditSelection(Math.max(0, Math.min(value.length(), cursor)),
                Math.max(0, Math.min(value.length(), anchor)), focused);
    }
}
