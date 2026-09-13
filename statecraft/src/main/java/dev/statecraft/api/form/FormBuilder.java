package dev.statecraft.api.form;

import dev.statecraft.api.CommandTemplate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FormBuilder {
    private final FormContext context;
    private final Map<String, FormField> fields = new LinkedHashMap<>();
    private final Map<String, List<FormChoice>> catalogs = new LinkedHashMap<>();

    public FormBuilder(FormContext context) {
        this.context = context;
        for (String key : new CommandTemplate(context.command()).fields()) {
            String normalized = key.toLowerCase(Locale.ROOT);
            String initial = switch (normalized) {
                case "page" -> "1";
                case "radius" -> "3";
                case "quantity", "shares" -> "1";
                case "offer_amount", "demand_amount" -> "0";
                case "chunk", "chunks", "chunkkeyorhere" -> "here";
                case "chunk_terms_or_dash" -> "-";
                default -> "";
            };
            boolean multiline = Set.of("body", "description", "text", "reason", "message", "completion_note").contains(normalized);
            text(key, label(key), "", initial, multiline);
        }
        if (has("yes_no_abstain")) {
            choice("yes_no_abstain", "Vote", "Choose your vote explicitly.",
                    List.of(new FormChoice("yes", "Yes"), new FormChoice("no", "No"), new FormChoice("abstain", "Abstain")),
                    "", List.of(), false);
        }
    }

    public boolean has(String key) { return fields.containsKey(key); }
    public Set<String> keys() { return Set.copyOf(fields.keySet()); }
    public String value(String key) { return fields.containsKey(key) ? fields.get(key).value() : ""; }
    public List<FormChoice> choices(String key) { return catalogs.getOrDefault(key, List.of()); }

    public void constraints(String key, FormConstraints constraints) {
        FormField field = fields.get(key);
        if (field != null) {
            fields.put(key, new FormField(field.key(), field.label(), field.kind(), field.value(), field.selectedLabel(),
                    field.hint(), field.dependencies(), field.allowCustom(), field.choices(), field.offset(), field.more(),
                    constraints));
        }
    }

    public void text(String key, String label, String hint, String defaultValue, boolean multiline, String... dependencies) {
        if (!fields.containsKey(key) && !new CommandTemplate(context.command()).fields().contains(key)) {
            return;
        }
        String value = context.values().getOrDefault(key, defaultValue);
        fields.put(key, new FormField(key, label, multiline ? FormField.Kind.MULTILINE : FormField.Kind.TEXT,
                value, "", hint, dependencies(List.of(dependencies)), false, List.of(), 0, false));
        catalogs.remove(key);
    }

    public void choice(String key, String label, String hint, Collection<FormChoice> eligible,
                       String preferredDefault, List<String> dependencies, boolean allowCustom) {
        if (!has(key)) {
            return;
        }
        Map<String, FormChoice> unique = new LinkedHashMap<>();
        eligible.forEach(choice -> unique.putIfAbsent(choice.value(), choice));
        List<FormChoice> all = unique.values().stream()
                .sorted(Comparator.comparing((FormChoice option) -> option.label().toLowerCase(Locale.ROOT))
                        .thenComparing(FormChoice::value)).toList();
        catalogs.put(key, all);
        String value;
        if (context.values().containsKey(key)) {
            String requested = context.values().get(key);
            value = unique.containsKey(requested) || allowCustom ? requested : "";
        } else if (unique.containsKey(preferredDefault)) {
            value = preferredDefault;
        } else {
            value = all.size() == 1 ? all.get(0).value() : "";
        }
        String selected = unique.containsKey(value) ? unique.get(value).label() : allowCustom ? shorten(value, 128) : "";
        boolean queried = key.equals(context.query().field());
        String search = queried ? context.query().search().strip().toLowerCase(Locale.ROOT) : "";
        List<FormChoice> matches = all.stream().filter(option -> search.isEmpty()
                || (option.label() + " " + option.detail() + " " + option.value()).toLowerCase(Locale.ROOT).contains(search)).toList();
        int lastPage = matches.isEmpty() ? 0 : (matches.size() - 1) / FormSchema.PAGE_SIZE * FormSchema.PAGE_SIZE;
        int offset = queried ? Math.min(context.query().offset() / FormSchema.PAGE_SIZE * FormSchema.PAGE_SIZE, lastPage) : 0;
        int end = Math.min(matches.size(), offset + FormSchema.PAGE_SIZE);
        fields.put(key, new FormField(key, label, FormField.Kind.CHOICE, value, selected, hint,
                dependencies(dependencies), allowCustom, matches.subList(offset, end), offset, end < matches.size()));
    }

    public FormSchema build() {
        return new FormSchema(new ArrayList<>(fields.values()));
    }

    private List<String> dependencies(List<String> requested) {
        List<String> keys = new CommandTemplate(context.command()).fields();
        return requested.stream().filter(keys::contains).distinct().toList();
    }

    public static String label(String key) {
        String value = key.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String shorten(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit - 3) + "...";
    }
}
