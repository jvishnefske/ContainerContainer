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

package org.artifactory.ui.rest.service.admin.configuration.ha.license;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.license.ArtifactoryHaLicenseDetails;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.ui.rest.model.admin.license.UILicensesDetails;
import org.artifactory.util.AolUtils;
import org.artifactory.util.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

import static org.artifactory.addon.ArtifactoryRunningMode.HA;

/**
 * Service that retrieves the HA cluster licenses details
 *
 * @author Shay Bagants
 */
@Slf4j
@Component
public class GetClusterLicensesService implements RestService {

    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private ArtifactoryServersCommonService serversCommonService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        // Block using on AOL and instances that are not HA configured
        AolUtils.assertNotAol("GetLicenseKey");
        // Block non HA configured instances
        assertHaConfigured();
        // Non admin should get empty response if no license exists, or get blocked if license exists
        if (!securityService.isAdmin()) {
            handleNonAdmin(response);
            return;
        }
        log.debug("Attempting to retrieve cluster licenses details");
        updateResponseWithLicensesDetails(response);
    }

    private void handleNonAdmin(RestResponse response) {
        if (addonsManager.isLicenseInstalled()) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
        } else {
            UILicensesDetails licensesDetails = new UILicensesDetails();
            response.iModel(licensesDetails);
        }
    }

    /**
     * Add the licenses details to the response
     */
    private void updateResponseWithLicensesDetails(RestResponse response) {
        UILicensesDetails licensesDetails = new UILicensesDetails();

        // This method intentionally returns List of String array
        List<ArtifactoryHaLicenseDetails> allLicensesDetails = addonsManager.getClusterLicensesDetails();
        log.debug("Creating a licenses model response");
        if (CollectionUtils.notNullOrEmpty(allLicensesDetails)) {
            allLicensesDetails.forEach(licenseDetails -> {
                UILicensesDetails.UILicenseFullDetails singleLicenseDetails =
                        new UILicensesDetails.UILicenseFullDetails();
                singleLicenseDetails.setLicensedTo(licenseDetails.getLicensedTo());
                singleLicenseDetails.setValidThrough(licenseDetails.getValidThrough());
                singleLicenseDetails.setType(licenseDetails.getType());
                singleLicenseDetails.setLicenseHash(licenseDetails.getLicenseHash());
                singleLicenseDetails.setNodeId(licenseDetails.getNodeId());
                singleLicenseDetails.setNodeUrl(licenseDetails.getNodeUrl());
                singleLicenseDetails.setExpired(licenseDetails.isExpired());
                licensesDetails.getLicenses().add(singleLicenseDetails);
            });
        }
        updateNumOfNodesWithNoLicenseParam(licensesDetails);
        response.iModel(licensesDetails);
    }

    private void updateNumOfNodesWithNoLicenseParam(UILicensesDetails licensesDetails) {
        int nodesWithNoLicense = 0;
        List<ArtifactoryServer> servers = serversCommonService.getActiveMembers();
        for (ArtifactoryServer server : servers) {
            if (server.getLicenseKeyHash().equals(AddonsManager.NO_LICENSE_HASH_WITH_OSS_PADDING)) {
                nodesWithNoLicense++;
            }
        }
        licensesDetails.setNodesWithNoLicense(nodesWithNoLicense);
    }

    /**
     * Ensure that HA configured instances (has ha node props) are not allowed to use this service
     */
    private void assertHaConfigured() {
        if (!addonsManager.getArtifactoryRunningMode().equals(HA)) {
            throw new ForbiddenWebAppException(
                    "In order to use this function, it is required to configure your Artifactory" +
                            "instance as HA.");
        }
    }
}
