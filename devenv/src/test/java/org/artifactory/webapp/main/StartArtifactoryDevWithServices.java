package org.artifactory.webapp.main;

import com.jfrog.sysconf.test.SystemYamlBuilder;
import com.jfrog.sysconf.test.YamlDbConfig;
import com.jfrog.sysconf.test.YamlNodeProps;
import com.jfrog.sysconf.test.YamlRouterEntrypoints;
import com.jfrog.sysconf.test.YamlServiceSysConfig;
import com.jfrog.sysconf.test.YamlSysConfig;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.db.DbTypePropertyResolver;
import org.artifactory.webapp.WebappUtils;
import org.artifactory.webapp.main.containers.DevContainerStarter;
import org.jfrog.common.YamlUtils;
import org.jfrog.storage.DbType;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;
import static org.artifactory.webapp.WebappUtils.copyLicenseIfMissing;
import static org.artifactory.webapp.WebappUtils.copyNewerDevResources;
import static org.artifactory.webapp.WebappUtils.updateMimetypes;
import static org.artifactory.webapp.main.StartArtifactoryDev.getArtifactoryDevHome;
import static org.artifactory.webapp.main.StartArtifactoryDev.setSystemProperties;
import static org.artifactory.webapp.main.StartArtifactoryDev.startAccessProcess;
import static org.artifactory.webapp.main.StartArtifactoryDev.startEventServerProcess;
import static org.artifactory.webapp.main.StartArtifactoryDev.startFrontendProcess;
import static org.artifactory.webapp.main.StartArtifactoryDev.startJFConnectProcess;
import static org.artifactory.webapp.main.StartArtifactoryDev.startMetadataProcess;
import static org.artifactory.webapp.main.StartArtifactoryDev.startObservabilityProcess;
import static org.artifactory.webapp.main.StartArtifactoryDev.startRouterProcess;
import static org.artifactory.webapp.main.StartArtifactoryDev.startTomcatWithRouter;
import static org.jfrog.config.home.JFrogHome.DATA_FOLDER;
import static org.jfrog.config.home.JFrogHome.ETC_FOLDER;
import static org.jfrog.config.home.JFrogHome.VAR_FOLDER;
import static org.jfrog.storage.DbType.POSTGRESQL;

/**
 * Starts an Artifactory instance together with the platform companion processes (metadata, frontend, access, router).
 * Contains the shared bootstrap logic reused by {@code StartArtifactoryProWithServices} and other devenv entry points.
 *
 * @author Tamir Hadad
 */
@Slf4j
public class StartArtifactoryDevWithServices {

    static final int ROUTER_INTERNAL_PORT = 8046;

    public static void main(String... args) throws Exception {
        File devArtHome = getArtifactoryDevHome(args);
        bootstrapDevHome(devArtHome, false);

        if (checkEnvironmentFlag("START_DB_CONTAINER", true)) {
            startDBContainer(devArtHome, DbTypePropertyResolver.resolveOrDefault(POSTGRESQL).toString());
        }

        List<Runnable> processes = gatherDefaultProcesses(devArtHome);

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(processes.size(), 1));
        List<? extends Future<?>> resultList = processes.stream().map(executor::submit).toList();

        if (checkEnvironmentFlag("JF_ACCESS_ENABLED", true) &&
                checkEnvironmentFlag("JF_ROUTER_ENABLED", true)) {
            AccessProcess accessProcess = startAccessProcess(8040, false, devArtHome, ROUTER_INTERNAL_PORT);
            startRouterProcess(accessProcess, devArtHome);
        }

