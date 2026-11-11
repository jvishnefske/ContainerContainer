package org.artifactory.usage.appender;

import org.jfrog.common.logging.logback.rolling.TimeBasedRollingFileAppenderBase;

/**
 * @author Andrey Tashlitsky
 */
public class UsageTrafficTimeBasedRollingFileAppender extends TimeBasedRollingFileAppenderBase {

    private static final String ROLLOVER_THREAD_ENABLED_PROPERTY = "artifactory.usage.rollover.thread.enabled";

    public UsageTrafficTimeBasedRollingFileAppender() {
        super(ROLLOVER_THREAD_ENABLED_PROPERTY);
    }
}
