package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.BowerConfiguration;

/**
 * @author Mor Merhav
 */
public interface BowerPackageTypeConfigMapper extends BasePackageTypeConfigMapper<BowerConfiguration> {

    String BOWER_REGISTRY_URL = "bowerRegistryUrl";

    BowerPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        BowerConfiguration config = new BowerConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setExternalDependencies(ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        String bowerRegistryUrl = packageTypeConfig.get(BOWER_REGISTRY_URL);
        if (StringUtils.isNotBlank(bowerRegistryUrl)) {
            config.setBowerRegistryUrl(bowerRegistryUrl);
        }

        config.setVcs(VcsPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));
        return config;
    };
}