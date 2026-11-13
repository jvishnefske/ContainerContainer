/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.storage.db.security.service.access;

import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.rest.user.CustomDatumRequest;
import org.jfrog.access.rest.user.UpdateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.artifactory.storage.db.security.service.access.UserMapper.MUST_CHANGE_PASSWORD_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IA5: {@link AccessUserGroupStoreService#setMustChangePassword} patches only the flag's custom data, and the
 * user-property methods cannot write or delete the flag's key.
 */
class AccessUserGroupStoreMustChangePasswordTest {

    private final AccessService accessService = mock(AccessService.class);
    private final UsersClient usersClient = mock(UsersClient.class);
    private AccessUserGroupStoreService store;

    @BeforeEach
    void setUp() {
        AccessClient accessClient = mock(AccessClient.class);
        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(accessClient.cachedUsers()).thenReturn(usersClient);
        store = new AccessUserGroupStoreService();
        store.setAccessService(accessService);
    }

    private CustomDatumRequest patchedFlag() {
        ArgumentCaptor<UpdateUserRequest> captor = ArgumentCaptor.forClass(UpdateUserRequest.class);
        verify(usersClient).updateUser(captor.capture());
        UpdateUserRequest request = captor.getValue();
        assertEquals("bob", request.getUsername());
        assertNull(request.getPassword());
        assertEquals(1, request.getCustomData().size());
        return request.getCustomData().get(MUST_CHANGE_PASSWORD_KEY);
    }

    @Test
    void settingTheFlagPatchesTheKeyToTrue() {
        store.setMustChangePassword("bob", true);
        CustomDatumRequest datum = patchedFlag();
        assertEquals("true", datum.getValue());
        assertFalse(datum.isSensitive());
    }

    @Test
    void clearingTheFlagRemovesTheKey() {
        store.setMustChangePassword("bob", false);
        assertNull(patchedFlag().getValue());
    }

    @Test
    void propertyMethodsCannotTouchTheReservedKey() {
        assertFalse(store.addUserProperty("bob", MUST_CHANGE_PASSWORD_KEY, "false"));
        assertFalse(store.deleteUserProperty("bob", MUST_CHANGE_PASSWORD_KEY));
        store.deletePropertyFromAllUsers(MUST_CHANGE_PASSWORD_KEY);
        verify(usersClient, never()).updateUser(any());
        verify(usersClient, never()).findUserByUsername(anyString(), anyBoolean());
        verify(usersClient, never()).findUsersByCustomData(anyString(), anyBoolean());
    }
}
