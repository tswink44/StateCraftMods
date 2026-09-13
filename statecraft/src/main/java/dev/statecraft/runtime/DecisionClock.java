package dev.statecraft.runtime;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public final class DecisionClock extends Clock {
    private final Clock source;
    private final ThreadLocal<Instant> decision;

    public DecisionClock(Clock source) {
        this(source, new ThreadLocal<>());
    }

    private DecisionClock(Clock source, ThreadLocal<Instant> decision) {
        this.source = Objects.requireNonNull(source);
        this.decision = decision;
    }

    public Scope freeze() {
        Instant previous = decision.get();
        decision.set(previous == null ? source.instant() : previous);
        return () -> {
            if (previous == null) decision.remove();
            else decision.set(previous);
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override void close();
    }

    @Override public ZoneId getZone() { return source.getZone(); }
    @Override public Clock withZone(ZoneId zone) { return new DecisionClock(source.withZone(zone), decision); }
    @Override public Instant instant() {
        Instant fixed = decision.get();
        return fixed == null ? source.instant() : fixed;
    }
}
