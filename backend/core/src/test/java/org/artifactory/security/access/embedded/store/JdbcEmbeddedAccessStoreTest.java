/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.store;

import org.artifactory.security.access.embedded.core.EaOutcome;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The store contract on in-memory Derby, plus the schema rules that need no particular database. */
class JdbcEmbeddedAccessStoreTest extends AbstractEmbeddedAccessStoreTest {

    private String url;

    @Override
    DataSource freshDatabase() {
        url = "jdbc:derby:memory:ea-" + UUID.randomUUID();
        return TestDataSources.driverManager(url + ";create=true", null, null);
    }

    @Override
    void dropDatabase() {
        try {
            DriverManager.getConnection(url + ";drop=true").close();
        } catch (SQLException expected) {
            // Derby reports a successful drop as SQLState 08006.
        }
    }

    @Override
    DbType dbType() {
        return DbType.DERBY;
    }

    @ParameterizedTest
    @EnumSource(value = DbType.class, names = {"MYSQL", "MARIADB", "ORACLE", "MSSQL"})
    void unsupportedDatabaseIsRefusedWithoutTouchingIt(DbType type) throws SQLException {
        String emptyUrl = "jdbc:derby:memory:ea-empty-" + UUID.randomUUID();
        DataSource empty = TestDataSources.driverManager(emptyUrl + ";create=true", null, null);
        assertRefused(EmbeddedAccessSchema.ensure(empty, type, clock), "supports only Derby and PostgreSQL");
        assertRefused(EmbeddedAccessSchema.ensure(empty, type, clock), type.name());
        assertFalse(EmbeddedAccessSchema.supports(type));
        try (Connection con = empty.getConnection()) {
            assertFalse(DbUtils.tableExists(con.getMetaData(), DbType.DERBY, EmbeddedAccessSchema.MARKER_TABLE));
        }
        try {
            DriverManager.getConnection(emptyUrl + ";drop=true").close();
        } catch (SQLException expected) {
            // dropped
        }
    }

    @Test
    void supportedTypes() {
        assertTrue(EmbeddedAccessSchema.supports(DbType.DERBY));
        assertTrue(EmbeddedAccessSchema.supports(DbType.POSTGRESQL));
    }

    @Test
    void scriptSplitterDropsCommentsAndBlankStatements() {
        assertEquals(List.of("CREATE TABLE a (x INT)", "CREATE INDEX i ON a (x)"),
                EmbeddedAccessSchema.splitStatements("-- header; with a semicolon\nCREATE TABLE a (x INT);\n\n"
                        + "  -- indented comment\nCREATE INDEX i ON a (x);\n;\n"));
    }

    @Test
    void bothDdlResourcesDefineTheSameTables() throws Exception {
        List<String> derby = tableNames(EmbeddedAccessSchema.loadStatements("/embedded-access/derby.sql")
                .orElseThrow());
        List<String> postgres = tableNames(EmbeddedAccessSchema.loadStatements("/embedded-access/postgresql.sql")
                .orElseThrow());
        assertEquals(List.of("ea_users", "ea_groups", "ea_user_groups", "ea_permissions", "ea_tokens", "ea_kv"),
                derby);
        assertEquals(derby, postgres);
        assertTrue(EmbeddedAccessSchema.loadStatements("/embedded-access/none.sql").isEmpty());
        assertEquals(EaOutcome.done(), EmbeddedAccessSchema.ensure(dataSource, DbType.DERBY, clock));
    }

    private static List<String> tableNames(List<String> statements) {
        return statements.stream().filter(s -> s.startsWith("CREATE TABLE"))
                .map(s -> s.substring("CREATE TABLE ".length(), s.indexOf(' ', "CREATE TABLE ".length())))
                .toList();
    }
}
