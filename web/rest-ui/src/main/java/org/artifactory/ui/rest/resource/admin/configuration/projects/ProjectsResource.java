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

package org.artifactory.ui.rest.resource.admin.configuration.projects;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.artifactory.util.EdgeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author BarakH
 */
@Path("projects")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ProjectsResource extends BaseResource {

    @Autowired
    protected ConfigServiceFactory configServiceFactory;

    @GET
    @Path("unassigned/statistics")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUnassignedResourcesStats() {
        if (EdgeUtils.isEdgeLicensed()) {
            throw new BadRequestException(EdgeUtils.NOT_SUPPORTED_FOR_EDGE);
        }
        return runService(configServiceFactory.getUnassignedResourcesStats());
    }
}
