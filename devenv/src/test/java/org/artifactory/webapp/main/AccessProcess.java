/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.webapp.main;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import com.jfrog.sysconf.test.SystemYamlBuilder;
import com.jfrog.sysconf.test.YamlDbConfig;
import com.jfrog.sysconf.test.YamlGrpc;
import com.jfrog.sysconf.test.YamlHttp;
import com.jfrog.sysconf.test.YamlRouterEntrypoints;
import com.jfrog.sysconf.test.YamlServiceSysConfig;
import com.jfrog.sysconf.test.YamlSysConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.artifactory.util.ThreadDumpUtils;
import org.artifactory.webapp.WebappUtils;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientBootstrap;
import org.jfrog.access.client.AccessClientBuilder;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.confstore.AccessClientConfigStore;
import org.jfrog.access.client.confstore.fsconfig.FileBasedAccessClientConfigStore;
import org.jfrog.access.client.http.AccessHttpClient;
import org.jfrog.access.client.http.HttpClientContext;
import org.jfrog.access.common.ServiceId;
import org.jfrog.client.http.CloseableHttpClientDecorator;
import org.jfrog.client.http.HttpBuilder;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.ResourceUtils;
import org.jfrog.common.RetryException;
import org.jfrog.common.YamlUtils;
import org.jfrog.common.platform.test.helpers.ProcessUtils;
import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jfrog.sysconf.SysConfigConstants.ENV_JF_PRODUCT_HOME;
import static com.jfrog.sysconf.SysConfigConstants.ENV_NODE_ID;
import static com.jfrog.sysconf.SysConfigConstants.SYS_JOIN_KEY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.removeStart;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.jfrog.config.home.JFrogHome.ETC_FOLDER;
import static org.jfrog.config.home.JFrogHome.SYSTEM_YAML_FILENAME;
import static org.jfrog.config.home.JFrogHome.VAR_FOLDER;

/**
 * This class spawns Access in a new process or re-uses an existing Access server.
 *
 * @author Yossi Shaul
 */
public class AccessProcess {

    private static final String DEFAULT_ACCESS_BOOTSTRAP = "/access/access.bootstrap.json";

    public static final String TEST_JFROG_JOIN_KEY = "cc949ef041b726994a225dc20e018f23";
    private static final String TEST_SERVICE_ID = "jfrt@01bkpzmmgz16ay0jmt04a11845";

    private final Logger log;

    private final File accessJarFile;
    private final File homeDir;
    private final File varDir;
    private final String contextPath;
    private final DebugConfig debugConfig;
    private final SystemProperties systemProperties = new SystemProperties();
    private final AccessProcessConfig config;
    private String serverUrl;

    private int grpcPort;
    private int routerPort;
    private int port;

    private File accessBackupFile;
    private StartedProcess process;

    private long pid;

    /**
     * Constructs a new Access Process. To start the process call {@link AccessProcess#start()} or
     * {@link AccessProcess#startAndWait()}.
     *
     * @param config the configuration for the access server process
     */
    public AccessProcess(AccessProcessConfig config) {
        log = LoggerFactory.getLogger(AccessProcess.class.getName() + "_" + config.instanceName);
        this.config = config;
        this.accessJarFile = resolveAccessJarFle();
        this.homeDir = config.homeDir;
        this.varDir = new File(config.homeDir, VAR_FOLDER);
        this.contextPath = resolveContextPath(config);
        this.debugConfig = config.debugConfig;
        this.routerPort = config.routerPort;
        resolveAvailablePorts(config.port, config.grpcPort);
    }

    private void resolveAvailablePorts(int port, int grpcPort) {
        this.port = ProcessUtils.resolvePort(port);
        this.grpcPort = ProcessUtils.resolvePort(grpcPort);
        this.serverUrl = "http://localhost:" + this.port + this.contextPath;
        log.info("resolved access port: {}, resolved access grpc port: {}", this.port, this.grpcPort);
    }

    public AccessProcessConfig getConfig() {
        return config;
    }

