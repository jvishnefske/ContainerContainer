package org.artifactory.ui.rest.service.artifacts.search.syntax;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Request-level wall-clock budget shared by every SQL statement and loop in a
 * single syntax-search request. Computed once at request entry and consulted
 * whenever code is about to issue a SQL statement or enter another loop
 * iteration.
 * <p>
 * The {@link Clock} is injectable so tests can drive the wall-clock without
 * sleeping.
 *
 * @author Amir Avidan
 */
public final class Deadline {

    /**
     * Smallest value we are willing to hand to {@code Statement.setQueryTimeout}.
     * JDBC treats {@code 0} as "no timeout", which is exactly what we do
     * <em>not</em> want once the budget is exhausted, so we floor at one second
     * and let Postgres cancel the query almost immediately.
     */
    private static final int MIN_QUERY_TIMEOUT_SECONDS = 1;

    private final Clock clock;
    private final Instant deadlineAt;

    private Deadline(Clock clock, Instant deadlineAt) {
        this.clock = clock;
        this.deadlineAt = deadlineAt;
    }

    static Deadline startingNow(int budgetSeconds) {
        return startingNow(Clock.systemUTC(), budgetSeconds);
    }

    static Deadline startingNow(Clock clock, int budgetSeconds) {
        Instant now = clock.instant();
        return new Deadline(clock, now.plusSeconds(budgetSeconds));
    }

    boolean expired() {
        return !clock.instant().isBefore(deadlineAt);
    }

    int remainingQueryTimeoutSeconds() {
        long remaining = Duration.between(clock.instant(), deadlineAt).getSeconds();
        if (remaining < MIN_QUERY_TIMEOUT_SECONDS) {
            return MIN_QUERY_TIMEOUT_SECONDS;
        }
        return (int) remaining;
    }
}
