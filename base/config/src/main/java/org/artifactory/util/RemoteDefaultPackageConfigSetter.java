package org.artifactory.util;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.artifactory.descriptor.repo.RepoType;

import java.util.Map;
import java.util.Objects;

import static org.artifactory.util.PackageTypeConfigFields.BLOCK_PUSHING_SCHEMA_1;
import static org.artifactory.util.PackageTypeConfigFields.BOWER_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_SPECS_URL;
import static org.artifactory.util.PackageTypeConfigFields.COMPOSER_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_API_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_PROJECT_ID;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_TAG_RETENTION;
import static org.artifactory.util.PackageTypeConfigFields.GITLAB_RESOLVE_SUBGROUPS;
import static org.artifactory.util.PackageTypeConfigFields.PYPI_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.PYPI_REPOSITORY_SUFFIX;
import static org.artifactory.util.PackageTypeConfigFields.SPECS_REPO_DOWNLOAD_PROVIDER;
import static org.artifactory.util.PackageTypeConfigFields.SUPPRESS_POM_CONSISTENCY_CHECKS;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_PROVIDERS_URL;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.VCS_GIT_PROVIDER;
import static org.artifactory.util.PackageTypeConfigFields.VCS_TYPE;

/**
 * @author Mor Merhav
 * A utility which sets the remote default package config values per package type.
 * Also see {@link PackageTypeConfigFields}.
 * This class is tested as part of {@link org.artifactory.model.RemoteRepoConfig} tests
 */
@UtilityClass
public class RemoteDefaultPackageConfigSetter {

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
                packageTypeConfig.put(DOCKER_PROJECT_ID.getKey(), DOCKER_PROJECT_ID.getDefaultValue());
            }
            case Bower -> packageTypeConfig.put(BOWER_REGISTRY_URL.getKey(), BOWER_REGISTRY_URL.getDefaultValue());
            case CocoaPods -> {
                packageTypeConfig.put(COCOA_PODS_SPECS_URL.getKey(), COCOA_PODS_SPECS_URL.getDefaultValue());
                packageTypeConfig.put(SPECS_REPO_DOWNLOAD_PROVIDER.getKey(),
                        SPECS_REPO_DOWNLOAD_PROVIDER.getDefaultValue());
                setVcsConfig(packageTypeConfig);
            }
            case Composer -> {
                packageTypeConfig.put(COMPOSER_REGISTRY_URL.getKey(), COMPOSER_REGISTRY_URL.getDefaultValue());
                setVcsConfig(packageTypeConfig);
            }
            case Pypi -> {
                packageTypeConfig.put(PYPI_REGISTRY_URL.getKey(), PYPI_REGISTRY_URL.getDefaultValue());
                packageTypeConfig.put(PYPI_REPOSITORY_SUFFIX.getKey(), PYPI_REPOSITORY_SUFFIX.getDefaultValue());
            }
            case VCS -> setVcsConfig(packageTypeConfig);
            case Go -> {
                packageTypeConfig.put(GITLAB_RESOLVE_SUBGROUPS.getKey(),
                        GITLAB_RESOLVE_SUBGROUPS.getDefaultValue());
                setVcsConfig(packageTypeConfig);
            }
            case Terraform -> {
                packageTypeConfig.put(TERRAFORM_PROVIDERS_URL.getKey(), TERRAFORM_PROVIDERS_URL.getDefaultValue());
                packageTypeConfig.put(TERRAFORM_REGISTRY_URL.getKey(), TERRAFORM_REGISTRY_URL.getDefaultValue());
                setVcsConfig(packageTypeConfig);
            }
            case AIEditorExtensions -> {
                packageTypeConfig.put(PackageTypeConfigFields.VSCODE_EXTERNAL_DEPENDENCIES_ENABLED.getKey(),
                        PackageTypeConfigFields.VSCODE_EXTERNAL_DEPENDENCIES_ENABLED.getDefaultValue());
                packageTypeConfig.put(PackageTypeConfigFields.VSCODE_EXTERNAL_DEPENDENCIES_PATTERNS.getKey(),
                        PackageTypeConfigFields.VSCODE_EXTERNAL_DEPENDENCIES_PATTERNS.getDefaultValue());
            }
            default -> {/*NOOP*/}
        }
    }

    private void setVcsConfig(Map<String, String> packageTypeConfig) {
        packageTypeConfig.put(VCS_GIT_PROVIDER.getKey(), VCS_GIT_PROVIDER.getDefaultValue());
        packageTypeConfig.put(VCS_TYPE.getKey(), VCS_TYPE.getDefaultValue());
    }
}
