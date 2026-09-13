package dev.statecraft.client.state;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Render-thread cache policy; resource disposal is supplied by the client renderer. */
public final class TerrainTileCache<K, V> implements AutoCloseable {
    public static final int MAX_TILES = 289;
    public static final int UPDATES_PER_FRAME = 4;
    public static final long REFRESH_MILLIS = 10_000;
    private record Entry<V>(V value, long sampledAt) {}

    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<K> queued = new LinkedHashSet<>();
    private final Set<K> seen = new LinkedHashSet<>();
    private final Consumer<V> release;
    private String scope = "";
    private int updates;
    private boolean closed;

    public TerrainTileCache(Consumer<V> release) {
        this.release = Objects.requireNonNull(release);
    }

    public void beginFrame(String scope) {
        if (closed) return;
        if (!this.scope.equals(scope)) {
            clear();
            this.scope = scope;
        }
        queued.retainAll(seen);
        seen.clear();
        updates = 0;
    }

    public V get(K key) {
        Entry<V> entry = entries.get(key);
        return entry == null ? null : entry.value();
    }

    public boolean contains(K key) { return entries.containsKey(key); }
    public int size() { return entries.size(); }

    public boolean reserveUpdate(K key, long now) {
        if (closed) return false;
        seen.add(key);
        Entry<V> entry = entries.get(key);
        if (entry != null && now >= entry.sampledAt() && now - entry.sampledAt() < REFRESH_MILLIS) {
            queued.remove(key);
            return false;
        }
        if (!queued.contains(key) && queued.size() >= MAX_TILES) return false;
        queued.add(key);
        // A low frame rate must not let nearby stale tiles starve unsampled distant tiles.
        if (updates >= UPDATES_PER_FRAME || !queued.iterator().next().equals(key)) return false;
        queued.remove(key);
        updates++;
        return true;
    }

    public void put(K key, V value, long now) {
        if (closed) {
            dispose(value);
            return;
        }
        Entry<V> old = entries.put(key, new Entry<>(value, now));
        queued.remove(key);
        if (old != null && !Objects.equals(old.value(), value)) dispose(old.value());
        while (entries.size() > MAX_TILES) {
            var iterator = entries.entrySet().iterator();
            Entry<V> eldest = iterator.next().getValue();
            iterator.remove();
            dispose(eldest.value());
        }
    }

    public void discard(K key) {
        queued.remove(key);
        seen.remove(key);
        Entry<V> removed = entries.remove(key);
        if (removed != null) dispose(removed.value());
    }

    private void dispose(V value) { if (value != null) release.accept(value); }
    private void clear() {
        entries.values().forEach(entry -> dispose(entry.value()));
        entries.clear();
        queued.clear();
        seen.clear();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        clear();
    }
}
