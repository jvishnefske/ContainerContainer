package org.artifactory.logging.layout;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.contrib.json.classic.JsonLayout;
import ch.qos.logback.core.CoreConstants;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.common.JsonUtils;

import java.util.Map;

/**
 * @author Tomer Slutsky
 */
@Slf4j
public class BaseJsonLayout extends JsonLayout {

    public BaseJsonLayout() {
        this.appendLineSeparator = true;
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, Object> data = toJsonMap(event);
        log.trace("Converting log data to string");
        String finalLog = JsonUtils.getInstance().valueToString(data);
        log.trace("Finished log data to string");
        return this.isAppendLineSeparator() ? finalLog + CoreConstants.LINE_SEPARATOR : finalLog;
    }

    /**
     * When Artifactory will stop using an old version of Logback in integration tests,
     * we will pass metadata as KeyValue pairs, attached to the event. Until then, we will
     * use an extra argument in the event to pass metadata.
     *
     * @param customJsonDataMap  a map of params that will be added to the JSON by
     *                           {@link JsonLayout#addCustomDataToJsonMap} method's implementations
     * @param event              a logging event that might contain "metadata" map as an additional logging param
     * @param metadataParamIndex index of the additional log param that contains a metadata map
     */
    protected void extractMetadataFromExtraLogParam(Map<String, Object> customJsonDataMap, ILoggingEvent event,
            int metadataParamIndex) {
        if (metadataParamIndex >= 0 && event.getArgumentArray().length > metadataParamIndex &&
                event.getArgumentArray()[metadataParamIndex] instanceof Map<?, ?> metaData) {
            for (Map.Entry<?, ?> entry : metaData.entrySet()) {
                String key = entry.getKey().toString();
                if (entry.getValue() != null) {
                    customJsonDataMap.putIfAbsent(key, entry.getValue());
                }
            }
        }
    }
}
