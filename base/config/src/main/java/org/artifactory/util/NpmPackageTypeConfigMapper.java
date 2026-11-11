package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.NpmConfiguration;

/**
 * @author Mor Merhav
 */
public interface NpmPackageTypeConfigMapper extends BasePackageTypeConfigMapper<NpmConfiguration> {

    NpmPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {

        NpmConfiguration config = new NpmConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        config.setExternalDependencies(ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));
        return config;
    };
}