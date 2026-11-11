package org.artifactory.storage.db.aql.stats;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe Logback appender for use in AQL stats tests. The worker thread in
 * {@link AqlQueryStatsManager} appends log lines concurrently with the test thread's
 * assertions, so {@link CopyOnWriteArrayList} is required here.
 */
class AqlStatsCapturingAppender extends AppenderBase<ILoggingEvent> {

    final List<String> lines = new CopyOnWriteArrayList<>();

    @Override
    protected void append(ILoggingEvent eventObject) {
        lines.add(eventObject.getFormattedMessage());
    }
}
