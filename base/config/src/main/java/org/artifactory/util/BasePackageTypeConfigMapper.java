package org.artifactory.util;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.common.JsonSensitiveDataException;
import org.jfrog.common.JsonUtils;

import java.util.List;
import java.util.Map;

/**
 * @author Mor Merhav
 */
public interface BasePackageTypeConfigMapper<T> {

    String MODEL_VERSION = "modelVersion";

    T map(Map<String, String> packageTypeConfig);

    /**
     * Fetch the string from {@code packageTypeConfig} by {@code key} and convert from JSON to list
     * @param key the key to fetch from the map
     * @param packageTypeConfig the package type config map
     * @return null if {@code packageTypeConfig.get(key);} is blank, list of strings otherwise
     *
     * @throws NullPointerException if {@code key} or {@code packageTypeConfig} is null
     * @throws JsonSensitiveDataException if {@code packageTypeConfig.get(key);} is not a JSON list
     */
    default List<String> jsonStringToList(@Nonnull String key, @Nonnull Map<String, String> packageTypeConfig) {
        String strList = packageTypeConfig.get(key);
        return StringUtils.isBlank(strList) ? null :
                JsonUtils.getInstance().readValue(strList, new TypeReference<>() {});
    }
}
