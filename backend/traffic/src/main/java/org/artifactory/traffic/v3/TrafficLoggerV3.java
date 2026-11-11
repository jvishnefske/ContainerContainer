package org.artifactory.traffic.v3;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.traffic.TrafficEntry;

/**
 * Logger class for traffic events in version 3
 *
 * @author Dvir Twina
 */
@Slf4j
@UtilityClass
public class TrafficLoggerV3 {

    /**
     * Log a transfer (upload/download/redirect) event
     *
     * @param entry Traffic entry to log
     */
    public void logEntry(TrafficEntry entry) {
        log.info(entry.toString());
    }
}
