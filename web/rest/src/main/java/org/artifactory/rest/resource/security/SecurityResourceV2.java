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

import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.CharEncoding;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.rest.constant.SecurityRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.DisabledApi;
import org.artifactory.security.permissions.PermissionTargetModel;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import static org.artifactory.api.rest.constant.SecurityRestConstants.ENTITY_KEY;
import static org.artifactory.api.rest.constant.SecurityRestConstants.PATH_GROUP_PERMISSION;
import static org.artifactory.api.rest.constant.SecurityRestConstants.PATH_PERMISSIONS;
import static org.artifactory.api.rest.constant.SecurityRestConstants.PATH_USER_PERMISSION;
import static org.artifactory.api.rest.constant.SecurityRestConstants.PERMISSIONS_ROOT;

/**
 * Provides REST API endpoints for Permission targets V2 (Repositories, Builds and Release Bundles permissions)
 *
 * @author Yuval Reches
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(SecurityRestConstants.PATH_ROOT_V2)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@RequiredArgsConstructor
public class SecurityResourceV2 {

    private final AddonsManager addonsManager;
    private final SecurityService securityService;

    @Context
    private HttpServletRequest request;

    @GET
    @Path(PERMISSIONS_ROOT)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    public Response getAllPermissionTargets() {
        securityService.assertValidAuthInCaseMfaEnabled();
        return addonsManager.addonByType(RestAddon.class).getAllPermissionTargetsV2(request);
    }

    @GET
    @Path(PATH_PERMISSIONS)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    public Response getPermissionTarget(@PathParam(ENTITY_KEY) String entityKey) throws UnsupportedEncodingException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return addonsManager.addonByType(RestAddon.class).getPermissionTargetV2(decodeEntityKey(entityKey));
    }

    @DELETE
    @Path(PATH_PERMISSIONS)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    public Response deletePermissionTarget(@PathParam(ENTITY_KEY) String entityKey)
            throws UnsupportedEncodingException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return addonsManager.addonByType(RestAddon.class).deletePermissionTargetV2(decodeEntityKey(entityKey));
    }

    @HEAD
    @Path(PATH_PERMISSIONS)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    public Response isPermissionTargetExists(@PathParam(ENTITY_KEY) String entityKey)
            throws UnsupportedEncodingException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return addonsManager.addonByType(RestAddon.class).isPermissionTargetExistsV2(decodeEntityKey(entityKey));
    }

    @POST
    @Path(PATH_PERMISSIONS)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    public Response createPermissionTarget(@PathParam(ENTITY_KEY) String entityKey,
            PermissionTargetModel permissionTargetModel) throws UnsupportedEncodingException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return addonsManager.addonByType(RestAddon.class)
                .createPermissionTargetV2(decodeEntityKey(entityKey), permissionTargetModel);
    }

    @PUT
    @Path(PATH_PERMISSIONS)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @DisabledApi(disabledWhen = ConstantValues.deprecatedPermissionsApiV2Disabled,
            responseMessage = "PUT /api/v2/security/permissions is deprecated. Use the Access Permissions API instead.")
    public Response updatePermissionTarget(@PathParam(ENTITY_KEY) String entityKey,
            PermissionTargetModel permissionTargetModel) throws UnsupportedEncodingException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return addonsManager.addonByType(RestAddon.class)
                .updatePermissionTargetV2(decodeEntityKey(entityKey), permissionTargetModel);
    }

    @GET
    @Path(PATH_USER_PERMISSION)
    @Produces({MediaType.APPLICATION_JSON})
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    public Response getUserPermissionsSecurityEntity(@PathParam(ENTITY_KEY) String entityKey)
            throws UnsupportedEncodingException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return addonsManager.addonByType(RestAddon.class).getUserPermissionsSecurityV2(decodeEntityKey(entityKey));
    }

    @GET
    @Path(PATH_GROUP_PERMISSION)
    @Produces({MediaType.APPLICATION_JSON})
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    public Response getGroupPermissionsSecurityEntity(@PathParam(ENTITY_KEY) String entityKey)
            throws UnsupportedEncodingException {
        securityService.assertValidAuthInCaseMfaEnabled();
        return addonsManager.addonByType(RestAddon.class).getGroupPermissionsSecurityV2(decodeEntityKey(entityKey));
    }

    /**
     * Decode entity key to replace plus with space to support old integrations.
     */
    private String decodeEntityKey(String entityKey) throws UnsupportedEncodingException {
        return ConstantValues.securityApiUsePlusInsteadOfSpace.getBoolean() ?
                URLDecoder.decode(entityKey, CharEncoding.UTF_8) : entityKey;
    }
}
