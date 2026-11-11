package org.artifactory.repo.converter;

import org.artifactory.model.RepoConfig;
import org.jfrog.common.JsonUtils;

import java.util.Map;
import java.util.Set;

/**
 * @author Tamar Rosen
 */
public class RepositoriesConverterManager {
    Set<RepositoriesConfigSubConverter> subConverterSet;

    public RepositoriesConverterManager(Set<RepositoriesConfigSubConverter> subConverterSet) {
        this.subConverterSet = subConverterSet;
    }

    /**
     * Gets a byte array representing a {@link RepoConfig} implementation
     */
    public RepoConfig convert(byte[] toConvert) {
        byte[] bytes = convertAsBytes(toConvert);
        return JsonUtils.getInstance().readValue(bytes, RepoConfig.class);
    }

    public byte[] convertAsBytes(byte[] toConvert) {
        Map<String, Object> repositoryConfigMap = JsonUtils.getInstance().readValue(toConvert, Map.class);
        subConverterSet.forEach(subConverter -> subConverter.convert(repositoryConfigMap));
        return JsonUtils.getInstance().valueToByteArray(repositoryConfigMap);
    }
}
