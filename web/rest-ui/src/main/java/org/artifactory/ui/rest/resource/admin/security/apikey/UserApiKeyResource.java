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

package org.artifactory.ui.rest.resource.admin.security.apikey;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.BlockOnConversion;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.ui.rest.service.admin.security.SecurityServiceFactory;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.UserProfileHelperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Chen Keinan
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Path("userApiKey{id:(/[^/]+?)?}")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UserApiKeyResource extends PasswordProtectedResource {

    protected SecurityServiceFactory securityFactory;

    protected ApiKeyManager apiKeyManager;

    @Autowired
    public void setApiKeyManager(ApiKeyManager apiKeyManager) {
        this.apiKeyManager = apiKeyManager;
    }

    @Autowired
    public UserApiKeyResource(SecurityServiceFactory securityFactory,
            UserProfileHelperService userProfileHelperService,
            AuthorizationService authorizationService, SecurityService securityService) {
        super(userProfileHelperService, authorizationService, securityService);
        this.securityFactory = securityFactory;
    }

    @HEAD
    @Produces(MediaType.APPLICATION_JSON)
    public Response isExist(@PathParam("id") String id) {
        if (securityService.isAnonymous()) {
            return Response.status(HttpStatus.SC_NOT_FOUND).build();
        }
        String userName = securityService.currentUsername();
        if (!StringUtils.isEmpty(id)) {
            id = id.startsWith("/") ? id.substring(1) : id;
            if (securityService.isAdmin()) {
                userName = id;
            } else {
                if (!id.equalsIgnoreCase(userName)) {
                    return Response.status(HttpStatus.SC_BAD_REQUEST).build();
                }
            }
        }
        TokenKeyValue token = apiKeyManager.getToken(userName);
        return token != null ? Response.ok().build() : Response.status(HttpStatus.SC_NOT_FOUND).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApiKey() {
        return runServiceWrapper(() -> runService(securityFactory.getApiKey()));
    }

    @DELETE
    @BlockOnConversion
    @Produces(MediaType.APPLICATION_JSON)
    public Response revokeApiKey() {
        return runService(securityFactory.revokeApiKey());
    }

    @PUT
    @BlockOnConversion
    @Produces(MediaType.APPLICATION_JSON)
    public Response regenerateApiKey() {
        return runServiceWrapper(() -> runService(securityFactory.regenerateApiKey()));
    }

    @POST
    @BlockOnConversion
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createApiKey() {
        return runServiceWrapper(() -> runService(securityFactory.createApiKey()));
    }

}
