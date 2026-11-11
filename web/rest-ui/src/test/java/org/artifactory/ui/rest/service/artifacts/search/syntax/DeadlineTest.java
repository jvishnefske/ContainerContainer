package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class DeadlineTest {

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void expired_returnsFalse_whenBudgetIsAmple() {
        MutableClock clock = new MutableClock(T0);
        Deadline deadline = Deadline.startingNow(clock, 30);

        assertFalse(deadline.expired());

        clock.advance(Duration.ofSeconds(29));
        assertFalse(deadline.expired());
    }

    @Test
    void expired_returnsTrue_atDeadlineBoundary() {
        MutableClock clock = new MutableClock(T0);
        Deadline deadline = Deadline.startingNow(clock, 30);

        clock.advance(Duration.ofSeconds(30));

        assertTrue(deadline.expired());
    }

    @Test
    void expired_returnsTrue_afterDeadlinePassed() {
        MutableClock clock = new MutableClock(T0);
        Deadline deadline = Deadline.startingNow(clock, 30);

        clock.advance(Duration.ofSeconds(31));

        assertTrue(deadline.expired());
    }

    @Test
    void remainingQueryTimeoutSeconds_returnsFullBudget_atStart() {
        MutableClock clock = new MutableClock(T0);
        Deadline deadline = Deadline.startingNow(clock, 30);

        assertEquals(30, deadline.remainingQueryTimeoutSeconds());
    }

    @ParameterizedTest
    @CsvSource({
            "0,  30",
            "1,  29",
            "10, 20",
            "29, 1",
            "30, 1",
            "31, 1",
            "60, 1"
    })
    void remainingQueryTimeoutSeconds_decreasesWithElapsed_andClampsToOne(
            long elapsedSeconds, int expectedRemaining) {
        MutableClock clock = new MutableClock(T0);
        Deadline deadline = Deadline.startingNow(clock, 30);

        clock.advance(Duration.ofSeconds(elapsedSeconds));

        assertEquals(expectedRemaining, deadline.remainingQueryTimeoutSeconds());
    }

    @Test
    void remainingQueryTimeoutSeconds_neverReturnsZero_so_setQueryTimeout_is_never_disabled() {
        // setQueryTimeout(0) means "no timeout" in JDBC, which would defeat the budget.
        MutableClock clock = new MutableClock(T0);
        Deadline deadline = Deadline.startingNow(clock, 5);

        clock.advance(Duration.ofSeconds(10));

        assertEquals(1, deadline.remainingQueryTimeoutSeconds());
    }

    @Test
    void remainingQueryTimeoutSeconds_handlesMaxIntBudget_withoutOverflow() {
        MutableClock clock = new MutableClock(T0);
        Deadline deadline = Deadline.startingNow(clock, Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, deadline.remainingQueryTimeoutSeconds());
    }

    @Test
    void startingNow_withoutClock_usesSystemClock() {
        Deadline deadline = Deadline.startingNow(30);

        assertFalse(deadline.expired());
        assertTrue(deadline.remainingQueryTimeoutSeconds() > 0);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration delta) {
            now = now.plus(delta);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
