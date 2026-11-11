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

import lombok.RequiredArgsConstructor;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.admin.security.permissions.AllUsersAndGroupsModel;
import org.artifactory.util.AolUtils;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.artifactory.aol.AolService.SUPER_USER_NAME;

/**
 * @author Chen keinan
 */
@Component
@RequiredArgsConstructor
public class GetAllUsersAndGroupsService implements RestService {

    private final UserGroupService userGroupService;
    private final AuthorizationService authService;
    private final SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        if (!authService.hasPermission(ArtifactoryPermission.MANAGE)) {
            int responseCode = securityService.isAnonymous() ?
                    Response.Status.UNAUTHORIZED.getStatusCode() : Response.Status.FORBIDDEN.getStatusCode();
            response.responseCode(responseCode);
            response.error("MANAGE permission is required");
            return;
        }
        Map<String, Boolean> allUsers = userGroupService.getAllUsersAndAdminStatus(false);
        if (AolUtils.isAol()) {
            allUsers.remove(SUPER_USER_NAME);
        }
        List<AllUsersAndGroupsModel.PrincipalInfo> allUsersInfo = allUsers.entrySet().stream()
                .map(entry -> new AllUsersAndGroupsModel.PrincipalInfo(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        List<AllUsersAndGroupsModel.PrincipalInfo> allGroups = userGroupService.getAllGroups()
                .stream()
                .map(groupInfo -> new AllUsersAndGroupsModel.PrincipalInfo(groupInfo.getGroupName(),
                        groupInfo.isAdminPrivileges()))
                .collect(Collectors.toList());

        AllUsersAndGroupsModel allUsersAndGroups = new AllUsersAndGroupsModel();
        allUsersAndGroups.setAllUsers(allUsersInfo);
        allUsersAndGroups.setAllGroups(allGroups);
        response.iModel(allUsersAndGroups);
    }
}