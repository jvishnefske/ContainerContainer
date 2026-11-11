package org.artifactory.probes.service;

import com.google.common.annotations.VisibleForTesting;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.metrics.framework.MetricsFacade;
import org.artifactory.probes.ProbesService;
import org.artifactory.probes.jobs.ReadinessJob;
import org.artifactory.probes.model.ReadinessStatus;
import org.artifactory.probes.tomcat.ServerReadinessMonitor;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.util.date.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import static org.artifactory.common.ConstantValues.readinessInternalExternalStatusSeparationEnabled;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
@Service
public class ProbesServiceImpl implements ProbesService, ContextCreationListener {

    private static final String ARTIFACTORY_NOT_READY_MESSAGE =
            "The readiness check failed. Artifactory is considered 'Not Ready'.";
    private static final String READINESS_STATUS_UNAVAILABLE_MESSAGE = "Readiness status is not available";

    static final String READINESS_TIMEOUT_THRESHOLD_KEY_NAME =
            "artifactory.probes.readiness.timeoutThresholdDurationSecs";
    static final int DEFAULT_READINESS_THRESHOLD_TIMEOUT = 30;
    private static final String HEALTH_CHECK_INTERVAL_KEY_NAME = "artifactory.healthCheck.intervalSecs";
    private static final int DEFAULT_HEALTH_CHECK_INTERVAL = 15;
    private static final int MIN_HEALTH_CHECK_INTERVAL = 3;
    private static final String LIVENESS_FAIL_READINESS_ENABLED_KEY_NAME =
            "artifactory.probes.liveness.failOnLongFailingReadiness.enabled";
    private static final boolean DEFAULT_LIVENESS_FAIL_READINESS_ENABLED = true;
    @VisibleForTesting
    static final String LIVENESS_FAIL_READINESS_DURATION_KEY_NAME =
            "artifactory.probes.liveness.failOnLongFailingReadiness.failureDurationSecs";
    @VisibleForTesting
    static final int DEFAULT_LIVENESS_FAIL_READINESS_DURATION = 60;
    private static final int MIN_LIVENESS_FAIL_READINESS_DURATION = 0;
    private static final long CURRENT_RUN_TIMESTAMP_NOT_INITIALIZED = -1L;
    private final ArtifactoryServersCommonService artifactoryServers;
    private final StorageService storageService;
    private final InternalDbService internalDbService;
    private final AccessService accessService;
    private final TaskService taskService;
    private final ServerReadinessMonitor serverReadinessMonitor;
    private final MetricsFacade metricsFacade;
    private final ReentrantLock readinessLock = new ReentrantLock();
    private final long readinessThresholdTimeoutMillis;
    private final boolean livenessDependentOnReadiness;
    private final AtomicReference<ReadinessStatus> readinessStatusRef = new AtomicReference<>();
    private final AtomicLong lastSuccessfulReadinessMillis = new AtomicLong(0);
    private final AtomicLong lastReadinessRunMillis = new AtomicLong(0);
    @Setter(AccessLevel.PACKAGE)
    private LongSupplier timeSupplier = System::currentTimeMillis;

    @Autowired
    public ProbesServiceImpl(ArtifactoryServersCommonService artifactoryServers, StorageService storageService,
            InternalDbService internalDbService, AccessService accessService, TaskService taskService,
            ServerReadinessMonitor serverReadinessMonitor, MetricsFacade metricsFacade) {
        this.artifactoryServers = artifactoryServers;
        this.storageService = storageService;
        this.internalDbService = internalDbService;
        this.accessService = accessService;
        this.taskService = taskService;
        this.serverReadinessMonitor = serverReadinessMonitor;
        this.metricsFacade = metricsFacade;
        this.readinessThresholdTimeoutMillis = TimeUnit.SECONDS.toMillis(getReadinessTimeoutThreshold());
        this.livenessDependentOnReadiness = isLivenessDependentOnReadiness();
    }

