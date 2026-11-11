/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.storage.db.initializer;

import org.artifactory.common.property.LinkedProperties;
import org.artifactory.storage.db.itest.DbTestUtils;
import org.jfrog.common.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author haims
 */
public class BaseTestDBInitializer implements TestDBInitializer {

    static final String JDBC_PREFIX = "jdbc:";
    public static final String LOCKINGDB_USERNAME = "lockingdb.username";
    public static final String LOCKINGDB_PASS = "lockingdb.password";
    public static final String LOCKINGDB_URL = "lockingdb.url";
    public static final String LOCKINGDB_DRIVER = "lockingdb.driver";
    public static final String LOCKINGDB_TYPE = "lockingdb.type";

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private String dbType;
    private Map<String, String> customDbProps = new HashMap<>();
    private Properties props;
    private boolean forceAnotherDbForLocking = false;

    ITestDbTestUtils dbTestUtils;

    BaseTestDBInitializer(String dbType) {
        updateDbName(dbType);
        dbTestUtils = new ITestDbTestUtils();
    }

    public void setDbName(String name) {
        updateDbName(name);
    }

    @Override
    public void setCustomDbProps(Map<String, String> customDbProps) {
        this.customDbProps = customDbProps == null ? new HashMap<>() : new HashMap<>(customDbProps);
    }

    private void updateDbName(String dbType) {
        this.dbType = dbType;
        try {
            props = getDefaultDBProperties();
            for (var customDbProp : customDbProps.entrySet()) {
                props.setProperty(customDbProp.getKey(), customDbProp.getValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Properties setup(String dbSchemaName) {
        return this.getProps();
    }

    private Properties getDefaultDBProperties() throws IOException {
        String dbPropertiesFile;
        if (DbTestUtils.isOracleTwentyThreeVersion(dbType)) {
            dbPropertiesFile = "/db/oracle23/" + dbType + ".properties";
        } else {
            dbPropertiesFile = "/db/" + dbType + ".properties";
        }
        log.debug("fetching properties from: {}", dbPropertiesFile);
        LinkedProperties dbProps = new LinkedProperties();
        dbProps.load(ResourceUtils.getResource(dbPropertiesFile));
        return dbProps;
    }

    @Override
    public Properties getProps() {
        return (Properties) props.clone();
    }

    @Override
    public void setForceAnotherDbForLocking(boolean forceAnotherDbForLocking) {
        this.forceAnotherDbForLocking = forceAnotherDbForLocking;
    }

    @Override
    public boolean isForceAnotherDbForLocking() {
        return forceAnotherDbForLocking;
    }

}
