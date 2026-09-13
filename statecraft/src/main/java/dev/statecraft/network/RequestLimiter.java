package dev.statecraft.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RequestLimiter {
    private static final double CAPACITY = 10;
    private static final double PER_NANOSECOND = 5.0 / 1_000_000_000.0;
    private final Map<UUID, Bucket> buckets = new HashMap<>();

    public boolean allow(UUID player, long now) {
        Bucket bucket = buckets.computeIfAbsent(player, ignored -> new Bucket(now));
        long elapsed = Math.max(0, now - bucket.last);
        bucket.tokens = Math.min(CAPACITY, bucket.tokens + elapsed * PER_NANOSECOND);
        bucket.last = now;
        if (bucket.tokens < 1) {
            return false;
        }
        bucket.tokens--;
        return true;
    }

    public void forget(UUID player) { buckets.remove(player); }
    public void clear() { buckets.clear(); }

    private static final class Bucket {
        private long last;
        private double tokens = CAPACITY;
        private Bucket(long last) { this.last = last; }
    }
}
