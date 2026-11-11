/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

import org.artifactory.common.home.ArtifactoryHome;

import java.io.InputStream;
import java.net.URL;
import java.util.regex.Pattern;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_VERSION_PROPERTIES;
import static org.artifactory.version.ArtifactoryVersionReader.readServicePropsContent;

/**
 * @author dudim
 */
public class CurrentVersionLoader {
    static Pattern RELEASE_VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(-[mp]\\d{3})?");

    private static volatile CurrentVersionLoader instance;
    private static ArtifactoryServiceVersion currentVersion;

    public static CurrentVersionLoader getInstance() {
        if (instance == null) {
            synchronized (CurrentVersionLoader.class) {
                if (instance == null) {
                    instance = new CurrentVersionLoader();
                }
            }
        }
        return instance;
    }

    private CurrentVersionLoader() {
        readCurrentVersionFromFile();
    }

    private static void readCurrentVersionFromFile() {
        try {
            // Get default Artifactory version properties from resource
            URL url = ArtifactoryServiceVersionImpl.class.getResource(ARTIFACTORY_VERSION_PROPERTIES);
            if (url == null) {
                throw new IllegalStateException(
                        "Could not read classpath resource '" + ARTIFACTORY_VERSION_PROPERTIES + "'.");
            }
            // Load the version properties from resource directly to the Properties instance
            InputStream inputStream = ArtifactoryHome.class.getResourceAsStream(ARTIFACTORY_VERSION_PROPERTIES);
            ArtifactoryVersionReader.VersionPropertiesContent versionPropertiesContent =
                    readServicePropsContent(inputStream, ARTIFACTORY_VERSION_PROPERTIES);
            String version = versionPropertiesContent.versionString;
            String revision = versionPropertiesContent.revisionString;

            if (revision.startsWith("dev")) {
                currentVersion = ArtifactoryServiceVersionProvider.get(version, Integer.MAX_VALUE);
            } else {
                currentVersion = ArtifactoryServiceVersionProvider.get(version, Integer.parseInt(revision));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Artifactory version from resource file.", e);
        }
    }

    public ArtifactoryServiceVersion getCurrent() {
        return currentVersion;
    }

}
