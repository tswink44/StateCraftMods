package dev.statecraft.domain;

import dev.statecraft.api.CommandLine;
import dev.statecraft.api.UserError;

import java.util.List;

final class Arguments {
    final List<String> values;

    Arguments(List<String> values) {
        this.values = values;
    }

    int size() {
        return values.size();
    }

    String get(int index) {
        if (index >= size()) throw new UserError("Missing argument. Use help <section> for command syntax.");
        return values.get(index);
    }

    String optional(int index, String fallback) {
        return index < size() ? get(index) : fallback;
    }

    String tail(int index) {
        return CommandLine.tail(values, index);
    }

    void between(int min, int max, String syntax) {
        if (size() < min || size() > max) throw new UserError("Usage: " + syntax);
    }

    void exactly(int count, String syntax) {
        between(count, count, syntax);
    }

    int page(int index) {
        return index < size() ? integer(get(index), 1, 1_000_000, "page") : 1;
    }

    static int integer(String text, int min, int max, String label) {
        try {
            int value = Integer.parseInt(text);
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new UserError(label + " must be a whole number between " + min + " and " + max + ".");
        }
    }

    static long quantity(String text) {
        try {
            long value = Long.parseLong(text);
            if (value < 1 || value > 1_000_000_000L) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new UserError("Share quantity must be a whole number between 1 and 1000000000.");
        }
    }
}
