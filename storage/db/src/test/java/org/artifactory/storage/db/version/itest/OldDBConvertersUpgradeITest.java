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

package org.artifactory.storage.db.version.itest;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.util.ArtifactoryDbVersionUtil;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.jfrog.converter.DbVersion;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Michael Akushsky
 */
@Test
public class OldDBConvertersUpgradeITest extends UpgradeBaseTest {

    @BeforeMethod
    public void clearDB() throws IOException, SQLException {
        resetToVersion(ArtifactoryServiceVersionProvider.v640m007, ArtifactoryDBVersion.v101);
    }

    public void testArtifactorySchemaTablesExist() throws SQLException {
        assertTrue(tableExists("schema_change_log"));

        // Now let's select for all db converters prior to 6.20.0 plus one more, which shouldn't be skipped.
        try (ResultSet rs = jdbcHelper.executeSelect(
                String.format(
                        "SELECT COUNT(*) AS count FROM schema_change_log WHERE status = 'SKIPPED' and version IN " +
                                "('%s')",
                        ArtifactoryDbVersionUtil.versionsBetween(ArtifactoryDBVersion.v10, ArtifactoryDBVersion.v22)
                                .stream().map(DbVersion::version).collect(Collectors.joining("','"))))) {
            rs.next();
            assertEquals(rs.getInt("COUNT"), 12);
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v101;
    }
}