        Tomcat tomcat = startTomcatWithRouter(devArtHome);
        for (Future<?> future : resultList) {
            future.get();
        }
        executor.shutdown();
        tomcat.getServer().await();
    }

    /**
     * @param useExternalHostAddress when {@code true} the node ip is set to the machine's external address
     *                               (needed for Xray integration).
     */
    static void bootstrapDevHome(File devArtHome, boolean useExternalHostAddress) throws IOException {
        File devEtcDir = WebappUtils.getDevEtcFolder();
        setSystemProperties(devArtHome);
        updateMimetypes(devEtcDir);
        copyNewerDevResources(devEtcDir, devArtHome, true);
        if (!copyOverrideSystemYamlIfNeeded(devArtHome)) {
            createDevSystemYaml(devArtHome, ROUTER_INTERNAL_PORT, useExternalHostAddress);
        }
        copyLicenseIfMissing(devArtHome);
        copyBinarystoreIfNeeded(devArtHome);
    }

    private static List<Runnable> gatherDefaultProcesses(File devArtHome) {
        List<Runnable> processes = new ArrayList<>();
        if (checkEnvironmentFlag("JF_FRONTEND_ENABLED", true)) {
            processes.add(() -> startFrontendProcess(devArtHome));
        }
        if (checkEnvironmentFlag("JF_EVENT_ENABLED", false)) {
            processes.add(() -> startEventServerProcess(devArtHome));
        }
        if (checkEnvironmentFlag("JF_OBSERVABILITY_ENABLED", false)) {
            processes.add(() -> startObservabilityProcess(devArtHome));
        }
        if (checkEnvironmentFlag("JF_JFCONNECT_ENABLED", false)) {
            processes.add(() -> startJFConnectProcess(devArtHome));
        }
        if (checkEnvironmentFlag("JF_METADATA_ENABLED", true)) {
            processes.add(() -> startMetadataProcess(devArtHome));
        }
        return processes;
    }

    public static boolean checkEnvironmentFlag(String environmentVariableName, boolean defaultValue) {
        String value = System.getenv(environmentVariableName);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    static void startDBContainer(File productHome, String dbType) {
        log.info("{} DB docker container is warming up ....", dbType);
        DevContainerStarter.startContainer(productHome, dbType);
    }

    static void copyBinarystoreIfNeeded(File devArtHome) {
        File binarystore = new File(
                Paths.get(devArtHome.getAbsolutePath(), VAR_FOLDER, ETC_FOLDER, "artifactory", "binarystore.xml")
                        .toUri());
        String overrideBinstore = System.getenv("override.binarystore");
        try {
            if (!binarystore.exists() && StringUtils.isNotBlank(overrideBinstore)) {
                File binaryStoreFile = new File(overrideBinstore);
                if (binaryStoreFile.exists()) {
                    final String username = System.getProperty("user.name");
                    if (isBlank(username)) {
                        log.warn("System username is empty 'user.name' will use default binarystore");
                        return;
                    }
                    final String binaryStoreAsString =
                            FileUtils.readFileToString(binaryStoreFile, UTF_8)
                                    .replace("{uuid}", username);
                    FileUtils.writeStringToFile(binarystore, binaryStoreAsString, UTF_8);
                } else {
                    log.warn("Couldn't find requested file {}, build again with -Pdev", binarystore);
                }
            }
        } catch (IOException e) {
            log.error("Failed to copy binarystore: \"{}\" {}", overrideBinstore, e.getMessage(), e);
        }
    }

    @SneakyThrows
    static boolean copyOverrideSystemYamlIfNeeded(File devArtHome) {
        String systemYamlOverridePath = SystemYamlOverridePropertyResolver.resolve();
        if (StringUtils.isBlank(systemYamlOverridePath)) {
            return false;
        }
        String yamlOverrideContent = FileUtils.readFileToString(new File(systemYamlOverridePath), UTF_8);
        File systemYamlFile = StartArtifactoryDev.getSystemYamlPath(devArtHome);
        FileUtils.writeStringToFile(systemYamlFile, yamlOverrideContent, UTF_8, false);
        return true;
    }

    static void createDevSystemYaml(File home, int routerPort, boolean useExternalHostAddress) {
        log.info("Creating system.yaml ....");
        File systemYaml = StartArtifactoryDev.getSystemYamlPath(home);
        try {
            YamlServiceSysConfig.Builder shared = YamlServiceSysConfig.builder();
            YamlServiceSysConfig.Builder artifactory = YamlServiceSysConfig.builder();
            YamlServiceSysConfig.Builder access = YamlServiceSysConfig.builder();
            YamlServiceSysConfig.Builder jfConnect = YamlServiceSysConfig.builder();
            YamlServiceSysConfig.Builder mc = YamlServiceSysConfig.builder();
            YamlServiceSysConfig.Builder federation = YamlServiceSysConfig.builder();

            if (systemYaml.exists()) {
                YamlSysConfig yamlSysConfig = YamlUtils.getInstance().readValue(systemYaml, YamlSysConfig.class);
                if (yamlSysConfig.getShared() != null) {
                    shared = yamlSysConfig.getShared().toBuilder();
                }
                if (yamlSysConfig.getArtifactory() != null) {
                    artifactory = yamlSysConfig.getArtifactory().toBuilder();
                }
                if (yamlSysConfig.getAccess() != null) {
                    access = yamlSysConfig.getAccess().toBuilder();
                }
                if (yamlSysConfig.getRtfs() != null) {
                    federation = yamlSysConfig.getRtfs().toBuilder();
                }
                if (yamlSysConfig.getJfconnect() != null) {
                    jfConnect = yamlSysConfig.getJfconnect().toBuilder();
                }
                if (yamlSysConfig.getMc() != null) {
                    mc = yamlSysConfig.getMc().toBuilder();
                }
            } else {
                shared = YamlServiceSysConfig.builder()
                        .node(YamlNodeProps.builder()
                                .id(UUID.randomUUID().toString())
                                .haEnabled(Boolean.parseBoolean(System.getenv("force.ha")))
                                .build());
                final YamlDbConfig dbCfg = getArtYamlDbConfig(home);
                if (DbType.parse(dbCfg.getType()) == DbType.DERBY) {
                    artifactory.database(dbCfg);
                } else {
                    shared.database(dbCfg);
                }
            }

            if (useExternalHostAddress) {
                shared.node(shared.build().getNode().toBuilder().ip(getLocalHostAddress()).build());
            } else {
                // Regarding issues with firewall on macOS 15 - it should be set to localhost
                shared.node(shared.build().getNode().toBuilder().ip("localhost").build());
            }

            jfConnect.enabled(checkEnvironmentFlag("JF_JFCONNECT_ENABLED", false));
            YamlServiceSysConfig router = YamlServiceSysConfig.builder()
                    .enabled(true)
                    .entrypoints(YamlRouterEntrypoints.builder().internalPort(routerPort).build())
                    .build();
            YamlSysConfig systemYamlConfig = YamlSysConfig.builder()
                    .shared(shared.build())
                    .artifactory(artifactory.build())
                    .access(access.build())
                    .jfconnect(jfConnect.build())
                    .mc(mc.build())
                    .router(router)
                    .rtfs(federation.build())
                    .build();
            FileUtils.deleteQuietly(systemYaml);
            FileUtils.writeStringToFile(systemYaml, SystemYamlBuilder.toYaml(systemYamlConfig), UTF_8, false);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create/append to system.yaml.", e);
        }
    }

    static YamlDbConfig getArtYamlDbConfig(final File artHome) {
        final DbType dbType = DbTypePropertyResolver.resolveOrDefault(POSTGRESQL);
        return switch (dbType) {
            case MYSQL -> getMysqlDb();
            case DERBY -> getDerbyDb(artHome.getAbsolutePath());
            case MSSQL -> getMssqlDb();
            case ORACLE -> getOracleDb();
            case MARIADB -> getMariaDb();
            case POSTGRESQL -> getPostgresqlDb();
        };
    }

    private static YamlDbConfig getDerbyDb(final String artHome) {
        //no need to set up url (will be resolved by jfrog-db-infra lib)
        final String strDerbyHome = String.join("/",
                artHome, VAR_FOLDER, DATA_FOLDER, ARTIFACTORY_SERVICE_NAME, "derby;create=true"
        );
        return YamlDbConfig.builder()
                .type("derby")
                .driver("org.apache.derby.jdbc.EmbeddedDriver")
                .url("jdbc:derby:" + strDerbyHome)
                .username("username")
                .password("password")
                .allowNonPostgresql(true)
                .build();
    }

    private static YamlDbConfig getMysqlDb() {
        return YamlDbConfig.builder()
                .type("mysql")
                .url("jdbc:mysql://localhost:3306/artdb?allowPublicKeyRetrieval=true&characterEncoding=UTF-8" +
                        "&elideSetAutoCommits=true&useSSL=false")
                .driver("com.mysql.cj.jdbc.Driver")
                .username("root")
                .password("password")
                .allowNonPostgresql(true)
                .build();
    }

    private static YamlDbConfig getMariaDb() {
        return YamlDbConfig.builder()
                .type("mariadb")
                .url("jdbc:mariadb://localhost:3306/artdb?characterEncoding=UTF-8&elideSetAutoCommits=true" +
                        "&useSSL=false&useMysqlMetadata=true")
                .driver("org.mariadb.jdbc.Driver")
                .username("artifactory")
                .password("password")
                .allowNonPostgresql(true)
                .build();
    }

    private static YamlDbConfig getPostgresqlDb() {
        return YamlDbConfig.builder()
                .type("postgresql")
                .url("jdbc:postgresql://localhost:5432/artdb")
                .driver("org.postgresql.Driver")
                .username("artifactory")
                .password("password")
                .allowNonPostgresql(false)
                .build();
    }

    private static YamlDbConfig getMssqlDb() {
        return YamlDbConfig.builder()
                .type("mssql")
                .driver("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                .url("jdbc:sqlserver://localhost:1433;databaseName=artdb;" +
                        "sendStringParametersAsUnicode=false;applicationName=Artifactory Binary Repository;" +
                        "lockTimeout=5000;encrypt=true;trustServerCertificate=true;")
                .username("sa")
                .password("StrongPassword1@")
                .allowNonPostgresql(true)
                .build();
    }

    private static YamlDbConfig getOracleDb() {
        return YamlDbConfig.builder()
                .type("oracle")
                .driver("oracle.jdbc.OracleDriver")
                .url("jdbc:oracle:thin:@localhost:1521:xe")
                .username("SMITH")
                .password("password")
                .root("system")
                .rootPassword("oracle")
                .allowNonPostgresql(true)
                .build();
    }

    static String getLocalHostAddress() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            return localhost.getHostAddress();
        } catch (UnknownHostException e) {
            return "Unable to retrieve localhost address: " + e.getMessage();
        }
    }
}
