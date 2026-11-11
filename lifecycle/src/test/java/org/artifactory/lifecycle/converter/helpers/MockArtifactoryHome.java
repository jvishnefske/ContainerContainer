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

package org.artifactory.lifecycle.converter.helpers;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;

import java.io.File;
import java.util.Date;

/**
 * Author: gidis
 */
public class MockArtifactoryHome extends ArtifactoryHome {

    public MockArtifactoryHome(File home) {
        super(new File(home, ".artifactory").getAbsolutePath());
    }

    @Override
    public CompoundVersionDetails getRunningArtifactoryServiceVersion() {
        return new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "MOCK", new Date().getTime());
    }

    @Override
    public CompoundVersionDetails getRunningArtifactoryProductVersion() {
        return new CompoundVersionDetails(ArtifactoryServiceVersion.getCurrent(), "MOCK", new Date().getTime());
    }
}
