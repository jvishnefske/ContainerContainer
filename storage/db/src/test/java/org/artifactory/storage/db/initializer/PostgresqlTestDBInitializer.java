/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.storage.db.initializer;

import java.util.Properties;

/**
 * @author haims
 */
public class PostgresqlTestDBInitializer extends BaseTestDBInitializer {

    public static final String URL = "url";
    private static final String POSTGRESQL = "postgresql";

    PostgresqlTestDBInitializer() {
        super(POSTGRESQL);
    }

    @Override
    public Properties setup(String dbSchemaName) {
        return prepareDbAndSchema(dbSchemaName);

    }

    private Properties prepareDbAndSchema(String dbSchemaName) {
        Properties updatedProps = getProps();
        dbTestUtils.prepareDbAndSchema(updatedProps, dbSchemaName, POSTGRESQL);
        String newURL = updatedProps.getProperty(URL) + "?currentSchema=" + dbSchemaName;
        updatedProps.setProperty(URL, newURL);
        return updatedProps;
    }
}
