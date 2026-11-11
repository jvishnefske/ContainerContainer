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

package org.artifactory.ui.rest.resource.admin.security.auth;

import org.apache.http.HttpHeaders;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.dataholder.PasswordContainer;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.model.admin.security.login.UserLogin;
import org.artifactory.ui.rest.service.admin.security.SecurityServiceFactory;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static jakarta.servlet.http.HttpServletResponse.SC_TEMPORARY_REDIRECT;
import static org.artifactory.ui.rest.service.admin.security.auth.authentication.GetAuthenticationStatusService.REALM_PARAM;
import static org.artifactory.ui.rest.service.admin.security.auth.authentication.GetAuthenticationStatusService.USERNAME_PARAM;

/**
 * @author chen keinan
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Path("auth")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@ActivityDetails(group = RestApiGroup.AUTH_API)
public class AuthResource extends BaseResource {

    public static final String WITH_GROUPS_PARAM = "withGroups";

    @Autowired
    protected SecurityServiceFactory securityFactory;
    @Autowired
    protected SecurityService securityService;

    @POST
    @Path("login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(UserLogin userLoginModel) {
        return runService(securityFactory.loginService(), userLoginModel);
    }

    @POST
    @Path("logout")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response logout() {
        return runService(securityFactory.logoutService());
    }

    @GET
    @Path("issaml")
    public Response isSamlAuthentication() {
        return runService(securityFactory.isSamlAuthentication());
    }

    /**
     * Tests are in OpenIdGatewayResourceTest
     */
    @GET
    @Path("redirect")
    @Produces(MediaType.APPLICATION_JSON)
    public Response redirect(@QueryParam("redirectTo") String redirectTo) {
        return runService(securityFactory.redirectService(), redirectTo);
    }

    @POST
    @Path("forgotpassword")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response forgotPassword(UserLogin userLoginModel) {
        return runService(securityFactory.forgotPassword(), userLoginModel);
    }

    /**
     * If users did not change their mail URL from Artifactory URL to mothership URL, their reset password mail will
     * be sent with URL of Artifactory.
     * This will redirect this request to the UI
     */
    @GET
    @Path("reset-password")
    public Response resetPasswordRedirect(@QueryParam("key") String key) {
        String redirectTo = HttpUtils.getPlatformServletContextUrl(servletRequest) + "/ui/auth/reset-password?key=" +
                key;
        return Response.status(SC_TEMPORARY_REDIRECT).header(HttpHeaders.LOCATION, redirectTo).build();
    }

    @POST
    @Path("validatetoken")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response validateToken() {
        return runService(securityFactory.validateToken());
    }

    @POST
    @Path("resetpassword")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetPassword(UserLogin userLoginModel) {
        return runService(securityFactory.resetPassword(), userLoginModel);
    }

    @POST
    @Path("changePassword")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response changePassword(PasswordContainer passwordContainer) {
        return runService(securityFactory.changePassword(), passwordContainer);
    }

    @POST
    @Path("loginRelatedData")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetchLoginRelatedData(UserLogin userLogin) {
        // Redirect to might be initialized in current user login data
        return runService(securityFactory.loginRelatedData(), userLogin);
    }

    @GET
    @Path("current")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCurrentUser(@QueryParam(WITH_GROUPS_PARAM) Boolean withGroups) {
        return runService(securityFactory.getCurrentUser());
    }

    @GET
    @Path("canAnnotate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response canAnnotate() {
        return runService(securityFactory.getCanAnnotateService());
    }

    @GET
    @Path("reauthenticate/{realm}/{username}")
    public Response reauthenticate(@PathParam(REALM_PARAM) String realm, @PathParam(USERNAME_PARAM) String username) {
        return runService(securityFactory.getAuthenticationStatusService());
    }
}

