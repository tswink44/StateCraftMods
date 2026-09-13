package dev.statecraft.runtime;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DecisionClockTest {
    @Test
    void oneDecisionUsesOneInstantAcrossNestedCallsAndZones() {
        AtomicLong now = new AtomicLong(1000);
        Clock mutable = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        };
        DecisionClock clock = new DecisionClock(mutable);
        try (var outer = clock.freeze()) {
            now.set(2000);
            assertEquals(1000, clock.millis());
            try (var nested = clock.freeze()) {
                assertEquals(1000, clock.withZone(ZoneOffset.ofHours(2)).millis());
                now.set(3000);
            }
            assertEquals(1000, clock.millis());
        }
        assertEquals(3000, clock.millis());
        assertThrows(IllegalStateException.class, () -> {
            try (var decision = clock.freeze()) {
                now.set(4000);
                throw new IllegalStateException("abort");
            }
        });
        assertEquals(4000, clock.millis());
    }
}
