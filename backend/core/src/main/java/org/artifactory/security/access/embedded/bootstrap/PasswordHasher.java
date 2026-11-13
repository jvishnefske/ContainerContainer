/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.bootstrap;

import org.artifactory.security.access.embedded.core.EaOutcome;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Hashes and checks local passwords with a slow, salted key-derivation function (IA4), using spring-security-crypto
 * (pure Java).
 * <p>
 * New hashes are {@value #BCRYPT_ID}-prefixed bcrypt with cost {@value #BCRYPT_COST}. Hashes prefixed
 * {@value #PBKDF2_ID} are also accepted: PBKDF2-HMAC-SHA256, {@value #PBKDF2_ITERATIONS} iterations, 16-byte salt,
 * hex encoded, no secret. The PBKDF2 iteration count is not stored in the hash, so only hashes made with exactly
 * these parameters verify.
 * <p>
 * {@link #matches} never throws. A {@code null} stored hash (an account without a password, or an unknown user), an
 * unknown prefix or a malformed hash is compared against a fixed dummy bcrypt hash made at construction, so that
 * the answer, always {@code false}, takes about as long as a real check.
 */
public final class PasswordHasher {

    /** The prefix of bcrypt hashes. */
    public static final String BCRYPT_ID = "{bcrypt}";
    /** The prefix of PBKDF2 hashes. */
    public static final String PBKDF2_ID = "{pbkdf2}";
    /** The bcrypt cost of new hashes. */
    public static final int BCRYPT_COST = 12;
    /** The PBKDF2 iteration count. */
    public static final int PBKDF2_ITERATIONS = 600_000;

    private static final int PBKDF2_SALT_BYTES = 16;
    private static final int DUMMY_SECRET_BYTES = 18;

    private final PasswordEncoder delegate;
    private final BCryptPasswordEncoder bcrypt;
    private final String dummyBcryptHash;

    private PasswordHasher(SecureRandom random) {
        this.bcrypt = new BCryptPasswordEncoder(BCRYPT_COST, random);
        Pbkdf2PasswordEncoder pbkdf2 = new Pbkdf2PasswordEncoder("", PBKDF2_SALT_BYTES, PBKDF2_ITERATIONS,
                SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        this.delegate = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt, "pbkdf2", pbkdf2));
        byte[] secret = new byte[DUMMY_SECRET_BYTES];
        random.nextBytes(secret);
        this.dummyBcryptHash = bcrypt.encode(Base64.getEncoder().encodeToString(secret));
    }

    /**
     * Creates the standard hasher. This computes the dummy hash, which takes as long as one bcrypt check.
     *
     * @return the hasher
     */
    public static PasswordHasher standard() {
        return new PasswordHasher(new SecureRandom());
    }

    /**
     * Hashes a password.
     *
     * @param rawPassword the password in clear
     * @return the {@value #BCRYPT_ID}-prefixed hash, or {@code Refused} if the password is {@code null} or longer
     *         than bcrypt accepts (72 bytes in UTF-8)
     */
    public EaOutcome<String> encode(String rawPassword) {
        if (rawPassword == null) {
            return EaOutcome.refused("password is missing");
        }
        try {
            return EaOutcome.ok(delegate.encode(rawPassword));
        } catch (IllegalArgumentException e) {
            return EaOutcome.refused("password cannot be hashed: " + e.getMessage());
        }
    }

    /**
     * Checks a password against a stored hash.
     *
     * @param rawPassword the presented password, or {@code null}
     * @param storedHash  the stored hash, or {@code null} if the account has no password or does not exist
     * @return {@code true} only if the stored hash is a supported, well-formed hash of the password
     */
    public boolean matches(String rawPassword, String storedHash) {
        String raw = rawPassword == null ? "" : rawPassword;
        boolean supported = storedHash != null
                && (storedHash.startsWith(BCRYPT_ID) || storedHash.startsWith(PBKDF2_ID));
        if (!supported) {
            dummyCheck(raw);
            return false;
        }
        try {
            return rawPassword != null && delegate.matches(raw, storedHash);
        } catch (IllegalArgumentException e) {
            dummyCheck(raw);
            return false;
        }
    }

    private void dummyCheck(String raw) {
        try {
            bcrypt.matches(raw, dummyBcryptHash);
        } catch (IllegalArgumentException e) {
            // The answer is false whatever happens; only the time spent matters.
        }
    }
}
