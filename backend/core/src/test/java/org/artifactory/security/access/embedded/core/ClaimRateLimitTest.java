/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import org.artifactory.security.access.embedded.core.ClaimRateLimit.Decision;
import org.artifactory.security.access.embedded.core.ClaimRateLimit.Result;
import org.artifactory.security.access.embedded.core.ClaimRateLimit.State;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IA4: claim attempts are limited per client and in total, with bounded memory. */
class ClaimRateLimitTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private State state = State.empty();

    private Decision attempt(ClaimRateLimit limit, String client, Instant now) {
        Result result = limit.check(state, client, now);
        state = result.state();
        return result.decision();
    }

    @Test
    void perClientLimitWithinAWindowThenResets() {
        ClaimRateLimit limit = new ClaimRateLimit(3, 100, WINDOW, 10);
        for (int i = 0; i < 3; i++) {
            assertEquals(Decision.ALLOWED, attempt(limit, "a", T0.plusSeconds(i)));
        }
        assertEquals(Decision.CLIENT_LIMITED, attempt(limit, "a", T0.plusSeconds(10)));
        assertEquals(Decision.ALLOWED, attempt(limit, "b", T0.plusSeconds(10)), "other clients unaffected");
        assertEquals(Decision.CLIENT_LIMITED, attempt(limit, "a", T0.plus(WINDOW).minusMillis(1)));
        assertEquals(Decision.ALLOWED, attempt(limit, "a", T0.plus(WINDOW)), "window ended");
    }

    @Test
    void globalLimitAcrossClients() {
        ClaimRateLimit limit = new ClaimRateLimit(5, 4, WINDOW, 100);
        for (int i = 0; i < 4; i++) {
            assertEquals(Decision.ALLOWED, attempt(limit, "client-" + i, T0));
        }
        assertEquals(Decision.GLOBAL_LIMITED, attempt(limit, "client-new", T0.plusSeconds(1)));
        assertFalse(state.clients().containsKey("client-new"), "denied attempt is not tracked");
        assertEquals(Decision.ALLOWED, attempt(limit, "client-new", T0.plus(WINDOW)));
    }

    @Test
    void deniedAttemptsDoNotCount() {
        ClaimRateLimit limit = new ClaimRateLimit(1, 2, WINDOW, 10);
        assertEquals(Decision.ALLOWED, attempt(limit, "a", T0));
        assertEquals(Decision.CLIENT_LIMITED, attempt(limit, "a", T0));
        assertEquals(Decision.CLIENT_LIMITED, attempt(limit, "a", T0));
        assertEquals(1, state.global().count());
        assertEquals(Decision.ALLOWED, attempt(limit, "b", T0));
    }

    @Test
    void trackedClientsAreBoundedEvictingOldest() {
        ClaimRateLimit limit = new ClaimRateLimit(1, 1000, Duration.ofHours(1), 3);
        attempt(limit, "a", T0);
        attempt(limit, "b", T0.plusSeconds(1));
        attempt(limit, "c", T0.plusSeconds(2));
        attempt(limit, "d", T0.plusSeconds(3));
        assertEquals(List.of("b", "c", "d"), List.copyOf(state.clients().keySet()));
        for (int i = 0; i < 50; i++) {
            attempt(limit, "x" + i, T0.plusSeconds(10 + i));
            assertTrue(state.clients().size() <= 3);
        }
    }

    @Test
    void endedWindowsAreDroppedBeforeLiveOnes() {
        ClaimRateLimit limit = new ClaimRateLimit(1, 1000, WINDOW, 2);
        attempt(limit, "old", T0);
        attempt(limit, "live", T0.plus(WINDOW).plusSeconds(1));
        attempt(limit, "new", T0.plus(WINDOW).plusSeconds(2));
        assertEquals(List.of("live", "new"), List.copyOf(state.clients().keySet()));
    }

    @Test
    void stateIsImmutable() {
        ClaimRateLimit limit = ClaimRateLimit.defaults();
        State before = State.empty();
        Result result = limit.check(before, "a", T0);
        assertTrue(before.clients().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.state().clients().clear());
        assertSame(Decision.ALLOWED, result.decision());
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClaimRateLimit(0, 1, WINDOW, 1));
        assertThrows(IllegalArgumentException.class, () -> new ClaimRateLimit(1, 1, Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new ClaimRateLimit(1, 1, WINDOW, 0));
    }
}
