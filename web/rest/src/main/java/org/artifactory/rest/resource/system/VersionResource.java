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

package org.artifactory.rest.resource.system;

import com.google.common.annotations.VisibleForTesting;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.VersionResult;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.VersionInfo;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.rest.search.result.VersionRestResult;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.artifactory.security.UserInfo;
import org.artifactory.util.CollectionUtils;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

import static org.artifactory.security.permissionscope.PermissionScopeToken.SYSTEM_INFO_READ;

/**
 * Resource to get information about Artifactory's current running version and revision as well as a list of currently
 * enabled addons in Artifactory.
 *
 * @author Tomer Cohen
 */
@Component
@Path(SystemRestConstants.PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@ActivityDetails(group = RestApiGroup.SYSTEM_INFO_API)
@Setter
public class VersionResource {

    @Autowired
    private CentralConfigService centralConfigService;
    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private SecurityService securityService;
    @Context
    private HttpHeaders httpHeaders;

    private final List<EntitlementType> replicationEntitlements =
            List.of(EntitlementType.REPO_REPLICATION, EntitlementType.MULTIPUSH_REPLICATION,
                    EntitlementType.EVENT_BASED_PULL_REPLICATION, EntitlementType.SMART_REMOTE_TARGET_FOR_EDGE);

    /**
     * @return The artifactory version and enabled addons
     */
    @GET
    @AllowedPermissionScope(SYSTEM_INFO_READ)
    @Path(SystemRestConstants.PATH_VERSION)
    @Produces({SystemRestConstants.MT_VERSION_RESULT, MediaType.APPLICATION_JSON})
    public Response getArtifactoryVersion() {
        // Check if this endpoint should not be accessible by anonymous user
        checkAnonymousAccessNotAllowed();
        return Response.ok().entity(getVersionRestResult(centralConfigService.getProductVersionInfo())).build();
    }

    /**
     * @return The artifactory product version and enabled addons
     */
    @GET
    @Path(SystemRestConstants.PATH_VERSION_PRODUCT)
    @Produces({SystemRestConstants.MT_VERSION_RESULT, MediaType.APPLICATION_JSON})
    public Response getArtifactoryProductVersion() {
        // Check if this endpoint should not be accessible by anonymous user
        checkAnonymousAccessNotAllowed();
        return Response.ok().entity(
                getVersionRestResult(centralConfigService.getProductVersionInfo())).build();
    }

    /**
     * @return The artifactory service version and enabled addons
     */
    @GET
    @Path(SystemRestConstants.PATH_VERSION_SERVICE)
    @Produces({SystemRestConstants.MT_VERSION_RESULT, MediaType.APPLICATION_JSON})
    public Response getArtifactoryServiceVersion() {
        // Check if this endpoint should not be accessible by anonymous user
        checkAnonymousAccessNotAllowed();
        return Response.ok().entity(
                getVersionRestResult(centralConfigService.getServiceVersionInfo())).build();
    }

    @VisibleForTesting
    VersionRestResult getVersionRestResult(VersionInfo versionInfo) {
        VersionResult versionResult = addonsManager.getVersionResult(
                versionInfo,
                replicationEntitlements,
                shouldIgnoreNewLicenses());
        return mapToVersionRestResult(versionResult);
    }

    VersionRestResult mapToVersionRestResult(VersionResult versionResult) {
        return versionResult == null ? null :
                new VersionRestResult(
                        versionResult.getVersion(),
                        versionResult.getRevision(),
                        versionResult.getServicesVersions(),
                        versionResult.getAddons(),
                        versionResult.getLicense(),
                        versionResult.getEntitlements()
                );
    }

    @VisibleForTesting
    protected void checkAnonymousAccessNotAllowed() {
        if (ConstantValues.systemVersionEndpointDisabledForAnonymous.getBoolean() &&
                securityService.isAnonAccessEnabled() &&
                UserInfo.isAnonymous(SecurityContextHolder.getContext().getAuthentication().getName())) {
            throw new AuthorizationRestException("Anonymous access is not allowed for this endpoint");
        }
    }

    private boolean shouldIgnoreNewLicenses() {
        if (httpHeaders != null) {
            List<String> userAgentHeaders = httpHeaders.getRequestHeader(HttpHeaders.USER_AGENT);
            if (CollectionUtils.notNullOrEmpty(userAgentHeaders)) {
                String artiHeader = userAgentHeaders.stream()
                        .filter(header -> header.startsWith("Artifactory/"))
                        .findFirst().orElse(null);
                String artVersionString = StringUtils.substringAfter(artiHeader, "Artifactory/");
                ArtifactoryServiceVersion artVersion = ArtifactoryServiceVersionProvider.get(artVersionString);
                return artVersion != null && artVersion.before(ArtifactoryServiceVersionProvider.v600.get());
            }
        }
        return false;
    }
}