    @Override
    public void onContextCreated() {
        int intervalSecs = getReadinessHealthCheckInterval();
        long intervalMillis = TimeUnit.SECONDS.toMillis(intervalSecs);
        Task t = TaskUtils.createRepeatingTask(ReadinessJob.class, intervalMillis, 0L);
        log.debug("Scheduling ReadinessJob to run every {} seconds", intervalSecs);
        taskService.startTask(t, false);
        log.debug("Scheduled ReadinessJob to run every {} seconds", intervalSecs);
    }

    @Override
    public void triggerReadinessCheck() {
        withQueryRateLimiter(SYSTEM, this::calculateAndUpdateReadinessStatus);
    }

    @Override
    public void checkReadinessStatus(boolean noCache) {
        log.trace("Checking readiness status");
        if (isReadinessCheckStuck()) {
            handleReadinessStuck();
        }
        if (noCache) {
            triggerReadinessCheck();
        }
        ReadinessStatus readinessStatus = readinessStatusRef.get();
        if (readinessStatus != null && readinessStatus.isReady()) {
            log.trace("Artifactory is ready");
            return;
        }
        handleReadinessFailure(readinessStatus);
    }

    @Override
    public void checkLivenessStatus() {
        log.trace("Checking liveness status");
        if (!livenessDependentOnReadiness) {
            return;
        }
        ReadinessStatus readinessStatus = readinessStatusRef.get();
        if (readinessStatus == null) {
            String message = "Liveness probe cannot test readiness since readiness was not triggered";
            log.warn(message);
            throw new ServiceUnavailableException(message);
        }
        if (!readinessStatus.isReady() || isReadinessCheckStuck()) {
            long lastSuccessMillis = lastSuccessfulReadinessMillis.get();
            if (lastSuccessMillis == 0L) {
                throw new ServiceUnavailableException("Liveness failed: readiness state has not been reached yet");
            }
            int failDuration = getReadinessFailureLimit();
            long secondsSinceLastSuccessfulReadiness =
                    TimeUnit.MILLISECONDS.toSeconds(timeSupplier.getAsLong() - lastSuccessMillis);
            if (secondsSinceLastSuccessfulReadiness > failDuration) {
                throw new ServiceUnavailableException(
                        "Liveness failed: readiness state failed for more than " + failDuration + " seconds");
            }
        }
        log.trace("Artifactory is alive");
    }

    @Override
    public boolean isReady() {
        ReadinessStatus readinessStatus = readinessStatusRef.get();
        if (readinessStatus == null) {
            log.warn("No readiness status yet. Artifactory is considered 'Not Ready'");
            return false;
        }
        if (!readinessStatus.isReady()) {
            log.warn(getReadinessStatusMsg(readinessStatus));
            return false;
        }
        return true;
    }

    private String getReadinessStatusMsg(ReadinessStatus readinessStatus) {
        return ARTIFACTORY_NOT_READY_MESSAGE + " " + readinessStatus.getStatusHolder().getStatusMsg();
    }

    private void handleReadinessStuck() {
        String stuckMessage = ARTIFACTORY_NOT_READY_MESSAGE + " The readiness job has not completed within "
                              + TimeUnit.MILLISECONDS.toSeconds(readinessThresholdTimeoutMillis)
                              + " seconds (stuck).";
        log.debug(stuckMessage);
        if (readinessInternalExternalStatusSeparationEnabled.getBoolean()) {
            throw new InternalServerErrorException(stuckMessage);
        } else {
            throw new ServiceUnavailableException(stuckMessage);
        }
    }

    private void handleReadinessFailure(ReadinessStatus readinessStatus) {
        if (readinessStatus == null) {
            readinessStatusUnavailable();
            return;
        }
        BasicStatusHolder statusHolder = readinessStatus.getStatusHolder();
        if (!statusHolder.hasErrors()) {
            readinessStatusUnavailable();
        }
        String readinessStatusMsg = getReadinessStatusMsg(readinessStatus);
        log.warn(readinessStatusMsg);
        if (readinessInternalExternalStatusSeparationEnabled.getBoolean()) {
            StatusEntry entry = statusHolder.getMostImportantErrorStatusCode();
            if (entry != null) {
                throw entry.getRuntimeException()
                        .orElseGet(() -> new InternalServerErrorException(readinessStatusMsg));
            }
            throw new InternalServerErrorException(readinessStatusMsg);
        } else {
            throw new ServiceUnavailableException(readinessStatusMsg);
        }
    }

