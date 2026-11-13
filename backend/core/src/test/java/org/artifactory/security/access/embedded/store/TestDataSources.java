/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.store;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Test fixtures: a {@link DataSource} over {@link DriverManager} and a settable {@link Clock}. */
final class TestDataSources {

    private TestDataSources() {
    }

    /** A data source that opens a new {@link DriverManager} connection per call. */
    static DataSource driverManager(String url, String user, String password) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user,
                        password);
            }

            @Override
            public Connection getConnection(String u, String p) throws SQLException {
                return DriverManager.getConnection(url, u, p);
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

    /** A clock whose instant the test sets; reads go through a supplier so a test can make them fail. */
    static final class TestClock extends Clock {
        private volatile Supplier<Instant> source;

        TestClock(Instant start) {
            set(start);
        }

        void set(Instant at) {
            this.source = () -> at;
        }

        void failWith(RuntimeException e) {
            this.source = () -> {
                throw e;
            };
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return source.get();
        }
    }
}
