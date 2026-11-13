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

import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.model.security.UserImpl;
import org.artifactory.model.security.UserProperty;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserInfo;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.model.Realm;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.user.CustomDatumRequest;
import org.jfrog.access.rest.user.UserRequest;
import org.jfrog.access.rest.user.UserResponse;
import org.jfrog.access.rest.user.UserWithGroupsResponse;
import org.jfrog.access.user.UserMFAStatus;
import org.jfrog.security.crypto.CipherAlg;
import org.jfrog.security.file.SecurityFolderHelper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.artifactory.storage.db.security.service.access.UserMapper.MUST_CHANGE_PASSWORD_KEY;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * IA5: the must-change-password flag on {@link UserInfo}, its builder and copy, and its mapping to and from Access
 * user custom data under {@link UserMapper#MUST_CHANGE_PASSWORD_KEY}.
 */
@Test
public class UserMapperMustChangePasswordTest extends ArtifactoryHomeBoundTest {

    @BeforeMethod
    public void setup() {
        homeStub.getArtifactoryKey().delete();
        SecurityFolderHelper.createKeyFile(homeStub.getArtifactoryKey(), CipherAlg.AES128);
    }

    public void keyIsTheDocumentedOne() {
        assertEquals(MUST_CHANGE_PASSWORD_KEY, "must_change_password");
    }

    public void builderDefaultsToFalseAndCarriesTheFlag() {
        assertFalse(new UserInfoBuilder("u").build().isMustChangePassword());
        assertTrue(new UserInfoBuilder("u").mustChangePassword(true).build().isMustChangePassword());
    }

    public void copyKeepsTheFlagAndAuthorizationContextSeesIt() {
        MutableUserInfo flagged = new UserInfoBuilder("u").mustChangePassword(true).build();
        UserImpl copy = new UserImpl(flagged);
        assertTrue(copy.isMustChangePassword());
        assertTrue(copy.hasSameAuthorizationContext(flagged));
        copy.setMustChangePassword(false);
        assertFalse(copy.hasSameAuthorizationContext(flagged));
    }

    public void accessUserWithTheKeyIsFlaggedAndTheKeyIsNotAProperty() {
        UserResponse accessUser = accessUser().addCustomData(MUST_CHANGE_PASSWORD_KEY, "true")
                .addCustomData("height", "210");
        MutableUserInfo user = UserMapper.toArtifactoryUser(accessUser);
        assertTrue(user.isMustChangePassword());
        assertTrue(UserMapper.isMustChangePassword(accessUser));
        assertFalse(user.getUserProperty(MUST_CHANGE_PASSWORD_KEY).isPresent());
        assertEquals(user.getUserProperty("height").orElse(null), "210");
    }

    public void accessUserWithoutTheKeyOrWithAnotherValueIsNotFlagged() {
        assertFalse(UserMapper.toArtifactoryUser(accessUser()).isMustChangePassword());
        assertFalse(UserMapper.toArtifactoryUser(accessUser().addCustomData(MUST_CHANGE_PASSWORD_KEY, "yes"))
                .isMustChangePassword());
    }

    public void principalPathCarriesTheFlag() {
        UserWithGroupsResponse accessUser = new UserWithGroupsResponse("bob")
                .status(UserStatus.ENABLED)
                .realm(Realm.INTERNAL)
                .mfaStatus(UserMFAStatus.NONE)
                .customData(Map.of(MUST_CHANGE_PASSWORD_KEY, "true"));
        SimpleUser principal = UserMapper.toArtifactoryUser(
                new UserPrincipal(accessUser, Set.of(), Set.of(), Set.of()), "pw");
        assertTrue(principal.getDescriptor().isMustChangePassword());
        assertFalse(principal.getDescriptor().getUserProperty(MUST_CHANGE_PASSWORD_KEY).isPresent());
    }

    public void flaggedUserIsWrittenWithTheKeyOnCreateAndUpdate() {
        MutableUserInfo flagged = sampleUser().mustChangePassword(true).build();
        assertEquals(customData(UserMapper.toAccessUser(flagged, true)).get(MUST_CHANGE_PASSWORD_KEY), "true");
        assertEquals(customData(UserMapper.toUpdateUserRequest(flagged, true)).get(MUST_CHANGE_PASSWORD_KEY), "true");
    }

    public void unflaggedUserNeverWritesOrRemovesTheKey() {
        MutableUserInfo user = sampleUser().build();
        assertFalse(customData(UserMapper.toAccessUser(user, true)).containsKey(MUST_CHANGE_PASSWORD_KEY));
        // a full update must not lift the flag: only UserGroupStoreService.setMustChangePassword clears it
        assertFalse(customData(UserMapper.toUpdateUserRequest(user, true)).containsKey(MUST_CHANGE_PASSWORD_KEY));
    }

    public void aUserPropertyWithTheReservedKeyIsNotWritten() {
        MutableUserInfo user = sampleUser().addProp(new UserProperty(MUST_CHANGE_PASSWORD_KEY, "false")).build();
        assertFalse(customData(UserMapper.toUpdateUserRequest(user, true)).containsKey(MUST_CHANGE_PASSWORD_KEY));
    }

    public void roundTripThroughAccessCustomDataKeepsTheFlag() {
        for (boolean flag : new boolean[] {true, false}) {
            UserRequest request = UserMapper.toAccessUser(sampleUser().mustChangePassword(flag).build(), true);
            UserResponse stored = accessUser();
            customData(request).forEach((key, value) -> {
                if (value != null) {
                    stored.addCustomData(key, value);
                }
            });
            UserInfo read = UserMapper.toArtifactoryUser(stored);
            assertEquals(read.isMustChangePassword(), flag);
            assertNull(read.getUserProperty(MUST_CHANGE_PASSWORD_KEY).orElse(null));
        }
    }

    private static Map<String, String> customData(UserRequest request) {
        Map<String, String> map = new HashMap<>();
        if (request.getCustomData() == null) {
            return map;
        }
        for (Map.Entry<String, CustomDatumRequest> entry : request.getCustomData().entrySet()) {
            map.put(entry.getKey(), entry.getValue().getValue());
        }
        return map;
    }

    private static UserResponse accessUser() {
        return new UserResponse()
                .username("bob")
                .email("bob@example.org")
                .status(UserStatus.ENABLED)
                .realm(Realm.INTERNAL)
                .mfaStatus(UserMFAStatus.NONE);
    }

    private static UserInfoBuilder sampleUser() {
        return new UserInfoBuilder("bob")
                .password(new SaltedPassword("temporary-pass", null))
                .email("bob@example.org")
                .realm("internal");
    }
}
