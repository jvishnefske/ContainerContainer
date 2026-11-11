package org.artifactory.logging.layout.request;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.logging.layout.BaseJsonLayout;
import org.artifactory.logging.layout.SaasLogParts;

import java.util.Map;

import static org.artifactory.logging.SaasLogNames.STORAGE_OUTBOUND_REQUEST_LOG;

/**
 * @author Idan Dadon
 */
@Slf4j
public class StorageOutboundRequestJsonLayout  extends BaseJsonLayout {
    public StorageOutboundRequestJsonLayout() {
        super();
    }

    @Override
    protected void addCustomDataToJsonMap(Map<String, Object> data, ILoggingEvent event) {
        log.trace("Clearing original data in storage request out log json layout");
        data.clear();
        data.put(STORAGE_OUTBOUND_REQUEST_LOG.getLogKey(), STORAGE_OUTBOUND_REQUEST_LOG.getLogName());
        log.trace("Adding request to storage request out log with json layout");
        data.put("request", SaasLogParts.getStorageOutboundRequestEntryPart(event));
        log.trace("Adding response to storage request log with json layout");
        data.put("response", SaasLogParts.getStorageResponseEntryPart(event));
    }
}
