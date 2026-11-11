/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.ui.rest.resource.admin.security.user;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.security.UserInfo;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.UserProfileHelperService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.ui.rest.service.admin.security.user.userprofile.UserProfileHelperService.BAD_CREDENTIALS_ERROR;

/**
 * Validates the admin's credentials.
 * Used by "get joinkey" part of admin page
 *
 * @author Yuval Reches
 */
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
@Component
@Path("validateUserPassword")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ValidateUserPasswordResource extends BaseResource {

    private final UserProfileHelperService userProfileHelperService;
    private final SecurityService securityService;

    @Context
    private HttpServletRequest httpServletRequest;

    /**
     * Uses @GET as the validation is done via headers only
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response validateUser() {
        securityService.assertValidAuthInCaseMfaEnabled();
        String errorMsg = isUserPasswordValid();
        if (isBlank(errorMsg)) {
            return Response.ok().build();
        }
        return artifactoryResponse.responseCode(HttpStatus.SC_UNAUTHORIZED).error(errorMsg).buildResponse();
    }

    String isUserPasswordValid() {
        UserInfo userInfo = userProfileHelperService.loadUserInfo();
        String errMsg = StringUtils.EMPTY;
        if (!userInfo.isEffectiveAdmin()) {
            errMsg = "The user: '" + userInfo.getUsername() + "' is not admin user.";
        }
        String userPassword = userProfileHelperService.getUserPasswordFromHeader(httpServletRequest);
        if (isBlank(userPassword)) {
            errMsg = BAD_CREDENTIALS_ERROR;
        }
        if (!userProfileHelperService.authenticate(userInfo, userPassword)) {
            errMsg = BAD_CREDENTIALS_ERROR;
        }
        return errMsg;
    }
}
