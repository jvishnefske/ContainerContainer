package org.artifactory.logging.layout.federation;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.logging.entry.ServiceEntryAppPart;
import org.artifactory.logging.layout.BaseJsonLayout;
import org.artifactory.logging.layout.SaasLogParts;

import java.util.Map;

import static org.artifactory.logging.SaasLogNames.SERVICE_FEDERATION_LOG;

/**
 * @author Alexei Vainshtein
 */
@Slf4j
public class ServiceFederationJsonLayout extends BaseJsonLayout {
    public ServiceFederationJsonLayout() {
        super();
    }

    @Override
    protected void addCustomDataToJsonMap(Map<String, Object> data, ILoggingEvent event) {
        log.trace("Adding data to artifactory service federation log with json layout");
        ServiceEntryAppPart build = SaasLogParts.getServiceEntryAppPart(data, event);
        log.trace("Clearing original data in artifactory service federation log json layout");
        data.clear();
        data.put(SERVICE_FEDERATION_LOG.getLogKey(), SERVICE_FEDERATION_LOG.getLogName());
        data.put("service_federation", build);
    }
}
