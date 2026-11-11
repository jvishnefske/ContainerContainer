package org.artifactory.event.migration;

import org.quartz.JobExecutionException;

public interface EventTableRepoKeyMigration {
    void eventTableRepoKeyMigrationRetryManager() throws JobExecutionException;
}
