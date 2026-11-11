/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.storage.db.initializer;

import org.apache.http.client.utils.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author haims
 */
public class MySqlTestDBInitializer extends BaseTestDBInitializer {

    private static final String MYSQL = "mysql";
    public static final String URL = "url";

    MySqlTestDBInitializer() {
        super(MYSQL);
    }

    @Override
    public Properties setup(String dbSchemaName) {
        try {
            return prepareDbAndSchema(dbSchemaName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Properties prepareDbAndSchema(String dbSchemaName) throws URISyntaxException {
        Properties updatedProps = getProps();
        dbTestUtils.prepareDbAndSchema(updatedProps, dbSchemaName, MYSQL);
        URI newURI = getMySqlSchemaUri(dbSchemaName, updatedProps);
        updatedProps.setProperty(URL, newURI.toString());
        return updatedProps;
    }

    private URI getMySqlSchemaUri(String dbSchemaName, Properties updatedProps) throws URISyntaxException {
        String jdbcUrl = updatedProps.getProperty(URL);
        String[] urlParts = jdbcUrl.split(JDBC_PREFIX);
        String schemaSpecificPart = urlParts[1];
        URI dbUri = new URI(schemaSpecificPart);
        return new URIBuilder()
                .setScheme(JDBC_PREFIX + dbUri.getScheme())
                .setHost(dbUri.getHost())
                .setPort(dbUri.getPort())
                .setPath("/" + dbSchemaName)
                .setCustomQuery(dbUri.getQuery())
                .build();
    }

    @Test
    public void getMySqlSchemaUriTest() throws URISyntaxException {
        String mySchema = "mySchema";

        Properties props = new Properties();
        String origUrl =
                "jdbc:mysql://localhost:3306/artifactory5test?characterEncoding=UTF-8&elideSetAutoCommits=true";
        String expectedUrl =
                "jdbc:mysql://localhost:3306/" + mySchema + "?characterEncoding=UTF-8&elideSetAutoCommits=true";
        props.setProperty(URL, origUrl);


        URI uri = getMySqlSchemaUri(mySchema, props);
        log.info("Calculated url: " + uri);
        String updatedURL = uri.toString();

        Assert.assertEquals(updatedURL, expectedUrl);
    }

}
