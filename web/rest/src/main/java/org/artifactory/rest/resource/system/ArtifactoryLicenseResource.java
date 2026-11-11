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

import com.google.common.collect.Sets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.license.AddRemoveLicenseVerificationResult;
import org.artifactory.addon.license.ArtifactoryBaseLicenseDetails;
import org.artifactory.addon.license.ArtifactoryHaLicenseDetails;
import org.artifactory.addon.license.LicenseOperationStatus;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.model.artifactorylicense.BaseLicenseDetails;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.util.AolUtils;
import org.artifactory.util.CollectionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;

/**
 * @author Yoav Luft
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class ArtifactoryLicenseResource {

    private static final Logger log = LoggerFactory.getLogger(ArtifactoryLicenseResource.class);
    static final String MISSING_LIC_ERROR = "No license key supplied.";

    private AddonsManager addonsManager;

    ArtifactoryLicenseResource() {
        addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
    }

    /**
     * Return details about the license/s installed. On non-HA, return single result
     * {@link ArtifactoryBaseLicenseDetails}, however, in ha_configured, return multiple detailed result, meaning list
     * of {@link ArtifactoryHaLicenseDetails}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLicenseInfo() {
        return getActiveLicenseDetails();
    }

    /**
     * Add single or more licenses into Artifactory.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response installLicense(InputStream inputStream) {
        if (AolUtils.isAol()) {
            String message = "Cannot manage license on Artifactory Online. Please contact JFrog " +
                    "support at support@jfrog.com for managing your AOL installation.";
            return Response.status(BAD_REQUEST).entity(
                    new SingleLicenseRestResponse(BAD_REQUEST.getStatusCode(), message)).build();
        }

        Set<String> licenses = extractLicensesFromRequest(inputStream);
        if (CollectionUtils.isNullOrEmpty(licenses)) {
            return Response.status(BAD_REQUEST).entity(
                    new SingleLicenseRestResponse(BAD_REQUEST.getStatusCode(), MISSING_LIC_ERROR)).build();
        }

        // Add license/s and respond to the user
        try {
            LicenseOperationStatus status = addonsManager.addAndActivateLicenses(licenses, true, false);
            if (status.hasException()) {
                throw new BadRequestException(
                        "License could not be installed due to an error:  " + status.getException().getCause());
            }
            return handleSingleLicenseInsertResponse(status);
        } catch (UnsupportedOperationException e) {
            //Unsupported means either you send multiple keys on Pro, or tried to install a license on OSS
            String reason = e.getMessage() != null ? e.getMessage() : "";
            throw new BadRequestException("Cannot install license. " + reason);
        }
    }

    /**
     * Return a response containing non-HA instance license details
     */
    private Response getActiveLicenseDetails() {
        ArtifactoryBaseLicenseDetails proDetails = addonsManager.getProAndAolLicenseDetails();
        BaseLicenseDetails licenseDetails = new BaseLicenseDetails(proDetails.getType(),
                proDetails.getValidThrough(),
                proDetails.getLicensedTo(), proDetails.getSubscriptionType());
        return Response.ok().entity(licenseDetails).build();
    }

    static Set<String> extractLicensesFromRequest(InputStream inputStream) {
        try {
            byte[] licenseBytes = IOUtils.toByteArray(inputStream);
            return extractLicenses(licenseBytes);
        } catch (IOException e) {
            log.error("Unable to add Artifactory license. {}", e.getMessage());
            log.debug("Unable to add Artifactory license.", e);
            throw new BadRequestException("Unable to add Artifactory license. " + e.getMessage());
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    private static Set<String> extractLicenses(byte[] licenseBytes) throws IOException {
        try {
            return extractSingleLicense(licenseBytes);
        } catch (JsonProcessingException e) {
            return extractMultipleLicenses(licenseBytes);
        }
    }

    private static Set<String> extractMultipleLicenses(byte[] licenseBytes) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Attempting to parse licenses: {}", new String(licenseBytes, StandardCharsets.UTF_8));
        }
        LicenseConfiguration[] licenseConfigs = JacksonReader.bytesAsClass(licenseBytes, LicenseConfiguration[].class);
        if (licenseConfigs != null && licenseConfigs.length > 0) {
            return Arrays.stream(licenseConfigs).map(LicenseConfiguration::getLicenseKey)
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private static Set<String> extractSingleLicense(byte[] licenseBytes) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Attempting to parse license: {}", new String(licenseBytes, StandardCharsets.UTF_8));
        }
        LicenseConfiguration licenseConfig = JacksonReader.bytesAsClass(licenseBytes, LicenseConfiguration.class);
        if (licenseConfig != null && StringUtils.isNotBlank(licenseConfig.getLicenseKey())) {
            return Sets.newHashSet(licenseConfig.getLicenseKey());
        }
        return Collections.emptySet();
    }

    private Response handleSingleLicenseInsertResponse(LicenseOperationStatus status) {
        SingleLicenseRestResponse licenseResponseModel = new SingleLicenseRestResponse();
        if (status.hasValidLicenses()) {
            licenseResponseModel.setStatus(OK.getStatusCode());
            licenseResponseModel.setMessage("The license has been successfully installed.");
            return Response.status(OK.getStatusCode()).entity(licenseResponseModel).build();
        }

        String error = "License could not be installed due to an error: ";
        Map<String, AddRemoveLicenseVerificationResult> results = status.getAddRemoveLicenseVerificationResult();
        Optional<Map.Entry<String, AddRemoveLicenseVerificationResult>> firstAddResult = results.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .findFirst();
        if (firstAddResult.isPresent()) {
            error += firstAddResult.get().getValue().showMassage();
        }
        licenseResponseModel.setStatus(BAD_REQUEST.getStatusCode());
        licenseResponseModel.setMessage(error);
        return Response.status(BAD_REQUEST).entity(licenseResponseModel).build();
    }
}
