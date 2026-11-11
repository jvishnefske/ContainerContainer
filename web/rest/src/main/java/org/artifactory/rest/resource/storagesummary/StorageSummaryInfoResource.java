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

package org.artifactory.rest.resource.storagesummary;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.artifactory.rest.services.ConfigServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.artifactory.security.permissionscope.PermissionScopeToken.INTERNAL_MISSION_CONTROL_READ;
import static org.artifactory.security.permissionscope.PermissionScopeToken.SYSTEM_STORAGE_READ;

/**
 * @author Chen Keinan
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Path("storageinfo")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class StorageSummaryInfoResource extends BaseResource {

    @Autowired
    protected ConfigServiceFactory configServiceFactory;

    @GET
    @AllowedPermissionScope(scopes = {INTERNAL_MISSION_CONTROL_READ, SYSTEM_STORAGE_READ})
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStorageSummary() {
        return runService(configServiceFactory.getStorageSummaryService());
    }

    @POST
    @Path("calculate")
    public Response calculateStorageSummaryAsync() {
        return runService(configServiceFactory.calculateStorageSummaryService());
    }

}
