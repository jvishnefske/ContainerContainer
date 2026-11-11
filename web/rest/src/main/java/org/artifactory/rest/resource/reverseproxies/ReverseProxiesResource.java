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

package org.artifactory.rest.resource.reverseproxies;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.rest.common.model.reverseproxy.ReverseProxyDescriptorModel;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.services.ConfigServiceFactory;
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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Shay Yaakov
 */
@Path("system/configuration/webServer{id:(/[^/]+?)?}")
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ReverseProxiesResource extends BaseResource {

    @Autowired
    protected ConfigServiceFactory configServiceFactory;

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createReverseProxy(ReverseProxyDescriptorModel reverseProxy) {
        return runService(configServiceFactory.createReverseProxy(), reverseProxy);
    }


    @PUT
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateReverseProxy(ReverseProxyDescriptor reverseProxy) {
        return runService(configServiceFactory.updateReverseProxy(), reverseProxy);
    }

    @GET
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReverseProxies() throws Exception {
        return runService(configServiceFactory.getReverseProxies());
    }
}
