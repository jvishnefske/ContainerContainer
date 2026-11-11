package org.artifactory.util;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.artifactory.descriptor.repo.RepoType;

import java.util.Map;
import java.util.Objects;

import static org.artifactory.util.PackageTypeConfigFields.BLOCK_PUSHING_SCHEMA_1;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_API_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_TAG_RETENTION;
import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;
import static org.artifactory.util.PackageTypeConfigFields.OPTIONAL_INDEX_COMPRESSION_FORMATS;
import static org.artifactory.util.PackageTypeConfigFields.SUPPRESS_POM_CONSISTENCY_CHECKS;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_TYPE;

/**
 * @author Mor Merhav
 * A utility which sets the local default package config values per package type.
 * Also see {@link PackageTypeConfigFields}.
 * This class is tested as part of {@link org.artifactory.model.LocalRepoConfig} tests
 */
@UtilityClass
public class LocalDefaultPackageConfigSetter {

    /**
     * Sets default values for local repo package config by {@code packageType}
     *
     * @param packageType       the package type by which to set the default values
     * @param packageTypeConfig the package type config map to set default values on
     * @throws NullPointerException if {@code packageType} param is null
     */
    public void setDefaultValues(RepoType packageType, @Nonnull Map<String, String> packageTypeConfig) {
        Objects.requireNonNull(packageType,
                "Cannot set package config default values. 'packageType' must not be null");

        if (packageType.isMavenGroup()) {
            packageTypeConfig.put(SUPPRESS_POM_CONSISTENCY_CHECKS.getKey(), Boolean.TRUE.toString());
        }

        switch (packageType) {
            case Docker, OCI, HelmOCI -> {
                packageTypeConfig.put(DOCKER_API_VERSION.getKey(), DOCKER_API_VERSION.getDefaultValue());
                packageTypeConfig.put(DOCKER_TAG_RETENTION.getKey(), DOCKER_TAG_RETENTION.getDefaultValue());
                packageTypeConfig.put(BLOCK_PUSHING_SCHEMA_1.getKey(), BLOCK_PUSHING_SCHEMA_1.getDefaultValue());
            }
            case Terraform -> packageTypeConfig.put(TERRAFORM_TYPE.getKey(), TERRAFORM_TYPE.getDefaultValue());
            case TerraformBackend -> packageTypeConfig.put(ENCRYPT_STATES.getKey(), ENCRYPT_STATES.getDefaultValue());
            case Debian -> packageTypeConfig.put(OPTIONAL_INDEX_COMPRESSION_FORMATS.getKey(),
                    OPTIONAL_INDEX_COMPRESSION_FORMATS.getDefaultValue());

            default -> { /*NOOP*/ }
        }
    }
}
