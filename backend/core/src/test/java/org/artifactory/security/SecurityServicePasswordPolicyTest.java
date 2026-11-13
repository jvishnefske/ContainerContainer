/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security;

import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.PasswordPolicy;
import org.artifactory.security.exceptions.PasswordChangeException;
import org.artifactory.security.interceptor.SecurityConfigurationChangesInterceptors;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.storage.security.service.UserLockInMemoryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.rest.system.UserLockPolicyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IA6: the local password policy applies to every password {@link SecurityServiceImpl} sets (create, update,
 * change, administrator reset), with the username, and never to a write that carries no new password.
 */
class SecurityServicePasswordPolicyTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String GOOD = "a-long-unusual-passphrase-42";

    private final UserGroupStoreService store = mock(UserGroupStoreService.class);
    private final UserPassAuthenticationProvider userPass = mock(UserPassAuthenticationProvider.class);
    private final AccessService accessService = mock(AccessService.class);
    private SecurityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SecurityServiceImpl();
        ReflectionTestUtils.setField(service, "userGroupStoreService", store);
        ReflectionTestUtils.setField(service, "userPassAuthenticationProvider", userPass);
        ReflectionTestUtils.setField(service, "userLockInMemoryService", mock(UserLockInMemoryService.class));
        ReflectionTestUtils.setField(service, "accessService", accessService);
        ReflectionTestUtils.setField(service, "interceptors", mock(SecurityConfigurationChangesInterceptors.class));
        when(accessService.getUserLockPolicyConfig()).thenReturn(UserLockPolicyConfig.builder().attempts(0).build());
        when(store.createUserWithProperties(any(), anyBoolean())).thenReturn(true);
        UserInfo root = new UserInfoBuilder("root").admin(true).build();
        SimpleUser principal = new SimpleUser(root);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static MutableUserInfo bob(String password) {
        return new UserInfoBuilder("bob").password(new SaltedPassword(password, null)).build();
    }

    @Test
    void createRefusesAWeakPasswordAndStoresNothing() {
        for (String weak : new String[] {"short", "password1234", "bob-is-my-name-ok"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> service.createUser(bob(weak)), weak);
            assertTrue(e.getMessage().startsWith("New password refused: "), e.getMessage());
            assertFalse(e.getMessage().contains(weak));
        }
        verify(store, never()).createUserWithProperties(any(), anyBoolean());
    }

    @Test
    void createAcceptsAGoodPasswordAndUsersWithoutAPassword() {
        assertTrue(service.createUser(bob(GOOD)));
        assertTrue(service.createUser(new UserInfoBuilder("ext").password(new SaltedPassword("", null)).build()));
    }

    @Test
    void updateChecksOnlyANewPassword() {
        when(store.findUser("bob")).thenReturn(bob("short"));
        service.updateUser(bob("short"), true);
        verify(store).updateUser(any());

        assertThrows(IllegalArgumentException.class, () -> service.updateUser(bob("tiny"), true));
        service.updateUser(bob(GOOD), true);
    }

    @Test
    void administratorResetAndHolderChangeAreChecked() {
        UserInfo bob = new UserInfoBuilder("bob").build();
        assertThrows(PasswordChangeException.class, () -> service.changePasswordWithoutValidation(bob, "short"));
        verify(store, never()).changePassword(any(), any(), any());

        when(store.findUser("bob")).thenReturn(bob);
        when(userPass.canUserLogin("bob", GOOD)).thenReturn(true);
        PasswordChangeException e = assertThrows(PasswordChangeException.class,
                () -> service.changePassword("bob", GOOD, "Bob-Bob-Bob-Bob", "Bob-Bob-Bob-Bob"));
        assertTrue(e.getMessage().contains("username"), e.getMessage());
        verify(store, never()).changePassword(any(), any(), any());

        when(userPass.canUserLogin("bob", "wrong-old-password")).thenReturn(false);
        PasswordChangeException wrongOld = assertThrows(PasswordChangeException.class,
                () -> service.changePassword("bob", "wrong-old-password", "short", "short"));
        assertFalse(wrongOld.getMessage().contains("policy"), "the old password is verified first");
    }

    @Test
    void checkerFailsClosedWithoutItsList() {
        LocalPasswordPolicyChecker broken = new LocalPasswordPolicyChecker(EaOutcome.refused("missing"));
        assertTrue(broken.violation("bob", GOOD).isPresent());
        LocalPasswordPolicyChecker bundled = new LocalPasswordPolicyChecker();
        assertEquals(Optional.empty(), bundled.violation("bob", GOOD));
        assertTrue(bundled.violation("admin", "password").isPresent(), "the old default password is refused");
        assertTrue(new LocalPasswordPolicyChecker(EaOutcome.ok(new PasswordPolicy(Set.of())))
                .violation("bob", "x").isPresent());
    }
}
