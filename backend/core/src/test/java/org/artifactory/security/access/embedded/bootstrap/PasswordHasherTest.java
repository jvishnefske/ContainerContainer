/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests {@link PasswordHasher}: bcrypt cost 12 output, PBKDF2 acceptance, and safe refusals. */
class PasswordHasherTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static PasswordHasher hasher;

    @BeforeAll
    static void createHasher() {
        hasher = PasswordHasher.standard();
    }

    @Test
    void encodesBcryptWithCost12AndVerifies() {
        String hash = hasher.encode(PASSWORD).toOptional().orElseThrow();
        assertTrue(hash.startsWith("{bcrypt}$2a$12$"), hash);
        assertTrue(hasher.matches(PASSWORD, hash));
        assertFalse(hasher.matches(PASSWORD + "x", hash));
    }

    @Test
    void saltsEachHash() {
        assertNotEquals(hasher.encode(PASSWORD).toOptional().orElseThrow(),
                hasher.encode(PASSWORD).toOptional().orElseThrow());
    }

    @Test
    void acceptsPbkdf2Sha256With600kIterations() {
        Pbkdf2PasswordEncoder pbkdf2 = new Pbkdf2PasswordEncoder("", 16, 600_000,
                SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        String hash = "{pbkdf2}" + pbkdf2.encode(PASSWORD);
        assertTrue(hasher.matches(PASSWORD, hash));
        assertFalse(hasher.matches("wrong password!", hash));
    }

    @Test
    void nullHashNeverMatches() {
        assertFalse(hasher.matches(PASSWORD, null));
        assertFalse(hasher.matches("", null));
        assertFalse(hasher.matches(null, null));
    }

    @Test
    void unknownOrMalformedHashNeverMatchesAndDoesNotThrow() {
        assertFalse(hasher.matches(PASSWORD, "{noop}" + PASSWORD));
        assertFalse(hasher.matches(PASSWORD, PASSWORD));
        assertFalse(hasher.matches(PASSWORD, "{bcrypt}not-a-bcrypt-hash"));
        assertFalse(hasher.matches(null, hasher.encode(PASSWORD).toOptional().orElseThrow()));
    }

    @Test
    void refusesNullAndOverlongPasswords() {
        assertFalse(hasher.encode(null).isOk());
        assertFalse(hasher.encode("a".repeat(73)).isOk());
    }
}
