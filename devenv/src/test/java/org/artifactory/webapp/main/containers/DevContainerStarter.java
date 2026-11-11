package org.artifactory.webapp.main.containers;


import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.storage.DbType;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import static java.lang.Boolean.parseBoolean;
import static java.util.Optional.ofNullable;


/**
 * @author William Arustamyan
 */
@Slf4j
@UtilityClass
public final class DevContainerStarter {

    private static final Map<DbType, Consumer<File>> registry;

    static {
        registry = new EnumMap<>(DbType.class);
        registry.put(DbType.POSTGRESQL, DevContainerStarter::postgresContainer);
        registry.put(DbType.MYSQL, DevContainerStarter::mysqlContainer);
        registry.put(DbType.MARIADB, DevContainerStarter::mariadbContainer);
    }

    public static void startContainer(final File artHome, final String strDbType) {
        log.info("Starting docker container build process for db type {}", strDbType);

        final DbType type = DbType.parse(strDbType);

        if (type == DbType.DERBY) {
            log.debug("No need to start docker container for derby. Returning");
            return;
        }

        log.debug("Resolved database type is : {}", type);

        ofNullable(registry.get(type))
                .orElseThrow(() -> new IllegalArgumentException(String.format("Unsupported db type %s", strDbType)))
                .accept(DatabaseDataHome.build(artHome, type));
    }

    private static void postgresContainer(final File dataDir) {
        log.debug("Start building postgres container ...");
        String version = ofNullable(System.getenv("postgresql.version")).orElse("17.9");
        DockerImageName postgres = DockerImageName.parse("docker.jfrog.io/library/postgres:" + version)
                .asCompatibleSubstituteFor("postgres");
        JdbcDatabaseContainer<?> pgContainer = new PostgreSQLContainer<>(postgres)
                .withDatabaseName("artdb")
                .withUsername("artifactory")
                .withPassword("password")
                .withReuse(parseBoolean(ofNullable(System.getenv("reuse.db.container")).orElse("false")))
                .withCreateContainerCmdModifier(
                        cmd -> cmd.withHostConfig(
                                hostConfig(5432, dataDir.getAbsolutePath(), "/var/lib/postgresql/data")))
                .waitingFor(new CompositeWaitStrategy());
        pgContainer.start();
    }

    private static void mysqlContainer(final File dataDir) {
        log.debug("Start building mysql container ...");
        String version = ofNullable(System.getenv("mysql.version")).orElse("8.0.45");
        DockerImageName mysql = DockerImageName.parse("docker.jfrog.io/library/mysql:" + version)
                .asCompatibleSubstituteFor("mysql");
        JdbcDatabaseContainer<?> mysqlContainer = new MySQLContainer<>(mysql)
                .withDatabaseName("artdb")
                .withUsername("artifactory")
                .withPassword("password")
                .withReuse(parseBoolean(ofNullable(System.getenv("reuse.db.container")).orElse("false")))
                .withCreateContainerCmdModifier(
                        cmd -> cmd.withHostConfig(
                                hostConfig(3306, dataDir.getAbsolutePath(), "/var/lib/mysql")
                        )
                );
        mysqlContainer.start();
    }

    private static void mariadbContainer(final File dataDir) {
        log.debug("Start building mariadb container ...");
        String version = ofNullable(System.getenv("mariadb.version")).orElse("10.6.25");
        DockerImageName mariadb = DockerImageName.parse("docker.jfrog.io/library/mariadb:" + version)
                .asCompatibleSubstituteFor("mariadb");
        JdbcDatabaseContainer<?> mariadbContainer = new MariaDBContainer<>(mariadb)
                .withDatabaseName("artdb")
                .withUsername("artifactory")
                .withPassword("password")
                .withReuse(parseBoolean(ofNullable(System.getenv("reuse.db.container")).orElse("false")))
                .withCreateContainerCmdModifier(
                        cmd -> cmd.withHostConfig(
                                hostConfig(3306, dataDir.getAbsolutePath(), "/var/lib/mariadb")
                        )
                );
        mariadbContainer.start();
    }

    private static HostConfig hostConfig(final int port, final String localDataDir, final String containerDataDir) {
        return new HostConfig()
                .withPortBindings(new PortBinding(Ports.Binding.bindPort(port), new ExposedPort(port)))
                .withBinds(Bind.parse(String.format("%s:%s", localDataDir, containerDataDir)));
    }

    private static class CompositeWaitStrategy extends AbstractWaitStrategy {
        @Override
        protected void waitUntilReady() {
            WaitStrategy logWaitStrategy = new LogMessageWaitStrategy()
                    .withRegEx(".*database system is ready to accept connections.*\\s")
                    .withTimes(1)
                    .withStartupTimeout(Duration.of(120L, ChronoUnit.SECONDS));

            WaitStrategy portWaitStrategy = new HostPortWaitStrategy()
                    .withStartupTimeout(Duration.of(30L, ChronoUnit.SECONDS));

            logWaitStrategy.waitUntilReady(waitStrategyTarget);
            portWaitStrategy.waitUntilReady(waitStrategyTarget);
        }
    }
}