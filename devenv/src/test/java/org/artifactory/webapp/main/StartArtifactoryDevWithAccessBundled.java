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

package org.artifactory.webapp.main;

import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.io.IOException;

import static org.artifactory.webapp.WebappUtils.copyNewerDevResources;
import static org.artifactory.webapp.WebappUtils.getDevEtcFolder;
import static org.artifactory.webapp.WebappUtils.updateMimetypes;
import static org.artifactory.webapp.main.StartArtifactoryDev.getArtifactoryDevHome;
import static org.artifactory.webapp.main.StartArtifactoryDev.setSystemProperties;
import static org.artifactory.webapp.main.StartArtifactoryDev.startAccessProcess;
import static org.artifactory.webapp.main.StartArtifactoryDev.startTomcat;

/**
 * @author Lior Hasson
 */
public class StartArtifactoryDevWithAccessBundled {

    /**
     * Main function, starts the Tomcat server.
     */
    public static void main(String... args) throws IOException {
        File devArtHome = getArtifactoryDevHome(args);
        File devEtcDir = getDevEtcFolder();
        setSystemProperties(devArtHome);
        updateMimetypes(devEtcDir);
        copyNewerDevResources(devEtcDir, devArtHome, true);
        startAccessProcess(8040, false, devArtHome, -1);
        Tomcat tomcat = startTomcat(devArtHome);
        tomcat.getServer().await();
    }
}
