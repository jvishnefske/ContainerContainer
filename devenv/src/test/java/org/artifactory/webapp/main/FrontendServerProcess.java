package org.artifactory.webapp.main;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.SystemUtils;
import org.artifactory.webapp.WebappUtils;
import org.jfrog.common.platform.test.helpers.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.artifactory.webapp.main.AccessProcess.TEST_JFROG_JOIN_KEY;
import static org.jfrog.config.home.JFrogHome.PROP_HOME_DIR;

/**
 * Frontend Server process wrapper. For development use only.
 *
 * @author Yuval Reches
 */
public class FrontendServerProcess {
    private static final Logger log = LoggerFactory.getLogger(FrontendServerProcess.class);

    private static final int GRACEFUL_SHUTDOWN_WAIT_SEC = 10;
    private static final int FORCED_SHUTDOWN_WAIT_SEC = 10;

    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

    private File frontendServerHome;
    private StartedProcess process;
    private int port;
    private int routerPort;

    /**
     * @param productHome is the path to <homeDir> (/var should be under it)
     */
    public FrontendServerProcess(File productHome, int routerPort, int port) {
        this.routerPort = routerPort;
        this.port = port;
        if (productHome != null) {
            this.frontendServerHome = productHome;
        } else {
            this.frontendServerHome = new File(System.getProperty("user.home") + "/.frontend");
        }
    }

    FrontendServerProcess(int port, int routerPort, File productHome) {
        this.port = port;
        this.routerPort = routerPort;
        this.frontendServerHome = productHome;
    }

    public void start() throws IOException {
        List<String> cmd = buildCommand();
        port = ProcessUtils.resolvePort(port);
        log.info("Starting external Frontend process with port {}", port);

        File varFolder = new File(frontendServerHome, "var");
        File logs = new File(varFolder, "log/frontend");
        boolean logDirCreated = logs.mkdirs();
        if (!logDirCreated) {
            log.warn("Failed to create frontend logs folder");
        }
        String homePath = frontendServerHome.getAbsolutePath();

        process = new ProcessExecutor()
                .command(cmd)
                .environment(PROP_HOME_DIR, homePath)
                .environment("JF_FRONTEND_PORT", String.valueOf(port))
                //.environment("JF_FRONTEND_LOGGING_APPLICATION_LEVEL", "debug") // for those who wants debug
                .environment("JF_SHARED_SECURITY_JOINKEY",
                        TEST_JFROG_JOIN_KEY) // currently node doesn't read from filesystem due to unknown reason
                .environment("ENV_TYPE", "development") // allows setting importMapOverrides for frontends
                .redirectOutput(System.out)
                .redirectError(System.err)
                .destroyOnExit()
                .start();
        log.info("Frontend process started with PID: {}", process.getProcess().pid());
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "frontend-shutdown"));
        }
    }

    /**
     * Stops the frontend child process.
     */
    public void stop() {
        if (process == null) {
            return;
        }
        Process osProcess = process.getProcess();
        // ZT ProcessExecutor future: stops internal pump/wait threads; does not send OS signals to the child.
        try {
            process.getFuture().cancel(true);
        } catch (Exception e) {
            log.debug("Ignoring error while cancelling frontend process future: {}", e.getMessage());
        }
        if (!osProcess.isAlive()) {
            log.info("Frontend process stopped successfully");
            return;
        }
        log.info("Stopping external Frontend process (PID: {})", osProcess.pid());
        osProcess.destroy();
        try {
            if (osProcess.waitFor(GRACEFUL_SHUTDOWN_WAIT_SEC, TimeUnit.SECONDS)) {
                log.info("Frontend process stopped after destroy()");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        destroyDescendantsOf(osProcess.toHandle());
        if (osProcess.isAlive()) {
            osProcess.destroyForcibly();
            try {
                if (!osProcess.waitFor(FORCED_SHUTDOWN_WAIT_SEC, TimeUnit.SECONDS)) {
                    log.warn("Frontend process (PID {}) did not exit within timeout after destroyForcibly()",
                            osProcess.pid());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (!osProcess.isAlive()) {
            log.info("Frontend process stopped");
        } else {
            log.error("Failed to stop frontend process (PID: {})", osProcess.pid());
        }
    }

    private void destroyDescendantsOf(ProcessHandle root) {
        root.descendants().forEach(h -> {
            try {
                h.destroy();
            } catch (Exception e) {
                log.debug("Could not destroy child PID: {} - {}", h.pid(), e.getMessage());
            }
        });
    }

    private List<String> buildCommand() {
        return Lists.newArrayList(
                WebappUtils.getNodeExecutable(SystemUtils.IS_OS_MAC).getAbsolutePath(),
                WebappUtils.getFrontendExecutable(SystemUtils.IS_OS_MAC).getAbsolutePath());
    }

}