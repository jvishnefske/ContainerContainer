package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.CargoConfiguration;

import java.util.Optional;

import static org.artifactory.util.GitPackageTypeConfigMapper.GIT_REGISTRY_URL;

/**
 * @author Tamar Rosen
 */
public interface CargoPackageTypeConfigMapper extends BasePackageTypeConfigMapper<CargoConfiguration> {

    String CARGO_ANONYMOUS_ACCESS = "cargoAnonymousAccess";
    String CARGO_INTERNAL_INDEX = "cargoInternalIndex";

    CargoPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        CargoConfiguration config = new CargoConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));

        Optional.ofNullable(packageTypeConfig.get(CARGO_ANONYMOUS_ACCESS))
                .map(Boolean::parseBoolean)
                .ifPresent(config::setCargoAnonymousAccess);

        Optional.ofNullable(packageTypeConfig.get(CARGO_INTERNAL_INDEX))
                .map(Boolean::parseBoolean)
                .ifPresent(config::setCargoInternalIndex);

        String gitRegistryUrl = packageTypeConfig.get(GIT_REGISTRY_URL);
        if (StringUtils.isNotBlank(gitRegistryUrl)) {
            config.setGit(GitPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig));
        }
        return config;
    };
}