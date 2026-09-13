package dev.statecraft.api.form;

import dev.statecraft.api.UserError;
import java.util.Objects;

public record FormQuery(String field, String search, int offset) {
    public static final FormQuery INITIAL = new FormQuery("", "", 0);

    public FormQuery {
        Objects.requireNonNull(field);
        Objects.requireNonNull(search);
        if (field.length() > 64 || search.length() > 80 || offset < 0 || offset > 1_000_000
                || search.chars().anyMatch(Character::isISOControl)) {
            throw new UserError("Invalid form search.");
        }
    }
}
