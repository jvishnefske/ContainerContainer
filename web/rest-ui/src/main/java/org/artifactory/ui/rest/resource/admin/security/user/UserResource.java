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

package org.artifactory.ui.rest.resource.admin.security.user;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.BlockOnConversion;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.ui.rest.model.admin.security.user.DeleteUsersModel;
import org.artifactory.ui.rest.model.admin.security.user.User;
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

/**
 * @author Chen Keinan
 */
@Path("users")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UserResource extends BaseResource {

    @Autowired
    private SecurityServiceFactory securityFactory;
    @Autowired
    private SecurityService securityService;

    @POST
    @BlockOnConversion
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createUser(User userModel) throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.createUser(), userModel);
    }

    @POST
    @BlockOnConversion
    @Path("{userName}/expirePassword")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response expireUserPassword(@PathParam("userName") String userName) throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.expireUserPassword(), userName);
    }

    @POST
    @BlockOnConversion
    @Path("{userName}/unexpirePassword")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response unexpirePassword(@PathParam("userName") String userName) throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.unexpirePassword(), userName);
    }

    @POST
    @BlockOnConversion
    @Path("expirePasswordForAllUsers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response expirePasswordForAllUsers() throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.expirePasswordForAllUsers());
    }

    @POST
    @BlockOnConversion
    @Path("unexpirePasswordForAllUsers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response unexpirePasswordForAllUsers() throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.unexpirePasswordForAllUsers());
    }

    @PUT
    @Path("{id : [^/]+}")
    @BlockOnConversion
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateUser(User userModel) throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.updateUser(), userModel);
    }

    @POST
    @Path("userDelete")
    @BlockOnConversion
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteUsers(DeleteUsersModel deleteUsersModel) throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.deleteUser(), deleteUsersModel);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllUsers() throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.getAllUsers());
    }

    @GET
    @Path("{id: [^/]+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUser() throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.getUser());
    }

    @GET
    @Path("groups")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllGroupNames() throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.getAllGroupNames());
    }

    @POST
    @Path("externalStatus")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkExternalStatus(User user) throws ForbiddenException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return runService(securityFactory.checkExternalStatus(), user);
    }
}