    private void readinessStatusUnavailable() {
        log.warn(READINESS_STATUS_UNAVAILABLE_MESSAGE);
        if (readinessInternalExternalStatusSeparationEnabled.getBoolean()) {
            throw new InternalServerErrorException(READINESS_STATUS_UNAVAILABLE_MESSAGE);
        } else {
            throw new ServiceUnavailableException(READINESS_STATUS_UNAVAILABLE_MESSAGE);
        }
    }

    /**
     * @return True if liveness check is dependent on readiness check. False otherwise.
     */
    private static boolean isLivenessDependentOnReadiness() {
        return ArtifactoryHome.get().getSysConfig()
                .getBoolean(LIVENESS_FAIL_READINESS_ENABLED_KEY_NAME, DEFAULT_LIVENESS_FAIL_READINESS_ENABLED);
    }

    /**
     * @return Readiness timeout threshold (seconds).
     */
    private static int getReadinessTimeoutThreshold() {
        return ArtifactoryHome.get().getSysConfig()
                .getInt(READINESS_TIMEOUT_THRESHOLD_KEY_NAME, DEFAULT_READINESS_THRESHOLD_TIMEOUT);
    }

    private void calculateAndUpdateReadinessStatus() {
        metricsFacade.checkMetricsLastUsage();
        if (readinessLock.tryLock()) {
            long currentRunTimestamp = CURRENT_RUN_TIMESTAMP_NOT_INITIALIZED;
            try {
                log.trace("Readiness check has started");
                ReadinessStatus readinessStatus = ReadinessStatus.builder()
                        .running(checkIsRunning())
                        .offline(checkIsOffline())
                        .canWrite(checkCanWrite())
                        .storageServiceReady(checkStorageServiceReady())
                        .internalDbServiceHealthy(checkInternalDbServiceHealthy())
                        .accessServiceReady(checkAccessServiceReady())
                        .tomcatReady(checkTomcatIsReady())
                        .build();
                readinessStatusRef.set(readinessStatus);
                if (readinessStatus.isReady()) {
                    currentRunTimestamp = timeSupplier.getAsLong();
                    lastSuccessfulReadinessMillis.set(currentRunTimestamp);
                }
            } finally {
                updateLastReadinessRunTimestamp(currentRunTimestamp);
                readinessLock.unlock();
            }
        }
    }

    private void updateLastReadinessRunTimestamp(long currentRunTimestamp) {
        if (currentRunTimestamp != CURRENT_RUN_TIMESTAMP_NOT_INITIALIZED) {
            lastReadinessRunMillis.set(currentRunTimestamp);
        } else {
            lastReadinessRunMillis.set(timeSupplier.getAsLong());
        }
    }

    /**
     * @return Health check interval (seconds).
     */
    private int getReadinessHealthCheckInterval() {
        return Math.max(ArtifactoryHome.get().getSysConfig()
                .getInt(HEALTH_CHECK_INTERVAL_KEY_NAME, DEFAULT_HEALTH_CHECK_INTERVAL), MIN_HEALTH_CHECK_INTERVAL);
    }

    /**
     * @return Readiness failure limit (seconds).
     */
    private int getReadinessFailureLimit() {
        return Math.max(ArtifactoryHome.get().getSysConfig()
                        .getInt(LIVENESS_FAIL_READINESS_DURATION_KEY_NAME, DEFAULT_LIVENESS_FAIL_READINESS_DURATION),
                MIN_LIVENESS_FAIL_READINESS_DURATION);
    }

