package dev.statecraft.client.state;

import dev.statecraft.api.ui.UiQuery;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class NavigationState {
    public static final int MAX_DEPTH = 32;
    public static final int MAX_SAVED_VIEWS = 24;
    public static final class Location {
        private final String category;
        private final ViewState view;
        private int offset;
        private Location(String category, ViewState view) { this.category = category; this.view = view; }
        public String category() { return category; }
        public ViewState view() { return view; }
        public int offset() { return offset; }
        public void offset(int value) { offset = Math.max(0, value); }
    }
    private final ArrayDeque<Location> history = new ArrayDeque<>();
    private final Map<String, ViewState> saved = new LinkedHashMap<>();
    private Location current = new Location("", null);

    public Location current() { return current; }
    public int depth() { return history.size(); }

    public void sections(String category) {
        if (current.view() == null && current.category().equals(category)) return;
        push(new Location(category, null));
    }

    public ViewState open(UiQuery query) {
        String key = key(query);
        if (current.view() != null && key(current.view().query()).equals(key)) return current.view();
        ViewState previous = saved.get(key);
        ViewState view = previous == null ? new ViewState(query) : previous.copy();
        if (!query.search().isEmpty() || query.offset() != 0) view.query(query);
        push(new Location("", view));
        return view;
    }

    public boolean back() {
        if (history.isEmpty()) return false;
        save();
        current = history.removeLast();
        return true;
    }

    public ViewState find(UUID id) {
        if (current.view() != null && current.view().id().equals(id)) return current.view();
        for (Location location : history) {
            if (location.view() != null && location.view().id().equals(id)) return location.view();
        }
        return saved.values().stream().filter(view -> view.id().equals(id)).findFirst().orElse(null);
    }
    public void invalidate() {
        var views = new java.util.HashSet<>(saved.values());
        if (current.view() != null) views.add(current.view());
        history.stream().map(Location::view).filter(java.util.Objects::nonNull).forEach(views::add);
        views.forEach(ViewState::invalidate);
    }

    private void push(Location location) {
        save();
        history.addLast(current);
        while (history.size() > MAX_DEPTH) history.removeFirst();
        current = location;
    }

    private void save() {
        if (current.view() == null) return;
        String key = key(current.view().query());
        saved.remove(key);
        saved.put(key, current.view());
        while (saved.size() > MAX_SAVED_VIEWS) saved.remove(saved.keySet().iterator().next());
    }

    private static String key(UiQuery query) { return query.page() + "\n" + query.entity(); }
}
