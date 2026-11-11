package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.TerraformBackendConfiguration;

import java.util.Optional;

import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;

/**
 * @author asafz
 */
public interface TerraformBackendPackageTypeConfigMapper
        extends BasePackageTypeConfigMapper<TerraformBackendConfiguration> {

    TerraformBackendPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        TerraformBackendConfiguration config = new TerraformBackendConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));

        Optional.ofNullable(packageTypeConfig.get(ENCRYPT_STATES.getKey()))
                .map(Boolean::parseBoolean)
                .ifPresent(config::setEncryptStates);
        return config;
    };
}