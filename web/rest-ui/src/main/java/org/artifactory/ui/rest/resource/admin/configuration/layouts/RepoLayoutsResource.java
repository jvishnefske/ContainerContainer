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

package org.artifactory.ui.rest.resource.admin.configuration.layouts;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.ui.rest.model.admin.configuration.layouts.LayoutConfigViewModel;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Lior Hasson
 */
@Component
@Path("admin/repolayouts")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Produces(MediaType.APPLICATION_JSON)
public class RepoLayoutsResource extends BaseResource {

    @Autowired
    protected ConfigServiceFactory configServiceFactory;
    @Autowired
    protected CentralConfigService centralConfigService;
    @Autowired
    private SecurityService securityService;

    @GET
    public Response getLayouts() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getLayoutsService());
    }

    @GET
    @Path("{layoutKey}")
    public Response getLayoutConfig() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getLayoutInfoService());
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateLayout(RepoLayout repoLayout) {
        if (!securityService.isAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.updateLayoutService(), repoLayout);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createLayout(RepoLayout repoLayout) {
        if (!securityService.isAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.createLayoutService(), repoLayout);
    }

    @DELETE
    @Path("{layoutKey}")
    public Response deleteLayout() {
        if (!securityService.isAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.deleteLayoutService());
    }

    @POST
    @Path("testArtPath")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response testArtifactPathLayout(LayoutConfigViewModel repositoryLayoutModel) {
        if (!securityService.isAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.testArtPathService(), repositoryLayoutModel);
    }

    @POST
    @Path("resolveRegex")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response resolveRegexLayout(RepoLayout repoLayout) {
        if (!securityService.isAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.resolveRegexService(), repoLayout);
    }
}
