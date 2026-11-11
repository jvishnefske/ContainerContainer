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

package org.artifactory.common.edition;

import org.artifactory.common.home.ArtifactoryHome;


/**
 * Enum of the various Artifactory editions.
 *
 * @author Yossi Shaul
 */
public enum ArtifactoryEdition {
    OSS,
    PRO,
    HA,
    AOL,
    CONAN_CE,
    JCR;

    public static ArtifactoryEdition detect() {
        return detect(null);
    }

    /**
     * Detects the edition based on configuration and available add-on implementations.
     *
     * @param artifactoryHome Running Artifactory home
     * @return The running Artifactory edition
     */
    public static ArtifactoryEdition detect(ArtifactoryHome artifactoryHome) {

        if (isAolEdition()) {
            return AOL;
        }
        if (artifactoryHome != null && artifactoryHome.isHaConfigured() &&
                classExists("org.artifactory.addon.ha.HaAddonImpl")) {
            return HA;
        }
        if (classExists("org.artifactory.addon.LicenseProvider")) {
            return PRO;
        }
        if (classExists("org.artifactory.addon.conan.rest.ConanResource")) {
            return CONAN_CE;
        }
        if (isJcrEdition()) {
            return JCR;
        }
        return OSS;
    }

    private static boolean isAolEdition() {
        return classExists("org.artifactory.addon.aol.webapp.AolCoreAddons");
    }

    public static boolean isJcrEdition() {
        return classExists("org.artifactory.addon.jcr.JcrRepoValidationInterceptor") &&
                classExists("org.artifactory.addon.docker.rest.DockerResource") &&
                classExists("com.jfrog.ph.helm.resource.HelmResource");
    }

    public static boolean classExists(String clazz) {
        try {
            Class.forName(clazz);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
