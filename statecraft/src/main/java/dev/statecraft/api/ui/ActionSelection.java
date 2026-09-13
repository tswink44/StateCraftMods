package dev.statecraft.api.ui;

import dev.statecraft.api.CommandLine;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormContext;
import java.util.Map;
import java.util.Objects;

public record ActionSelection(String page, String template, Map<String, String> values, String command) {
    public ActionSelection {
        Objects.requireNonNull(page);
        Objects.requireNonNull(template);
        Objects.requireNonNull(command);
        values = Map.copyOf(values);
        if (!page.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_-]*") || page.length() > 96
                || template.length() > CommandLine.MAX_LENGTH || command.length() > CommandLine.MAX_LENGTH) {
            throw new UserError("Invalid action selection.");
        }
        if (template.isEmpty()) {
            if (!values.isEmpty() || command.isBlank()) throw new UserError("Enter an advanced command.");
            CommandLine.split(command);
        } else {
            if (!command.isEmpty()) throw new UserError("A form cannot also supply an advanced command.");
            FormContext.validateValues(template, values);
        }
    }

    public static ActionSelection form(String page, String template, Map<String, String> values) {
        return new ActionSelection(page, template, values, "");
    }

    public static ActionSelection raw(String page, String command) {
        return new ActionSelection(page, "", Map.of(), command);
    }

    public String namespace() { return page.substring(0, page.indexOf(':')); }
    public String rendered() { return template.isEmpty() ? command : new CommandTemplate(template).render(values); }

    public MenuPage.Action registeredAction() {
        return MenuRegistry.get(page).actions().stream().filter(action -> action.command().equals(template))
                .findFirst().orElseThrow(() -> new UserError("This action is not registered on the server."));
    }

    public ActionIntent intent() {
        var definition = MenuRegistry.get(page);
        if (!template.isEmpty()) return registeredAction().intent();
        return CommandLine.split(command).equals(CommandLine.split(definition.query())) ? ActionIntent.QUERY : ActionIntent.RAW;
    }
}
