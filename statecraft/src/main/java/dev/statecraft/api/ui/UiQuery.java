package dev.statecraft.api.ui;

import java.util.Objects;

public record UiQuery(String page, EntityRef entity, String search, int offset) {
    public UiQuery {
        Objects.requireNonNull(page);
        Objects.requireNonNull(entity);
        Objects.requireNonNull(search);
        if (!page.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_-]*") || page.length() > 96
                || search.length() > 80 || offset < 0 || offset > 100_000
                || entity.present() && !page.startsWith(entity.namespace() + ":")) {
            throw new IllegalArgumentException("Invalid UI query.");
        }
    }

    public static UiQuery page(String page) { return new UiQuery(page, EntityRef.NONE, "", 0); }
    public static UiQuery detail(EntityRef entity) { return new UiQuery(entity.page(), entity, "", 0); }
    public String namespace() { return page.substring(0, page.indexOf(':')); }
}
