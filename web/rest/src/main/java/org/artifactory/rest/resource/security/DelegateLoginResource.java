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
package org.artifactory.rest.resource.security;

import org.artifactory.api.rest.constant.SecurityRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.security.SingleSignOnService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;

/**
 * @author Tamir Hadad
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
@Path(SecurityRestConstants.PATH_ROOT + "/auth")
@Deprecated
public class DelegateLoginResource {
    private static final Logger log = LoggerFactory.getLogger(DelegateLoginResource.class);

    private static final String AUTH_ERR = "{\"error\" : \"'%s'\"}";
    private static final String ERR = "{\"error\" : \"%s\"}";

    @Autowired
    private SingleSignOnService singleSignOnService;

    @Context
    private HttpServletRequest request;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("login")
    public Response getUsersInfo(UserAuthDetails userAuthDetails) {
        AuthDelegationHandler authDelegationHandler = new AuthDelegationHandler(request);
        try {
            return authDelegationHandler.handleRequest(singleSignOnService, userAuthDetails);
        } catch (AuthenticationException auth) {
            log.debug("", auth);
            return Response.ok().entity(String.format(AUTH_ERR, auth.getMessage())).build();
        } catch (IllegalStateException e) {
            log.debug("", e);
            return Response.status(SC_INTERNAL_SERVER_ERROR)
                    .entity(String.format(AUTH_ERR, e.getMessage())).build();
        } catch (Exception e) {
            log.debug("", e);
            return Response.status(SC_BAD_REQUEST).entity(String.format(ERR, e.getMessage())).build();
        }
    }
}