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
 * An authentication derived from a credential that expires or can be withdrawn, such as a JWT accepted by a plugin
 * realm.
 *
 * <p>Artifactory keeps successful authentications in the HTTP session and in a header-keyed cache for a few
 * minutes ({@code security.authentication.cache.idleTimeSecs}). {@code AccessFilter} asks {@link #isUnusable}
 * before reusing one, so a cached authentication is never used past the expiry of the credential it came from, nor
 * after whatever granted it was withdrawn (for a plugin realm: the plugin was reloaded or removed).
 */
public interface ExpiringAuthentication extends Authentication {

    /** @return epoch seconds at which this authentication stops being valid, empty when it does not expire */
    OptionalLong expiresAt();

    /** @return whether whatever granted this authentication has been withdrawn since */
    default boolean revoked() {
        return false;
    }

    /**
     * Pure apart from {@link #revoked()}.
     *
     * @return whether {@code authentication} is an {@link ExpiringAuthentication} that is revoked or whose expiry is
     *         at or before {@code nowSeconds}
     */
    static boolean isUnusable(Authentication authentication, long nowSeconds) {
        if (authentication instanceof ExpiringAuthentication expiring) {
            OptionalLong expiresAt = expiring.expiresAt();
            return expiring.revoked() || (expiresAt.isPresent() && expiresAt.getAsLong() <= nowSeconds);
        }
        return false;
    }
}
