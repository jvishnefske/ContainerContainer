package org.artifactory.storage.db.support.database.itest.dao;

import lombok.SneakyThrows;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.support.database.dao.DatabaseActivityDao;
import org.artifactory.storage.db.support.database.model.DatabaseActivity;
import org.jfrog.storage.DbType;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertTrue;

@Test
public class DatabaseActivityDaoTest extends DbBaseTest {

    private static final String POSTGRESQL_SLOW_QUERY_STATEMENT =
            " SELECT pid, TO_CHAR(age(clock_timestamp(), query_start), 'HH24:MI:SS') AS age, usename, " +
                    "state, wait_event_type, wait_event, query FROM pg_stat_activity WHERE state != 'idle' AND query " +
                    "NOT LIKE '%pg_stat_activity%' ORDER BY age DESC LIMIT 10";

    private static final String MYSQL_AND_MARIADB_SLOW_QUERY_STATEMENT =
            "SELECT ID,SEC_TO_TIME(TIME) AS TIME,USER,STATE,INFO FROM information_schema.processlist where state != " +
                    "'' order by time desc LIMIT 10";

    private static final String MSSQL_SLOW_QUERY_STATEMENT =
            "SELECT r.session_id AS pid, FORMAT(DATEADD(ms, DATEDIFF(ms, r.start_time, GETDATE()), '19000101'), " +
                    "'HH:mm:ss') AS age, s.login_name AS usename, r.status AS state, st.text AS query FROM sys" +
                    ".dm_exec_requests r INNER JOIN sys.dm_exec_sessions s ON r.session_id = s.session_id OUTER APPLY" +
                    " sys.dm_exec_sql_text(r.sql_handle) AS st WHERE r.status != 'background' AND st.text IS NOT NULL" +
                    " AND st.text NOT LIKE '%sys.dm_exec_requests%' ORDER BY r.start_time DESC OFFSET 0 ROWS FETCH " +
                    "NEXT 10 ROWS ONLY;";

    @Autowired
    private DatabaseActivityDao dao;

    private DbType dbType;

    @BeforeClass
    public void setUp() throws Exception {
        importSql("/sql/nodes.sql");
    }

    @BeforeMethod
    public void setup() {
        dbType = dbProperties.getDbType();
    }

    @Test
    @SneakyThrows
    public void testGetSlowQueriesFromDatabaseForPostgresql() {
        if (dbType == DbType.POSTGRESQL) {
            List<DatabaseActivity> databaseActivityList =
                    dao.getSlowQueriesFromDatabase(
                            POSTGRESQL_SLOW_QUERY_STATEMENT, DbType.POSTGRESQL);
            assertTrue(databaseActivityList.size() >= 0);
        } else {
            throwExceptionIfNeeded(dbType);
        }
    }

    @Test
    @SneakyThrows
    public void testGetSlowQueriesFromDatabaseForMysql() {
        if (dbType == DbType.MYSQL) {
            List<DatabaseActivity> databaseActivityList =
                    dao.getSlowQueriesFromDatabase(
                            MYSQL_AND_MARIADB_SLOW_QUERY_STATEMENT, DbType.MYSQL);
            assertTrue(databaseActivityList.size() >= 0);
        } else {
            throwExceptionIfNeeded(dbType);
        }
    }

    @Test
    @SneakyThrows
    public void testGetSlowQueriesFromDatabaseForMariaDB() {
        if (dbType == DbType.MARIADB) {
            List<DatabaseActivity> databaseActivityList =
                    dao.getSlowQueriesFromDatabase(
                            MYSQL_AND_MARIADB_SLOW_QUERY_STATEMENT, DbType.MARIADB);
            assertTrue(databaseActivityList.size() >= 0);
        } else {
            throwExceptionIfNeeded(dbType);
        }
    }

    @Test
    @SneakyThrows
    public void testGetSlowQueriesFromDatabaseForMssql() {
        if (dbType == DbType.MSSQL) {
            List<DatabaseActivity> databaseActivityList =
                    dao.getSlowQueriesFromDatabase(
                            MSSQL_SLOW_QUERY_STATEMENT, DbType.MSSQL);
            assertTrue(databaseActivityList.size() >= 0);
        } else {
            throwExceptionIfNeeded(dbType);
        }
    }

    private void throwExceptionIfNeeded(DbType dbType) {
        throw new SkipException(dbType + " DB detected, skipping this test as its irrelevant for " +
                dbType);
    }
}