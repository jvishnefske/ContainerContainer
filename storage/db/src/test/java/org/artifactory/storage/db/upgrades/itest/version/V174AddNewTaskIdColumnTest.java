package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v173;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v174;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * @author Hussein Alshqerat
 */
public class V174AddNewTaskIdColumnTest extends UpgradeBaseTest {

    @Test
    public void testColumnExists() throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v174)) {
            v174.converter().convert(context);
        }
        assertTrue(columnExists("tasks", "task_id"));
    }

    @Test
    public void testPrimaryKeyExists() throws SQLException {
        testOnlyWithPostgresql();
        assertTrue(primaryKeyExists("tasks", "tasks_pk"));
    }

    @Test
    public void testInsertDuplicatesFails() {
        assertThrows(SQLException.class,
                () -> jdbcHelper.executeUpdate(
                        "INSERT INTO tasks (task_type, task_context, created, task_id) VALUES\n" +
                                "('INDEX', 'reponone:test', 0, 1), ('INDEX', 'reponone:test', 0, 1)"));

    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v173;
    }
}