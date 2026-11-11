package org.artifactory.event.work;

import org.artifactory.storage.db.event.error.model.ErrorStatistics;

/**
 * @author Mor Merhav
 * @author avishayh
 */
@FunctionalInterface
public interface NodeEventsErrorsBackOffStrategy {

    boolean isBackoffDelayRequired(ErrorStatistics error, long currentTime);
}
