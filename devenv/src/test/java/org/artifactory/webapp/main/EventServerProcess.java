package org.artifactory.webapp.main;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.SystemUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.artifactory.webapp.WebappUtils;
import org.jfrog.client.http.CloseableHttpClientDecorator;
import org.jfrog.client.http.HttpBuilder;
import org.jfrog.common.platform.test.helpers.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.artifactory.webapp.main.AccessProcess.TEST_JFROG_JOIN_KEY;
import static org.jfrog.config.home.JFrogHome.PROP_HOME_DIR;

/**
 * @author Yevdo Abramov
 * Created on 08/06/2020
 */
public class EventServerProcess {
    private final Logger log;
    private File eventServerHome;
    private StartedProcess process;
    private int port;
    private int routerPort;

    /**
     * @param productHome is the path to <homeDir> (/var should be under it)
     */
    public EventServerProcess(File productHome, String testInstanceName, int routerPort, int port) {
        log = LoggerFactory.getLogger(EventServerProcess.class.getName() + "_" + testInstanceName);
        this.routerPort = routerPort;
        this.port = port;
        if (productHome != null) {
            this.eventServerHome = productHome;
        } else {
            this.eventServerHome = new File(System.getProperty("user.home") + "/.event");
        }
    }

    EventServerProcess(int port, int routerPort, File productHome) {
        this.log = LoggerFactory.getLogger(EventServerProcess.class.getName());
        this.port = port;
        this.routerPort = routerPort;
        this.eventServerHome = productHome;
    }

    public void start() throws IOException {
        List<String> cmd = buildCommand();
        port = ProcessUtils.resolvePort(port);
        log.info("Starting external Event Server process with port {}", port);
        String homeAbsolutePath = eventServerHome.getAbsolutePath();

        process = new ProcessExecutor()
                .command(cmd)
                .environment(PROP_HOME_DIR, homeAbsolutePath)
                .environment("JF_SHARED_SECURITY_JOINKEY", TEST_JFROG_JOIN_KEY)
                .environment("JF_EVENT_PORT", String.valueOf(port))
                .redirectOutput(System.out)
                .redirectError(System.err)
                .destroyOnExit()
                .start();
        if (!waitForStartup()) {
            throw new IllegalStateException("Event Server did not start.");
        }
        log.info("Event Server process start with PID {}", "unknown :D");
    }

    public void stop() {
        if (process != null && process.getProcess().isAlive()) {
            process.getProcess().destroyForcibly();
        }
    }

    private List<String> buildCommand() {
        String eventServerExecutablePath =
                WebappUtils.getEventServerExecutable(SystemUtils.IS_OS_MAC).getAbsolutePath();
        log.info("Referencing Event Server binary at {}", eventServerExecutablePath);
        return Lists.newArrayList(eventServerExecutablePath);
    }

    private boolean waitForStartup() {
        long start = System.currentTimeMillis();
        int retries = 0;
        final int maxTime = 60_000;
        while (System.currentTimeMillis() - start < maxTime) {
            if (isProcessAlive()) {
                log.info("Finished waiting for Event Server process after {} millis with {} ping retries",
                        System.currentTimeMillis() - start, retries);
                return true;
            } else if (process.getFuture().isDone()) {
                log.warn("Event Server process exited.");
                return false;
            }
            retries++;
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String message = "Event Server is not responding after " + maxTime + " millis";
        log.error(message);
        return false;
    }

    private boolean isProcessAlive() {
        HttpGet request = new HttpGet(getEventServerUrl() + "/api/v1/system/ping");
        try (CloseableHttpClientDecorator client = buildHttpClient()) {
            CloseableHttpResponse response = client.execute(request);
            return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
        } catch (IOException e) {
            log.debug("Unable to ping Event Server: {}", e.getMessage());
        }
        return false;
    }

    private static CloseableHttpClientDecorator buildHttpClient() {
        return (CloseableHttpClientDecorator) new HttpBuilder()
                .connectionTimeout(2000)
                .socketTimeout(10000)
                .noHostVerification(true)
                .trustSelfSignCert(true)
                .build();
    }

    public int getPort() {
        return port;
    }

    public String getEventServerUrl() {
        return "http://localhost:" + port;
    }
}
