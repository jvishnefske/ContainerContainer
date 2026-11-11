/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.storage.db.initializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author haims
 */
public class MsSqlTestDBInitializer extends BaseTestDBInitializer {

    private static final String URL_KEY = "url";
    private static final String MSSQL = "mssql";
    private static final String MSSQL_PREFIX = JDBC_PREFIX + "sqlserver://";
    private static final String DATABASE_PARAM = "databaseName";

    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    MsSqlTestDBInitializer() {
        super(MSSQL);
    }

    @Override
    public Properties setup(String dbSchemaName) {
        return prepareDbAndSchema(dbSchemaName);
    }

    private Properties prepareDbAndSchema(String dbSchemaName) {
        Properties updatedProps = getProps();
        dbTestUtils.prepareDbAndSchema(updatedProps, dbSchemaName, MSSQL);
        String newURL = getMsSqlUrl(dbSchemaName, updatedProps);
        updatedProps.setProperty(URL_KEY, newURL);
        return updatedProps;
    }

    private String getMsSqlUrl(String dbSchemaName, Properties props) {
        String jdbcUrl = props.getProperty(URL_KEY);
        String[] urlParts = jdbcUrl.split(MSSQL_PREFIX);
        String schemaSpecificPart = urlParts[1];
        String[] connectionDetails = schemaSpecificPart.split(";");

        boolean assignedDBName = false;
        String updatedDatabaseParam = DATABASE_PARAM + "=" + dbSchemaName;
        List<String> updatedParams = new ArrayList<>();
        for (String param : connectionDetails) {
            log.info(this.getClass().getSimpleName() + " Attempting to process " + param);
            String newDatabaseParam = param;
            String[] paramDetails = param.split("=");
            if (DATABASE_PARAM.equals(paramDetails[0])) {
                log.info(this.getClass().getSimpleName() + " found  " + DATABASE_PARAM + " of " + paramDetails[1] +
                        ". Replacing with: " + updatedDatabaseParam);
                newDatabaseParam = updatedDatabaseParam;
                assignedDBName = true;
            }
            updatedParams.add(newDatabaseParam);
        }

        if (!assignedDBName) {
            updatedParams.add(updatedDatabaseParam);
        }
        final String[] updatedConnectionString = {MSSQL_PREFIX};

        updatedParams.stream().forEach(param -> updatedConnectionString[0] += param + ";");
        log.info(this.getClass().getSimpleName() + " final modified Connection URL: " + updatedConnectionString[0]);

        return updatedConnectionString[0];
    }

    @Test
    public void testUpdateMsSqlUrl() {
        String mySchema = "mySchema";

        Properties props = new Properties();
        String origUrl =
                "jdbc:sqlserver://localhost:1433;databaseName=artifactory5test;sendStringParametersAsUnicode=false;" +
                        "applicationName=Artifactory Binary Repository;lockTimeout=5000;";
        String expectedUrl =
                "jdbc:sqlserver://localhost:1433;databaseName=mySchema;sendStringParametersAsUnicode=false;" +
                        "applicationName=Artifactory Binary Repository;lockTimeout=5000;";
        props.setProperty(URL_KEY, origUrl);


        String uri = getMsSqlUrl(mySchema, props);
        log.info("Calculated url: " + uri);

        Assert.assertEquals(uri, expectedUrl);
    }
}
