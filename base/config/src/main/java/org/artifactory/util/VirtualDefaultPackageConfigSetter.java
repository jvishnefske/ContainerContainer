package org.artifactory.util;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.artifactory.descriptor.repo.RepoType;

import java.util.Map;
import java.util.Objects;

import static org.artifactory.util.PackageTypeConfigFields.DEBIAN_DEFAULT_ARCHITECTURES;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_API_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.POM_REPOSITORY_REFERENCES_CLEANUP_POLICY;

/**
 * @author Mor Merhav
 * A utility which sets the virtual default package config values per package type.
 * Also see {@link PackageTypeConfigFields}.
 * This class is tested as part of {@link org.artifactory.model.VirtualRepoConfig} tests
 */
@UtilityClass
public class VirtualDefaultPackageConfigSetter {

    public void setDefaultValues(RepoType packageType, @Nonnull Map<String,String> packageTypeConfig) {
        Objects.requireNonNull(packageType,
                "Cannot set package config default values. 'packageType' must not be null");

        if (packageType.isMavenGroup()) {
            packageTypeConfig.put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(),
                    POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getDefaultValue());
        }

        switch (packageType) {
            case Debian -> packageTypeConfig.put(DEBIAN_DEFAULT_ARCHITECTURES.getKey(),
                    DEBIAN_DEFAULT_ARCHITECTURES.getDefaultValue());
            case Docker, OCI,HelmOCI
                    -> packageTypeConfig.put(DOCKER_API_VERSION.getKey(), DOCKER_API_VERSION.getDefaultValue());

            default -> { /*NOOP*/ }
        }
    }
}
