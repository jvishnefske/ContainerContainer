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

package org.artifactory.addon;

import org.artifactory.api.rest.search.result.VersionRestResult;
import org.artifactory.common.addon.Addon;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Core services addons interface.
 *
 * @author Yossi Shaul
 */
public interface CoreAddons extends Addon {

    /**
     * @return Returns email addresses of Artifactory administrators to send error notification to.
     */
    @Nonnull
    List<String> getPlatformAdminUsersEmails();

    void validateTargetHasDifferentLicenseKeyHash(String targetLicenseHash, List<String> addons);

    /**
     * Validates that given licenseHash is different from license installed on this instance,
     * unless artifactoryId and current instance artifactoryId are equal (e.g same Artifactory)
     *
     * @param targetLicenseHash license hash of target artifactory to check
     * @param artifactoryId     artifactory id of the checked license
     */
    boolean validateTargetHasDifferentLicense(String targetLicenseHash, String artifactoryId);

    void validateMultiPushReplicationSupportedForTargetLicense(VersionRestResult versionRestResult,
            boolean isMultiPushConfigureForThisRepo, String targetUrl);

    String getBuildNum();

    /**
     * @return Artifactory version string for list browsing
     */
    String getListBrowsingVersion();

    String getArtifactoryServerName();

    /**
     * Get the Artifactory URL for mail operations (e.g. send emails containing the Artifactory URL) including the
     * 'webapp/' suffix. Has two different implementations, see impl fo further information.
     */
    String getMailConfigArtifactoryUrl();
}