package org.artifactory.usage.logger;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.usage.entry.UsageConsumptionEntry;

/**
 * Logger class for the Usage Consumption log.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
@UtilityClass
public class UsageConsumptionLogger {

    /**
     * Log the usage consumption entries to artifactory-usage.log.
     *
     * @param usageConsumptionEntry The usage consumption entry to log.
     */
    public void logEntry(UsageConsumptionEntry usageConsumptionEntry) {
        log.info(usageConsumptionEntry.toString());
    }
}
