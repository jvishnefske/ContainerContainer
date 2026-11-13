/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The JFrog Access revocability rule: a short-lived, non-refreshable token is not revocable, so verifying it never
 * needs a revocation lookup. A token is revocable if and only if it is refreshable, has no expiry, or lives longer
 * than {@link #NON_REVOCABLE_MAX_LIFETIME_SECONDS} (six hours). A non-refreshable token whose lifetime is exactly six
 * hours is therefore not revocable.
 */
public final class Revocability {

    /** The longest lifetime, in seconds, of a token that may be non-revocable: 21600 (six hours). */
    public static final long NON_REVOCABLE_MAX_LIFETIME_SECONDS = 21600L;

    private Revocability() {
    }

    /**
     * Applies the rule to a requested lifetime.
     *
     * @param refreshable      whether the token is refreshable
     * @param expiresInSeconds the lifetime in seconds; {@code 0} or less means the token does not expire
     * @return {@code true} if the token is revocable
     */
    public static boolean isRevocable(boolean refreshable, long expiresInSeconds) {
        return refreshable || expiresInSeconds <= 0 || expiresInSeconds > NON_REVOCABLE_MAX_LIFETIME_SECONDS;
    }

    /**
     * Applies the rule to an issued token.
     *
     * @param refreshable whether the token is refreshable
     * @param issuedAt    the issue time
     * @param expiresAt   the expiry, or {@code null} for a token that does not expire
     * @return {@code true} if the token is revocable
     */
    public static boolean isRevocable(boolean refreshable, Instant issuedAt, Instant expiresAt) {
        if (expiresAt == null) {
            return true;
        }
        Duration lifetime = Duration.between(Objects.requireNonNull(issuedAt, "issuedAt"), expiresAt);
        return refreshable || lifetime.compareTo(Duration.ofSeconds(NON_REVOCABLE_MAX_LIFETIME_SECONDS)) > 0;
    }
}
