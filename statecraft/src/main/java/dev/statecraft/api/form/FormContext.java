package dev.statecraft.api.form;

import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.UserError;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FormContext(Actor actor, String page, String command, Map<String, String> values, FormQuery query) {
    public FormContext {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(page);
        Objects.requireNonNull(command);
        Objects.requireNonNull(query);
        values = Map.copyOf(values);
        List<String> fields = new CommandTemplate(command).fields();
        if (!page.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_-]*") || page.length() > 96
                || command.length() > CommandLine.MAX_LENGTH || fields.size() > FormSchema.MAX_FIELDS
                || (!query.field().isEmpty() && !fields.contains(query.field()))) {
            throw new UserError("The requested form does not match this action.");
        }
        validateValues(command, values);
    }

    public static void validateValues(String command, Map<String, String> values) {
        List<String> fields = new CommandTemplate(command).fields();
        if (values.size() > FormSchema.MAX_FIELDS || !fields.containsAll(values.keySet())) {
            throw new UserError("The form values do not match this action.");
        }
        int length = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().length() > 64 || entry.getValue().length() > 2048) {
                throw new UserError("A form value is too long.");
            }
            length += entry.getValue().length();
        }
        if (length > CommandLine.MAX_LENGTH) {
            throw new UserError("The combined form values are too long.");
        }
    }

    public String namespace() {
        return page.substring(0, page.indexOf(':'));
    }

    public List<String> words() {
        return CommandLine.split(command);
    }
}
