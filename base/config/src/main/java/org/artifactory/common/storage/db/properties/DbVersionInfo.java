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

package org.artifactory.common.storage.db.properties;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * Version and state of the database modeled after the db_properties table
 * Date: 7/10/13 3:02 PM
 *
 * @author freds
 */
@Getter
public class DbVersionInfo {

    private final long installationDate;
    private final String artifactoryVersion;
    private final int artifactoryRevision;
    private final long artifactoryRelease;
    private final String artifactoryProductVersion;


    public DbVersionInfo(long installationDate, String artifactoryVersion, int artifactoryRevision,
            long artifactoryRelease, String artifactoryProductVersion) {
        if (installationDate <= 0L) {
            throw new IllegalArgumentException("Installation date cannot be zero or negative!");
        }
        if (StringUtils.isBlank(artifactoryVersion)) {
            throw new IllegalArgumentException(
                    "Artifactory service version and Artifactory running mode cannot be empty or null!");
        }
        this.installationDate = installationDate;
        this.artifactoryVersion = artifactoryVersion;
        this.artifactoryRevision = artifactoryRevision;
        this.artifactoryRelease = artifactoryRelease;
        this.artifactoryProductVersion = artifactoryProductVersion;
    }

    public boolean sameVersion(DbVersionInfo other) {
        return this.getArtifactoryVersion().equals(other.artifactoryVersion)
                && this.artifactoryRevision == other.artifactoryRevision
                && this.artifactoryProductVersion.equals(other.artifactoryProductVersion);
    }
}
