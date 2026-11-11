package org.artifactory.util;

import org.artifactory.repo.packagetype.configuration.AnsibleConfiguration;

import java.util.Optional;

import static org.artifactory.util.PackageTypeConfigFields.AUTH_URL;

public interface AnsiblePackageTypeConfigMapper extends BasePackageTypeConfigMapper<AnsibleConfiguration> {
    AnsiblePackageTypeConfigMapper INSTANCE = packageTypeConfig -> {
        AnsibleConfiguration config = new AnsibleConfiguration();
        Optional.ofNullable(packageTypeConfig.get(AUTH_URL.getKey())).ifPresent(config::setAuthUrl);
        return config;
    };
}
