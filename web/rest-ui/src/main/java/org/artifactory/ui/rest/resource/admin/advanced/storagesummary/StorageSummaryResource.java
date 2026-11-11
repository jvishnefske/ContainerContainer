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

package org.artifactory.ui.rest.resource.admin.advanced.storagesummary;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.service.admin.advanced.AdvancedServiceFactory;
import org.artifactory.util.EdgeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Chen Keinan
 */
@Component
@Path("storagesummary")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class StorageSummaryResource extends BaseResource {

    @Autowired
    protected AdvancedServiceFactory advanceFactory;
    @Autowired
    protected SecurityService securityService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStorageSummary(@QueryParam(ProjectsService.PROJECT_KEY) String projectKey) {
        if (!securityService.isProjectAdmin(projectKey)) {
            throw new AuthorizationRestException();
        }
        return runService(advanceFactory.getStorageSummaryService());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("projects")
    public Response getProjectsStorageState() {
        if (EdgeUtils.isEdgeLicensed()) {
            throw new BadRequestException(EdgeUtils.NOT_SUPPORTED_FOR_EDGE);
        }
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(advanceFactory.getUIProjectsStorageUsage());
    }

    @POST
    @Path("refresh")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Consumes(MediaType.APPLICATION_JSON)
    public Response refreshStorageSummary() {
        return runService(advanceFactory.refreshStorageSummaryService());
    }

    @GET
    @Path("refresh/status")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response getRefreshStatus() {
        return runService(advanceFactory.getRefreshStatusService());
    }
}
