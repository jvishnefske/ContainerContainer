/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 helpers for values embedded access stores only as hashes: setup tokens, refresh tokens and token scopes.
 * Strings are hashed as UTF-8, and hex output is lower case. {@link #constantTimeEquals} compares digests with
 * {@link MessageDigest#isEqual}, whose running time does not depend on where the inputs differ.
 */
public final class EaHashes {

    private static final HexFormat HEX = HexFormat.of();

    private EaHashes() {
    }

    /**
     * Hashes a string.
     *
     * @param value the string, hashed as UTF-8
     * @return the 32-byte digest
     */
    public static byte[] sha256(String value) {
        return digest().digest(Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Hashes a string and encodes the digest as lower-case hex.
     *
     * @param value the string, hashed as UTF-8
     * @return 64 hex characters
     */
    public static String sha256Hex(String value) {
        return HEX.formatHex(sha256(value));
    }

    /**
     * Compares two digests in time that depends only on their lengths.
     *
     * @param a the first digest, or {@code null}
     * @param b the second digest, or {@code null}
     * @return {@code true} if both are non-null and equal
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        return a != null && b != null && MessageDigest.isEqual(a, b);
    }

    /**
     * Tells whether a string is 64 lower-case hex characters, the form {@link #sha256Hex} produces.
     *
     * @param value the string to check, or {@code null}
     * @return {@code true} if it is a well-formed SHA-256 hex digest
     */
    public static boolean isSha256Hex(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) {
                return false;
            }
        }
        return true;
    }

    static byte[] fromHex(String hex) {
        return HEX.parseHex(hex);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to provide SHA-256 (MessageDigest Javadoc).
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
