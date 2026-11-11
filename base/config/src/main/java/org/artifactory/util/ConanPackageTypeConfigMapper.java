package org.artifactory.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.packagetype.configuration.ConanConfiguration;

/**
 * @author Mor Merhav
 */
public interface ConanPackageTypeConfigMapper extends BasePackageTypeConfigMapper<ConanConfiguration> {

    String FORCE_CONAN_AUTHENTICATION = "forceConanAuthentication";

    ConanPackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        ConanConfiguration config = new ConanConfiguration();
        String modelVersion = packageTypeConfig.get(MODEL_VERSION);

        config.setModelVersion(StringUtils.isBlank(modelVersion) ? 0L : Long.parseLong(modelVersion));
        config.setForceConanAuthentication(Boolean.parseBoolean(packageTypeConfig.get(FORCE_CONAN_AUTHENTICATION)));
        return config;
    };
}