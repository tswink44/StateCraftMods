package dev.statecraft.client.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RequestTracker<T> {
    public static final long TIMEOUT_MILLIS = 15_000;
    public static final int MAX_REQUESTS = 64;
    private record Watch<T>(UiScope scope, long connection, Object key, long deadline,
                            T receiver, Runnable timeout) {}
    private final Map<Integer, Watch<T>> requests = new LinkedHashMap<>();

    public void watch(int id, UiScope scope, long connection, Object key, long now, T receiver, Runnable timeout) {
        if (requests.size() >= MAX_REQUESTS && !requests.containsKey(id)) {
            int oldest = requests.keySet().iterator().next();
            Watch<T> abandoned = requests.remove(oldest);
            abandoned.timeout().run();
        }
        requests.put(id, new Watch<>(scope, connection, key, now + TIMEOUT_MILLIS, receiver, timeout));
    }

    public Optional<T> take(int id, UiScope scope, long connection, Object key) {
        Watch<T> watch = requests.get(id);
        if (watch == null || !Objects.equals(scope, watch.scope()) || connection != watch.connection()
                || !Objects.equals(key, watch.key())) return Optional.empty();
        requests.remove(id);
        return Optional.of(watch.receiver());
    }

    public void forget(int id) { requests.remove(id); }
    public int size() { return requests.size(); }
    public void clear() { requests.clear(); }

    public void expire(long now) {
        var expired = new ArrayList<Runnable>();
        requests.entrySet().removeIf(entry -> {
            if (now < entry.getValue().deadline()) return false;
            expired.add(entry.getValue().timeout());
            return true;
        });
        expired.forEach(Runnable::run);
    }
}
