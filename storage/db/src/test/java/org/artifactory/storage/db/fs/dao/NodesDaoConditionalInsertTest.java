package org.artifactory.storage.db.fs.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodeBuilder;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.util.stream.Stream;

import static org.artifactory.storage.db.util.BaseDao.isUniqueConstraintViolation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rima Polsky
 */
class NodesDaoConditionalInsertTest extends ArtifactoryHomeBoundJUnitTest {

    private ArtifactoryJdbcHelper jdbcHelper;
    private ArtifactoryDbProperties dbProperties;

    @BeforeEach
    public void setUp() {
        dbProperties = mock(ArtifactoryDbProperties.class);
        jdbcHelper = mock(ArtifactoryJdbcHelper.class);
    }

    private NodesDao createDao(DbType dbType) {
        when(dbProperties.getDbType()).thenReturn(dbType);
        return new NodesDao(jdbcHelper, dbProperties);
    }

    private Node testNode() {
        return new NodeBuilder()
                .nodeId(1L)
                .file(true)
                .repo("test-repo")
                .path("folder")
                .name("file.jar")
                .sha1Actual("abc123")
                .sha1Original("abc123")
                .md5Actual("def456")
                .md5Original("def456")
                .sha2("sha256hash")
                .length(1024L)
                .build();
    }

