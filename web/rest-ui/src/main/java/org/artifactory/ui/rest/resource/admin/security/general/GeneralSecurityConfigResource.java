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

package org.artifactory.ui.rest.resource.admin.security.general;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.security.UserLockPolicy;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.model.admin.security.general.SecurityConfig;
import org.artifactory.ui.rest.service.admin.security.SecurityServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * @author Chen Keinan
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Path("securityconfig")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GeneralSecurityConfigResource extends BaseResource {

    @Autowired
    private SecurityServiceFactory securityFactory;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateConfig(SecurityConfig securityConfig) {
        Response response = runService(securityFactory.updateSecurityConfig(), securityConfig);
        return response;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig() {
        Response response = runService(securityFactory.getSecurityConfig());
        return response;
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("userLockPolicy")
    public Response updateUserLockPolicy(UserLockPolicy userLockPolicy) {
        return runService(securityFactory.updateUserLockPolicy(), userLockPolicy);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("userLockPolicy")
    public Response getUserLockPolicy() {
        return runService(securityFactory.getUserLockPolicy());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("unlockUsers/{userName}")
    public Response unlockUser(@PathParam("userName") String userName) {
        return runService(securityFactory.unlockUser(), userName);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("unlockAllUsers")
    public Response unlockAllUsers() {
        return runService(securityFactory.unlockAllUsers());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("unlockUsers")
    public Response unlockUsers(List<String> users) {
        return runService(securityFactory.unlockUsers(), users);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("lockedUsers")
    public Response getAllLockedUsers() {
        return runService(securityFactory.getAllLockedUsers());
    }
}
