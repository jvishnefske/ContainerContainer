package org.artifactory.addon.observability;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Oleksandr Legenkiy
 */
@Getter
@ToString(callSuper = true)
public class UsageInfo extends ObservabilityInfo {

    public static final String NODE_ID_FIELD = "nodeId";
    public static final String SERVICE_ID_FIELD = "serviceId";
    public static final String TYPE_FIELD = "type";
    public static final String CONSOLIDATED_FIELD = "consolidated";

    private final String nodeId;
    private final String serviceId;
    private final String type;
    private final String consolidated;

    @Builder
    public UsageInfo(String logName, String productName, String type, InputStream logFileInputStream, String nodeId,
            String serviceId, String consolidated) {
        super(logName, productName, logFileInputStream);
        this.nodeId = nodeId;
        this.serviceId = serviceId;
        this.type = type;
        this.consolidated = consolidated;
    }

    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        putIfNotBlank(properties, LOG_NAME_FIELD, getLogName());
        putIfNotBlank(properties, PRODUCT_NAME_FIELD, getProductName());
        putIfNotBlank(properties, TYPE_FIELD, getType());
        putIfNotBlank(properties, NODE_ID_FIELD, getNodeId());
        putIfNotBlank(properties, SERVICE_ID_FIELD, getServiceId());
        putIfNotBlank(properties, CONSOLIDATED_FIELD, getConsolidated());
        return properties;
    }
}
