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

package org.artifactory.version;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds all the version data about Artifactory. version name, and revision from the properties file and
 * ArtifactoryVersion that matches those values.
 *
 * @author Yossi Shaul
 */
@EqualsAndHashCode
@ToString
public class CompoundVersionDetails {
    @Getter
    private final ArtifactoryServiceVersion version;
    @Getter
    private final String packageHandlerVersion;
    @Getter
    private final String buildNumber;
    @Getter
    private final long timestamp;
    private final Map<String, String> mfeVersions;

    public CompoundVersionDetails(ArtifactoryServiceVersion version, String packageHandlerVersion, String buildNumber,
            long timestamp, Map<String, String> mfeVersions) {
        this.version = version;
        this.packageHandlerVersion = packageHandlerVersion;
        this.buildNumber = buildNumber;
        this.timestamp = timestamp;
        this.mfeVersions = mfeVersions != null ? new HashMap<>(mfeVersions) : new HashMap<>();
    }

    public CompoundVersionDetails(ArtifactoryServiceVersion version, String buildNumber,
            long timestamp) {
        this.version = version;
        this.packageHandlerVersion = "";
        this.mfeVersions = new HashMap<>();
        this.buildNumber = buildNumber;
        this.timestamp = timestamp;
    }

    /**
     * Gets the MFE version for a specific MFE name.
     * @param mfeName The MFE name as a string (e.g., "core", "rtui", "buildinfomfe", "packages")
     * @return The version string for the specified MFE, or null if not found
     */
    public String getMfeVersion(String mfeName) {
        return mfeVersions.get(mfeName);
    }

    /**
     * Gets all MFE versions as an unmodifiable map.
     * @return An unmodifiable map of MFE names to version strings
     */
    public Map<String, String> getMfeVersions() {
        return Collections.unmodifiableMap(mfeVersions);
    }

    /**
     * @return The raw version string as read from the input stream/file
     */
    public String getVersionName() {
        return version.getVersion();
    }

    /**
     * @return The raw revision string as read from the input stream/file
     */
    public long getRevision() {
        return version.getRevision();
    }

    public boolean isCurrent() {
        return version.isCurrent();
    }

    public boolean before(CompoundVersionDetails other) {
        return version.before(other.getVersion());
    }
    /**
     * Outputs in the same format as the artifactory.properties file. used by export, tests etc.
     */
    public String getFileDump() {
        StringBuilder fileDump = new StringBuilder();
        fileDump.append("artifactory.version=").append(version.getVersion()).append("\n");
        fileDump.append("artifactory.revision=").append(version.getRevision()).append("\n");
        if (StringUtils.isNotEmpty(packageHandlerVersion)) {
            fileDump.append("artifactory.package.handler.version=").append(packageHandlerVersion).append("\n");
        }
        // Output all MFE versions
        for (Map.Entry<String, String> entry : mfeVersions.entrySet()) {
            if (StringUtils.isNotEmpty(entry.getValue())) {
                fileDump.append("artifactory.").append(entry.getKey()).append(".mfe.version=")
                        .append(entry.getValue()).append("\n");
            }
        }
        fileDump.append("artifactory.timestamp=").append(timestamp).append("\n");
        fileDump.append("artifactory.buildNumber=").append(buildNumber).append("\n");
        return fileDump.toString();
    }

}
