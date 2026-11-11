package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.GitConfiguration;

/**
 * @author Tamar Rosen
 */
public interface GitPackageTypeConfigMapper extends BasePackageTypeConfigMapper<GitConfiguration> {

    String GIT_REGISTRY_URL = "gitRegistryUrl";

    GitPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        GitConfiguration config = new GitConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));

        String gitRegistryUrl = packageTypeConfig.get(GIT_REGISTRY_URL);
        if (StringUtils.isNotBlank(gitRegistryUrl)) {
            config.setRegistryUrl(gitRegistryUrl);
        }

        return config;
    };
}