package org.artifactory.probes.model;

import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.common.status.StatusEntry;

import static ch.qos.logback.classic.Level.DEBUG;
import static org.artifactory.probes.model.ReadinessStatusEnum.ACCESS_SERVICE_NOT_READY;
import static org.artifactory.probes.model.ReadinessStatusEnum.CANNOT_WRITE;
import static org.artifactory.probes.model.ReadinessStatusEnum.DB_NOT_HEALTHY;
import static org.artifactory.probes.model.ReadinessStatusEnum.NOT_RUNNING;
import static org.artifactory.probes.model.ReadinessStatusEnum.OFFLINE;
import static org.artifactory.probes.model.ReadinessStatusEnum.READINESS_SUCCESS_MESSAGE;
import static org.artifactory.probes.model.ReadinessStatusEnum.STORAGE_SERVICE_NOT_READY;
import static org.artifactory.probes.model.ReadinessStatusEnum.TOMCAT_NOT_READY;

/**
 * Readiness status object to store all the readiness checks result.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
@Builder
public class ReadinessStatus {

    /**
     * True if Artifactory is in RUNNING state. All other states (CONVERTING, STARTING, etc) are considered "false".
     */
    private final boolean running;
    /**
     * True if Artifactory application context is in Offline state.
     */
    private final boolean offline;
    /**
     * True if Artifactory can write to disk.
     */
    private final boolean canWrite;
    /**
     * True if the storage service (binary provider) is ready.
     */
    private final boolean storageServiceReady;
    /**
     * True if the DB service is ready.
     */
    private final boolean internalDbServiceHealthy;
    /**
     * True if Access service is ready.
     */
    private final boolean accessServiceReady;
    /**
     * True if Tomcat Server is ready.
     */
    private final boolean tomcatReady;

    /**
     * Returns the readiness status.
     *
     * @return True if all the conditions for readiness are met. False otherwise.
     */
    public boolean isReady() {
        return running && !offline && canWrite && storageServiceReady && internalDbServiceHealthy
                && accessServiceReady && tomcatReady;
    }

    public BasicStatusHolder getStatusHolder() {
        BasicStatusHolder tmpStatusHolder = new BasicStatusHolder();

        if (!running) {
            tmpStatusHolder.errorWithDifferentLogLevel(NOT_RUNNING.getMessage(),
                    NOT_RUNNING.getStatusCode(), NOT_RUNNING.getException(), log, DEBUG);
        }
        if (offline) {
            tmpStatusHolder.errorWithDifferentLogLevel(OFFLINE.getMessage(), OFFLINE.getStatusCode(),
                    OFFLINE.getException(), log, DEBUG);
        }
        if (!canWrite) {
            tmpStatusHolder.errorWithDifferentLogLevel(CANNOT_WRITE.getMessage(),
                    CANNOT_WRITE.getStatusCode(), CANNOT_WRITE.getException(), log, DEBUG);
        }
        if (!storageServiceReady) {
            tmpStatusHolder.errorWithDifferentLogLevel(STORAGE_SERVICE_NOT_READY.getMessage(),
                    STORAGE_SERVICE_NOT_READY.getStatusCode(), STORAGE_SERVICE_NOT_READY.getException(), log, DEBUG);
        }
        if (!internalDbServiceHealthy) {
            tmpStatusHolder.errorWithDifferentLogLevel(DB_NOT_HEALTHY.getMessage(),
                    DB_NOT_HEALTHY.getStatusCode(), DB_NOT_HEALTHY.getException(), log, DEBUG);
        }
        if (!accessServiceReady) {
            tmpStatusHolder.errorWithDifferentLogLevel(ACCESS_SERVICE_NOT_READY.getMessage(),
                    ACCESS_SERVICE_NOT_READY.getStatusCode(), ACCESS_SERVICE_NOT_READY.getException(), log, DEBUG);
        }
        if (!tomcatReady) {
            tmpStatusHolder.errorWithDifferentLogLevel(TOMCAT_NOT_READY.getMessage(),
                    TOMCAT_NOT_READY.getStatusCode(), TOMCAT_NOT_READY.getException(), log, DEBUG);
        }
        if (!tmpStatusHolder.hasErrorsOrWarnings()) {
            log.debug(READINESS_SUCCESS_MESSAGE);
            return tmpStatusHolder;
        }

        return createOverallStatusHolder(tmpStatusHolder);
    }

    private static BasicStatusHolder createOverallStatusHolder(BasicStatusHolder tmpStatusHolder) {
        BasicStatusHolder resultStatusHolder = new BasicStatusHolder();
        String overallStatusMsg = tmpStatusHolder.getAggregatedStatusMsg();
        int statusCode  = tmpStatusHolder.getStatusCode();
        RuntimeException exception = null;
        StatusEntry mostImportantErrorStatusEntry = tmpStatusHolder.getMostImportantErrorStatusCode();
        if (mostImportantErrorStatusEntry != null) {
            exception = mostImportantErrorStatusEntry.getRuntimeException()
                    .map(e -> createOverallException(e, overallStatusMsg))
                    .orElse(null);
        }
        resultStatusHolder.errorWithDifferentLogLevel(overallStatusMsg, statusCode, exception, log, DEBUG);
        return resultStatusHolder;
    }

    private static RuntimeException createOverallException(RuntimeException runtimeException, String overallStatusMsg) {
        if (runtimeException.getClass().equals(InternalServerErrorException.class)) {
            return new InternalServerErrorException(overallStatusMsg);
        } else if (runtimeException.getClass().equals(ServiceUnavailableException.class)) {
            return new ServiceUnavailableException(overallStatusMsg);
        }
        return null;
    }
}