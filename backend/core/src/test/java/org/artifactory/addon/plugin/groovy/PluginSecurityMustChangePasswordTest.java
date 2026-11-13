/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.security.UserInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** IA5: {@link PluginSecurity#createAccessToken(long)} refuses a caller who must change the password first. */
class PluginSecurityMustChangePasswordTest {

    private final SecurityService securityService = mock(SecurityService.class);
    private final List<String> minted = new ArrayList<>();

    private PluginSecurity securityFor(UserInfo caller) {
        when(securityService.currentUsername()).thenReturn(caller.getUsername());
        when(securityService.isAnonymous()).thenReturn(false);
        return new PluginSecurity(securityService,
                name -> name.equals(caller.getUsername()) ? Optional.of(caller) : Optional.empty(),
                (username, seconds) -> {
                    minted.add(username);
                    return new PluginSecurity.Minted("tok-" + username, seconds);
                },
                () -> null, () -> 1_800_000_000L);
    }

    private static PluginSecurity.PluginAccessToken mint(PluginSecurity security) {
        return PluginCallScope.run(PluginCallScope.Kind.EXECUTION, () -> security.createAccessToken(60));
    }

    @Test
    void flaggedCallerIsRefusedWithoutMinting() {
        PluginSecurity.PluginAccessToken token =
                mint(securityFor(new UserInfoBuilder("bob").mustChangePassword(true).build()));
        assertFalse(token.issued());
        assertEquals("password change required", token.reason());
        assertTrue(minted.isEmpty());
    }

    @Test
    void unflaggedCallerStillGetsAToken() {
        PluginSecurity.PluginAccessToken token = mint(securityFor(new UserInfoBuilder("bob").build()));
        assertTrue(token.issued());
        assertEquals(List.of("bob"), minted);
    }
}
