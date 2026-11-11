package org.artifactory.storage.db.aql.dao;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.storage.db.aql.service.AqlPermissionProviderImpl;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlQuery;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.storage.DbType;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class AqlDaoTest extends DbBaseTest {
    @Mock
    AqlRepoProvider providerMock;

    @Mock
    AqlPermissionProviderImpl permissionMock;

    @Autowired
    private AqlDao aqlDao;

    private AutoCloseable mockitoClosable;

    @DataProvider
    public static Object[][] timeProvider() {
        return new Object[][] {
                {TimeUnit.MINUTES.toMillis(20L), true},
                {400L, false}
        };
    }

    @BeforeMethod
    public void before() throws IOException, SQLException {
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void after() throws Exception {
        this.mockitoClosable.close();
        unbindDummyContext();
    }

    @Test
    public void testExecuteQueryLazy() {
        throwExceptionIfNeeded();
        SqlQuery sqlQuery = mock(SqlQuery.class);
        when(sqlQuery.getQueryString()).thenReturn(getSqlQuery());
        when(sqlQuery.getQueryParams()).thenReturn(new Object[] {});
        try {
            aqlDao.executeQueryLazy(sqlQuery, permissionMock, providerMock, 1L);
        } catch (AqlException e) {
            assertTrue(e.getCause().getMessage().contains("cancel") ||
                    e.getCause() instanceof SQLTimeoutException);
        }
    }

    private void throwExceptionIfNeeded() {
        if (dbProperties.getDbType() == DbType.ORACLE) {
            throw new SkipException("Oracle DB detected, skipping the test as its irrelevant for oracle");
        } else if (dbProperties.getDbType() == DbType.DERBY) {
            throw new SkipException("Derby DB detected, skipping the test as its irrelevant for derby");
        }
    }

    private String getSqlQuery() {
        switch (dbProperties.getDbType()) {
            default:
            case MSSQL:
                return "WAITFOR DELAY '10:00:00.000'";
            case MARIADB:
            case MYSQL:
                return "SELECT SLEEP(300)";
            case POSTGRESQL:
                return "SELECT pg_sleep(300)";
        }
    }

    @Test(dataProvider = "timeProvider")
    public void testLogForLongQuery(long time, boolean showLog) {
        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(AqlDao.class, Level.DEBUG);
        aqlDao.logForLongQuery(System.currentTimeMillis(),
                System.currentTimeMillis() + time, null, "query");

        if (showLog) {
            assertEquals(2, appender.list.size());
            ILoggingEvent logEvent = appender.list.get(0);
            ILoggingEvent logEvent2 = appender.list.get(1);
            assertEquals(logEvent.getLevel(), Level.WARN);
            assertTrue(logEvent.getMessage().contains("query"));
            assertEquals(logEvent2.getLevel(), Level.DEBUG);
            assertTrue(logEvent2.getMessage().contains("stackTrace"));
        } else {
            assertEquals(0, appender.list.size());
        }
    }
}