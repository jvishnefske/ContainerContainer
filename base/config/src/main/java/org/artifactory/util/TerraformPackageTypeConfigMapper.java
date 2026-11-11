package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.TerraformType;
import org.artifactory.repo.packagetype.configuration.TerraformConfiguration;

import java.util.Optional;

public interface TerraformPackageTypeConfigMapper extends BasePackageTypeConfigMapper<TerraformConfiguration> {

    final String TERRAFORM_REGISTRY_URL = "terraformRegistryUrl";

    TerraformPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        if (packageTypeConfig == null) {
            throw new IllegalArgumentException("Package type config can not be null");
        }
        TerraformConfiguration config = new TerraformConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        Optional.ofNullable(packageTypeConfig.get("terraformProvidersUrl"))
                .ifPresent(config::setTerraformProvidersUrl);
        Optional.ofNullable(packageTypeConfig.get("terraformRegistryUrl"))
                .ifPresent(config::setTerraformRegistryUrl);
        Optional.ofNullable(packageTypeConfig.get("terraformType"))
                .map(TerraformType::valueOfIgnoreCase)
                .ifPresent(config::setTerraformType);

        config.setExternalDependencies(ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));

        return config;
    };
}