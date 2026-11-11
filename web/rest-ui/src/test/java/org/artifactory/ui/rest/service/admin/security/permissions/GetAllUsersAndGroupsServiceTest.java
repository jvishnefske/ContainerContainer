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

package org.artifactory.ui.rest.service.admin.security.permissions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.admin.security.group.Group;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static org.artifactory.aol.AolService.SUPER_USER_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Maxim Yurkovsky
 */
public class GetAllUsersAndGroupsServiceTest {

    private AolService aolService;

    @BeforeMethod
    private void setup() {
        aolService = mock(AolService.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void destroy() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testExecuteForUserWithoutManagePermission() {
        AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.hasPermission(ArtifactoryPermission.MANAGE)).thenReturn(false);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnonymous()).thenReturn(false);
        when(aolService.isAol()).thenReturn(false);
        GetAllUsersAndGroupsService getAllUsersAndGroupsService = new GetAllUsersAndGroupsService(null,
                authService, securityService);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        getAllUsersAndGroupsService.execute(null, response);

        assertEquals(response.getResponseCode(), Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    public void testExecuteForAnonymous() {
        AuthorizationService authService = mock(AuthorizationService.class);
        when(authService.hasPermission(ArtifactoryPermission.MANAGE)).thenReturn(false);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAnonymous()).thenReturn(true);
        when(aolService.isAol()).thenReturn(true);
        GetAllUsersAndGroupsService getAllUsersAndGroupsService = new GetAllUsersAndGroupsService(null,
                authService, securityService);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        getAllUsersAndGroupsService.execute(null, response);

        assertEquals(response.getResponseCode(), Response.Status.UNAUTHORIZED.getStatusCode());
    }

    @Test
    public void testExecuteForUserWithManagePermission() {
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        when(authService.hasPermission(ArtifactoryPermission.MANAGE)).thenReturn(true);
        UserGroupService userGroupService = mock(UserGroupService.class);
        when(userGroupService.getAllUsersAndAdminStatus(false)).thenReturn(ImmutableMap.of("a", false));
        Group group = new Group();
        group.setGroupName("b");
        group.setAdminPrivileges(true);
        when(userGroupService.getAllGroups()).thenReturn(ImmutableList.of(group));
        when(aolService.isAol()).thenReturn(false);
        GetAllUsersAndGroupsService getAllUsersAndGroupsService = new GetAllUsersAndGroupsService(userGroupService,
                authService, securityService);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        getAllUsersAndGroupsService.execute(null, response);

        assertEquals(response.getResponseCode(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity().toString(),
                "{\"allGroups\":[{\"principal\":\"b\",\"admin\":true}],\"allUsers\":[{\"principal\":\"a\"," +
                        "\"admin\":false}]}");
    }

    @Test
    public void testExecuteDoesntReturnSuperUserOnAol() {
        AuthorizationService authService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        when(authService.hasPermission(ArtifactoryPermission.MANAGE)).thenReturn(true);
        UserGroupService userGroupService = mock(UserGroupService.class);
        Map<String, Boolean> users = new HashMap<>();
        users.put("a", false);
        users.put(SUPER_USER_NAME, true);
        when(userGroupService.getAllUsersAndAdminStatus(false)).thenReturn(users);
        when(userGroupService.getAllGroups()).thenReturn(ImmutableList.of());
        when(aolService.isAol()).thenReturn(true);
        GetAllUsersAndGroupsService getAllUsersAndGroupsService = new GetAllUsersAndGroupsService(userGroupService,
                authService, securityService);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        getAllUsersAndGroupsService.execute(null, response);

        assertEquals(response.getResponseCode(), Response.Status.OK.getStatusCode());
        assertEquals(response.getEntity().toString(),
                "{\"allGroups\":[],\"allUsers\":[{\"principal\":\"a\",\"admin\":false}]}");
    }
}