package org.artifactory.descriptor.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.common.JsonUtils;

import java.util.List;
import java.util.Map;

/**
 * @author Tamar Rosen
 */
@UtilityClass
public class RepositoryConfigSource {

    public List<String> jsonStringToList(Map<String, String> packageTypeConfig, String key) {
        String strList = packageTypeConfig.get(key);
        return StringUtils.isBlank(strList) ? null : JsonUtils.getInstance().readValue(strList,
                new TypeReference<>() {});
    }

    public void entityToFlatMapKeys(Object value, String key, Map<String, String> props) {
        if (value instanceof List) {
            props.put(key, JsonUtils.getInstance().writeValueAsString(value));
        } else {
            props.put(key, buildValue(value));
        }
    }

    private String buildValue(Object value) {
        return value == null ? "" : "" + value;
    }
}