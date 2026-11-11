package org.artifactory.webapp.main;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.artifactory.util.TestServiceListLoader;
import org.artifactory.webapp.WebappUtils;
import org.jfrog.client.http.CloseableHttpClientDecorator;
import org.jfrog.client.http.ServiceHttpBuilder;
import org.jfrog.common.platform.test.helpers.ProcessUtils;
import org.jfrog.metadata.client.exception.MetadataClientException;
import org.jfrog.metadata.client.http.MetadataHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.artifactory.webapp.main.AccessProcess.TEST_JFROG_JOIN_KEY;
import static org.jfrog.client.http.ServiceHttpBuilder.JFrogService.METADATA;
import static org.jfrog.config.home.JFrogHome.PROP_HOME_DIR;
import static org.jfrog.config.home.JFrogHome.VAR_FOLDER;

/**
 * @author Uriah Levy
 * Metadata Server process wrapper. For development use only.
 */
public class MetadataServerProcess {
    private final Logger log;
    private File metadataServerHome;
    private StartedProcess process;
    private int port;
    private int routerPort;

    /**
     * @param productHome is the path to <homeDir> (/var should be under it)
     */
    public MetadataServerProcess(File productHome, String testInstanceName, int routerPort, int port) {
        log = LoggerFactory.getLogger(MetadataServerProcess.class.getName() + "_" + testInstanceName);
        this.routerPort = routerPort;
        this.port = port;
        if (productHome != null) {
            this.metadataServerHome = productHome;
        } else {
            this.metadataServerHome = new File(System.getProperty("user.home") + "/.metadata");
        }
    }

    MetadataServerProcess(int port, int routerPort, File productHome) {
        this.log = LoggerFactory.getLogger(MetadataServerProcess.class.getName());
        this.port = port;
        this.routerPort = routerPort;
        this.metadataServerHome = productHome;
    }

    public void cleanMdsDb() {
        FileUtils.deleteQuietly(new File(metadataServerHome + "/" + VAR_FOLDER + "/data/metadata/sqlite.db"));
    }

    public void start() throws IOException {
        List<String> cmd = buildCommand();
        port = ProcessUtils.resolvePort(port);
        log.info("Starting external MDS process with port {}", port);
        String mdsPath = metadataServerHome.getAbsolutePath();

        process = new ProcessExecutor()
                .command(cmd)
                .environment("JF_METADATA_ROUTER_PORT", String.valueOf(routerPort))
                .environment(PROP_HOME_DIR, mdsPath)
                .environment("JF_METADATA_PORT", String.valueOf(port))
                .environment("JF_SHARED_SECURITY_JOINKEY", TEST_JFROG_JOIN_KEY)
                .environment("JF_METADATA_DATABASE_ALLOWNONPOSTGRESQL", "true") // for non-PG
                //.environment("JF_METADATA_SERVER_MODE", "debug") // for those who wants debug
                .redirectOutput(System.out)
                .redirectError(System.err)
                .destroyOnExit()
                .start();
        if (!waitForStartup()) {
            throw new IllegalStateException("Metadata Server did not start.");
        }
        log.info("Metadata Server process start with PID {}", "unknown :D");
    }

    public void stop() {
        if (process != null && process.getProcess().isAlive()) {
            process.getProcess().destroyForcibly();
        }
    }

    private List<String> buildCommand() {
        String metadataPath = WebappUtils.getMetadataApiExecutable(SystemUtils.IS_OS_MAC).getAbsolutePath();
        log.info("Referencing Metadata Server binary at {}", metadataPath);
        return Lists.newArrayList(metadataPath);
    }

    private boolean waitForStartup() {
        long start = System.currentTimeMillis();
        int retries = 0;
        final int maxTime = 120_000;
        while (System.currentTimeMillis() - start < maxTime) {
            if (isProcessAlive()) {
                log.info("Finished waiting for Metadata process after {} millis with {} ping retries",
                        System.currentTimeMillis() - start, retries);
                return true;
            } else if (process.getFuture().isDone()) {
                log.warn("Metadata process exited.");
                return false;
            }
            retries++;
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String message = "Metadata is not responding after " + maxTime + " millis";
        log.error(message);
        return false;
    }

    private boolean isProcessAlive() {
        try (MetadataHttpClient client = new MetadataHttpClient(buildHttpClient(), null)) {
            client.adHocPing();
            return true;
        } catch (MetadataClientException | IllegalStateException e) {
            log.debug("Unable to ping metadata server: {}", e.getMessage());
        }
        return false;
    }

    private CloseableHttpClientDecorator buildHttpClient() {
        // Can't use the default loader, because SysConfig is not initialized yet.
        ServiceHttpBuilder httpBuilder =
                new ServiceHttpBuilder(METADATA, new TestServiceListLoader(METADATA, getMdsUrl()));

        return (CloseableHttpClientDecorator) httpBuilder
                .connectionTimeout(2000)
                .socketTimeout(10000)
                .trustSelfSignCert(true)
                .noHostVerification(true)
                .build();
    }

    public int getPort() {
        return port;
    }

    public String getMdsUrl() {
        return "http://localhost:" + getPort();
    }
}