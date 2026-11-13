/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import org.artifactory.security.access.embedded.core.BootstrapRules.BootstrapState;
import org.artifactory.security.access.embedded.core.BootstrapRules.ClaimVerdict;
import org.artifactory.security.access.embedded.core.BootstrapRules.Claimed;
import org.artifactory.security.access.embedded.core.BootstrapRules.Generate;
import org.artifactory.security.access.embedded.core.BootstrapRules.GenerateReason;
import org.artifactory.security.access.embedded.core.BootstrapRules.Keep;
import org.artifactory.security.access.embedded.core.BootstrapRules.Source;
import org.artifactory.security.access.embedded.core.BootstrapRules.StartDecision;
import org.artifactory.security.access.embedded.core.BootstrapRules.Unclaimed;
import org.artifactory.security.access.embedded.core.BootstrapRules.UseSupplied;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.artifactory.security.access.embedded.core.BootstrapRules.GENERIC_REFUSAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IA2 and IA4: the bootstrap state machine, token format, constant-time claim and serialisation. */
class BootstrapRulesTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Duration TTL = BootstrapRules.DEFAULT_TOKEN_TTL;
    private static final String TOKEN = "q8Zt3v_KpW1xYb7nR2mJcA9sLd4fGh6uE0iOaPzXwVk";
    private static final String OTHER = "Mm2nB4vC6xZ8lK0jH1gF3dS5aQ7wE9rT_yU-iOpLkJh";

    private static Unclaimed generated() {
        return BootstrapRules.unclaimed(TOKEN, T0, TTL, Source.GENERATED).toOptional().orElseThrow();
    }

    private static StartDecision decide(BootstrapState stored, String supplied, Instant now) {
        return BootstrapRules.decideOnStart(Optional.ofNullable(stored), Optional.ofNullable(supplied), now, TTL)
                .toOptional().orElseThrow();
    }

    @Test
    void unclaimedKeepsOnlyTheHash() {
        Unclaimed state = generated();
        assertEquals(EaHashes.sha256Hex(TOKEN), state.tokenSha256());
        assertEquals(T0.plus(TTL), state.expiresAt());
        assertFalse(BootstrapRules.serialise(state).contains(TOKEN));
    }

    @Test
    void firstStartGenerates() {
        assertEquals(new Generate(GenerateReason.FIRST_START), decide(null, null, T0));
    }

    @Test
    void validGeneratedTokenIsKeptAndExpiredOneRegenerated() {
        Unclaimed state = generated();
        assertEquals(new Keep(state), decide(state, null, T0.plus(TTL).minusSeconds(1)));
        assertEquals(new Generate(GenerateReason.EXPIRED), decide(state, null, T0.plus(TTL)));
    }

    @Test
    void suppliedTokenIsUsedInsteadOfGenerating() {
        UseSupplied first = assertInstanceOf(UseSupplied.class, decide(null, TOKEN, T0));
        assertEquals(Source.FILE, first.state().source());
        assertEquals(EaHashes.sha256Hex(TOKEN), first.state().tokenSha256());
        UseSupplied replacesGenerated = assertInstanceOf(UseSupplied.class, decide(generated(), OTHER, T0));
        assertEquals(EaHashes.sha256Hex(OTHER), replacesGenerated.state().tokenSha256());
    }

    @Test
    void sameSuppliedTokenIsKeptUntilItExpires() {
        Unclaimed fromFile = BootstrapRules.unclaimed(TOKEN, T0, TTL, Source.FILE).toOptional().orElseThrow();
        assertEquals(new Keep(fromFile), decide(fromFile, TOKEN, T0.plusSeconds(60)));
        EaOutcome<StartDecision> expired = BootstrapRules.decideOnStart(Optional.of(fromFile), Optional.of(TOKEN),
                T0.plus(TTL), TTL);
        assertInstanceOf(EaOutcome.Refused.class, expired);
        UseSupplied rotated = assertInstanceOf(UseSupplied.class, decide(fromFile, OTHER, T0.plus(TTL)));
        assertEquals(T0.plus(TTL).plus(TTL), rotated.state().expiresAt());
    }

    @Test
    void withdrawnSuppliedTokenIsReplacedByAGeneratedOne() {
        Unclaimed fromFile = BootstrapRules.unclaimed(TOKEN, T0, TTL, Source.FILE).toOptional().orElseThrow();
        assertEquals(new Generate(GenerateReason.SUPPLIED_TOKEN_WITHDRAWN), decide(fromFile, null, T0));
    }

    @Test
    void claimedStaysClaimedWhateverIsSupplied() {
        Claimed claimed = new Claimed(T0, "10.0.0.1");
        assertEquals(new Keep(claimed), decide(claimed, null, T0));
        assertEquals(new Keep(claimed), decide(claimed, TOKEN, T0));
        assertTrue(BootstrapRules.isClaimed(Optional.of(claimed)));
        assertFalse(BootstrapRules.isClaimed(Optional.of(generated())));
        assertFalse(BootstrapRules.isClaimed(Optional.empty()));
    }

    @Test
    void malformedSuppliedTokenOrBadTtlIsRefused() {
        assertInstanceOf(EaOutcome.Refused.class,
                BootstrapRules.decideOnStart(Optional.empty(), Optional.of("short"), T0, TTL));
        assertInstanceOf(EaOutcome.Refused.class,
                BootstrapRules.decideOnStart(Optional.empty(), Optional.empty(), T0, Duration.ZERO));
        assertInstanceOf(EaOutcome.Refused.class, BootstrapRules.unclaimed(TOKEN, T0, Duration.ofSeconds(-1),
                Source.GENERATED));
    }

    @Test
    void tokenFormatBounds() {
        assertFalse(BootstrapRules.isWellFormedToken("a".repeat(21)));
        assertTrue(BootstrapRules.isWellFormedToken("a".repeat(22)));
        assertTrue(BootstrapRules.isWellFormedToken("a".repeat(1024)));
        assertFalse(BootstrapRules.isWellFormedToken("a".repeat(1025)));
        assertFalse(BootstrapRules.isWellFormedToken("a".repeat(21) + " "));
        assertFalse(BootstrapRules.isWellFormedToken("a".repeat(21) + "é"));
        assertFalse(BootstrapRules.isWellFormedToken(null));
    }

    @Test
    void suppliedFileContentIsStripped() {
        assertEquals(Optional.of(TOKEN), BootstrapRules.normaliseSupplied("  " + TOKEN + "\n"));
        assertEquals(Optional.empty(), BootstrapRules.normaliseSupplied("\n"));
        assertEquals(Optional.empty(), BootstrapRules.normaliseSupplied(null));
    }

    @Test
    void correctTokenClaims() {
        Claimed claimed = BootstrapRules.attemptClaim(generated(), TOKEN, T0.plusSeconds(5), "192.0.2.7")
                .toOptional().orElseThrow();
        assertEquals(new Claimed(T0.plusSeconds(5), "192.0.2.7"), claimed);
    }

    @Test
    void secondClaimReusedTokenExpiredAndWrongTokenAllGetTheSameError() {
        Unclaimed state = generated();
        Claimed claimed = BootstrapRules.attemptClaim(state, TOKEN, T0, "192.0.2.7").toOptional().orElseThrow();
        assertGeneric(BootstrapRules.attemptClaim(claimed, TOKEN, T0, "192.0.2.7"));
        assertGeneric(BootstrapRules.attemptClaim(claimed, OTHER, T0, "192.0.2.8"));
        assertGeneric(BootstrapRules.attemptClaim(state, TOKEN, T0.plus(TTL), "192.0.2.7"));
        assertGeneric(BootstrapRules.attemptClaim(state, OTHER, T0, "192.0.2.7"));
        assertGeneric(BootstrapRules.attemptClaim(state, "short", T0, "192.0.2.7"));
        assertGeneric(BootstrapRules.attemptClaim(state, null, T0, "192.0.2.7"));
    }

    @Test
    void verdictsAreDistinguishedForLogsOnly() {
        Unclaimed state = generated();
        assertEquals(ClaimVerdict.ACCEPTED, BootstrapRules.classifyClaim(state, TOKEN, T0));
        assertEquals(ClaimVerdict.MISMATCH, BootstrapRules.classifyClaim(state, OTHER, T0));
        assertEquals(ClaimVerdict.EXPIRED, BootstrapRules.classifyClaim(state, TOKEN, T0.plus(TTL)));
        assertEquals(ClaimVerdict.MALFORMED, BootstrapRules.classifyClaim(state, "x", T0));
        assertEquals(ClaimVerdict.ALREADY_CLAIMED,
                BootstrapRules.classifyClaim(new Claimed(T0, "a"), TOKEN, T0));
    }

    @Test
    void unusableClientAddressIsRecordedAsUnknown() {
        Claimed claimed = BootstrapRules.attemptClaim(generated(), TOKEN, T0, "bad;value")
                .toOptional().orElseThrow();
        assertEquals("unknown", claimed.clientIp());
        assertThrows(IllegalArgumentException.class, () -> new Claimed(T0, "a\nb"));
    }

    @Test
    void stateRoundTripsThroughText() {
        Unclaimed unclaimed = generated();
        assertEquals(unclaimed, BootstrapRules.parse(BootstrapRules.serialise(unclaimed)).toOptional().orElseThrow());
        Claimed claimed = new Claimed(T0, "2001:db8::1");
        assertEquals(claimed, BootstrapRules.parse(BootstrapRules.serialise(claimed)).toOptional().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "v2;claimed;2026-03-01T10:00:00Z;a", "v1;claimed;notatime;a", "v1;claimed;x",
            "v1;unclaimed;abc;2026-03-01T10:00:00Z;FILE",
            "v1;unclaimed;0000000000000000000000000000000000000000000000000000000000000000;2026-03-01T10:00:00Z;NOPE",
            "v1;other;a;b"})
    void malformedStateIsRefused(String text) {
        assertInstanceOf(EaOutcome.Refused.class, BootstrapRules.parse(text));
    }

    @Test
    void constantTimeCompare() {
        byte[] a = EaHashes.sha256(TOKEN);
        assertTrue(EaHashes.constantTimeEquals(a, EaHashes.sha256(TOKEN)));
        assertFalse(EaHashes.constantTimeEquals(a, EaHashes.sha256(OTHER)));
        assertFalse(EaHashes.constantTimeEquals(a, null));
        assertFalse(EaHashes.constantTimeEquals(null, null));
    }

    private static void assertGeneric(EaOutcome<Claimed> outcome) {
        EaOutcome.Refused<Claimed> refused = assertInstanceOf(EaOutcome.Refused.class, outcome);
        assertEquals(GENERIC_REFUSAL, refused.reason());
    }
}
