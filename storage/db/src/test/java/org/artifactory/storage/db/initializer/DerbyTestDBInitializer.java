/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.storage.db.initializer;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.artifactory.storage.db.initializer.ITestDbTestUtils.LOCK_DB_PREFIX;

/**
 * @author haims
 */
public class DerbyTestDBInitializer extends BaseTestDBInitializer {

    private static final String IN_MEMORY_DB_URL = "jdbc:derby:memory:myDB;create=true";

    DerbyTestDBInitializer() {
        super("derby");
    }

    @Override
    public Properties setup(String dbSchemaName) {
        Properties updatedProps = getProps();
        prepareDbAndSchemaForAdditionalLockingDb(dbSchemaName, updatedProps, isForceAnotherDbForLocking());
        setLocksWaitTimeout();
        return updatedProps;
    }

    private void prepareDbAndSchemaForAdditionalLockingDb(String dbSchemaName, Properties updatedProps,
            boolean forceAnotherDbForLocking) {
        if (forceAnotherDbForLocking) {
            log.info("Additional DB should be created for locking");
            String lockingDbUser = LOCK_DB_PREFIX + dbSchemaName;
            String lockingDbPass = LOCK_DB_PREFIX + "pass";
            updatedProps.setProperty(LOCKINGDB_TYPE, updatedProps.getProperty("type"));
            updatedProps.setProperty(LOCKINGDB_URL, updatedProps.getProperty("url"));
            updatedProps.setProperty(LOCKINGDB_DRIVER, updatedProps.getProperty("driver"));
            updatedProps.setProperty(LOCKINGDB_USERNAME, lockingDbUser);
            updatedProps.setProperty(LOCKINGDB_PASS, lockingDbPass);
        }
    }

    private void setLocksWaitTimeout() {
        try (Connection connection = DriverManager.
                getConnection(IN_MEMORY_DB_URL); Statement statement = connection.createStatement()) {
            int result = statement.executeUpdate(
                    "CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.locks.waitTimeout', '120')");
            log.info("Derby locks wait timeout increased to 120 seconds, result: {}", result);
        } catch (SQLException e) {
            log.error("Failed to increase derby locks wait timeout", e);
        }
    }
}
