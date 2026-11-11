/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.model.xstream.security;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.model.security.GroupImpl;
import org.artifactory.model.security.UserImpl;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.UserInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link UserImpl}
 *
 * @author Yossi Shaul
 */
public class UserImplTest {

    @Mock
    private ArtifactoryContext context;

    @Mock
    private UserGroupService userGroupService;
    private AutoCloseable mocks;

    @BeforeEach
    public void initMocks() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void sameAuthContextForSameInstance() {
        UserImpl paul = new UserImpl("paul");
        paul.setPassword(new SaltedPassword("atreides", "000"));
        assertTrue(paul.hasSameAuthorizationContext(paul));
    }

    @Test
    void authContextDifferentIfPasswordIfDifferent() {
        UserImpl leto = new UserImpl("leto");
        leto.setPassword(new SaltedPassword("atreides", "000"));

        UserImpl letoWithNewPass = new UserImpl("leto");
        leto.setPassword(new SaltedPassword("tyrant", "000"));

        assertFalse(leto.hasSameAuthorizationContext(letoWithNewPass));
    }

    @Test
    void sameAuthContextUserWithNoPassword() {
        UserImpl paul = new UserImpl("paul");
        assertTrue(paul.hasSameAuthorizationContext(paul));
    }

    @Test
    void testIsGroupAdmin() {
        when(context.beanForType(UserGroupService.class)).thenReturn(userGroupService);
        GroupInfo admins = new GroupImpl("admins", "", false, true);
        GroupInfo readers = new GroupImpl("readers", "", false, false);
        Map<String, GroupInfo> groupInfos = Map.of("admins", admins, "readers", readers);
        when(userGroupService.getAllGroupsByGroupNames(List.of("readers", "admins"))).thenReturn(groupInfos);
        UserImpl user = new UserImpl("adminUser");
        user.addGroup("readers");
        user.addGroup("admins");
        assertTrue(user.isEffectiveAdmin(), "User should be an effective admin when in 'admins' group");

        user.setAdmin(false);
        user.setGroupAdmin(null);
        user.removeGroup("admins");
        assertFalse(user.isEffectiveAdmin(), "User should not be an effective admin when not in 'admins' group");

        user.setGroupAdmin(null);
        user.removeGroup("readers");
        assertFalse(user.isEffectiveAdmin(), "User should not be an effective admin when no groups are present");
    }

    @Test
    void testIsAdminMultipleAssociations() {
        UserImpl paul = new UserImpl("adminUser");
        paul.addGroup(new GroupImpl("admins", "", false, true));

        assertTrue(paul.isGroupAdmin());
        assertTrue(paul.isEffectiveAdmin());
        assertTrue(paul.isGroupAdminVerbatim());

        paul.addGroup(new GroupImpl("non-admins", "", false, false));
        // Still an admin
        assertTrue(paul.isGroupAdmin());
        assertTrue(paul.isEffectiveAdmin());
        assertTrue(paul.isGroupAdminVerbatim());
    }

    @ParameterizedTest
    @MethodSource("anonymous")
    void testIsAnonymous(String username, boolean expected) {
        assertEquals(UserInfo.isAnonymous(username), expected);
    }

    public static Stream<Arguments> anonymous() {
        return Stream.of(
                Arguments.of("", false),
                Arguments.of(null, false),
                Arguments.of("asda:122", false),
                Arguments.of(UserInfo.ANONYMOUS, true),
                Arguments.of("token:" + UserInfo.ANONYMOUS, true),
                Arguments.of("token:saddkasnudnaoin", false));
    }
}