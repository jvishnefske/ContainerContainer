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
public class BillingInfo extends ObservabilityInfo {

    public static final String FEATURE_FIELD = "feature";
    public static final String TYPE_FIELD = "type";

    private final String feature;
    private final String type;

    @Builder
    public BillingInfo(String logName, String productName, String type, InputStream logFileInputStream,
            String feature) {
        super(logName, productName, logFileInputStream);
        this.feature = feature;
        this.type = type;
    }

    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        putIfNotBlank(properties, LOG_NAME_FIELD, getLogName());
        putIfNotBlank(properties, PRODUCT_NAME_FIELD, getProductName());
        putIfNotBlank(properties, TYPE_FIELD, getType());
        putIfNotBlank(properties, FEATURE_FIELD, getFeature());
        return properties;
    }
}
