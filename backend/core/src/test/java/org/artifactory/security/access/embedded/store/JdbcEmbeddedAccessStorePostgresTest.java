/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.store;

import org.jfrog.storage.DbType;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * The store contract on PostgreSQL. Skipped unless {@code EA_PG_URL} names a JDBC URL of a database that may be
 * written to ({@code EA_PG_USER} and {@code EA_PG_PASSWORD} supply the credentials). The test drops and recreates
 * the {@code ea_*} tables, so point it at a throw-away database. It never starts a server itself.
 */
@EnabledIfEnvironmentVariable(named = "EA_PG_URL", matches = ".+")
class JdbcEmbeddedAccessStorePostgresTest extends AbstractEmbeddedAccessStoreTest {

    private static final String[] TABLES = {"ea_user_groups", "ea_users", "ea_groups", "ea_permissions",
            "ea_tokens", "ea_kv"};

    private DataSource pg;

    @Override
    DataSource freshDatabase() throws Exception {
        pg = TestDataSources.driverManager(System.getenv("EA_PG_URL"), System.getenv("EA_PG_USER"),
                System.getenv("EA_PG_PASSWORD"));
        dropDatabase();
        return pg;
    }

    @Override
    void dropDatabase() throws Exception {
        try (Connection con = pg.getConnection(); Statement st = con.createStatement()) {
            for (String table : TABLES) {
                st.execute("DROP TABLE IF EXISTS " + table);
            }
        }
    }

    @Override
    DbType dbType() {
        return DbType.POSTGRESQL;
    }
}
