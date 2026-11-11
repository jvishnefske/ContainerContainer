package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.HexConfiguration;

import java.util.Map;

/**
 * @author Leela Padmaja
 */
public interface HexPackageTypeConfigMapper extends BasePackageTypeConfigMapper<HexConfiguration> {

    HexPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {

        HexConfiguration config = new HexConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);
        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        config.setHexRepositoryUrl(
                StringUtils.isBlank(packageTypeConfig.get("hexRepositoryUrl")) ? "https://repo.hex.pm" :
                        packageTypeConfig.get(PackageTypeConfigFields.HEX_REGISTRY_URL.getKey()));
        config.setHexPublicKey(
                StringUtils.isBlank(packageTypeConfig.get("hexPublicKey")) ?
                        PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY.getDefaultValue() :
                        packageTypeConfig.get(PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY.getKey()));
        return config;
    };

    @Override
    HexConfiguration map(Map<String, String> packageTypeConfig);
}