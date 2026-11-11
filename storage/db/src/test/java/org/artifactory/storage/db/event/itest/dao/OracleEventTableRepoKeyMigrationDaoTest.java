
package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.EventTableRepoKeyMigrationDao;
import org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.query.AddIndexOnRepoAndEventHandler;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.mariadb.jdbc.client.Context;
import org.mariadb.jdbc.client.DataType;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.mariadb.jdbc.client.result.CompleteResult.createResultSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;


/**
 * Units tests for {@link EventTableRepoKeyMigrationDao}.
 *
 * @author Elad Shoval
 */
@Test
public class OracleEventTableRepoKeyMigrationDaoTest extends DbBaseTest {

    private final DbType dbType = DbType.ORACLE;

    @Test
    public void testAddIndexOnRepoAndEventIdPositive() throws SQLException {

        ArtifactoryJdbcHelper jdbcHelper1 =
                Mockito.mock(ArtifactoryJdbcHelper.class);

        EventTableRepoKeyMigrationDao eventTableRepoKeyMigrationDao =
                new EventTableRepoKeyMigrationDao(Mockito.mock(MigrationStatusStorageService.class), jdbcHelper1);

        ReflectionTestUtils.setField(eventTableRepoKeyMigrationDao, "dbType", DbType.ORACLE);
        ReflectionTestUtils.setField(eventTableRepoKeyMigrationDao, "addIndexOnRepoAndEventHandler",
                new AddIndexOnRepoAndEventHandler(dbType));

        String[][] data = new String[][] {new String[] {"1"}};

        ResultSet resultSet = createResultSet("cnt", DataType.INTEGER, data, Mockito.mock(Context.class), 0, 0);
        when(jdbcHelper1.executeSelect(any())).thenReturn(resultSet);

        ReflectionTestUtils.setField(eventTableRepoKeyMigrationDao, "jdbcHelper", jdbcHelper1);

        String sqlQueryOnlineSuffix = eventTableRepoKeyMigrationDao.getSqlQueryOnlineSuffix();

        assertTrue(sqlQueryOnlineSuffix.toLowerCase().contains("online"));
    }
}