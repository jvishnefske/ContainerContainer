package org.artifactory.storage.db.aql.dao;

import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlQuery;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.jfrog.storage.QueryControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Slutsky
 */
class AqlDaoUnitTest {
    @Mock
    DbService dbService;
    @Mock
    AqlDao dao;
    AutoCloseable closeable;

    public static Stream<DbType> getType() {
        return Stream.of(DbType.DERBY, DbType.MARIADB, DbType.MSSQL, DbType.MYSQL, DbType.POSTGRESQL, DbType.ORACLE);
    }

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(dao, "dbService", dbService);
    }

    @AfterEach
    void teardown() throws Exception {
        closeable.close();
    }

    @Test
    void testStreamQuery() throws SQLException {
        SqlQuery sqlQuery = mock(SqlQuery.class);
        ArtifactoryJdbcHelper helper = mock(ArtifactoryJdbcHelper.class);
        when(dbService.getDatabaseType()).thenReturn(DbType.DERBY);
        when(sqlQuery.getQueryString()).thenReturn("");
        when(sqlQuery.getQueryParams()).thenReturn(new Object[] {});
        when(helper.executeSelectStreamed(any(), any(), any(), any())).thenReturn(mock(ResultSet.class));
        ReflectionTestUtils.setField(dao, "jdbcHelper", helper);
        when(dao.executeStreamedResponse(sqlQuery, null, null, 1L, 5)).thenCallRealMethod();
        dao.executeStreamedResponse(sqlQuery, null, null, 1L, 5);
        verify(helper, times(1)).executeSelectStreamed(any(QueryControls.class), anyString());
    }

    @ParameterizedTest
    @MethodSource("getType")
    void addFetchSize(DbType type) {
        QueryControls.QueryControlsBuilder builder = QueryControls.builder();
        when(dbService.getDatabaseType()).thenReturn(type);
        doCallRealMethod().when(dao).addFetchSize(1, builder);
        dao.addFetchSize(1, builder);
        if (!type.equals(DbType.DERBY)) {
            assertEquals(1, builder.build().getFetchSize());
        } else {
            assertEquals(0, builder.build().getFetchSize());
        }
    }
}