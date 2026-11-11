package org.artifactory.addon.observability;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.Map;

/**
 * @author Tomer Slutsky
 */
@Getter
@AllArgsConstructor
@ToString
public abstract class ObservabilityInfo {

    public static final String LOG_NAME_FIELD = "logName";
    public static final String PRODUCT_NAME_FIELD = "productName";
    public static final String LOG_FILE_FIELD = "logFile";

    private String logName;
    private String productName;
    @ToString.Exclude
    private InputStream logFileInputStream;

    public abstract Map<String, String> getProperties();

    protected void putIfNotBlank(Map<String, String> props, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            props.put(key, value);
        }
    }
}
