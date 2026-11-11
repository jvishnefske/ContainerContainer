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

package org.artifactory.storage.db.home;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * A convenience class for tests that require bind/unbind of {@link ArtifactoryHome} (usually for system
 * properties and mime types).
 *
 * @author Yossi Shaul
 */
@SuppressWarnings("squid:S2187")
public class ArtifactoryUniqueFolderHomeBoundTest extends ArtifactoryHomeBoundTest {

    private static final Logger log = LoggerFactory.getLogger(ArtifactoryUniqueFolderHomeBoundTest.class);

    String testInstanceName;

    public ArtifactoryUniqueFolderHomeBoundTest(String testInstanceName) {
        this.testInstanceName = testInstanceName;
    }

    @Override
    protected String getHomeIdentifier() {
        return testInstanceName;
    }

    public void deleteBinaryStore() {
        try {
            FileUtils.forceDelete(homeStub.getBinaryStoreXmlFile());
        } catch (IOException e) {
            log.error("Not able to delete file {}", homeStub.getBinaryStoreXmlFile(),
                    e);
        }
    }

}
