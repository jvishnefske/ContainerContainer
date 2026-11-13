/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Test fixture: a fresh in-memory Derby database for one test, with a {@link DriverManager} data source, dropped on
 * close.
 */
public final class DerbyMemoryDatabase implements AutoCloseable {

    private final String url;
    private final DataSource dataSource;

    /**
     * Names a new database; Derby creates it on the first connection.
     *
     * @param prefix a readable prefix of the random database name
     */
    public DerbyMemoryDatabase(String prefix) {
        this.url = "jdbc:derby:memory:" + prefix + "-" + UUID.randomUUID();
        String create = url + ";create=true";
        this.dataSource = new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(create);
            }

            @Override
            public Connection getConnection(String u, String p) throws SQLException {
                return DriverManager.getConnection(create, u, p);
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                throw new SQLFeatureNotSupportedException();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                throw new SQLException("not a wrapper");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }

    /**
     * Returns the data source.
     *
     * @return a data source opening a new connection per call
     */
    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        try {
            DriverManager.getConnection(url + ";drop=true").close();
        } catch (SQLException expected) {
            // Derby reports a successful drop as SQLState 08006.
        }
    }
}
