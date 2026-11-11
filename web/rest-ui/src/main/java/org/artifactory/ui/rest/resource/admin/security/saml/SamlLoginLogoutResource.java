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

package org.artifactory.ui.rest.resource.admin.security.saml;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.service.admin.security.SecurityServiceFactory;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Gidi Shabat
 */
@Component
@Path("saml")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SamlLoginLogoutResource extends BaseResource {

    @Autowired
    private SecurityServiceFactory securityFactory;

    public final static String SAML_LOGIN_ENDPOINT = "/ui/api/v1/auth/saml/loginResponse";

    @Path("loginRequest")
    @GET
    public Response loginRequest() {
        return runService(securityFactory.handleLoginRequest());
    }

    @Path("loginResponse")
    @POST
    public Response loginResponse() {
        String targetUrl = HttpUtils.getPlatformServletContextUrl(servletRequest) + SAML_LOGIN_ENDPOINT;
        return Response.status(HttpStatus.SC_TEMPORARY_REDIRECT).header(HttpHeaders.LOCATION, targetUrl).build();
    }

    @Path("loginResponsePlatform")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response loginResponsePlatform() {
        return runService(securityFactory.handleLoginResponse());
    }

    @Path("logoutRequest")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response logoutRequest() {
        return runService(securityFactory.handleLogoutRequest());
    }
}
