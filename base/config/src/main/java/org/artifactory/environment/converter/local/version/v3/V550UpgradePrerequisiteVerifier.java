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

package org.artifactory.environment.converter.local.version.v3;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.environment.converter.utils.VersionAllowedUtil;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simply put, we prevent HA upgrades from versions < 5.4.6 to > 5.5.0 --> users must upgrade to 5.4.6 before upgrading
 * to 5.5.0
 * For pro installations any upgrade is still possible since downtime (caused by non-compatible dao) will not occur.
 *
 * @author Dan Feldman
 */
public class V550UpgradePrerequisiteVerifier implements BasicEnvironmentConverter {
    private static final Logger log = LoggerFactory.getLogger(V550UpgradePrerequisiteVerifier.class);

    private static final String ERR_MSG = "For an Artifactory HA installation, before upgrading to this version, " +
            "you first need to upgrade your cluster to Artifactory 5.4.6 to accommodate a database schema change " +
            "implemented in that version to support SHA256 checksums. You can consent downtime by appending " +
            "'artifactory.upgrade.allowAnyUpgrade.forVersion=" + ArtifactoryServiceVersion.getCurrent()
            + "' in your artifactory.system.properties file.";

    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        //up being on 5.4.6 does not matter (same case as pro)
        return home.isHaConfigured() && source.getVersion().before(ArtifactoryServiceVersionProvider.v546.get());
    }

    @Override
    public void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source, CompoundVersionDetails target) {
        if (VersionAllowedUtil.isUpgradeAllowed(artifactoryHome, ArtifactoryServiceVersionProvider.v550m001, log)) {
            return;
        }
        //isInterested verified this is HA and coming from v < 5.4.6 so that's a no-no!
        log.error(ERR_MSG);
        throw new IllegalStateException(ERR_MSG);
    }

    @Override
    public void assertConversionPreconditions(ArtifactoryHome home) {
        // nothing to do here, no files are being converted
    }
}