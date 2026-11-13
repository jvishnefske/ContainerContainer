/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security;

import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.api.security.SecurityListener;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.exceptions.PasswordChangeException;
import org.artifactory.security.interceptor.SecurityConfigurationChangesInterceptors;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.storage.security.service.UserLockInMemoryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.rest.system.UserLockPolicyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IA5: the must-change-password flag lifecycle in {@link SecurityServiceImpl} (set when an administrator sets
 * another user's password, cleared by the holder's own password change to a different password) and the refusal of
 * API keys for flagged users.
 */
class SecurityServiceMustChangePasswordTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String TEMPORARY = "Temporary-pass-1";

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
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String username, boolean admin) {
        UserInfo user = new UserInfoBuilder(username).admin(admin).build();
        SimpleUser principal = new SimpleUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities()));
    }

    private static MutableUserInfo bob(boolean mustChangePassword) {
        return new UserInfoBuilder("bob").password(new SaltedPassword(TEMPORARY, null))
                .mustChangePassword(mustChangePassword).build();
    }

    private MutableUserInfo createdUser() {
        ArgumentCaptor<MutableUserInfo> captor = ArgumentCaptor.forClass(MutableUserInfo.class);
        verify(store).createUserWithProperties(captor.capture(), anyBoolean());
        return captor.getValue();
    }

    private MutableUserInfo updatedUser() {
        ArgumentCaptor<MutableUserInfo> captor = ArgumentCaptor.forClass(MutableUserInfo.class);
        verify(store).updateUser(captor.capture());
        return captor.getValue();
    }

    @Test
    void adminCreatingAnotherUserWithAPasswordSetsTheFlag() {
        authenticateAs("root", true);
        service.createUser(bob(false));
        assertTrue(createdUser().isMustChangePassword());
    }

    @Test
    void createWithoutAdminOrWithoutPasswordDoesNotSetTheFlag() {
        authenticateAs("carol", false);
        service.createUser(bob(false));
        assertFalse(createdUser().isMustChangePassword());
    }

    @Test
    void createWithDisabledInternalPasswordDoesNotSetTheFlag() {
        authenticateAs("root", true);
        MutableUserInfo external = new UserInfoBuilder("bob").password(new SaltedPassword("", null))
                .passwordDisabled(true).build();
        service.createUser(external);
        assertFalse(createdUser().isMustChangePassword());
    }

    @Test
    void adminUpdatingAnotherUsersPasswordSetsTheFlag() {
        authenticateAs("root", true);
        when(store.findUser("bob")).thenReturn(new UserInfoBuilder("bob").build());
        service.updateUser(bob(false), true);
        assertTrue(updatedUser().isMustChangePassword());
    }

    @Test
    void adminUpdatingAnotherUserWithoutANewPasswordDoesNotSetTheFlag() {
        authenticateAs("root", true);
        when(store.findUser("bob")).thenReturn(bob(false));
        service.updateUser(bob(false), true);
        assertFalse(updatedUser().isMustChangePassword());
    }

    @Test
    void adminUpdatingOwnPasswordDoesNotSetTheFlag() {
        authenticateAs("bob", true);
        service.updateUser(bob(false), true);
        assertFalse(updatedUser().isMustChangePassword());
        verify(store, never()).findUser(anyString());
    }

    @Test
    void adminResetWithoutValidationSetsTheFlagButAnonymousResetDoesNot() {
        authenticateAs("root", true);
        service.changePasswordWithoutValidation(bob(false), TEMPORARY);
        verify(store).setMustChangePassword("bob", true);

        SecurityContextHolder.clearContext();
        MutableUserInfo alice = new UserInfoBuilder("alice").build();
        service.changePasswordWithoutValidation(alice, TEMPORARY);
        verify(store, never()).setMustChangePassword(eq("alice"), anyBoolean());
    }

    @Test
    void holderChangingToADifferentPasswordClearsTheFlag() {
        authenticateAs("bob", false);
        when(store.findUser("bob")).thenReturn(bob(true));
        when(userPass.canUserLogin("bob", TEMPORARY)).thenReturn(true);
        service.changePassword("bob", TEMPORARY, "Chosen-pass-22", "Chosen-pass-22");
        verify(store).changePassword(any(), any(), eq("Chosen-pass-22"));
        verify(store).setMustChangePassword("bob", false);
    }

    @Test
    void holderReusingTheTemporaryPasswordIsRefusedAndKeepsTheFlag() {
        authenticateAs("bob", false);
        when(store.findUser("bob")).thenReturn(bob(true));
        when(userPass.canUserLogin("bob", TEMPORARY)).thenReturn(true);
        assertThrows(PasswordChangeException.class,
                () -> service.changePassword("bob", TEMPORARY, TEMPORARY, TEMPORARY));
        verify(store, never()).changePassword(any(), any(), any());
        verify(store, never()).setMustChangePassword(anyString(), anyBoolean());
    }

    @Test
    void unflaggedPasswordChangeDoesNotTouchTheFlag() {
        authenticateAs("bob", false);
        when(store.findUser("bob")).thenReturn(bob(false));
        when(userPass.canUserLogin("bob", TEMPORARY)).thenReturn(true);
        service.changePassword("bob", TEMPORARY, "Chosen-pass-22", "Chosen-pass-22");
        verify(store, never()).setMustChangePassword(anyString(), anyBoolean());
    }

    @Test
    void apiKeyIsRefusedForAFlaggedUser() {
        when(store.findUser("bob")).thenReturn(bob(true));
        assertFalse(service.createPropsToken("bob", ApiKeyManager.API_KEY, "key"));
        assertFalse(service.updatePropsToken("bob", ApiKeyManager.API_KEY, "key"));
        verify(store, never()).addUserProperty(anyString(), anyString(), anyString());
        verify(store, never()).deleteUserProperty(anyString(), anyString());
    }

    @Test
    void apiKeyIsCreatedForAnUnflaggedUserAndOtherPropsTokensAreNotAffected() {
        when(store.findUser("bob")).thenReturn(bob(false));
        when(store.addUserProperty(anyString(), anyString(), anyString())).thenReturn(true);
        assertTrue(service.createPropsToken("bob", ApiKeyManager.API_KEY, "key"));
        when(store.findUser("bob")).thenReturn(bob(true));
        assertTrue(service.createPropsToken("bob", "ssh.basictoken", "value"));
    }

    @Test
    void setMustChangePasswordWritesTheStoreAndEvictsCachedAuthentications() {
        SecurityListener listener = mock(SecurityListener.class);
        service.addListener(listener);
        service.setMustChangePassword("bob", true);
        verify(store).setMustChangePassword("bob", true);
        verify(listener).onUserUpdate("bob");
    }
}
