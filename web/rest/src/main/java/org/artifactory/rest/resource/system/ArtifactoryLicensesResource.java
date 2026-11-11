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

import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.license.AddRemoveLicenseVerificationResult;
import org.artifactory.addon.license.ArtifactoryBaseLicenseDetails;
import org.artifactory.addon.license.ArtifactoryHaLicenseDetails;
import org.artifactory.addon.license.LicenseOperationStatus;
import org.artifactory.addon.license.VerificationResult;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.model.artifactorylicense.ActivateClusterLicenseModel;
import org.artifactory.rest.common.model.artifactorylicense.BaseLicenseDetails;
import org.artifactory.rest.common.model.artifactorylicense.LicensesDetails;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.artifactory.util.AolUtils;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.StringList;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static org.artifactory.security.permissionscope.PermissionScopeToken.INTERNAL_MISSION_CONTROL_EXECUTE;
import static org.artifactory.security.permissionscope.PermissionScopeToken.INTERNAL_MISSION_CONTROL_READ;
import static org.artifactory.security.permissionscope.PermissionScopeToken.SYSTEM_LICENSE_READ;

/**
 * @author Shay Bagants
 */
@Slf4j
@RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class ArtifactoryLicensesResource {
    private final AddonsManager addonsManager;
    private final SecurityService securityService;

    ArtifactoryLicensesResource() {
        addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        securityService = ContextHelper.get().beanForType(SecurityService.class);
    }

    /**
     * Add single or more licenses into Artifactory.
     */
    @POST
    @AllowedPermissionScope(INTERNAL_MISSION_CONTROL_EXECUTE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response installLicense(InputStream inputStream) {
        assertNotAol();
        assertNotPartnerLicense();
        Set<String> licenses = ArtifactoryLicenseResource.extractLicensesFromRequest(inputStream);
        if (CollectionUtils.isNullOrEmpty(licenses)) {
            log.debug("Unable to extract license from request");
            throw new BadRequestException(ArtifactoryLicenseResource.MISSING_LIC_ERROR);
        }
        // Add license/s and respond to the user
        try {
            LicenseOperationStatus status = addonsManager.addAndActivateLicenses(licenses, true, false);
            if (status.hasException()) {
                throw new BadRequestException("Unable to add license. " + status.getException().getCause());
            }
            if (addonsManager.getArtifactoryRunningMode().isHa()) {
                return handleHaAddLicenseResponse(status);
            } else {
                return handleProAddLicenseResponse(status);
            }
        } catch (UnsupportedOperationException e) {
            //Unsupported means either you send multiple keys on Pro, or tried to install a license on OSS
            String reason = e.getMessage() != null ? e.getMessage() : "";
            throw new BadRequestException("Cannot install license. " + reason);
        }
    }

    /**
     * Return details about the license/s installed. On non-HA, return single result
     * {@link ArtifactoryBaseLicenseDetails}, however, in ha_configured, return multiple detailed result, meaning list
     * of {@link ArtifactoryHaLicenseDetails}
     */
    @GET
    @AllowedPermissionScope(scopes = {INTERNAL_MISSION_CONTROL_READ, SYSTEM_LICENSE_READ})
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLicenseInfo() {
        AolUtils.assertApiAllowedInternally(
                "Artifactory Online does not require license, Please contact support@jfrog.com for further assistance" +
                        " if required.");
        assertNotPartnerLicense();
        if (isHaConfigured()) {
            return getHaLicensesDetails();
        } else {
            return getNonHaLicenseDetails();
        }
    }

    /**
     * This rest should not be public. It is used internally to 'force' other node to activate different license than
     * the current installed one. When using this method to force activating license, you can pass an
     * ActivateClusterLicenseModel containing a license to ignore in it.
     *
     * @param activateModel An activation model, includes license to ignore int it.
     */
    @POST
    @Path("activate")
    public Response activateLicense(ActivateClusterLicenseModel activateModel) {
        assertNotPartnerLicense();
        Set<String> ignoredLicensesHashes = activateModel.getIgnoredLicenses().stream()
                .map(ActivateClusterLicenseModel.IgnoredLicense::getLicenseHash)
                .collect(Collectors.toSet());
        LicenseOperationStatus status = new LicenseOperationStatus();
        // TODO [shayb] consider add param to the REST call to choose if to notify listeners
        addonsManager.activateLicense(ignoredLicensesHashes, status, true, false);
        return Response.ok().build();
    }

    /**
     * Notied by other node that filesystem art.cluster.licenses file has changed - causes a reload of the license cache
     */
    @POST
    @Path("licenseChanged")
    public Response licensesChanged() {
        assertNotPartnerLicense();
        addonsManager.resetLicenseCache();
        return Response.ok().build();
    }

    /**
     * Delete existing license. Allowed on HA configured only for admin user
     **/
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteLicenses(@QueryParam("licenseHash") StringList licenseHashes) {
        if (!securityService.isAdmin()) {
            String msg = "Only Artifactory admin user is allowed to use this call";
            throw new ForbiddenWebAppException(msg);
        }
        assertNotAol();
        assertNotPartnerLicense();
        if (!isHaConfigured()) {
            String msg = "This call is only available for Artifactory High Availability";
            throw new ForbiddenWebAppException(msg);
        }
        if (CollectionUtils.isNullOrEmpty(licenseHashes)) {
            throw new BadRequestException("No licenses were provided");
        }
        HashSet<String> licensesToDelete = new HashSet<>(licenseHashes);
        if (CollectionUtils.isNullOrEmpty(licensesToDelete)) {
            throw new BadRequestException("No license supplied.");
        }
        LicenseOperationStatus status = new LicenseOperationStatus();
        try {
            addonsManager.removeLicenses(licensesToDelete, status);
            if (status.hasException()) {
                String message = "Unable to delete licenses.";
                log.error(message, status.getException().getCause());
                return Response.status(CONFLICT).entity(message + " Please see logs for further details").build();
            }

            Map<String, AddRemoveLicenseVerificationResult> results = status.getAddRemoveLicenseVerificationResult();
            LicenseRestResponse licenseRestResponse = new LicenseRestResponse();
            // Collect the number of statuses that are not valid, return error if all the delete requests failed, or
            //200 if one or more succeeded
            int numOfErrors = 0;
            for (Map.Entry<String, AddRemoveLicenseVerificationResult> licenseResult : results.entrySet()) {
                if (!licenseResult.getValue().isValid()) {
                    numOfErrors++;
                }
                licenseRestResponse.addMessage(licenseResult.getKey(), licenseResult.getValue().showMassage());
            }
            if (numOfErrors > 0 && numOfErrors == licensesToDelete.size()) {
                licenseRestResponse.setStatus(400);
                return Response.status(BAD_REQUEST).entity(licenseRestResponse).build();
            }
            return Response.ok(licenseRestResponse).build();
        } catch (UnsupportedOperationException e) {
            throw new ForbiddenWebAppException("This call is only available for Artifactory High Availability");
        }
    }

    private boolean isHaConfigured() {
        return ArtifactoryHome.get().isHaConfigured();
    }

    /**
     * Return response containing the HA cluster licenses full details
     */
    private Response getHaLicensesDetails() {
        List<ArtifactoryHaLicenseDetails> clusterLicDetails = addonsManager.getClusterLicensesDetails();
        LicensesDetails licensesDetails = new LicensesDetails();
        clusterLicDetails.forEach(details -> {
            LicensesDetails.LicenseFullDetails nodeDetails = new LicensesDetails.LicenseFullDetails(
                    details.getType(), details.getValidThrough(), details.getLicensedTo(),
                    details.getLicenseHash(), details.isExpired(), details.getNodeId(), details.getNodeUrl(),
                    details.getSubscriptionType());
            licensesDetails.getLicenses().add(nodeDetails);
        });
        return Response.ok().entity(licensesDetails).build();
    }

    /**
     * Return a response containing non-HA instance license details
     */
    private Response getNonHaLicenseDetails() {
        ArtifactoryBaseLicenseDetails proDetails = addonsManager.getProAndAolLicenseDetails();
        BaseLicenseDetails licenseDetails = new BaseLicenseDetails(proDetails.getType(),
                proDetails.getValidThrough(),
                proDetails.getLicensedTo(), proDetails.getSubscriptionType());
        return Response.ok().entity(licenseDetails).build();
    }

    /**
     * Throw bad request if using AOL
     */
    private void assertNotAol() {
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        if (aolService.isAol() && !aolService.isDashboardUser()) {
            String message =
                    "Artifactory Online does not require license, Please contact support@jfrog.com for further " +
                            "assistance if required.";
            throw new BadRequestException(message);
        }
    }

    private void assertNotPartnerLicense() {
        if (addonsManager.isPartnerLicense()) {
            throw new BadRequestException("Artifactory is running with a partner license," +
                    " this feature isn't supported");
        }
    }

    /**
     * Return a response for add license/s operations on HA environment
     */
    private Response handleHaAddLicenseResponse(LicenseOperationStatus status) {
        Map<String, AddRemoveLicenseVerificationResult> addResults = status.getAddRemoveLicenseVerificationResult();
        LicenseRestResponse licenseRestResult = new LicenseRestResponse();
        addResults.forEach((key, value) -> licenseRestResult.addMessage(key, value.showMassage()));
        if (!status.hasValidLicenses()) {
            licenseRestResult.setStatus(BAD_REQUEST.getStatusCode());
        }
        return Response.status(licenseRestResult.getStatus()).entity(licenseRestResult).build();
    }

    /**
     * Return a response for add license operations on Pro environment
     */
    private Response handleProAddLicenseResponse(LicenseOperationStatus status) {
        // Should always have single result (non ha is not allowed to pass more than a single license), we also block
        //in on the AddonManagerImpl#assertValidNumberOfLicenses
        LicenseRestResponse licenseRestResult = new LicenseRestResponse();
        if (status.hasValidLicenses()) {
            Map<String, VerificationResult> activationResult = status.getLicenseActivationResult();
            // This should not be null
            if (activationResult != null && activationResult.size() > 0) {
                Map.Entry<String, VerificationResult> result = activationResult.entrySet().iterator().next();
                licenseRestResult.addMessage(result.getKey(), result.getValue().showMassage());
                if (!result.getValue().isValid()) {
                    licenseRestResult.setStatus(BAD_REQUEST.getStatusCode());
                }
            }
        } else {
            Map<String, AddRemoveLicenseVerificationResult> addResults = status.getAddRemoveLicenseVerificationResult();
            Map.Entry<String, AddRemoveLicenseVerificationResult> singleAddResult = addResults.entrySet().iterator()
                    .next();
            licenseRestResult.addMessage(singleAddResult.getKey(), singleAddResult.getValue().showMassage());
            licenseRestResult.setStatus(BAD_REQUEST.getStatusCode());

        }
        return Response.status(licenseRestResult.getStatus()).entity(licenseRestResult).build();
    }
}
