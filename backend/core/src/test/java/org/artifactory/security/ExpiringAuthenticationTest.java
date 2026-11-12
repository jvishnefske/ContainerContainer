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

import org.artifactory.addon.plugin.groovy.PluginRealmAuthentication;
import org.artifactory.api.security.UserInfoBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3: cached realm authentications are not reused past their credential's expiry or after a plugin reload. */
class ExpiringAuthenticationTest {

    private static PluginRealmAuthentication expiringAt(OptionalLong expiresAt) {
        return new PluginRealmAuthentication(new SimpleUser(new UserInfoBuilder("u").build()), "p", "r", expiresAt,
                null);
    }

    @Test
    void unusableOnlyAtOrAfterTheCredentialsExpiry() {
        assertFalse(ExpiringAuthentication.isUnusable(expiringAt(OptionalLong.of(100)), 99));
        assertTrue(ExpiringAuthentication.isUnusable(expiringAt(OptionalLong.of(100)), 100));
        assertFalse(ExpiringAuthentication.isUnusable(expiringAt(OptionalLong.empty()), Long.MAX_VALUE));
        assertFalse(ExpiringAuthentication.isUnusable(new UsernamePasswordAuthenticationToken("u", "p"), 0));
        assertFalse(ExpiringAuthentication.isUnusable(null, 0));
    }

    @Test
    void unusableOnceRevoked() {
        AtomicBoolean loaded = new AtomicBoolean(true);
        PluginRealmAuthentication auth = new PluginRealmAuthentication(
                new SimpleUser(new UserInfoBuilder("u").build()), "p", "r", OptionalLong.empty(), null, loaded::get);
        assertFalse(ExpiringAuthentication.isUnusable(auth, 0));
        loaded.set(false);
        assertTrue(ExpiringAuthentication.isUnusable(auth, 0));
    }
}
