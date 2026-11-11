package org.artifactory.addon.observability;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Andrey Tashlitsky
 */
@Getter
@ToString(callSuper = true)
public class LogShippingInfo extends ObservabilityInfo {

    public static final String NODE_ID_FIELD = "nodeId";
    public static final String SERVICE_ID_FIELD = "serviceId";

    private final String nodeId;
    private final String serviceId;

    @Builder
    public LogShippingInfo(String logName, String productName, InputStream logFileInputStream,
            String nodeId, String serviceId) {
        super(logName, productName, logFileInputStream);
        this.nodeId = nodeId;
        this.serviceId = serviceId;
    }

    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        putIfNotBlank(properties, LOG_NAME_FIELD, getLogName());
        putIfNotBlank(properties, PRODUCT_NAME_FIELD, getProductName());
        putIfNotBlank(properties, NODE_ID_FIELD, getNodeId());
        putIfNotBlank(properties, SERVICE_ID_FIELD, getServiceId());
        return properties;
    }
}
