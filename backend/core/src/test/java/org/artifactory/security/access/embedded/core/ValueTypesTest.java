/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Value types: validation, defensive copies and derived properties; and the outcome helpers. */
class ValueTypesTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final String PEM = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n";

    @Test
    void userDefaultsAndPasswordlessAdmin() {
        EaUser admin = EaUser.builder("admin", T0).admin(true).build();
        assertEquals(EaUser.INTERNAL_REALM, admin.realm());
        assertEquals(EaUser.Status.ENABLED, admin.status());
        assertTrue(admin.passwordHashIfSet().isEmpty());
        assertFalse(admin.canAuthenticateByPassword(), "IA1: no hash, no password login");
        EaUser withHash = admin.toBuilder().passwordHash("{bcrypt}x").modified(T0.plusSeconds(1)).build();
        assertTrue(withHash.canAuthenticateByPassword());
        assertEquals(T0, withHash.created());
        assertFalse(withHash.toBuilder().status(EaUser.Status.LOCKED).build().canAuthenticateByPassword());
        assertFalse(withHash.toBuilder().status(EaUser.Status.DISABLED).build().canAuthenticateByPassword());
    }

    @Test
    void userCopiesCollections() {
        Set<String> groups = new HashSet<>(Set.of("b", "a"));
        EaUser user = EaUser.builder("u", T0).groups(groups).customData(Map.of("k", "v")).build();
        groups.add("c");
        assertEquals(List.of("a", "b"), List.copyOf(user.groups()));
        assertThrows(UnsupportedOperationException.class, () -> user.groups().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> user.customData().put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> EaUser.builder(" ", T0).build());
        assertThrows(NullPointerException.class, () -> EaUser.builder("u", null).build());
    }

    @Test
    void groupAndPermission() {
        EaGroup group = EaGroup.internal("readers", T0);
        assertEquals(T0.plusSeconds(1), group.modifiedAt(T0.plusSeconds(1)).modified());
        EaPermission perm = new EaPermission("p", "P", "repo", "jfrt@abc", "{\"repos\":[\"ANY\"]}",
                Map.of("bob", Set.of("WRITE", "READ")), Map.of("readers", Set.of("READ")), false, T0, T0);
        assertEquals(List.of("READ", "WRITE"), List.copyOf(perm.userActions().get("bob")));
        assertTrue(perm.mentions("readers", true));
        assertFalse(perm.mentions("readers", false));
        EaPermission changed = perm.withGrants(null, Map.of(), Map.of(), T0.plusSeconds(2));
        assertEquals("p", changed.name());
        assertTrue(changed.userActions().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> new EaPermission("p", "P", "", "jfrt@abc", null, Map.of(), Map.of(), false, T0, T0));
    }

    @Test
    void tokenInvariantsAndRevocability() {
        String refresh = EaHashes.sha256Hex("refresh-secret");
        EaToken shortLived = new EaToken("j1", "sub", "jfac@abc", "applied-permissions/user", null, T0,
                T0.plusSeconds(3600), false, null, false);
        assertFalse(shortLived.revocable());
        assertFalse(shortLived.isExpiredAt(T0.plusSeconds(3599)));
        assertTrue(shortLived.isExpiredAt(T0.plusSeconds(3600)));
        assertEquals(EaHashes.sha256Hex("applied-permissions/user"), shortLived.scopeSha256());
        EaToken refreshable = new EaToken("j2", "sub", "jfac@abc", "api:*", "ci", T0, T0.plusSeconds(60), true,
                refresh, false);
        assertTrue(refreshable.revocable());
        assertTrue(refreshable.asRevoked().revoked());
        EaToken forever = new EaToken("j3", "sub", "jfac@abc", "api:*", null, T0, null, false, null, false);
        assertTrue(forever.revocable());
        assertTrue(forever.expiry().isEmpty());
        assertFalse(forever.isExpiredAt(Instant.MAX));
        assertThrows(IllegalArgumentException.class, () -> new EaToken("j", "s", "i", "api:*", null, T0, T0, false,
                null, false));
        assertThrows(IllegalArgumentException.class, () -> new EaToken("j", "s", "i", "api:*", null, T0, null, true,
                null, false));
        assertThrows(IllegalArgumentException.class, () -> new EaToken("j", "s", "i", "api:*", null, T0, null, true,
                "not-a-hash", false));
    }

    @Test
    void signingKeysCopyRoundTripAndRedact() {
        byte[] key = "pkcs8-bytes".getBytes(StandardCharsets.US_ASCII);
        SigningKeys keys = SigningKeys.of(key, PEM).toOptional().orElseThrow();
        key[0] = 'X';
        assertEquals('p', keys.privateKeyPkcs8()[0]);
        keys.privateKeyPkcs8()[0] = 'Y';
        assertEquals('p', keys.privateKeyPkcs8()[0]);
        SigningKeys parsed = SigningKeys.parse(keys.serialise()).toOptional().orElseThrow();
        assertEquals(keys, parsed);
        assertEquals(keys.hashCode(), parsed.hashCode());
        assertArrayEquals(keys.privateKeyPkcs8(), parsed.privateKeyPkcs8());
        assertEquals(PEM, parsed.certificatePem());
        assertFalse(keys.toString().contains("pkcs8"));
        assertInstanceOf(EaOutcome.Refused.class, SigningKeys.of(new byte[0], PEM));
        assertInstanceOf(EaOutcome.Refused.class, SigningKeys.of(key, "not pem"));
        assertInstanceOf(EaOutcome.Refused.class, SigningKeys.parse("v1:!!:!!"));
        assertInstanceOf(EaOutcome.Refused.class, SigningKeys.parse("v9:a:b"));
        assertInstanceOf(EaOutcome.Refused.class, SigningKeys.parse(null));
        assertNotEquals(keys, SigningKeys.of(new byte[]{1}, PEM).toOptional().orElseThrow());
    }

    @Test
    void outcomeHelpers() {
        EaOutcome<Integer> ok = EaOutcome.ok(2);
        assertEquals(EaOutcome.ok(3), ok.map(i -> i + 1));
        assertEquals(EaOutcome.refused("no"), ok.flatMap(i -> EaOutcome.<String>refused("no")));
        EaOutcome<Integer> refused = EaOutcome.refused("bad");
        assertEquals(EaOutcome.refused("bad"), refused.map(i -> i + 1));
        EaOutcome<Integer> unsupported = EaOutcome.unsupported("cert.rotate");
        assertEquals(EaOutcome.unsupported("cert.rotate"), unsupported.flatMap(i -> EaOutcome.ok("x")));
        assertEquals("ok:2", ok.fold(v -> "ok:" + v, r -> "r:" + r, u -> "u:" + u));
        assertEquals("r:bad", refused.fold(v -> "ok:" + v, r -> "r:" + r, u -> "u:" + u));
        assertEquals("u:cert.rotate", unsupported.fold(v -> "ok:" + v, r -> "r:" + r, u -> "u:" + u));
        assertTrue(ok.isOk());
        assertFalse(refused.isOk());
        assertTrue(refused.toOptional().isEmpty());
        assertEquals(EaOutcome.Done.DONE, EaOutcome.done().toOptional().orElseThrow());
        assertThrows(NullPointerException.class, () -> EaOutcome.ok(null));
    }
}
