package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.HuggingFaceMLConfiguration;

import java.util.Optional;

/**
 * @author Volodymyr Shulga
 */
public interface HuggingFaceMLPackageTypeConfigMapper extends BasePackageTypeConfigMapper<HuggingFaceMLConfiguration> {
    String IS_ML_REPO_LAYOUT = "isMlRepoLayout";
    HuggingFaceMLPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        HuggingFaceMLConfiguration config = new HuggingFaceMLConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        Optional.ofNullable(packageTypeConfig.get(IS_ML_REPO_LAYOUT))
                .map(Boolean::parseBoolean)
                .ifPresent(config::setMlRepoLayout);
        return config;
    };
}