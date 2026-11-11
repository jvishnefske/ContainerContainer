package com.jfrog.build.dao.util;

import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.entity.BuildIdEntity;
import com.jfrog.build.model.BuildIdImpl;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.querybuilder.BaseQueryBuilder;
import org.jfrog.storage.util.querybuilder.DerbyQueryBuilder;
import org.jfrog.storage.util.querybuilder.MysqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.OracleQueryBuilder;
import org.jfrog.storage.util.querybuilder.PostgresqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.SqlServerQueryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.jfrog.build.model.api.ContinueBuildFilter.OrderBy.BUILD_DATE;
import static com.jfrog.build.model.api.ContinueBuildFilter.OrderBy.BUILD_NAME;
import static com.jfrog.build.model.api.ContinueBuildFilter.OrderBy.BUILD_NUMBER;
import static com.jfrog.build.dao.util.BuildsDaoUtils.createOrderByStr;
import static com.jfrog.build.dao.util.BuildsDaoUtils.createSqlBuilder;
import static com.jfrog.build.dao.util.BuildsDaoUtils.createWhereClauseFromContinueBuildFilter;
import static com.jfrog.build.dao.util.BuildsDaoUtils.executeLimitedSizedQuery;
import static com.jfrog.build.dao.util.BuildsDaoUtils.getBuildNodePath;
import static com.jfrog.build.dao.util.BuildsDaoUtils.getDbIndexedValueMaxSize;
import static com.jfrog.build.dao.util.BuildsDaoUtils.nullIfZero;
import static com.jfrog.build.dao.util.BuildsDaoUtils.preparePropValue;
import static com.jfrog.build.dao.util.BuildsDaoUtils.populateBuildIdEntryList;
import static com.jfrog.build.dao.util.BuildsDaoUtils.safeGetDate;
import static com.jfrog.build.dao.util.BuildsDaoUtils.safeGetLongOrNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.jfrog.commons.continues.util.Direction.ASC;
import static org.jfrog.commons.continues.util.Direction.DESC;
import static org.jfrog.storage.DbType.MSSQL;
import static org.jfrog.storage.DbType.POSTGRESQL;

class BuildsDaoUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "name, , ,",
            "name,.,name",
            "name,path,path/name"
    })
    void getBuildNodePath_shouldReturnExpectedPath_whenGivenPathAndName(
            @NonNull String name,
            @Nullable String path,
            @Nullable String expectedPath) {
        BuildNode node = mock(BuildNode.class);
        path = isBlank(path) ? null : path;
        when(node.getPath()).thenReturn(path);
        when(node.getName()).thenReturn(name);
        expectedPath = isBlank(expectedPath) ? null : expectedPath;
        assertEquals(expectedPath, getBuildNodePath(node));
    }

    @Test
    void createWhereClauseFromContinueBuildFilter_shouldAddBuildRepoAndSearchStr_whenContinueBuildIdIsNull() {
        ContinueBuildFilter filter = new ContinueBuildFilter();
        filter.setBuildRepo("some-repo");
        filter.setSearchStr("foo");
        List<Object> params = new ArrayList<>();

        String where = createWhereClauseFromContinueBuildFilter(filter, params, null);

        assertTrue(where.contains("(repo = ?)"));
        assertTrue(where.contains("(build_name like ?)"));
        assertEquals(List.of("some-repo", "%foo%"), params);
    }

    @Test
    void createWhereClauseFromContinueBuildFilter_shouldAddBuildNameCondition_whenOrderByIsBuildNameAndAsc() {
        long started = 1000L;
        BuildIdImpl continueBuild = BuildIdImpl.builder()
                .buildId(1L)
                .name("some-build-name")
                .number("1")
                .started(started)
                .buildRepo("repo")
                .build();
        ContinueBuildFilter filter = new ContinueBuildFilter();
        filter.setContinueBuildId(continueBuild);
        filter.setOrderBy(BUILD_NAME);
        filter.setDirection(ASC);
        List<Object> params = new ArrayList<>();

        String where = createWhereClauseFromContinueBuildFilter(filter, params, null);

        assertTrue(where.contains("build_name > ?"));
        assertEquals(List.of("repo", "some-build-name"), params);
    }

    @Test
    void createWhereClauseFromContinueBuildFilter_shouldAddCompoundCondition_whenOrderByIsBuildNumber() {
        BuildIdImpl continueBuild = BuildIdImpl.builder()
                .buildId(1L)
                .name("some-build-name")
                .number("some-build-number")
                .started(1000L)
                .buildRepo("repo")
                .build();
        ContinueBuildFilter filter = new ContinueBuildFilter();
        filter.setContinueBuildId(continueBuild);
        filter.setOrderBy(BUILD_NUMBER);
        filter.setDirection(DESC);
        List<Object> params = new ArrayList<>();

        String where = createWhereClauseFromContinueBuildFilter(filter, params, null);

        assertTrue(where.contains("build_number"));
        assertTrue(where.contains("<"));
        assertEquals(List.of("repo", "some-build-number", "some-build-number", "some-build-name"), params);
    }

    @Test
    void createWhereClauseFromContinueBuildFilter_shouldUseAliasPrefix_whenAliasProvided() {
        ContinueBuildFilter filter = new ContinueBuildFilter();
        filter.setBuildRepo("repo");
        List<Object> params = new ArrayList<>();

        String where = createWhereClauseFromContinueBuildFilter(filter, params, "b");

        assertTrue(where.contains("(b.repo = ?)"));
        assertEquals(List.of("repo"), params);
    }

    @Test
    void populateBuildIdEntryList_shouldReturnEmptyList_whenResultSetHasNoRows() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);

        List<BuildIdEntity> result = populateBuildIdEntryList(rs);

        assertTrue(result.isEmpty());
    }

    @Test
    void populateBuildIdEntryList_shouldReturnOneEntity_whenResultSetHasOneRow() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getLong("build_id")).thenReturn(1L);
        when(rs.getString("build_name")).thenReturn("b");
        when(rs.getString("build_number")).thenReturn("2");
        when(rs.getLong("build_date")).thenReturn(1000L);
        when(rs.getString("repo")).thenReturn("r");

        List<BuildIdEntity> result = populateBuildIdEntryList(rs);

        assertEquals(1, result.size());
        BuildIdEntity entity = result.getFirst();
        assertEquals(1L, entity.getBuildId());
        assertEquals("b", entity.getBuildName());
        assertEquals("2", entity.getBuildNumber());
        assertEquals(1000L, entity.getBuildDate());
        assertEquals("r", entity.getBuildRepo());
    }

    @Test
    void populateBuildIdEntryList_shouldReturnTwoEntities_whenResultSetHasTwoRows() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getLong("build_id")).thenReturn(1L, 2L);
        when(rs.getString("build_name")).thenReturn("a", "b");
        when(rs.getString("build_number")).thenReturn("1", "2");
        when(rs.getLong("build_date")).thenReturn(1000L, 2000L);
        when(rs.getString("repo")).thenReturn("r1", "r2");

        List<BuildIdEntity> result = populateBuildIdEntryList(rs);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getBuildId());
        assertEquals(2L, result.get(1).getBuildId());
    }

    @ParameterizedTest
    @CsvSource({
            "BUILD_NAME, ASC, 'build_name ASC'",
            "BUILD_NAME, DESC, 'build_name DESC'"
    })
    void createOrderByStr_shouldReturnBuildNameOnly_whenOrderByIsBuildName(
            String orderByStr, String directionStr, String expected) {
        ContinueBuildFilter filter = new ContinueBuildFilter();
        filter.setOrderBy(ContinueBuildFilter.OrderBy.valueOf(orderByStr));
        filter.setDirection(ASC.name().equals(directionStr) ? ASC : DESC);

        assertEquals(expected, createOrderByStr(filter));
    }

    @Test
    void createOrderByStr_shouldIncludeOrderByAndBuildName_whenOrderByIsNotBuildName() {
        ContinueBuildFilter filter = new ContinueBuildFilter();
        filter.setOrderBy(BUILD_NUMBER);
        filter.setDirection(ASC);

        String result = createOrderByStr(filter);

        assertTrue(result.startsWith("build_number ASC"));
        assertTrue(result.contains("build_name ASC"));
    }

    @Test
    void createOrderByStr_shouldIncludeBuildDateAndBuildName_whenOrderByIsBuildDate() {
        ContinueBuildFilter filter = new ContinueBuildFilter();
        filter.setOrderBy(BUILD_DATE);
        filter.setDirection(DESC);

        String result = createOrderByStr(filter);

        assertTrue(result.startsWith("build_date DESC"));
        assertTrue(result.contains("build_name DESC"));
    }

    @Test
    void executeLimitedSizedQuery_shouldCallExecuteUpdatePerPartition_andReturnTotalRowsAffected()
            throws SQLException {
        JdbcHelper jdbcHelper = mock(JdbcHelper.class);
        when(jdbcHelper.executeUpdate("DELETE FROM x", List.of(1L, 2L))).thenReturn(2);
        when(jdbcHelper.executeUpdate("DELETE FROM x", List.of(3L, 4L))).thenReturn(2);
        when(jdbcHelper.executeUpdate("DELETE FROM x", List.of(5L))).thenReturn(1);
        List<Long> params = List.of(1L, 2L, 3L, 4L, 5L);

        int rowsAffected = executeLimitedSizedQuery(jdbcHelper, "DELETE FROM x", params, 2);

        assertEquals(5, rowsAffected);
    }

    @Test
    void preparePropValue_shouldReturnSame_whenNullOrWithinMax() {
        assertEquals("short", preparePropValue(10, "key", "short"));
        assertNull(preparePropValue(10, "key", null));
    }

    @Test
    void preparePropValue_shouldTruncate_whenLengthExceedsMax() {
        String longVal = "a".repeat(25);

        String result = preparePropValue(10, "key", longVal);

        assertNotNull(result);
        assertEquals(10, result.length());
        assertEquals("aaaaaaaaaa", result);
    }

    @Test
    void safeGetLongOrNull_shouldReturnValue_whenWasNullFalse() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("col")).thenReturn(42L);
        when(rs.wasNull()).thenReturn(false);

        assertEquals(42L, safeGetLongOrNull(rs, "col"));
    }

    @Test
    void safeGetLongOrNull_shouldReturnNull_whenWasNullTrue() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("col")).thenReturn(0L);
        when(rs.wasNull()).thenReturn(true);

        assertNull(safeGetLongOrNull(rs, "col"));
    }

    @Test
    void safeGetDate_shouldReturnDate_whenWasNullFalse() throws SQLException {
        long millis = 1234567890L;
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("col")).thenReturn(millis);
        when(rs.wasNull()).thenReturn(false);

        Date result = safeGetDate(rs, "col");

        assertNotNull(result);
        assertEquals(millis, result.getTime());
    }

    @Test
    void safeGetDate_shouldReturnNull_whenWasNullTrue() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("col")).thenReturn(0L);
        when(rs.wasNull()).thenReturn(true);

        assertNull(safeGetDate(rs, "col"));
    }

    @Test
    void getDbIndexedValueMaxSize_shouldReturn900_whenMssql() {
        assertEquals(900, getDbIndexedValueMaxSize(MSSQL));
    }

    @Test
    void getDbIndexedValueMaxSize_shouldReturn2048_whenOtherDbType() {
        assertEquals(2048, getDbIndexedValueMaxSize(POSTGRESQL));
    }

    @Test
    void nullIfZero_shouldReturnNull_whenZero() {
        assertNull(nullIfZero(0L));
    }

    @Test
    void nullIfZero_shouldReturnValue_whenNonZero() {
        assertEquals(42L, nullIfZero(42L));
    }

    @Test
    void createSqlBuilder_shouldReturnOracleQueryBuilder_whenOracle() {
        BaseQueryBuilder builder = createSqlBuilder(DbType.ORACLE);
        assertInstanceOf(OracleQueryBuilder.class, builder);
    }

    @Test
    void createSqlBuilder_shouldReturnSqlServerQueryBuilder_whenMssql() {
        BaseQueryBuilder builder = createSqlBuilder(MSSQL);
        assertInstanceOf(SqlServerQueryBuilder.class, builder);
    }

    @Test
    void createSqlBuilder_shouldReturnPostgresqlQueryBuilder_whenPostgresql() {
        BaseQueryBuilder builder = createSqlBuilder(POSTGRESQL);
        assertInstanceOf(PostgresqlQueryBuilder.class, builder);
    }

    @Test
    void createSqlBuilder_shouldReturnMysqlQueryBuilder_whenMysqlOrMariadb() {
        assertInstanceOf(MysqlQueryBuilder.class, createSqlBuilder(DbType.MYSQL));
        assertInstanceOf(MysqlQueryBuilder.class, createSqlBuilder(DbType.MARIADB));
    }

    @Test
    void createSqlBuilder_shouldReturnDerbyQueryBuilder_whenDefault() {
        BaseQueryBuilder builder = createSqlBuilder(DbType.DERBY);
        assertInstanceOf(DerbyQueryBuilder.class, builder);
    }
}