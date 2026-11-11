package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.ComposerConfiguration;

import java.util.Optional;

/**
 * @author Mor Merhav
 */
public interface ComposerPackageTypeConfigMapper extends BasePackageTypeConfigMapper<ComposerConfiguration> {

    String ENABLE_COMPOSER_V1_INDEXING = "enableComposerV1Indexing";
    String COMPOSER_REGISTRY_URL = "composerRegistryUrl";

    ComposerPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        ComposerConfiguration config = new ComposerConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        config.setEnableComposerV1Indexing(
                Boolean.parseBoolean(packageTypeConfig.get(ENABLE_COMPOSER_V1_INDEXING)));
        Optional.ofNullable(packageTypeConfig.get(COMPOSER_REGISTRY_URL)).ifPresent(config::setComposerRegistryUrl);
        config.setVcs(VcsPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));
        return config;
    };
}