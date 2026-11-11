package org.artifactory.throttling.lh.mode;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class LoadHealerModeConfigTest {

    @Test
    void testOfCreatesConfigWithCorrectMillis() {
        Instant expiresAt = Instant.now().truncatedTo(ChronoUnit.MILLIS).plus(Duration.ofHours(1));
        LoadHealerModeConfig config = LoadHealerModeConfig.of("ENABLED", expiresAt);

        assertEquals("ENABLED", config.currentMode());
        assertEquals(expiresAt.toEpochMilli(), config.expiresAtMs());
    }

    @Test
    void testExpiresAtReturnsCorrectInstant() {
        Instant expiresAt = Instant.now().truncatedTo(ChronoUnit.MILLIS).plus(Duration.ofHours(2));
        LoadHealerModeConfig config = LoadHealerModeConfig.of("REACTIVE", expiresAt);

        assertEquals(expiresAt, config.expiresAt());
    }

    @Test
    void testExpiresAtRoundTripsWithConstructor() {
        long epochMs = System.currentTimeMillis() + 60_000;
        LoadHealerModeConfig config = new LoadHealerModeConfig("ENABLED", epochMs);

        assertEquals(Instant.ofEpochMilli(epochMs), config.expiresAt());
    }

    @Test
    void testIsExpiredReturnsTrueForPastInstant() {
        LoadHealerModeConfig config = LoadHealerModeConfig.of("ENABLED", Instant.now().minus(Duration.ofSeconds(1)));

        assertTrue(config.isExpired());
    }

    @Test
    void testIsExpiredReturnsFalseForFutureInstant() {
        LoadHealerModeConfig config = LoadHealerModeConfig.of("ENABLED", Instant.now().plus(Duration.ofHours(1)));

        assertFalse(config.isExpired());
    }

    @Test
    void testIsExpiredReturnsTrueWhenExpiresAtMsIsZero() {
        LoadHealerModeConfig config = new LoadHealerModeConfig("ENABLED", 0);

        assertTrue(config.isExpired());
    }

    @Test
    void testIsExpiredReturnsTrueWhenExpiresAtMsIsNegative() {
        LoadHealerModeConfig config = new LoadHealerModeConfig("ENABLED", -1);

        assertTrue(config.isExpired());
    }
}
