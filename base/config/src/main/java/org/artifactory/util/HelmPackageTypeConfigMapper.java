package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.HelmConfiguration;

import java.util.Optional;

/**
 * @author Tamar Rosen
 */
public interface HelmPackageTypeConfigMapper extends BasePackageTypeConfigMapper<HelmConfiguration> {
    String CHARTS_BASE_URL = "chartsBaseUrl";
    String USE_NAMESPACES = "useNamespaces";
    String FORCE_NON_DUPLICATE_CHART = "forceNonDuplicateChart";
    String FORCE_METADATA_NAME_VERSION = "forceMetadataNameVersion";
    HelmPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        HelmConfiguration config = new HelmConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));

        config.setExternalDependencies(ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));

        config.setChartsBaseUrl(packageTypeConfig.get(CHARTS_BASE_URL));
        Optional.ofNullable(packageTypeConfig.get(USE_NAMESPACES))
                .map(Boolean::parseBoolean)
                .ifPresent(config::setUseNamespaces);
        Optional.ofNullable(packageTypeConfig.get(FORCE_NON_DUPLICATE_CHART))
                .map(Boolean::parseBoolean)
                .ifPresent(config::setForceNonDuplicateChart);
        Optional.ofNullable(packageTypeConfig.get(FORCE_METADATA_NAME_VERSION))
                .map(Boolean::parseBoolean)
                .ifPresent(config::setForceMetadataNameVersion);
        return config;
    };
}