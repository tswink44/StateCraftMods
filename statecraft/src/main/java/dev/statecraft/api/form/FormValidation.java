package dev.statecraft.api.form;

import dev.statecraft.api.ui.UiText;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FormValidation {
    private FormValidation() {}

    public static Map<String, UiText> errors(FormSchema schema, Map<String, String> values) {
        Map<String, UiText> errors = new LinkedHashMap<>();
        for (FormField field : schema.fields()) {
            String value = values.getOrDefault(field.key(), "");
            field.constraints().error(value, field.label()).ifPresent(error -> errors.put(field.key(), error));
            if (!errors.containsKey(field.key()) && field.kind() == FormField.Kind.CHOICE && !field.allowCustom()
                    && (!field.value().equals(value) || field.selectedLabel().isBlank())) {
                errors.put(field.key(), UiText.tr("gui.statecraft.field.choice", "Choose an eligible " + field.label() + ".", field.label()));
            }
        }
        return Map.copyOf(errors);
    }
}
