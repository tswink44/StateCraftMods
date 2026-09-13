package dev.statecraft.client;

import dev.statecraft.api.MenuCategory;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.UiText;
import java.util.Locale;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

final class ClientText {
    private ClientText() {}

    static Component tr(String key, String fallback, Object... arguments) {
        return I18n.exists(key) ? Component.translatable(key, arguments)
                : Component.literal(arguments.length == 0 ? fallback : String.format(Locale.ROOT, fallback, arguments));
    }

    static Component of(UiText text) {
        return !text.key().isEmpty() && I18n.exists(text.key())
                ? Component.translatable(text.key(), text.arguments().toArray())
                : Component.literal(text.fallback());
    }

    static Component page(MenuPage page) {
        return tr("gui.statecraft.page." + page.id().replace(':', '.'), page.title());
    }
    static Component category(MenuCategory category) {
        return tr("gui.statecraft.category." + category.name().toLowerCase(Locale.ROOT), category.title());
    }
    static Component description(MenuCategory category) {
        return tr("gui.statecraft.category." + category.name().toLowerCase(Locale.ROOT) + ".description",
                category.description());
    }
    static Component action(String page, MenuPage.Action action) {
        return tr("gui.statecraft.action." + page.replace(':', '.') + "."
                + action.command().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_$", ""),
                action.label());
    }
    static Component outcome(ActionOutcome outcome) {
        return switch (outcome) {
            case COMPLETED -> tr("gui.statecraft.outcome.completed", "Completed");
            case REJECTED -> tr("gui.statecraft.outcome.rejected", "Not executed");
            case UNCERTAIN -> tr("gui.statecraft.outcome.uncertain", "Outcome uncertain — do not repeat");
            case REVIEW_REQUIRED -> tr("gui.statecraft.outcome.review_required", "Not executed — review again");
            case READY -> tr("gui.statecraft.outcome.ready", "Not executed — same operation may be retried");
            case UNKNOWN -> tr("gui.statecraft.outcome.unknown", "Unknown receipt — do not repeat");
        };
    }
}
