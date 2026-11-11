package org.artifactory.storage.db.itest.spring;

import com.fasterxml.jackson.databind.JsonNode;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.jfrog.common.YamlUtils;
import org.jfrog.storage.DbType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Overrides the basic DbTestConfigFactory when spring profile is: with-external-locking-db
 * If the main DB is PostgreSql it is routing the db config file name to a db config file
 * that contains locking db properties.
 *
 * Also, create and drop external_locks schema in the initialization and destroy of the bean.
 *
 * @author shaharz
 */

@Configuration
@Profile("with-external-locking-db")
public class DbTestConfigFactoryWithExternalLockingDB extends DbTestConfigFactory {

    public static final String WITH_EXTERNAL_LOCKING_DB = "-with-external-locking-db";
    @Autowired
    private ArtifactoryDbProperties dbProperties;


    /**
     * If locking postgress locking db is enabled:
     * Drop and Create Locking DB schema
     *
     * @throws Exception
     */
    @PostConstruct
    private void init() throws Exception {
        if (dbProperties.getLockingDbSpecificType().isPresent() &&
                dbProperties.getLockingDbSpecificType().get().equals(DbType.POSTGRESQL)) {
            try (Connection connection = DriverManager
                    .getConnection(dbProperties.getConnectionUrl(),
                            dbProperties.getLockingDbUsername(),
                            dbProperties.getLockingDbPassword())) {
                Statement stmt = connection.createStatement();
                stmt.execute("DROP SCHEMA IF EXISTS external_locks CASCADE");
                stmt.execute("CREATE SCHEMA external_locks");
            }
        }
    }


    /**
     * If locking postgress locking db is enabled:
     * Drop Locking DB schema
     *
     * @throws Exception
     */
    @PreDestroy
    private void destroy() throws Exception {
        if (dbProperties.getLockingDbSpecificType().isPresent() &&
                dbProperties.getLockingDbSpecificType().get().equals(DbType.POSTGRESQL)) {
            try (Connection connection = DriverManager
                    .getConnection(dbProperties.getConnectionUrl(),
                            dbProperties.getLockingDbUsername(),
                            dbProperties.getLockingDbPassword())) {
                Statement stmt = connection.createStatement();
                stmt.execute("DROP SCHEMA IF EXISTS external_locks CASCADE");
            }
        }
    }


    /**
     * Return with-external-locking-db db config, only if the type of the db in the original
     * config file is postgresql
     */
    @Override
    protected File getDbPropsFileByDbConfigName(String dbConfigName) {
        File dbPropsFile = super.getDbPropsFileByDbConfigName(dbConfigName);
        JsonNode jsonNode = YamlUtils.getInstance().readTree(dbPropsFile);
        JsonNode dbType = jsonNode.get("artifactory").get("database").get("type");
        if (DbType.POSTGRESQL.equals(DbType.parse(dbType.asText()))) {
            dbPropsFile = super.getDbPropsFileByDbConfigName(dbConfigName + WITH_EXTERNAL_LOCKING_DB);
        }
        return dbPropsFile;
    }
}
