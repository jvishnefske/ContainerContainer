/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security;

import org.springframework.security.core.Authentication;

import java.util.OptionalLong;

/**
 * An authentication derived from a credential that expires, such as a JWT accepted by a plugin realm.
 *
 * <p>Artifactory keeps successful authentications in the HTTP session and in a header-keyed cache for a few
 * minutes ({@code security.authentication.cache.idleTimeSecs}). {@code AccessFilter} asks {@link #isExpired} before
 * reusing one, so a cached authentication is never used past the expiry of the credential it came from.
 */
public interface ExpiringAuthentication extends Authentication {

    /** @return epoch seconds at which this authentication stops being valid, empty when it does not expire */
    OptionalLong expiresAt();

    /**
     * Pure.
     *
     * @return whether {@code authentication} is an {@link ExpiringAuthentication} whose expiry is at or before
     *         {@code nowSeconds}
     */
    static boolean isExpired(Authentication authentication, long nowSeconds) {
        if (authentication instanceof ExpiringAuthentication expiring) {
            OptionalLong expiresAt = expiring.expiresAt();
            return expiresAt.isPresent() && expiresAt.getAsLong() <= nowSeconds;
        }
        return false;
    }
}
