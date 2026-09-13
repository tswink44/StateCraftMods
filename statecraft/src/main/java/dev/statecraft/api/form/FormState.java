package dev.statecraft.api.form;

import dev.statecraft.api.UserError;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FormState {
    private FormSchema schema = FormSchema.EMPTY;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private long dependencyRevision;

    public FormSchema schema() { return schema; }
    public Map<String, String> values() { return Map.copyOf(values); }
    public String value(String key) { return values.getOrDefault(key, ""); }
    public String label(String key) { return labels.getOrDefault(key, ""); }
    public long dependencyRevision() { return dependencyRevision; }

    public Set<String> change(String key, String value) {
        if (Objects.equals(values.get(key), value)) {
            return Set.of();
        }
        values.put(key, value);
        labels.remove(key);
        Set<String> cleared = new HashSet<>();
        var pending = new ArrayDeque<String>();
        pending.add(key);
        while (!pending.isEmpty()) {
            String parent = pending.removeFirst();
            for (FormField field : schema.fields()) {
                if (field.dependencies().contains(parent) && !field.key().equals(key) && cleared.add(field.key())) {
                    values.remove(field.key());
                    labels.remove(field.key());
                    pending.add(field.key());
                }
            }
        }
        if (!cleared.isEmpty()) {
            dependencyRevision++;
        }
        return Set.copyOf(cleared);
    }

    public void select(String key, FormChoice choice) {
        change(key, choice.value());
        labels.put(key, choice.label());
    }

    public void selectCustom(String key, String value) {
        FormField field = schema.field(key).orElseThrow(() -> new UserError("Unknown form field."));
        if (!field.allowCustom() || value.isBlank() || value.length() > field.constraints().maxLength()) {
            throw new UserError("Enter a supported custom value of at most " + field.constraints().maxLength() + " characters.");
        }
        change(key, value);
        labels.put(key, value.length() <= 128 ? value : value.substring(0, 125) + "...");
    }

    public void apply(FormSchema replacement, Map<String, String> requestedValues) {
        for (FormField field : replacement.fields()) {
            boolean editedWhileWaiting = field.kind() != FormField.Kind.CHOICE
                    && !Objects.equals(values.get(field.key()), requestedValues.get(field.key()));
            if (!editedWhileWaiting) {
                values.put(field.key(), field.value());
            }
            if (field.kind() == FormField.Kind.CHOICE) {
                labels.put(field.key(), field.selectedLabel());
            }
        }
        Set<String> validKeys = replacement.fields().stream().map(FormField::key)
                .collect(java.util.stream.Collectors.toSet());
        values.keySet().retainAll(validKeys);
        labels.keySet().retainAll(validKeys);
        schema = replacement;
    }

    public boolean complete() {
        return schema.fields().stream().allMatch(field -> !value(field.key()).isBlank()
                && (field.kind() != FormField.Kind.CHOICE || field.allowCustom() || !label(field.key()).isBlank()));
    }
}
