/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.SigningKeys;
import org.jfrog.access.common.ServiceId;
import org.junit.jupiter.api.Test;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generation and parsing of the embedded signing material. */
class EaKeyMaterialTest {

    private static final ServiceId ACCESS = ServiceId.fromFormattedName("jfac@01keytest");

    @Test
    void generatedKeysParseAndNameTheService() throws Exception {
        Instant now = Instant.parse("2026-03-01T00:00:00Z");
        SigningKeys keys = EaFixture.ok(EaKeyMaterial.generate(ACCESS, Clock.fixed(now, ZoneOffset.UTC)));
        EaKeyMaterial.Parsed parsed = EaFixture.ok(EaKeyMaterial.parse(keys));
        assertEquals(EaKeyMaterial.KEY_BITS,
                ((RSAPublicKey) parsed.certificate().getPublicKey()).getModulus().bitLength());
        parsed.certificate().verify(parsed.certificate().getPublicKey());
        parsed.certificate().checkValidity(java.util.Date.from(now));
        assertEquals(now.plus(EaKeyMaterial.VALIDITY), parsed.certificate().getNotAfter().toInstant());
        String cn = parsed.certificate().getSubjectX500Principal().getName().replaceFirst("^CN=", "");
        assertEquals(ACCESS, ServiceId.fromCnName(cn));
        assertTrue(keys.certificatePem().startsWith("-----BEGIN CERTIFICATE-----\n"));
        assertEquals(keys, EaFixture.ok(SigningKeys.parse(keys.serialise())));
    }

    @Test
    void mismatchedKeyAndCertificateAreRefused() {
        SigningKeys a = EaFixture.signingKeys();
        SigningKeys b = EaFixture.ok(EaKeyMaterial.generate(ACCESS, Clock.systemUTC()));
        SigningKeys mixed = EaFixture.ok(SigningKeys.of(a.privateKeyPkcs8(), b.certificatePem()));
        assertInstanceOf(EaOutcome.Refused.class, EaKeyMaterial.parse(mixed));
    }
}
