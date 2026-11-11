package org.artifactory.logging.layout.request;

import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.logging.layout.BaseJsonLayout;
import org.artifactory.logging.layout.SaasLogParts;

import java.util.Map;

import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_GROUP;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_PACKAGE;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_REPO_KEY;
import static org.artifactory.activity.ActivityConstants.ACTIVITY_PROP_REPO_TYPE;
import static org.artifactory.logging.SaasLogNames.OUTBOUND_REQUEST_LOG;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public class OutboundRequestJsonLayout extends BaseJsonLayout {

    private static final int METADATA_PARAM_INDEX = 10;

    public OutboundRequestJsonLayout() {
        super();
    }

    @Override
    protected void addCustomDataToJsonMap(Map<String, Object> data, ILoggingEvent event) {
        log.trace("Clearing original data in artifactory request out log json layout");
        data.clear();
        data.put(OUTBOUND_REQUEST_LOG.getLogKey(), OUTBOUND_REQUEST_LOG.getLogName());
        log.trace("Adding request to artifactory request out log with json layout");
        data.put("request", SaasLogParts.getOutboundRequestEntryPart(event));
        log.trace("Adding response to artifactory request log with json layout");
        data.put("response", SaasLogParts.getResponseEntryPart(event));
        addContextFields(data, event);
    }

    protected void addContextFields(Map<String, Object> data, ILoggingEvent event) {
        final Map<String, String> mdc = event.getMDCPropertyMap();
        String repoTypeChar = mdc.get(ACTIVITY_PROP_REPO_TYPE) != null ?
                mdc.get(ACTIVITY_PROP_REPO_TYPE).substring(0, 1) : null;
        add("package", true, mdc.get(ACTIVITY_PROP_PACKAGE), data);
        add("group", true, mdc.get(ACTIVITY_PROP_GROUP), data);
        add(RequestExtraFields.REPO, true, mdc.get(ACTIVITY_PROP_REPO_KEY), data);
        add(RequestExtraFields.REPO_TYPE, true, repoTypeChar, data);
        extractMetadataFromExtraLogParam(data, event, METADATA_PARAM_INDEX);
    }
}
