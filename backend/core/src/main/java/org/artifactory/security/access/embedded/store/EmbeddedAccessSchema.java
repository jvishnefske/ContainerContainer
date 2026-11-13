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
import org.artifactory.security.access.embedded.core.EaOutcome.Done;
import org.artifactory.security.access.embedded.core.EaUser;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Creates the embedded Access tables when they are absent and seeds the built-in {@code admin} account, in the
 * style of {@code DbInitializationManager.initDbLockSchemaIfNeeded}.
 * <p>
 * The DDL is the classpath resource {@code /embedded-access/<db>.sql}, shipped in the {@code artifactory-storage-db}
 * jar. The {@code ea_kv} table is created last and serves as the marker: when it exists the script is not run
 * again. The script runs in one transaction, so a failed run leaves nothing behind on databases with transactional
 * DDL (both supported ones).
 * <p>
 * Only Derby and PostgreSQL are supported. Any other {@link DbType} is {@code Refused} before the database is
 * touched, with a reason meant for the startup failure message; the caller should abort startup on any
 * non-{@code Ok} outcome.
 * <p>
 * After the tables exist, {@link #ensure} inserts, if absent, the row {@code permissions.last_updated = 0} in
 * {@code ea_kv} and the user {@code admin}: internal realm, enabled, administrator, no password hash, not
 * must-change (IA1: it cannot authenticate until the server is claimed).
 */
public final class EmbeddedAccessSchema {

    /** The username of the built-in administrator. */
    public static final String ADMIN_USERNAME = "admin";

    /** The table whose presence means the schema exists. */
    static final String MARKER_TABLE = "ea_kv";

    private static final Logger log = LoggerFactory.getLogger(EmbeddedAccessSchema.class);

    private static final Map<DbType, String> RESOURCES = Map.of(
            DbType.DERBY, "/embedded-access/derby.sql",
            DbType.POSTGRESQL, "/embedded-access/postgresql.sql");

    private EmbeddedAccessSchema() {
    }

    /**
     * Tells whether embedded Access can run on a database type.
     *
     * @param dbType the database type
     * @return {@code true} for Derby and PostgreSQL
     */
    public static boolean supports(DbType dbType) {
        return RESOURCES.containsKey(dbType);
    }

    /**
     * Creates the tables if absent, then seeds the {@code permissions.last_updated} row and the {@code admin} user
     * if absent. Safe to call on every start.
     *
     * @param dataSource the Artifactory database
     * @param dbType     the configured database type
     * @param clock      the source of the admin account's creation time
     * @return {@code Done}; {@code Refused} for an unsupported database type, a missing DDL resource or a database
     *         failure
     */
    public static EaOutcome<Done> ensure(DataSource dataSource, DbType dbType, Clock clock) {
        String resource = RESOURCES.get(dbType);
        if (resource == null) {
            return EaOutcome.refused("Embedded access supports only Derby and PostgreSQL, but the configured "
                    + "database type is " + dbType.name()
                    + ". Use one of those databases or disable embedded access.");
        }
        try (Connection con = dataSource.getConnection()) {
            if (!DbUtils.tableExists(con.getMetaData(), dbType, MARKER_TABLE)) {
                log.info("*** Creating embedded access schema ***");
                Optional<List<String>> statements = loadStatements(resource);
                if (statements.isEmpty()) {
                    return EaOutcome.refused("Embedded access DDL resource not found at: '" + resource + "'");
                }
                inTransaction(con, () -> {
                    try (Statement st = con.createStatement()) {
                        for (String sql : statements.get()) {
                            st.execute(sql);
                        }
                    }
                });
            }
            inTransaction(con, () -> seed(con, clock));
            return EaOutcome.done();
        } catch (SQLException | IOException e) {
            log.error("Could not create the embedded access schema", e);
            return EaOutcome.refused(EmbeddedAccessStore.STORAGE_ERROR_PREFIX + "embedded access schema: "
                    + e.getMessage());
        }
    }

    private static void seed(Connection con, Clock clock) throws SQLException {
        if (!exists(con, "SELECT 1 FROM ea_kv WHERE k = ?", EmbeddedAccessStore.PERMISSIONS_LAST_UPDATED_KEY)) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO ea_kv (k, revision, v) VALUES (?, ?, ?)")) {
                ps.setString(1, EmbeddedAccessStore.PERMISSIONS_LAST_UPDATED_KEY);
                ps.setLong(2, 1L);
                ps.setString(3, "0");
                ps.executeUpdate();
            }
        }
        if (!exists(con, "SELECT 1 FROM ea_users WHERE username = ?", ADMIN_USERNAME)) {
            long now = clock.millis();
            EaUser admin = EaUser.builder(ADMIN_USERNAME, Instant.ofEpochMilli(now)).admin(true).build();
            JdbcEmbeddedAccessStore.insertUserRow(con, admin);
            log.info("Created the built-in '{}' account without a password; it is unusable until the server is "
                    + "claimed", ADMIN_USERNAME);
        }
    }

    private static boolean exists(Connection con, String sql, String key) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Reads a DDL resource and splits it into statements: comment lines ({@code --}) are dropped and statements
     * are separated by {@code ;}.
     *
     * @param resource the classpath path
     * @return the statements, or empty if the resource does not exist
     * @throws IOException if the resource cannot be read
     */
    static Optional<List<String>> loadStatements(String resource) throws IOException {
        try (InputStream in = EmbeddedAccessSchema.class.getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(splitStatements(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    /**
     * Splits a script into statements; pure.
     *
     * @param script the script text
     * @return the non-blank statements without their terminating {@code ;}
     */
    static List<String> splitStatements(String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\n", -1)) {
            if (!line.strip().startsWith("--")) {
                withoutComments.append(line).append('\n');
            }
        }
        List<String> out = new ArrayList<>();
        for (String part : withoutComments.toString().split(";", -1)) {
            if (!part.isBlank()) {
                out.add(part.strip());
            }
        }
        return List.copyOf(out);
    }

    @FunctionalInterface
    private interface SqlWork {
        void run() throws SQLException;
    }

    private static void inTransaction(Connection con, SqlWork work) throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            work.run();
            con.commit();
        } catch (SQLException | RuntimeException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }
}
