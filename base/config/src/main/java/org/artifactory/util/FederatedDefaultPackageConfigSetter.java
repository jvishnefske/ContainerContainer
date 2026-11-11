package org.artifactory.util;

import lombok.experimental.UtilityClass;
import org.artifactory.descriptor.repo.RepoType;

import java.util.Map;

/**
 * @author Mor Merhav
 * A utility which sets the federated default package config values per package type.
 * Currently, the default values for federated and local repo configs are identical, thus
 * {@link LocalDefaultPackageConfigSetter} is used. If any discrepancy in default values between the repo types is
 * introduced, this must be changed and new tests should be written.
 * Also see {@link PackageTypeConfigFields}.
 * This class is tested as part of {@link org.artifactory.model.LocalConfig} tests
 */
@UtilityClass
public class FederatedDefaultPackageConfigSetter  {

    /**
     * @see LocalDefaultPackageConfigSetter#setDefaultValues
     */
    public void setDefaultValues(RepoType packageType, Map<String,String> packageTypeConfig) {
        LocalDefaultPackageConfigSetter.setDefaultValues(packageType, packageTypeConfig);
    }
}
