package org.artifactory.metrics.providers.features;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Idan Dadon
 */
@UtilityClass
public class StorageFeatureWhitelist {
    private final Set<String> whitelist = Set.of("type", "region", "readBehavior", "writeBehavior");

    public Map<String, String> whitelist(Map<String, String> originalMap) {
        Map<String, String> result = new HashMap<>();
        originalMap.forEach((key, value) -> {
            if (whitelist.contains(key) || isBooleanOrInteger(value)) {
                result.put(key, value);
            }
        });
        return result;
    }

    private boolean isBooleanOrInteger(String value) {
        return StringUtils.isNotEmpty(value) &&
                (StringUtils.isNumeric(value) || "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value));
    }
}
