package dev.statecraft.api;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandTemplate {
    private static final Pattern FIELD = Pattern.compile("<([^<>]+)>");
    private final String template;
    private final List<String> fields;

    public CommandTemplate(String template) {
        this.template = template;
        Matcher matcher = FIELD.matcher(template);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        fields = List.copyOf(names);
    }

    public List<String> fields() { return fields; }

    public String render(Map<String, String> values) {
        Matcher matcher = FIELD.matcher(template);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null || value.isBlank()) {
                throw new UserError("Enter a value for " + matcher.group(1) + ".");
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(CommandLine.quote(value.strip())));
        }
        matcher.appendTail(output);
        String command = output.toString();
        CommandLine.split(command);
        return command;
    }
}
