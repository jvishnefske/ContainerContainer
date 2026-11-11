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

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.config.db.TemporaryDBChannel;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.util.DbUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.artifactory.storage.db.itest.DbTestUtils.createSimpleJdbcInsert;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v283;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v284;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Verifies the {@code v284} converter across all supported DB types: it adds the additive {@code repo_type_v2} column
 * to {@code repository_config} and backfills it with {@code CASE WHEN type = 'federated' THEN 'local' ELSE type END}.
 * Converters are run only up to {@code v283} by the harness, then {@code v284} is driven explicitly after seeding
 * rows so the backfill mapping can be asserted on real data. A second run must be {@code SKIPPED} because the
 * converter is guarded by a {@code columnNotExists} condition.
 */
@Test
public class V284AddRepoTypeV2ColumnTest extends UpgradeBaseTest {

    private static final String TABLE = "repository_config";
    private static final String REPO_TYPE_V2 = "repo_type_v2";
    private static final List<String> SEEDED_KEYS = List.of("fed-repo", "local-repo", "remote-repo", "virtual-repo");

    private TemporaryDBChannel temporaryDBChannel;
    private JdbcTemplate jdbc;
    private SimpleJdbcInsert simpleJdbcInsert;

    @BeforeMethod
    public void beforeMethod() {
        temporaryDBChannel = new TemporaryDBChannel(ArtifactoryHome.get().getDBProperties());
        jdbc = temporaryDBChannel.asJdbcTemplate();
        simpleJdbcInsert = createSimpleJdbcInsert(jdbc.getDataSource()).withTableName(TABLE);
    }

    @AfterMethod
    public void afterMethod() throws SQLException {
        // DELETE (not TRUNCATE) the seeded rows only: repository_config is referenced by foreign keys from other
        // tables, so TRUNCATE is rejected on Derby/PostgreSQL.
        try {
            if (DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), TABLE)) {
                for (String key : SEEDED_KEYS) {
                    jdbc.update("DELETE FROM " + TABLE + " WHERE repository_key = ?", key);
                }
            }
        } finally {
            temporaryDBChannel.close();
        }
    }

    public void testAddRepoTypeV2ColumnAndBackfill() throws SQLException {
        insertRepo("fed-repo", "federated");
        insertRepo("local-repo", "local");
        insertRepo("remote-repo", "remote");
        insertRepo("virtual-repo", "virtual");

        runV284Converter(ConverterApplyStatus.COMPLETED);

        assertTrue(columnExists(TABLE, REPO_TYPE_V2), "repo_type_v2 column was not added");
        assertEquals(columnSize(TABLE, REPO_TYPE_V2), 32, "repo_type_v2 must be varchar(32)");
        assertTrue(isColumnNullable(TABLE, REPO_TYPE_V2), "repo_type_v2 must be nullable");

        assertEquals(repoTypeV2Of("fed-repo"), "local", "federated must be mapped to local");
        assertEquals(repoTypeV2Of("local-repo"), "local");
        assertEquals(repoTypeV2Of("remote-repo"), "remote");
        assertEquals(repoTypeV2Of("virtual-repo"), "virtual");

        // Re-running the converter must be a no-op: it is guarded by columnNotExists.
        runV284Converter(ConverterApplyStatus.SKIPPED);
    }

    private void insertRepo(String key, String type) {
        simpleJdbcInsert.execute(Map.of(
                "repository_key", key,
                "type", type,
                "package_type", "generic",
                "revision", 1L,
                "created", 1L,
                "modified", 1L));
    }

    private void runV284Converter(ConverterApplyStatus expectedStatus) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v284)) {
            DbConverterResult result = v284.converter().convert(context);
            assertEquals(result.getStatus(), expectedStatus);
        }
    }

    private String repoTypeV2Of(String repositoryKey) {
        return jdbc.queryForObject("SELECT " + REPO_TYPE_V2 + " FROM " + TABLE + " WHERE repository_key = ?",
                String.class, repositoryKey);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v283;
    }
}
