package dev.statecraft.api.form;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public record FormSchema(List<FormField> fields) {
    public static final int MAX_FIELDS = 16;
    public static final int MAX_VALUE_LENGTH = dev.statecraft.api.CommandLine.MAX_LENGTH;
    public static final int PAGE_SIZE = 20;
    public static final FormSchema EMPTY = new FormSchema(List.of());

    public FormSchema {
        fields = List.copyOf(fields);
        var keys = new HashSet<String>();
        if (fields.size() > MAX_FIELDS || fields.stream().anyMatch(field -> !keys.add(field.key()))) {
            throw new IllegalArgumentException("Duplicate or excessive form fields.");
        }
        if (fields.stream().anyMatch(field -> !keys.containsAll(field.dependencies())
                || field.dependencies().contains(field.key()))) {
            throw new IllegalArgumentException("Invalid form dependency.");
        }
    }

    public Optional<FormField> field(String key) {
        return fields.stream().filter(field -> field.key().equals(key)).findFirst();
    }
}
