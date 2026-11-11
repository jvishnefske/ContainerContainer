/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.storage.db.initializer;

import java.util.Properties;

import static org.artifactory.storage.db.initializer.ITestDbTestUtils.LOCK_DB_PREFIX;

/**
 * @author haims
 */
public class OracleTestDBInitializer extends BaseTestDBInitializer {

    private static final String USERNAME = "username";
    public static final String URL = "url";
    private static final String ORACLE = "oracle";

    OracleTestDBInitializer() {
        super(ORACLE);
    }

    @Override
    public Properties setup(String dbSchemaName) {
        return prepareDbAndSchema(dbSchemaName);
    }

    private Properties prepareDbAndSchema(String dbSchemaName) {
        Properties updatedProps = getProps();
        dbTestUtils.prepareDbAndSchema(updatedProps, dbSchemaName, ORACLE);
        prepareDbAndSchemaForAdditionalLockingDb(dbSchemaName, updatedProps, isForceAnotherDbForLocking());
        updatedProps.setProperty(USERNAME, dbSchemaName);
        return updatedProps;
    }

    private void prepareDbAndSchemaForAdditionalLockingDb(String dbSchemaName, Properties updatedProps,
            boolean forceAnotherDbForLocking) {
        if (forceAnotherDbForLocking) {
            log.info("Additional DB should be created for locking");
            String lockingDbUser = LOCK_DB_PREFIX + dbSchemaName;
            String lockingDbPass = LOCK_DB_PREFIX + "pass";
            dbTestUtils.prepareDbAndSchema(updatedProps, ORACLE, lockingDbUser, lockingDbPass);
            updatedProps.setProperty(LOCKINGDB_TYPE, updatedProps.getProperty("type"));
            updatedProps.setProperty(LOCKINGDB_URL, updatedProps.getProperty("url"));
            updatedProps.setProperty(LOCKINGDB_DRIVER, updatedProps.getProperty("driver"));
            updatedProps.setProperty(LOCKINGDB_USERNAME, lockingDbUser);
            updatedProps.setProperty(LOCKINGDB_PASS, lockingDbPass);
        }
    }
}
