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

package org.artifactory.ui.rest.resource.admin.configuration.proxies;

import com.google.common.annotations.VisibleForTesting;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.model.proxies.ProxiesModel;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.ui.rest.model.admin.configuration.proxy.Proxy;
import org.artifactory.ui.rest.resource.admin.configuration.proxies.model.ProxyBasicInfo;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
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

import java.util.List;

/**
 * @author Chen Keinan
 */
@Path("proxies")
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ProxyResource extends BaseResource {
    protected ConfigServiceFactory configServiceFactory;
    private SecurityService securityService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response createProxies(Proxy proxy) {
        return runService(configServiceFactory.createProxiesService(), proxy);
    }

    @PUT
    @Path("{id: [^/]+}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response updateProxies(Proxy proxy) {
        return runService(configServiceFactory.updateProxiesService(), proxy);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProxies() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        // password should be returned for admin only. Project admin should see username only.
        return runService(configServiceFactory.getProxiesService(), false);
    }

    @GET
    @Path("{id: [^/]+}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProxy() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getProxiesService(), true);
    }

    @Path("deleteProxies")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response deleteProxies(ProxiesModel proxiesModel) {
        return runService(configServiceFactory.deleteProxiesService(), proxiesModel);
    }

    @GET
    @Path("proxyKeys")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getProxyKeys() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getProxyKeysService());
    }

    @Autowired
    public void setConfigServiceFactory(ConfigServiceFactory configServiceFactory) {
        this.configServiceFactory = configServiceFactory;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    @VisibleForTesting
    protected <Y> Response runService(RestService<Y> service, Y model) {
        return super.runService(service, model);
    }

    @GET
    @Path("getNonAdminProxies")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNonAdminProxies() {
        Response response = runService(configServiceFactory.getProxiesService(), false);
        List<ProxyBasicInfo> proxyBasicInfoResponse = ProxyBasicInfo.toBasicInfo(response);
        if (proxyBasicInfoResponse.isEmpty()) {
            return response;
        }
        return Response.fromResponse(response).entity(proxyBasicInfoResponse).build();
    }
}
