package com.jfrog.build.test.database.derby;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.derby.jdbc.EmbeddedDriver;
import org.jfrog.storage.JFrogDataSource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * An abstraction for JFrog-specific in-memory database.
 * No persistence layer involved, use only for unit tests.
 *
 * @see TestJdbcHelperProvider for the factory method.
 */
@Slf4j
public class TestJFrogDataSource implements JFrogDataSource {

    private final EmbeddedDriver derbyEmbeddedDriver;

    private PrintWriter logWriter;
    private int loginTimeout;

    public TestJFrogDataSource(@NonNull EmbeddedDriver derbyEmbeddedDriver) {
        this.derbyEmbeddedDriver = derbyEmbeddedDriver;
    }

    @Override
    public void close() {
        log.trace("Close test-JFrog-data-source");
        try {
            derbyEmbeddedDriver.connect("jdbc:derby:memory:artifactory_database;drop=true", new Properties());
        } catch (SQLException e) {
            log.debug("Close test-JFrog-data-source: already dropped");
        }
    }

    @NonNull
    @Override
    public Connection getConnection() throws SQLException {
        log.trace("Get connection from test-JFrog-data-source");
        return derbyEmbeddedDriver.connect("jdbc:derby:memory:artifactory_database;create=true", new Properties());
    }

    @NonNull
    @Override
    public Connection getConnection(@NonNull String username, @NonNull String password) throws SQLException {
        log.trace("Get connection with credentials from test-JFrog-data-source");
        return derbyEmbeddedDriver.connect("jdbc:derby:memory:artifactory_database;create=true", new Properties());
    }

    @Nullable
    @Override
    public PrintWriter getLogWriter() {
        log.trace("Get test-JFrog-data-source log writer");
        return logWriter;
    }

    @Override
    public void setLogWriter(@NonNull PrintWriter logWriter) {
        log.trace("Set test-JFrog-data-source log writer");
        this.logWriter = logWriter;
    }

    @Override
    public void setLoginTimeout(int loginTimeout) {
        log.trace("Set test-JFrog-data-source login timeout={}", loginTimeout);
        this.loginTimeout = loginTimeout;
    }

    @Override
    public int getLoginTimeout() {
        log.trace("Get test-JFrog-data-source login timeout");
        return loginTimeout;
    }

    @Nullable
    @Override
    public <T> T unwrap(@NonNull Class<T> clazz) {
        log.trace("Unwrap test-JFrog-data-source for class={}", clazz.getName());
        return null;
    }

    @Override
    public boolean isWrapperFor(@NonNull Class<?> clazz) {
        log.trace("Check test-JFrog-data-source wrapping for class={}", clazz.getName());
        return false;
    }

    @Nullable
    @Override
    public Logger getParentLogger() {
        log.trace("Get test-JFrog-data-source parent logger");
        return null;
    }

    @Nullable
    @Override
    public String getPoolName() {
        log.trace("Get test-JFrog-data-source pool name");
        return null;
    }
}
