package dev.statecraft.api.form;

import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.UiText;
import java.util.List;
import java.util.Optional;

public record FormConstraints(Type type, int maxLength, long minimum, long maximum, List<String> alternatives) {
    public enum Type { TEXT, INTEGER, MONEY }
    public static final FormConstraints DEFAULT = text(2048);

    public FormConstraints {
        java.util.Objects.requireNonNull(type);
        alternatives = List.copyOf(alternatives);
        if (maxLength < 1 || maxLength > FormSchema.MAX_VALUE_LENGTH || minimum > maximum || alternatives.size() > 16
                || alternatives.stream().anyMatch(value -> value.isBlank() || value.length() > 32)
                || type == Type.MONEY && (minimum < 0 || maximum > Money.MAX)) {
            throw new IllegalArgumentException("Invalid field constraints.");
        }
    }

    public static FormConstraints text(int maxLength) { return new FormConstraints(Type.TEXT, maxLength, 0, 0, List.of()); }
    public static FormConstraints integer(long min, long max) { return new FormConstraints(Type.INTEGER, 24, min, max, List.of()); }
    public static FormConstraints money(long min, long max) { return new FormConstraints(Type.MONEY, 32, min, max, List.of()); }

    public FormConstraints or(String... values) {
        return new FormConstraints(type, maxLength, minimum, maximum, List.of(values));
    }

    public Optional<UiText> error(String value, String label) {
        if (value.isBlank()) {
            return Optional.of(UiText.tr("gui.statecraft.field.required", "Enter " + label + ".", label));
        }
        if (value.length() > maxLength) {
            return Optional.of(UiText.tr("gui.statecraft.field.length", label + " is limited to " + maxLength + " characters.",
                    label, Integer.toString(maxLength)));
        }
        String input = value.strip();
        if (alternatives.stream().anyMatch(input::equalsIgnoreCase) || type == Type.TEXT) return Optional.empty();
        long number;
        try {
            if (type == Type.MONEY) {
                number = Money.parse(input);
            } else {
                if (!input.matches("-?[0-9]+")) throw new NumberFormatException();
                number = Long.parseLong(input);
            }
        } catch (NumberFormatException | UserError invalid) {
            String key = type == Type.MONEY ? "money" : "integer";
            return Optional.of(UiText.tr("gui.statecraft.field." + key,
                    type == Type.MONEY ? label + " needs a nonnegative amount with at most two decimal places."
                            : label + " needs a whole number.", label));
        }
        if (number < minimum || number > maximum) {
            String min = type == Type.MONEY ? Money.format(minimum) : Long.toString(minimum);
            String max = type == Type.MONEY ? Money.format(maximum) : Long.toString(maximum);
            return Optional.of(UiText.tr("gui.statecraft.field.range",
                    label + " must be between " + min + " and " + max + ".", label, min, max));
        }
        return Optional.empty();
    }
}
