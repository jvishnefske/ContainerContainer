/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import org.artifactory.security.access.embedded.core.TokenClaimsFactory.TokenClaims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Revocability, token claims and the last-updated clock. */
class TokenRulesTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00.750Z");
    private static final TokenClaimsFactory FACTORY = new TokenClaimsFactory(ScopeGrammar.standard());

    @ParameterizedTest
    @CsvSource({
            "false, 21599, false",
            "false, 21600, false",
            "false, 21601, true",
            "false, 0, true",
            "false, -1, true",
            "true, 60, true",
            "true, 21600, true"})
    void revocabilityThreshold(boolean refreshable, long expiresIn, boolean revocable) {
        assertEquals(revocable, Revocability.isRevocable(refreshable, expiresIn));
        Instant expiresAt = expiresIn > 0 ? NOW.plusSeconds(expiresIn) : null;
        assertEquals(revocable, Revocability.isRevocable(refreshable, NOW, expiresAt));
    }

    @Test
    void revocabilityWithSubSecondLifetimeAboveThreshold() {
        assertTrue(Revocability.isRevocable(false, NOW, NOW.plusSeconds(21600).plusMillis(1)));
    }

    @Test
    void claimsHaveAccessShape() {
        TokenClaims claims = FACTORY.create("jfac@abc/users/admin", "jfac@abc", "applied-permissions/user",
                List.of("*@*"), NOW, 3600, "jti-1").toOptional().orElseThrow();
        Map<String, Object> map = claims.asMap();
        assertEquals(List.of("sub", "scp", "aud", "iss", "exp", "iat", "jti"), List.copyOf(map.keySet()));
        assertEquals("jfac@abc/users/admin", map.get("sub"));
        assertEquals("applied-permissions/user", map.get("scp"));
        assertEquals("*@*", map.get("aud"));
        assertEquals("jfac@abc", map.get("iss"));
        assertEquals(NOW.getEpochSecond(), map.get("iat"));
        assertEquals(NOW.getEpochSecond() + 3600, map.get("exp"));
        assertEquals("jti-1", map.get("jti"));
        assertEquals(NOW.plusSeconds(3600).getEpochSecond(), claims.expiry().orElseThrow().getEpochSecond());
        assertThrows(UnsupportedOperationException.class, () -> map.put("x", 1));
    }

    @Test
    void nonExpiringTokenHasNoExpAndMultiAudienceIsAList() {
        TokenClaims claims = FACTORY.create("s", "jfac@abc", "api:* applied-permissions/user",
                List.of("jfrt@abc", "jfac@abc"), NOW, 0, "j").toOptional().orElseThrow();
        Map<String, Object> map = claims.asMap();
        assertFalse(map.containsKey("exp"));
        assertTrue(claims.expiry().isEmpty());
        assertEquals(List.of("jfrt@abc", "jfac@abc"), map.get("aud"));
    }

    @Test
    void claimInputsAreValidated() {
        List<String> aud = List.of("jfrt@*");
        assertRefused(FACTORY.create(" ", "jfac@abc", "api:*", aud, NOW, 60, "j"));
        assertRefused(FACTORY.create("s", "not-a-service", "api:*", aud, NOW, 60, "j"));
        assertRefused(FACTORY.create("s", "jfac@*", "api:*", aud, NOW, 60, "j"));
        assertRefused(FACTORY.create("s", "jfac@abc", "api:*", List.of(), NOW, 60, "j"));
        assertRefused(FACTORY.create("s", "jfac@abc", "api:*", List.of("nope"), NOW, 60, "j"));
        assertRefused(FACTORY.create("s", "jfac@abc", "bogus", aud, NOW, 60, "j"));
        assertRefused(FACTORY.create("s", "jfac@abc", "api:*", aud, NOW, -1, "j"));
        assertRefused(FACTORY.create("s", "jfac@abc", "api:*", aud, NOW, Long.MAX_VALUE, "j"));
        assertRefused(FACTORY.create("s", "jfac@abc", "api:*", aud, NOW, 60, ""));
        assertRefused(FACTORY.create("s", "jfac@abc", "api:*", aud, NOW, 60, "x".repeat(129)));
        assertRefused(FACTORY.create("s", "jfac@abc", "api:*", aud, null, 60, "j"));
    }

    @Test
    void serviceIds() {
        assertEquals("jfac@01abc", TokenClaimsFactory.accessServiceId("01abc").toOptional().orElseThrow());
        assertEquals("jfrt@01abc", TokenClaimsFactory.artifactoryServiceId("01abc").toOptional().orElseThrow());
        assertRefused(TokenClaimsFactory.accessServiceId("a b"));
        assertRefused(TokenClaimsFactory.accessServiceId(null));
    }

    @Test
    void lastUpdatedClockIsStrictlyIncreasing() {
        assertEquals(1000L, LastUpdatedClock.next(1000, 500).toOptional().orElseThrow());
        assertEquals(501L, LastUpdatedClock.next(500, 500).toOptional().orElseThrow());
        assertEquals(501L, LastUpdatedClock.next(100, 500).toOptional().orElseThrow(), "clock stepped back");
        long stamp = 0;
        for (int i = 0; i < 100; i++) {
            long next = LastUpdatedClock.next(42, stamp).toOptional().orElseThrow();
            assertTrue(next > stamp);
            stamp = next;
        }
        assertRefused(LastUpdatedClock.next(0, Long.MAX_VALUE));
    }

    private static void assertRefused(EaOutcome<?> outcome) {
        assertInstanceOf(EaOutcome.Refused.class, outcome, () -> String.valueOf(outcome));
    }
}
