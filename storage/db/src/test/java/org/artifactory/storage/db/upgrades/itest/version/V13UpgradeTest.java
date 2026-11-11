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

package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v10;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v11;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v13;
import static org.jfrog.storage.util.DbUtils.indexExists;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Gal Ben Ami
 */
@Test
public class V13UpgradeTest extends UpgradeBaseTest {

    @BeforeMethod
    public void junkifyDb() throws SQLException {
        importSql("/sql/trusted_keys_with_violation.sql");
        final List<ArtifactoryDBVersion> dbVersions = List.of(v11, v13);
        for (DbVersion dbVersion : dbVersions) {
            try (final ChangeLogContext context = new ChangeLogContext(
                    jdbcHelper.getDataSource().getConnection(), dbProperties.getDbType(), dbVersion)) {
                dbVersion.converter().convert(context);
            }
        }
    }

    @Test
    public void testIndexExistsNoNullsAndUniq() throws SQLException {
        assertTrue(indexExists(jdbcHelper, "trusted_keys", "alias", "trusted_keys_alias",
                dbProperties.getDbType()));
        try (ResultSet res = jdbcHelper.executeSelect("SELECT * FROM trusted_keys WHERE alias is null")) {
            assertFalse(res.next());
        }
        try (ResultSet res = jdbcHelper.executeSelect("SELECT count(*) FROM trusted_keys GROUP BY alias")) {
            while (res.next()) {
                int count = res.getInt(1);
                assertEquals(count, 1);
            }
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v10;
    }
}