    public int getPort() {
        return port;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    private File resolveAccessJarFle() {
        return WebappUtils.getAccessStandaloneJar();
    }

    private String resolveContextPath(AccessProcessConfig config) {
        return isBlank(config.contextPath) ? "" : "/" + removeStart(config.contextPath, "/");
    }

    /**
     * Starts a new Access server in a new process if no other Access service already listens at the give port.
     * If a new process is spawned, this will also register a shutdown hook to shut it down when the JVM exists.
     *
     * @return Reference to this
     */
    public AccessProcess start() {
        try {
            if (isAccessAlive()) {
                if (config.requireNewProcess) {
                    throw new IllegalStateException("Access process found listening on: " + getAccessUrl() +
                            " reuse is not allowed!");
                }
                log.info("Access process found listening on: {}", getAccessUrl());
                return this;
            }
            log.info("Starting Access process on: {} (home dir: {})", getAccessUrl(), homeDir);

            if (SystemYamlOverridePropertyResolver.resolve() == null) {
                createOrAppendToSystemYaml();
            }

            systemProperties.put("access.debug.version", "artifactory-dev");
            systemProperties.put(SYS_JOIN_KEY, TEST_JFROG_JOIN_KEY);
            List<String> cmd = buildCommand();
            log.info("Starting Access, cmd:{}", Arrays.toString(cmd.toArray()));
            ProcessExecutor processExecutor = new ProcessExecutor()
                    .command(cmd)
                    .environment(ENV_JF_PRODUCT_HOME, homeDir == null ? "" : homeDir.getAbsolutePath())
                    .environment(ENV_NODE_ID, UUID.randomUUID().toString())
                    .environment("runMode.devHa", "true")
                    .redirectOutput(System.out)
                    .redirectError(System.err)
                    .destroyOnExit();

            this.process = processExecutor.start();
            pid = process.getProcess().pid();
            log.info("External Access PID is {}", pid);
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
            return this;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to start Access Process", e);
        }
    }

    private void createOrAppendToSystemYaml() {
        File systemYamlFile = new File(
                Paths.get(homeDir.getAbsolutePath(), VAR_FOLDER, ETC_FOLDER, SYSTEM_YAML_FILENAME).toUri());
        YamlSysConfig systemYamlConfig;
        try {
            if (systemYamlFile.exists()) {
                YamlSysConfig yamlSysConfig = YamlUtils.getInstance().readValue(systemYamlFile, YamlSysConfig.class);
                YamlServiceSysConfig router =
                        yamlSysConfig.getRouter() != null ? yamlSysConfig.getRouter() : getRouterConfig();
                systemYamlConfig = yamlSysConfig
                        .withAccess(getAccessYamlConfig(yamlSysConfig.getAccess(),
                                yamlSysConfig.getShared().getDatabase()))
                        .withShared(yamlSysConfig.getShared())
                        .withArtifactory(yamlSysConfig.getArtifactory())
                        .withRouter(router);
            } else {
                systemYamlConfig = YamlSysConfig.builder()
                        .access(getAccessYamlConfig())
                        .router(getRouterConfig())
                        .build();
            }
            systemYamlFile.delete();
            FileUtils.writeStringToFile(systemYamlFile, SystemYamlBuilder.toYaml(systemYamlConfig), Charsets.UTF_8,
                    false);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create/append to system.yaml.", e);
        }
    }

    private YamlServiceSysConfig getAccessYamlConfig() {
        return getAccessYamlConfig(null, null);
    }

    private YamlServiceSysConfig getAccessYamlConfig(YamlServiceSysConfig config, YamlDbConfig db) {
        String url = String.join(File.separator, homeDir.getAbsolutePath(), "var", "data", "access", "derby");
        YamlServiceSysConfig.Builder builder = config != null ? config.toBuilder() : YamlServiceSysConfig.builder();
        builder
                .http(YamlHttp.builder()
                        .port(port)
                        .build())
                .grpc(YamlGrpc.builder()
                        .port(grpcPort)
                        .build());
        if (db == null) {
            builder.database(YamlDbConfig.builder()
                    .type("derby")
                    .driver("org.apache.derby.jdbc.EmbeddedDriver")
                    .url("jdbc:derby:" + url + ";create=true")
                    .allowNonPostgresql(true)
                    .build());
        } else {
            builder.database(YamlDbConfig.builder()
                    .type(db.getType())
                    .driver(db.getDriver())
                    .url(db.getUrl())
                    .allowNonPostgresql(true)
                    .username(db.getUsername())
                    .password(db.getPassword())
                    .build());
        }
        return builder.build();
    }

    private YamlServiceSysConfig getRouterConfig() {
        YamlServiceSysConfig.Builder routerBuilder = YamlServiceSysConfig.builder();
        if (routerPort != -1) {
            routerBuilder.enabled(true);
            routerBuilder.entrypoints(YamlRouterEntrypoints.builder()
                    .internalPort(routerPort)
                    .build());
        } else {
            routerBuilder.enabled(false);
        }
        return routerBuilder.build();
    }

    private List<String> buildCommand() {
        String accessStandAloneXmx = Optional.ofNullable(System.getProperty("artifactory.access.standalone.xmx"))
                .orElse("512m");
        List<String> cmd = Lists.newArrayList("java", "-Xmx" + accessStandAloneXmx);
        cmd.add("-D" + SYS_JOIN_KEY + "=" + TEST_JFROG_JOIN_KEY);
        cmd.add("-Dstaging.mode=true");
        if (!System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            cmd.add("-Djava.security.egd=file:/dev/urandom");
        }

        if (isNotBlank(contextPath)) {
            cmd.add("-Dserver.contextPath=" + contextPath);
            cmd.add("-Dserver.servlet.context-path=" + contextPath);
        }
        if (debugConfig != null) {
            cmd.add(debugConfig.toVmArgument());
        } else {
            String javaOpts = System.getenv("ACCESS_PROCESS_DEBUG_OPTS");
            if (javaOpts != null) {
                cmd.add(javaOpts);
            }
        }
        List<String> additionalJdbcDrivers = Stream.of(DbType.values())
                .map(dbType -> new File(accessJarFile.getParentFile(), "jdbc_" + dbType + ".jar"))
                .filter(File::exists)
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
        if (!additionalJdbcDrivers.isEmpty()) {
            cmd.add("-Dloader.path=" + String.join(",", additionalJdbcDrivers));
        }
        cmd.addAll(List.of("-jar", accessJarFile.getAbsolutePath()));
        return cmd;
    }

    private List<String> buildJstackCommand() {
        String javaHomePath = System.getenv("JAVA_HOME");
        if (StringUtils.isNotBlank(javaHomePath)) {
            String jstackPath = Joiner.on("/").join(StringUtils.removeEnd(javaHomePath, "/"), "bin");
            List<String> cmd = Lists.newArrayList(Joiner.on("/").join(jstackPath, "jstack"), "-l");
            cmd.add(String.valueOf(pid));
            return cmd;
        } else {
            // jstack might be in the path, we'll fail otherwise but it's no biggie.
            List<String> cmd = Lists.newArrayList("jstack", "-l");
            cmd.add(String.valueOf(pid));
            return cmd;
        }
    }


    /**
     * Starts the Access process and waits until Access server is ready
     */
    AccessProcess startAndWait() {
        try {
            ExecutionUtils.retry(() -> {
                log.info("Starting to run Access");
                start();
                boolean success = waitForStartup();
                if (!success) {
                    String msg = "Access could not start using URL: " + getAccessUrl() + ". ";
                    log.info(msg);
                    if (process.getFuture().isDone()) {
                        // it might be that access failed to start due to specific port binding issue, retrying with
                        //different port
                        resolveAvailablePorts(0, 0);
                        throw new RetryException(msg + " Restarting access");
                    }
                }
                return null;
            }, ExecutionUtils.RetryOptions.builder()
                    .numberOfRetries(3)
                    .timeout(2000)
                    .backoffMaxDelay(2000)
                    .build(), MoreExecutors.newDirectExecutorService())
                    .get(3, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException("Failed to start Access, this may cause the current test to fail", e);
        }
        return this;
    }

    /**
     * @return URL of the Access server
     */
    public String getAccessUrl() {
        return serverUrl;
    }

    /**
     * @return URL of the Router grpc port
     */
    public int getRouterGrpcPort() {
        return routerPort;
    }

    /**
     * @return The home dir of Access server
     */
    public File getHomeDir() {
        return homeDir;
    }

    /**
     * @return The home dir of Access server
     */
    public File getVarDir() {
        return varDir;
    }

    /**
     * Block for a specific amount of time or until Access responds.
     *
     * @throws RuntimeException if interrupted or if Access is not ready
     */
    private boolean waitForStartup() {
        return waitForStartup(180000);
    }

    /**
     * Block for specified time in millis or until Access responds.
     *
     * @param maxTime Max time in milliseconds to wait for Access to start
     * @throws RuntimeException if interrupted or if Access is not ready in the specified time
     */
    private boolean waitForStartup(int maxTime) {
        long start = System.currentTimeMillis();
        int retries = 0;
        while (System.currentTimeMillis() - start < maxTime) {
            if (isAccessAlive()) {
                log.info("Finished waiting for Access process after {} millis with {} ping retries",
                        System.currentTimeMillis() - start, retries);
                return true;
            } else if (process.getFuture().isDone()) {
                log.warn("Access process is done. returning false");
                return false;
            }
            retries++;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        String message = "Access is not responding after " + maxTime + " millis";
        log.error(message);
        dumpThreadsOnAccess();
        dumpThreadsOnArtifactory();
        return false;
    }

    private void dumpThreadsOnArtifactory() {
        log.info("Dumping the threads on Artifactory");
        StringBuilder threadDump = new StringBuilder();
        ThreadDumpUtils.builder()
                .count(1)
                .build()
                .dumpThreadsForcefully(threadDump);
        log.info(threadDump.toString());
    }

    private void dumpThreadsOnAccess() {
        log.info("Dumping the threads on Access");
        if (pid != 0) {
            List<String> threadDumpCmd = buildJstackCommand();
            try {
                ByteArrayOutputStream jstackOutput = new ByteArrayOutputStream();
                new ProcessExecutor()
                        .command(threadDumpCmd)
                        .redirectOutput(jstackOutput)
                        .redirectError(jstackOutput)
                        .destroyOnExit()
                        .start().getFuture().get(10, TimeUnit.SECONDS);
                log.info(jstackOutput.toString());
            } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
                log.warn("Error while taking thread dumps on Access Process", e);
            }
        }
    }

    /**
     * Stops Access server if it was started by this instance.
     */
    public synchronized void stop() {
        systemProperties.restoreOriginal();
        if (process != null) {
            log.info("Stopping Access process on: {} (home dir: {})", getAccessUrl(), homeDir);
            Process osProcess = process.getProcess();
            process.getFuture().cancel(true);
            try {
                osProcess.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                if (process.getProcess().isAlive()) {
                    log.error("Failed to stop Access process. Retrying ...");
                    System.err.println("Failed to stop Access process. Retrying ...");
                    try {
                        osProcess.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                    } catch (InterruptedException ie) {
                        // ignore, see below
                    }
                }
            }
            if (!osProcess.isAlive()) {
                log.info("Access process stopped successfully");
            } else {
                log.error("Failed to forcibly stop Access process!");
                System.err.println("Failed to forcibly stop Access process!");
            }
        }
    }

    private static CloseableHttpClientDecorator buildHttpClient() {
        return (CloseableHttpClientDecorator) new HttpBuilder()
                .connectionTimeout(2000)
                .socketTimeout(10000)
                .noHostVerification(true)
                .trustSelfSignCert(
                        true) //FIXME [YA] This is too permissive - need to trust the root certificate (also fix on
                // RestBusAdapter)
                .build();
    }

    private boolean isAccessAlive() {
        try (AccessHttpClient client = new AccessHttpClient(getAccessUrl(), buildHttpClient(),
                new HttpClientContext(RequestConfig.DEFAULT), null, null)) {
            client.ping();
            return true;
        } catch (AccessClientException e) {
            // log and continue retry
            log.debug("Communication to Access failed: {}", e.getMessage());
        }
        return false;
    }

    public void initDefaultAccessServer() throws IOException {
        initAccessServer();
    }

    private void initAccessServer() throws IOException {
        try (InputStream accessBootstrap = ResourceUtils.getResource(DEFAULT_ACCESS_BOOTSTRAP)) {
            stop();
            log.info("Importing default test data into Access");
            startAndWait();
            resetAccess(accessBootstrap);
        }
    }

    public void resetAccess(@Nullable InputStream accessBootstrap) {
        try {
            importDefaultAccess(accessBootstrap, getAccessUrl());
        } catch (Exception e) {
            log.error("Failed importing Access data.", e);
        }
    }

    private void importDefaultAccess(InputStream accessBootstrap, String serverUrl) throws IOException {
        InputStream bootstrapConfStream = null;
        try {
            bootstrapConfStream = accessBootstrap != null ? accessBootstrap :
                    accessBackupFile != null ? new FileInputStream(accessBackupFile) :
                            ResourceUtils.getResource(DEFAULT_ACCESS_BOOTSTRAP);
            FileUtils.copyInputStreamToFile(bootstrapConfStream,
                    new File(getVarDir(), "etc/access/access.bootstrap.json"));
        } finally {
            IOUtils.closeQuietly(bootstrapConfStream);
        }

        try (AccessClient accessClient = getAdminAccessClient(serverUrl)) {
            accessClient.system().importAccessServer();
        }
        // import revokes token
        try (AccessClient accessClient = getAdminAccessClient(serverUrl)) {
            accessClient.ping();
        }
    }

    public void export() {
        try (AccessClient accessClient = getAdminAccessClient(serverUrl)) {
            accessClient.system().exportAccessServer();
            File backupFile = resolveBackupFile();
            if (backupFile != null && backupFile.exists()) {
                accessBackupFile = backupFile;
            }
        } catch (Exception e) {
            log.error("Could not export access content.", e);
        }
    }

    private File resolveBackupFile() throws IOException {
        File backupDir = new File(homeDir, "backup");
        if (!backupDir.exists()) {
            backupDir = new File(homeDir, "ha-backup");
        }
        Optional<Path> backupFile = Files.list(backupDir.toPath())
                .filter(f -> f.getFileName().toString().startsWith("access.backup") &&
                        f.getFileName().toString().endsWith(".json"))
                .max(Comparator.comparingLong(f -> f.toFile().lastModified()));

        return backupFile.map(Path::toFile).orElse(null);
    }

    /**
     * Temp access client for import/export
     */
    private AccessClient getAdminAccessClient(String serverUrl) {
        AccessClientConfigStore accessClientConfigStore = new FileBasedAccessClientConfigStore(homeDir,
                () -> ServiceId.fromFormattedName(TEST_SERVICE_ID), "TEMP_NODE_ID");
        AccessClient accessClient =
                AccessClientBuilder.newBuilder().serverUrl(serverUrl).grpcUrl("localhost:" + getGrpcPort()).create();
        AccessClientBootstrap accessClientBootstrap =
                new AccessClientBootstrap(accessClient, () -> accessClientConfigStore,
                        () -> TEST_JFROG_JOIN_KEY);
        accessClientBootstrap.setRejoinEnabled(false); // remove along with ensureAuth() flow - RTFACT-23265
        return accessClientBootstrap.getAccessClient();
    }

    public void backup() {

    }

    public static class AccessProcessConfig {
        private final File homeDir;
        private String contextPath = null;
        private int port = 0;
        private int grpcPort = 0;
        private int routerPort = -1;
        private DebugConfig debugConfig = null;
        private boolean requireNewProcess;
        String instanceName = "";

        public AccessProcessConfig(@Nonnull File homeDir) {
            this.homeDir = homeDir;
        }

        public AccessProcessConfig contextPath(String path) {
            this.contextPath = path;
            return this;
        }

        public AccessProcessConfig port(int port) {
            this.port = port;
            return this;
        }

        public AccessProcessConfig grpcPort(int grpcPort) {
            this.grpcPort = grpcPort;
            return this;
        }


        public AccessProcessConfig routerPort(int port) {
            this.routerPort = port;
            return this;
        }

        public AccessProcessConfig instanceName(String instanceName) {
            this.instanceName = instanceName;
            return this;
        }

        /**
         * If new process is required, trying to start this process will fail if another Access server already
         * listens on the same port.
         */
        public AccessProcessConfig requireNewProcess() {
            requireNewProcess = true;
            return this;
        }

        public AccessProcessConfig debug(int port, boolean suspend) {
            this.debugConfig = new DebugConfig(port, suspend);
            return this;
        }
    }

    private static class DebugConfig {
        private final int port;
        private final boolean suspend;

        private DebugConfig(int port, boolean suspend) {
            this.port = port;
            this.suspend = suspend;
        }

        String toVmArgument() {
            return "-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + (suspend ? "y" : "n") + ",address=" + port;
        }
    }

    private static class SystemProperties {
        private final Map<String, String> originalProperties = Maps.newHashMap();
        private final Map<String, String> currentProperties = Maps.newHashMap();

        void put(String key, String value) {
            init(key);
            currentProperties.put(key, value);
            System.setProperty(key, value);
        }

        void restoreOriginal() {
            originalProperties.forEach((key, value) -> {
                //restore original value only if the current system property was set by this instance.
                if (Objects.equals(System.getProperty(key), currentProperties.get(key))) {
                    if (value == null) {
                        System.clearProperty(key);
                    } else {
                        System.setProperty(key, value);
                    }
                }
            });
        }

        private void init(String key) {
            if (!originalProperties.containsKey(key)) {
                originalProperties.put(key, System.getProperty(key));
            }
        }
    }
}
