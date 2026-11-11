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

package org.artifactory.storage.db.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.jfrog.converter.DbDetails;
import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Dan Feldman
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ArtifactoryDbUtils {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryDbUtils.class);

    public static void verifyDbDetails(DbDetails dbDetails, ArtifactoryHome artifactoryHome) {
        if (dbDetails == null || dbDetails.getJdbcHelper() == null) {
            throw new ConverterPreconditionException("JdbcHelper is missing");
        }
        if (artifactoryHome.getDBProperties() == null) {
            throw new ConverterPreconditionException("DbProperties is missing");
        }
    }

    public static boolean isSupportsStreaming(DbType type) {
        return !type.equals(DbType.DERBY);
    }
}
