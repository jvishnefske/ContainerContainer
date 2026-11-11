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

package org.artifactory.ui.rest.resource.admin.security.crowdsso;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.entitlement.Entitlement;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.model.admin.security.crowdsso.CrowdGroupModel;
import org.artifactory.ui.rest.model.admin.security.crowdsso.CrowdIntegration;
import org.artifactory.ui.rest.model.admin.security.login.UserLoginSso;
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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * @author Chen Keinan
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Path("crowd")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Entitlement(EntitlementType.CROWD)
public class CrowdSsoResource extends BaseResource {

    @Autowired
    private SecurityServiceFactory securityFactory;

    @PUT
    public Response updateCrowdIntegration(CrowdIntegration crowdIntegration) {
        return runService(securityFactory.updateCrowdIntegration(), crowdIntegration);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCrowdIntegration() {
        return runService(securityFactory.getCrowdIntegration());
    }

    @POST
    @Path("refresh{name:(/[^/]+?)?}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response refreshCrowdGroups(CrowdIntegration crowdIntegration) {
        return runService(securityFactory.refreshCrowdGroups(), crowdIntegration);
    }

    @POST
    @Path("import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importCrowdGroups(List<CrowdGroupModel> crowdGroupsModelList) {
        return runService(securityFactory.importCrowdGroups(), crowdGroupsModelList);
    }

    @POST
    @Path("test")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response testConnection(CrowdIntegration crowdIntegration) {
        return runService(securityFactory.testCrowdConnectionService(), crowdIntegration);
    }

    @POST
    @Path("loginSso")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loginSso(UserLoginSso userLoginSso) {
        return runService(securityFactory.crowdSsoLoginService(), userLoginSso);
    }

}