    /**
     * Check whether Artifactory is in a RUNNING state. Only in this state, Artifactory can be considered "ready". Other
     * states such as CONVERTING, STARTING, STOPPING, etc. are considered as "not ready".
     *
     * @return True if Artifactory is in a RUNNING state. False otherwise.
     */
    private boolean checkIsRunning() {
        try {
            boolean running = artifactoryServers.getCurrentMember().getServerState() == ArtifactoryServerState.RUNNING;
            if (running) {
                log.debug("Readiness running test success");
            }
            return running;
        } catch (Exception e) {
            log.warn("Readiness running test failed with the following error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks whether Artifactory application context is in an "offline" state.
     *
     * @return True if Artifactory is in an "offline" state. False otherwise.
     */
    private boolean checkIsOffline() {
        try {
            boolean offline = ContextHelper.get().isOffline();
            if (!offline) {
                log.debug("Readiness online test success");
            }
            return offline;
        } catch (Exception e) {
            log.warn("Readiness online test failed with the following error: {}", e.getMessage(), e);
            return true;
        }
    }

    /**
     * Checks whether Artifactory can write to data and log directories.
     *
     * @return True if Artifactory can write. False otherwise.
     */
    private boolean checkCanWrite() {
        try {
            boolean canWrite =
                    ArtifactoryHome.get().getDataDir().canWrite() && ArtifactoryHome.get().getLogDir().canWrite();
            if (canWrite) {
                log.debug("Readiness can write test success");
            }
            return canWrite;
        } catch (Exception e) {
            log.warn("Readiness can write test failed with the following error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks whether Artifactory's storage service is ready.
     *
     * @return True if Artifactory's storage service is ready. False otherwise.
     */
    private boolean checkStorageServiceReady() {
        try {
            storageService.ping(true);
            log.debug("Readiness storage service ping success");
            return true;
        } catch (Exception e) {
            log.warn("Readiness storage service ping test failed with the following error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks whether Artifactory's DB service is ready.
     *
     * @return True if Artifactory's DB service is ready. False otherwise.
     */
    private boolean checkInternalDbServiceHealthy() {
        try {
            internalDbService.health();
            log.debug("Readiness internal DB service health success");
            return true;
        } catch (Exception e) {
            log.warn("Readiness internal DB service health test failed with the following error: {}", e.getMessage(),
                    e);
            return false;
        }
    }

    /**
     * Checks whether Artifactory's Access service is ready.
     *
     * @return True if Artifactory's Access service is ready. False otherwise.
     */
    private boolean checkAccessServiceReady() {
        try {
            accessService.readiness();
            log.debug("Readiness Access service test success");
            return true;
        } catch (Exception e) {
            log.warn("Readiness Access service test failed with the following error: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isReadinessCheckStuck() {
        long currentLastReadinessRunMillis = lastReadinessRunMillis.get();
        if (currentLastReadinessRunMillis == 0L) {
            log.debug("Readiness job has never run.");
            return false;
        }
        boolean isReadinessStuck =
                timeSupplier.getAsLong() - currentLastReadinessRunMillis > readinessThresholdTimeoutMillis;
        if (isReadinessStuck) {
            log.warn("Readiness check is stuck. Last readiness check by the readiness job was at {}, which is " +
                     "more than {} seconds ago. Note this gap should be greater than intervalsSec ({}).",
                    DateUtils.formatBuildDate(currentLastReadinessRunMillis),
                    TimeUnit.MILLISECONDS.toSeconds(readinessThresholdTimeoutMillis),
                    getReadinessHealthCheckInterval());
        }
        return isReadinessStuck;
    }

    private boolean checkTomcatIsReady() {
        try {
            boolean result = serverReadinessMonitor.isReady();
            log.debug("Tomcat Server Readiness test completed with status {}", result);
            return result;
        } catch (Exception e) {
            log.warn("Tomcat Server Readiness test failed with the following error: {}. Will return true",
                    e.getMessage(), e);
            return true;
        }
    }
}
