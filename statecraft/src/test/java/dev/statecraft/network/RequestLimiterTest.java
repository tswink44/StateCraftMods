package dev.statecraft.network;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestLimiterTest {
    @Test
    void allowsABoundedBurstThenFiveRequestsPerSecond() {
        RequestLimiter limiter = new RequestLimiter();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.allow(player, 0));
        }
        assertFalse(limiter.allow(player, 0));
        assertFalse(limiter.allow(player, 199_999_999));
        assertTrue(limiter.allow(player, 200_000_000));
        assertFalse(limiter.allow(player, 200_000_000));
        assertTrue(limiter.allow(UUID.randomUUID(), 200_000_000));
        limiter.forget(player);
        assertTrue(limiter.allow(player, 200_000_000));
    }
}
