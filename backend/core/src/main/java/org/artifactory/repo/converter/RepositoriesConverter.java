package org.artifactory.repo.converter;

import org.jfrog.common.JsonUtils;

import java.util.Map;

/**
 * @author Tamar Rosen
 */
public interface RepositoriesConverter {
    void convert(Map<String, Object> repositoryConfigMap);

    default Map<String, Object> deserializeMap(Object obj) {
        return JsonUtils.getInstance().readValue(JsonUtils.getInstance().valueToByteArray(obj), Map.class);
    }
}
