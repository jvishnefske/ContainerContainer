package org.artifactory.sha2;

import org.artifactory.api.rest.sha2.Sha256MigrationModel;
import org.artifactory.async.Async;
import org.artifactory.schedule.Task;

/**
 * @author Inbar Tal
 */
public interface Sha256MigrationTaskHelper {

    @Async
    void stopMigrationTask(long sleepIntervalMillis);

    Task createManualTask(Sha256MigrationModel model);

    boolean shouldPropagateStartRequest(String serverId);

    boolean shouldPropagateStopRequest();

    void assertNotAol();

    boolean jobIsAlreadyRunning();
}