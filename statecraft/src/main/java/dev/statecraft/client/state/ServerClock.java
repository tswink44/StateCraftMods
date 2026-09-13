package dev.statecraft.client.state;

public final class ServerClock {
    public static final long MAX_AGE_MILLIS = 60_000;
    private long offset;
    private long observedAt;
    private boolean known;

    public void observe(long serverTime, long receivedAt) {
        offset = serverTime - receivedAt;
        observedAt = receivedAt;
        known = true;
    }

    public long now(long localTime) { return localTime + offset; }
    public boolean fresh(long localTime) {
        return known && localTime >= observedAt && localTime - observedAt <= MAX_AGE_MILLIS;
    }
    public boolean validUntil(long expiresAt, long localTime) {
        return fresh(localTime) && now(localTime) < expiresAt;
    }
    public long secondsRemaining(long expiresAt, long localTime) {
        return Math.max(0, (expiresAt - now(localTime) + 999) / 1000);
    }
    public void clear() { known = false; }
}
