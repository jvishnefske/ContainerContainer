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

package org.artifactory.environment.converter.local.version.v7;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.environment.converter.utils.VersionAllowedUtil;
import org.artifactory.version.CompoundVersionDetails;

import static org.artifactory.version.ArtifactoryServiceVersionProvider.v6100;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v700m001;

/**
 * Upgrade from a version earlier than 6.10.0 to 7.0.0 and above is not allowed.
 * Users must upgrade to 6.10.x before upgrading to 7.0.0.
 * For more info, please refer to the official documentation:
 * <a href="https://jfrog.com/help/r/jfrog-installation-setup-documentation/upgrading-from-artifactory-version-6.10.x-onwards-to-7.x">Upgrading from Artifactory Version 6.10.x Onwards to 7.x</a>
 *
 * @author Tamir Hadad
 */
@Slf4j
public class V700UpgradePrerequisiteVerifier implements BasicEnvironmentConverter {

    private static final String ERR_MSG =
            "Before upgrading to this version, you must upgrade to Artifactory version 6.10.x or above.";

    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(v6100.get()) && target.getVersion().afterOrEqual(v700m001.get());
    }

    @Override
    public void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source, CompoundVersionDetails target) {
        if (VersionAllowedUtil.isUpgradeAllowed(artifactoryHome, v700m001, log)) {
            log.info("Upgrade is Allowed due to user approval overriding upgrade.allowAnyUpgrade.forVersion property");
            return;
        }
        log.error(ERR_MSG);
        throw new IllegalStateException(ERR_MSG);
    }

    @Override
    public void assertConversionPreconditions(ArtifactoryHome home) {
        // nothing to do here, no files are being converted
    }
}
