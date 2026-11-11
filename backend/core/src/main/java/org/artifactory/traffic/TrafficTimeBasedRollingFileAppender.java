package org.artifactory.traffic;

import lombok.extern.slf4j.Slf4j;
import org.jfrog.common.logging.logback.rolling.TimeBasedRollingFileAppenderBase;

import java.io.File;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
public class TrafficTimeBasedRollingFileAppender extends TimeBasedRollingFileAppenderBase {

    private static final String ROLLOVER_THREAD_ENABLED_PROPERTY = "artifactory.traffic.rollover.thread.enabled";

    public TrafficTimeBasedRollingFileAppender() {
        super(ROLLOVER_THREAD_ENABLED_PROPERTY);
    }

    /**
     * Skips the interval-based rollover while the active file is empty, preventing 0-byte files from being
     * rotated into the archive and exhausting the rolling index window during periods of no traffic.
     */
    @Override
    protected void internalRollover() {
        String activeFile = getFile();
        if (isBlank(activeFile) || new File(activeFile).length() == 0) {
            log.debug("Skipping rollover for appender {}, active file {} is empty", getName(), activeFile);
            return;
        }
        super.internalRollover();
    }
}
