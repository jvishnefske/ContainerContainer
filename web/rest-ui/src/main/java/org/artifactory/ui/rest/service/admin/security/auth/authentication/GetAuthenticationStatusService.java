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

package org.artifactory.ui.rest.service.admin.security.auth.authentication;

import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.ldap.UnavailableRememberMeException;
import org.artifactory.session.ReauthenticateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.artifactory.ui.rest.resource.admin.security.auth.AuthResource.WITH_GROUPS_PARAM;


/**
 * @author Nadav Yogev
 */
@Component
public class GetAuthenticationStatusService implements RestService {

    @Autowired
    private ReauthenticateService reauthenticateService;
    @Autowired
    private SecurityService securityService;
    public static final String USERNAME_PARAM = "username";
    public static final String REALM_PARAM = "realm";

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String username = request.getPathParamByKey(USERNAME_PARAM);
        String realm = request.getPathParamByKey(REALM_PARAM);
        if (!securityService.isAdmin() && !securityService.currentUsername().equals(username)) {
            response.responseCode(SC_UNAUTHORIZED).error("User is only allowed to reauthenticate himself");
            return;
        }
        try {
            SimpleUser userDetails = reauthenticateService.reauthenticateByRealm(username, realm);
            AuthenticationStatusModel authenticationStatus = new AuthenticationStatusModel();
            String status =
                    userDetails.isEnabled() ? AuthenticationStatusModel.ENABLED : AuthenticationStatusModel.DISABLED;
            authenticationStatus.setStatus(status);
            boolean withGroups = Boolean.parseBoolean(request.getQueryParamByKey(WITH_GROUPS_PARAM));
            if (withGroups) {
                List<String> groupNames = userDetails.getDescriptor().getGroups().stream()
                        .map(UserGroupInfo::getGroupName)
                        .collect(Collectors.toList());
                authenticationStatus.setGroups(groupNames);
            }
            response.iModel(authenticationStatus);
        } catch (AuthenticationException e) {
            AuthenticationStatusModel authenticationStatus = new AuthenticationStatusModel();
            String status;
            if (e instanceof UsernameNotFoundException) {
                status = AuthenticationStatusModel.NOT_FOUND;
            } else if (e instanceof UnavailableRememberMeException) {
                status = AuthenticationStatusModel.UNAVAILABLE;
            } else {
                status = AuthenticationStatusModel.DISABLED;
            }
            authenticationStatus.setStatus(status);
            response.iModel(authenticationStatus);
        }
    }
}
