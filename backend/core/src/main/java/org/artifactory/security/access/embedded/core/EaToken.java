/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The record embedded access keeps of an access token it issued. The signed JWT itself is never stored; this holds
 * what is needed to list, revoke and refresh it.
 * <p>
 * The refresh token is stored only as its SHA-256 hex ({@link EaHashes#sha256Hex}), and {@link #scopeSha256()} is
 * the SHA-256 hex of {@link #scope()} for indexing. {@link #revocable()} applies {@link Revocability}.
 *
 * @param jti         the token id ({@code jti} claim), not blank
 * @param subject     the {@code sub} claim, for example {@code jfac@<id>/users/admin}, not blank
 * @param issuer      the {@code iss} claim, for example {@code jfac@<id>}, not blank
 * @param scope       the space-separated {@code scp} claim, not blank
 * @param description an optional free-text description, or {@code null}
 * @param issuedAt    the {@code iat} instant
 * @param expiresAt   the {@code exp} instant, or {@code null} for a token that does not expire
 * @param refreshable whether a refresh token was issued with it
 * @param refreshHash the SHA-256 hex of the refresh token, present exactly when {@code refreshable}
 * @param revoked     whether the token has been revoked
 */
public record EaToken(String jti, String subject, String issuer, String scope, String description,
        Instant issuedAt, Instant expiresAt, boolean refreshable, String refreshHash, boolean revoked) {

    /** Validates the fields; {@code expiresAt} must be after {@code issuedAt}. */
    public EaToken {
        requireNonBlank(jti, "jti");
        requireNonBlank(subject, "subject");
        requireNonBlank(issuer, "issuer");
        requireNonBlank(scope, "scope");
        Objects.requireNonNull(issuedAt, "issuedAt");
        if (expiresAt != null && !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        if (refreshable != (refreshHash != null)) {
            throw new IllegalArgumentException("refreshHash must be present exactly when refreshable");
        }
        if (refreshHash != null && !EaHashes.isSha256Hex(refreshHash)) {
            throw new IllegalArgumentException("refreshHash must be a SHA-256 hex digest");
        }
    }

    /**
     * Returns the SHA-256 hex of the scope.
     *
     * @return 64 hex characters
     */
    public String scopeSha256() {
        return EaHashes.sha256Hex(scope);
    }

    /**
     * Returns the expiry if the token has one.
     *
     * @return the expiry, or empty for a non-expiring token
     */
    public Optional<Instant> expiry() {
        return Optional.ofNullable(expiresAt);
    }

    /**
     * Tells whether the token is expired at {@code now}: it has an expiry and {@code now} is at or after it.
     *
     * @param now the current time
     * @return {@code true} if expired
     */
    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /**
     * Tells whether the token can be revoked, by {@link Revocability#isRevocable(boolean, Instant, Instant)}.
     *
     * @return {@code true} if revocable
     */
    public boolean revocable() {
        return Revocability.isRevocable(refreshable, issuedAt, expiresAt);
    }

    /**
     * Returns a revoked copy.
     *
     * @return the copy with {@code revoked} set
     */
    public EaToken asRevoked() {
        return new EaToken(jti, subject, issuer, scope, description, issuedAt, expiresAt, refreshable, refreshHash,
                true);
    }

    private static void requireNonBlank(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
