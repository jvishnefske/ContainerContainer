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

package org.artifactory.ui.rest.resource.admin.configuration.propertysets;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.ui.rest.model.admin.configuration.propertysets.AdminPropertySetModel;
import org.artifactory.ui.rest.model.admin.configuration.propertysets.DeletePropertySetModel;
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

/**
 * @author Dan Feldman
 */
@Component
@Path("propertysets")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class PropertySetsResource extends BaseResource {

    public static final String PROP_SET_NAME = "name";
    public static final String PATH_PROP_SET_NAME = "{" + PROP_SET_NAME + "}";

    @Autowired
    protected ConfigServiceFactory configServiceFactory;
    @Autowired
    private SecurityService securityService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response createPropertySet(AdminPropertySetModel propertySet) {
        return runService(configServiceFactory.createPropertySet(), propertySet);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPropertySetNames() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getPropertySetNames());
    }

    @GET
    @Path(PATH_PROP_SET_NAME)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPropertySet() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getPropertySet());
    }

    @PUT
    @Path(PATH_PROP_SET_NAME)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response updatePropertySet(AdminPropertySetModel propertySet) {
        return runService(configServiceFactory.updatePropertySet(), propertySet);
    }

    @POST
    @Path("deletePropertySet")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response deletePropertySet(DeletePropertySetModel propertySetModel) {
        return runService(configServiceFactory.deletePropertySet(), propertySetModel);
    }
}
