package org.artifactory.logging.layout.access;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.logging.entry.AccessEntryPart;
import org.artifactory.logging.layout.BaseJsonLayout;
import org.artifactory.logging.layout.SaasLogParts;

import java.util.Map;

import static org.artifactory.logging.SaasLogNames.ACCESS_LOG;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public class AccessJsonLayout extends BaseJsonLayout {
    public AccessJsonLayout() {
        super();
    }

    @Override
    protected void addCustomDataToJsonMap(Map<String, Object> data, ILoggingEvent event) {
        log.trace("Adding data to artifactory access log with json layout");
        AccessEntryPart part = SaasLogParts.getAccessEntryPart(data, event);
        log.trace("Clearing original data in artifactory access log json layout");
        data.clear();
        data.put(ACCESS_LOG.getLogKey(), ACCESS_LOG.getLogName());
        data.put("access", part);
    }
}