package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.JdbcHelper;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v172;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v173;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Hussein Alshqerat
 */
public class V173CleanupTasksTableTest extends UpgradeBaseTest {

    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;
    @Mock
    private ArtifactoryContext context;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() throws SQLException {
        mocks = openMocks(this);
        when(context.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(context.beanForType(ArtifactoryDbProperties.class)).thenReturn(dbProperties);
        when(context.beanForType(JdbcHelper.class)).thenReturn(jdbcHelper);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        ArtifactoryContextThreadBinder.bind(context);
        jdbcHelper.executeUpdate("TRUNCATE TABLE tasks");
        importSql("/sql/insert_tasks_records.sql");
    }

    @AfterMethod
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void cleanupTasksTableTest() throws SQLException {
        assertEquals(getTasksTableSize(), 1006);
        try (final ChangeLogContext context = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(), dbProperties.getDbType(), v173)) {
            v173.converter().convert(context);
        }
        assertTrue(indexExist("tasks", "task_type", "tasks_type_context_idx"));
        assertTrue(indexExist("tasks", "task_context", "tasks_type_context_idx"));
        assertEquals(getTasksTableSize(), 5);
    }

    private int getTasksTableSize() throws SQLException {
        return jdbcHelper.executeSelectCount("SELECT COUNT(1) FROM tasks");
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v172;
    }
}