    @Test
    void createIfNotExists_postgres_usesOnConflict() throws SQLException {
        NodesDao dao = createDao(DbType.POSTGRESQL);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(NodesDao.POSTGRES_CONDITIONAL_INSERT), any(Object[].class)))
                .thenReturn(1);

        int result = dao.createIfNotExists(node);

        assertEquals(1, result);
        verify(jdbcHelper).executeUpdate(
                eq(NodesDao.POSTGRES_CONDITIONAL_INSERT), any(Object[].class));
    }

    @Test
    void createIfNotExists_postgres_returnsZeroOnDuplicate()
            throws SQLException {
        NodesDao dao = createDao(DbType.POSTGRESQL);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(NodesDao.POSTGRES_CONDITIONAL_INSERT), any(Object[].class)))
                .thenReturn(0);

        int result = dao.createIfNotExists(node);

        assertEquals(0, result);
    }

    static Stream<Object[]> conditionalInsertDbTypes() {
        return Stream.of(
                new Object[]{DbType.POSTGRESQL,
                        NodesDao.POSTGRES_CONDITIONAL_INSERT},
                new Object[]{DbType.ORACLE,
                        NodesDao.ORACLE_CONDITIONAL_INSERT},
                new Object[]{DbType.MSSQL,
                        NodesDao.MSSQL_CONDITIONAL_INSERT}
        );
    }

    @ParameterizedTest
    @MethodSource("conditionalInsertDbTypes")
    void createIfNotExists_success(
            DbType dbType, String expectedSql)
            throws SQLException {
        NodesDao dao = createDao(dbType);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(expectedSql), any(Object[].class)))
                .thenReturn(1);

        int result = dao.createIfNotExists(node);

        assertEquals(1, result);
        verify(jdbcHelper).executeUpdate(
                eq(expectedSql), any(Object[].class));
    }

    static Stream<DbType> plainInsertDbTypes() {
        return Stream.of(DbType.MYSQL, DbType.MARIADB);
    }

    @ParameterizedTest
    @MethodSource("plainInsertDbTypes")
    void createIfNotExists_mysql_usesPlainInsert(DbType dbType)
            throws SQLException {
        NodesDao dao = createDao(dbType);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(NodesDao.INSERT_QUERY), any(Object[].class)))
                .thenReturn(1);

        int result = dao.createIfNotExists(node);

        assertEquals(1, result);
        verify(jdbcHelper).executeUpdate(
                eq(NodesDao.INSERT_QUERY), any(Object[].class));
    }

    static Stream<DbType> fallbackDbTypes() {
        return Stream.of(DbType.DERBY);
    }

    @ParameterizedTest
    @MethodSource("fallbackDbTypes")
    void createIfNotExists_fallback_usesPlainInsert(DbType dbType)
            throws SQLException {
        NodesDao dao = createDao(dbType);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(NodesDao.INSERT_QUERY), any(Object[].class)))
                .thenReturn(1);

        int result = dao.createIfNotExists(node);

        assertEquals(1, result);
        verify(jdbcHelper).executeUpdate(
                eq(NodesDao.INSERT_QUERY), any(Object[].class));
    }

    @ParameterizedTest
    @MethodSource("fallbackDbTypes")
    void createIfNotExists_fallback_catchesDuplicateKeyViolation(
            DbType dbType) throws SQLException {
        NodesDao dao = createDao(dbType);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(NodesDao.INSERT_QUERY), any(Object[].class)))
                .thenThrow(new SQLException("duplicate", "23505"));

        int result = dao.createIfNotExists(node);

        assertEquals(0, result);
    }

    @ParameterizedTest
    @MethodSource("fallbackDbTypes")
    void createIfNotExists_fallback_rethrowsNonUniqueViolation(
            DbType dbType) throws SQLException {
        NodesDao dao = createDao(dbType);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(NodesDao.INSERT_QUERY), any(Object[].class)))
                .thenThrow(new SQLException("foreign key", "23503"));

        assertThrows(SQLException.class,
                () -> dao.createIfNotExists(node));
    }

    @Test
    void createIfNotExists_derby_firstSucceeds_secondCatchesDuplicate()
            throws SQLException {
        NodesDao dao = createDao(DbType.DERBY);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(NodesDao.INSERT_QUERY), any(Object[].class)))
                .thenReturn(1)
                .thenThrow(new SQLException(
                        "duplicate key value violates unique constraint",
                        "23505"));

        int firstResult = dao.createIfNotExists(node);
        assertEquals(1, firstResult);

        int secondResult = dao.createIfNotExists(node);
        assertEquals(0, secondResult);
    }

    @Test
    void createIfNotExists_derby_catchesMssqlError2601()
            throws SQLException {
        NodesDao dao = createDao(DbType.DERBY);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(NodesDao.INSERT_QUERY), any(Object[].class)))
                .thenThrow(new SQLException(
                        "unique index violation", "23000", 2601));

        int result = dao.createIfNotExists(node);

        assertEquals(0, result);
    }

    @Test
    void createIfNotExists_derby_catchesMysqlError1062()
            throws SQLException {
        NodesDao dao = createDao(DbType.DERBY);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(NodesDao.INSERT_QUERY), any(Object[].class)))
                .thenThrow(new SQLException(
                        "Duplicate entry", "23000", 1062));

        int result = dao.createIfNotExists(node);

        assertEquals(0, result);
    }

    @Test
    void createIfNotExists_derby_catchesOracleError1()
            throws SQLException {
        NodesDao dao = createDao(DbType.DERBY);
        Node node = testNode();
        when(jdbcHelper.executeUpdate(
                eq(NodesDao.INSERT_QUERY), any(Object[].class)))
                .thenThrow(new SQLException(
                        "ORA-00001: unique constraint violated",
                        "23000", 1));

        int result = dao.createIfNotExists(node);

        assertEquals(0, result);
    }

    @Test
    void isUniqueConstraintViolation_sqlState23505() {
        assertTrue(isUniqueConstraintViolation(
                new SQLException("dup", "23505")));
    }

    @Test
    void isUniqueConstraintViolation_mysqlError1062() {
        SQLException ex = new SQLException("dup", "23000", 1062);
        assertTrue(isUniqueConstraintViolation(ex));
    }

    @Test
    void isUniqueConstraintViolation_oracleError1() {
        SQLException ex = new SQLException("dup", "23000", 1);
        assertTrue(isUniqueConstraintViolation(ex));
    }

    @Test
    void isUniqueConstraintViolation_mssqlError2627() {
        SQLException ex = new SQLException("dup", "23000", 2627);
        assertTrue(isUniqueConstraintViolation(ex));
    }

    @Test
    void isUniqueConstraintViolation_nonUniqueViolation() {
        assertFalse(isUniqueConstraintViolation(
                new SQLException("fk", "23503")));
    }

    @Test
    void isUniqueConstraintViolation_wrappedCause() {
        SQLException inner = new SQLException("dup", "23505");
        RuntimeException outer = new RuntimeException("wrapped", inner);
        assertTrue(isUniqueConstraintViolation(outer));
    }
}
