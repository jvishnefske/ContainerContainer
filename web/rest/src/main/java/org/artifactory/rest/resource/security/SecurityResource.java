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

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.SecurityResourceAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.rest.constant.SecurityRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.AccessConfigMapUtils;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.security.PasswordSettings;
import org.artifactory.descriptor.security.UserLockPolicy;
import org.artifactory.rest.common.BlockOnConversion;
import org.artifactory.rest.common.DisabledApi;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.exception.RestException;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.rest.system.UserLockPolicyConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Noam Y. Tenne
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(SecurityRestConstants.PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
public class SecurityResource {

    private static final String ENTITY_TYPE = "entityType";
    private static final String ENTITY_KEY = "entityKey";
    private static final String INNER_PATH = "{" + ENTITY_TYPE + ": [^/]+}/{" + ENTITY_KEY + ": .+}";

    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private SecurityService securityService;
    @Context
    private HttpServletRequest request;
    @Autowired
    private CentralConfigService centralConfigService;
    @Autowired
    private AccessService accessService;

    @GET
    @Path("{" + ENTITY_TYPE + ": .+}")
    @Produces({SecurityRestConstants.MT_USERS, SecurityRestConstants.MT_GROUPS,
            SecurityRestConstants.MT_PERMISSION_TARGETS, MediaType.APPLICATION_JSON})
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response getSecurityEntities(@PathParam(ENTITY_TYPE) String entityType) throws UnsupportedEncodingException {
        securityService.assertValidAuthInCaseMfaEnabled();
        SecurityResourceAddon securityResourceAddon = addonsManager.addonByType(SecurityResourceAddon.class);
        return securityResourceAddon.getSecurityEntities(request, decodeEntityKey(entityType));
    }

    @GET
    @Path(INNER_PATH)
    @Produces({SecurityRestConstants.MT_USER, SecurityRestConstants.MT_GROUP,
            SecurityRestConstants.MT_PERMISSION_TARGET, MediaType.APPLICATION_JSON})
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response getSecurityEntity(@PathParam(ENTITY_TYPE) String entityType,
            @PathParam(ENTITY_KEY) String entityKey,
            @QueryParam("includeUsers") @DefaultValue("false") boolean includeUsers)
            throws UnsupportedEncodingException {
        securityService.assertValidAuthInCaseMfaEnabled();
        SecurityResourceAddon securityResourceAddon = addonsManager.addonByType(SecurityResourceAddon.class);
        return securityResourceAddon.getSecurityEntity(entityType, decodeEntityKey(entityKey), includeUsers);
    }

    @DELETE
    @Path(INNER_PATH)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @BlockOnConversion
    public Response deleteSecurityEntity(@PathParam(ENTITY_TYPE) String entityType,
            @PathParam(ENTITY_KEY) String entityKey) throws UnsupportedEncodingException {
        securityService.assertValidAuthInCaseMfaEnabled();
        SecurityResourceAddon securityResourceAddon = addonsManager.addonByType(SecurityResourceAddon.class);
        return securityResourceAddon.deleteSecurityEntity(entityType, decodeEntityKey(entityKey));
    }

    @PUT
    @Path("permissions/{" + ENTITY_KEY + ": .+}")
    @Consumes({SecurityRestConstants.MT_PERMISSION_TARGET, MediaType.APPLICATION_JSON})
    @BlockOnConversion
    @DisabledApi(disabledWhen = ConstantValues.deprecatedPermissionsApiDisabled,
            responseMessage = "PUT /api/security/permissions is deprecated. Use the Access Permissions API instead.")
    public Response createOrReplacePermissionTarget(@PathParam(ENTITY_KEY) String entityKey) throws IOException {
        securityService.assertValidAuthInCaseMfaEnabled();
        SecurityResourceAddon securityResourceAddon = addonsManager.addonByType(SecurityResourceAddon.class);
        return securityResourceAddon.createOrReplaceSecurityEntity("permissions", decodeEntityKey(entityKey), request);
    }

    @PUT
    @Path(INNER_PATH)
    @Consumes({SecurityRestConstants.MT_USER, SecurityRestConstants.MT_GROUP, MediaType.APPLICATION_JSON})
    @BlockOnConversion
    public Response createOrReplaceSecurityEntity(@PathParam(ENTITY_TYPE) String entityType,
            @PathParam(ENTITY_KEY) String entityKey) throws IOException {
        securityService.assertValidAuthInCaseMfaEnabled();
        SecurityResourceAddon securityResourceAddon = addonsManager.addonByType(SecurityResourceAddon.class);
        return securityResourceAddon.createOrReplaceSecurityEntity(entityType, decodeEntityKey(entityKey), request);
    }

    @POST
    @Path(INNER_PATH)
    @BlockOnConversion
    @Consumes({SecurityRestConstants.MT_USER, SecurityRestConstants.MT_GROUP,
            SecurityRestConstants.MT_PERMISSION_TARGET, MediaType.APPLICATION_JSON})
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response updateSecurityEntity(@PathParam(ENTITY_TYPE) String entityType,
            @PathParam(ENTITY_KEY) String entityKey) throws IOException {
        securityService.assertValidAuthInCaseMfaEnabled();
        SecurityResourceAddon securityResourceAddon = addonsManager.addonByType(SecurityResourceAddon.class);
        return securityResourceAddon.updateSecurityEntity(entityType, decodeEntityKey(entityKey), request);
    }

    @GET
    @Path("encryptedPassword")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getEncryptedPassword() {
        if (!securityService.isPasswordEncryptionEnabled()) {
            throw new RestException(HttpStatus.SC_CONFLICT, "Server doesn't support encrypted passwords");
        }

        String encryptedPassword = securityService.currentUserEncryptedPassword();
        if (StringUtils.isNotBlank(encryptedPassword)) {
            return Response.ok(encryptedPassword).build();
        }

        throw new NotFoundException("The user: '" + securityService.currentUsername() + "' not found");
    }

    /**
     * Decode entity key to replace plus with space to support old integrations.
     */
    private String decodeEntityKey(String entityKey) throws UnsupportedEncodingException {
        return ConstantValues.securityApiUsePlusInsteadOfSpace.getBoolean() ?
                URLDecoder.decode(entityKey, CharEncoding.UTF_8) : entityKey;
    }

    @PUT
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("userLockPolicy")
    @BlockOnConversion
    public Response updateUserLockPolicy(UserLockPolicy userLockPolicy) {
        securityService.assertValidAuthInCaseMfaEnabled();
        Map<String, Object> accessMap = new HashMap<>();
        AccessConfigMapUtils.addLoginAttempts(userLockPolicy.getLoginAttempts(), accessMap);
        try {
            accessService.updateConfigurationModel(accessMap);
        } catch (AccessClientException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
        return Response.ok().entity("UserLockPolicy was successfully updated").build();
    }

    @GET
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("userLockPolicy")
    public UserLockPolicy getUserLockPolicy() {
        securityService.assertValidAuthInCaseMfaEnabled();
        UserLockPolicyConfig userLockPolicyConfig = accessService.getUserLockPolicyConfig();
        UserLockPolicy userLockPolicy = new UserLockPolicy();
        userLockPolicy.setEnabled(userLockPolicyConfig.getAttempts() > 0);
        userLockPolicy.setLoginAttempts(userLockPolicyConfig.getAttempts());
        return userLockPolicy;
    }

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Path("unlockUsers/{userName}")
    @BlockOnConversion
    public String unlockUser(@PathParam("userName") String userName) {
        securityService.assertValidAuthInCaseMfaEnabled();
        ((UserGroupService) securityService).unlockUser(userName);
        return "User " + userName + " was successfully unlocked";
    }

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Path("unlockAllUsers")
    @BlockOnConversion
    public String unlockAllUsers() {
        securityService.assertValidAuthInCaseMfaEnabled();
        ((UserGroupService) securityService).unlockAllUsers();
        return "All users were successfully unlocked";
    }

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("unlockUsers")
    @BlockOnConversion
    public String unlockUsers(List<String> users) {
        securityService.assertValidAuthInCaseMfaEnabled();
        if (users != null) {
            users.parallelStream().forEach(u -> {
                ((UserGroupService) securityService).unlockUser(u);
            });
        }
        return "Specified users were successfully unlocked";
    }

    @GET
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("lockedUsers")
    public Set<String> getAllLockedUsers() {
        securityService.assertValidAuthInCaseMfaEnabled();
        return ((UserGroupService) securityService).getLockedUsers();
    }

    @PUT
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("passwordSettings")
    @BlockOnConversion
    public Response updatePasswordPolicy(PasswordSettings userPasswordSettings) {
        securityService.assertValidAuthInCaseMfaEnabled();
        if (userPasswordSettings.getExpirationPolicy().getPasswordMaxAge() > 999 ||
                userPasswordSettings.getExpirationPolicy().getPasswordMaxAge() < 1) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Password expiration must be between 1 - 999")
                    .build();
        }

        MutableCentralConfigDescriptor descriptor = centralConfigService.getMutableDescriptor();
        PasswordSettings originalPasswordSettings = descriptor.getSecurity().getPasswordSettings();

        if (originalPasswordSettings == null) {
            descriptor.getSecurity().setPasswordSettings(userPasswordSettings);
        } else {
            originalPasswordSettings.getExpirationPolicy()
                    .setEnabled(userPasswordSettings.getExpirationPolicy().getEnabled());
            originalPasswordSettings.getExpirationPolicy()
                    .setPasswordMaxAge(userPasswordSettings.getExpirationPolicy().getPasswordMaxAge());
        }
        centralConfigService.saveEditedDescriptorAndReload(descriptor, userPasswordSettings);
        return Response.ok().entity("PasswordSettings were successfully updated").build();
    }

    @GET
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("passwordSettings")
    public PasswordSettings getPasswordPolicy() {
        securityService.assertValidAuthInCaseMfaEnabled();
        return centralConfigService.getDescriptor().getSecurity().getPasswordSettings();
    }
}
