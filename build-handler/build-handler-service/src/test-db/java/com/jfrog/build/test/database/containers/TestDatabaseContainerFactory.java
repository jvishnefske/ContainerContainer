package com.jfrog.build.test.database.containers;

import lombok.NonNull;
import org.jfrog.storage.DbType;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static com.jfrog.build.test.database.TestDatabaseProvider.DB_VERSIONS;
import static org.jfrog.storage.DbType.MSSQL;
import static org.jfrog.storage.DbType.ORACLE;

public class TestDatabaseContainerFactory {

    @NonNull
    public static JdbcDatabaseContainer<?> createDatabaseContainer(@NonNull DbType type) {
        return switch (type) {
            case MSSQL -> createMssqlDatabaseContainer();
            case ORACLE -> createOracleDatabaseContainer();
            case MYSQL -> new MySQLContainer("mysql:" + DB_VERSIONS.get(type));
            case MARIADB -> new MariaDBContainer("mariadb:" + DB_VERSIONS.get(type));
            case POSTGRESQL -> new PostgreSQLContainer("postgres:" + DB_VERSIONS.get(type));
            default -> throw new UnsupportedOperationException("DB container is not supported for the type=" + type);
        };
    }

    @NonNull
    private static JdbcDatabaseContainer<?> createMssqlDatabaseContainer() {
        DockerImageName imageName = DockerImageName.parse("mcr.microsoft.com/mssql/server:" + DB_VERSIONS.get(MSSQL));
        return new MSSQLServerContainer(imageName).acceptLicense();
    }

    @NonNull
    private static JdbcDatabaseContainer<?> createOracleDatabaseContainer() {
        OracleContainer container = buildOracleDatabaseContainer();
        container.setWaitStrategy(buildOracleDatabaseWaitStrategy());
        container.usingSid().withUsername("sys as sysdba").withPassword("oracle");
        return container;
    }

    @NonNull
    private static OracleContainer buildOracleDatabaseContainer() {
        return new OracleContainer(parseDockerImageNameForOracle()) {

            @Override
            public String getSid() {
                return "ee";
            }
        };
    }

    @NonNull
    private static DockerImageName parseDockerImageNameForOracle() {
        return DockerImageName.parse("entplus.jfrog.io/common-docker-local/jfrog/artifactory-pro/oracle:" +
                        DB_VERSIONS.get(ORACLE))
                .asCompatibleSubstituteFor("gvenzl/oracle-xe");
    }

    @NonNull
    private static WaitStrategy buildOracleDatabaseWaitStrategy() {
        return new LogMessageWaitStrategy()
                .withRegEx(".*Database ready to use.*\\s").withTimes(1)
                .withStartupTimeout(Duration.of(240L, ChronoUnit.SECONDS));
    }
}