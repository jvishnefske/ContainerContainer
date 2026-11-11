package com.jfrog.build.test.database;

import com.jfrog.build.test.database.containers.TestDatabaseProperties;
import com.jfrog.build.test.database.containers.TestHikariDataSource;
import com.jfrog.build.test.database.derby.TestJdbcHelperProvider;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.util.Map;

import static com.jfrog.build.test.database.containers.TestDatabaseContainerFactory.createDatabaseContainer;
import static java.util.Optional.ofNullable;
import static org.jfrog.storage.DbType.DERBY;
import static org.jfrog.storage.DbType.MARIADB;
import static org.jfrog.storage.DbType.MSSQL;
import static org.jfrog.storage.DbType.MYSQL;
import static org.jfrog.storage.DbType.ORACLE;
import static org.jfrog.storage.DbType.POSTGRESQL;

@Slf4j
public class TestDatabaseProvider {

    private static DbType databaseType;
    private static JdbcDatabaseContainer<?> databaseContainer;
    private static JdbcHelper jdbcHelper;

    public static final Map<DbType, String> DB_VERSIONS = Map.of(
            MYSQL, "5.7.33",
            ORACLE, "11-ee",
            MSSQL, "2017-CU12",
            POSTGRESQL, "13.4",
            MARIADB, "10.6.5"
    );

    public static void initDatabase() {
        databaseType = DbType.parse(System.getProperty("db.type", "derby"));
        log.info("Using database type: {}", databaseType);
        if (databaseType == DERBY) {
            jdbcHelper = TestJdbcHelperProvider.buildTestJdbcHelper();
        } else {
            databaseContainer = createDatabaseContainer(databaseType);
            databaseContainer.start();
            TestDatabaseProperties dbProperties = buildDatabaseProperties();
            jdbcHelper = new JdbcHelper(new TestHikariDataSource(dbProperties));
        }
    }

    @NonNull
    private static TestDatabaseProperties buildDatabaseProperties() {
        return TestDatabaseProperties.builder()
                .driverClassName(databaseContainer.getDriverClassName())
                .password(databaseContainer.getPassword())
                .username(databaseContainer.getUsername())
                .jdbcUrl(databaseContainer.getJdbcUrl())
                .build();
    }

    @SneakyThrows
    public static void stopDatabase() {
        jdbcHelper.close();
        ofNullable(databaseContainer).ifPresent(GenericContainer::stop);
    }

    @NonNull
    public static DbType getDatabaseType() {
        return databaseType;
    }

    @NonNull
    public static JdbcHelper getJdbcHelper() {
        return jdbcHelper;
    }
}
