package org.artifactory.ph.repo.provider;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;

/**
 * Provides default settings for NPM remote repositories that use the GitHub package registry.
 * Specifically, it enforces the "Bypass HEAD Requests" setting to be true for such repositories.
 *
 * @author Dor Shriki
 */
@Slf4j
@Component
public class NpmGithubRegistrySettingsProviderPackage implements PackageRemoteRepoSettingsProvider {

    private static final String NPM_GITHUB_URL = "https://npm.pkg.github.com";

    @Override
    public boolean isPackageTypeSupported(@Nonnull RemoteRepoConfig config) {
        boolean featureEnabled = ConstantValues.npmRemoteEnforceDefaultConfigurationEnabled.getBoolean();
        return featureEnabled && RepoType.Npm.equals(config.getPackageType());
    }

    @Override
    public boolean isApplicable(@Nonnull RemoteRepoConfig config) {
        String url = config.getRepoTypeConfig().getUrl();
        return StringUtils.isNotEmpty(url) && url.startsWith(NPM_GITHUB_URL);

    }

    @Override
    public void applySettings(@Nonnull RemoteRepoConfig config) {
        if (!config.getRepoTypeConfig().isBypassHeadRequests()) {
            log.info(
                    "Applying default Npm Github repository configuration, setting 'Bypass HEAD Requests' to true " +
                            "for: " +
                            "'{}' for following remote repo '{}'",
                    config.getKey(), config.getRepoTypeConfig().getUrl());
            config.getRepoTypeConfig().setBypassHeadRequests(true);
        }
    }
}

