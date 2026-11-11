package org.artifactory.ph.repo.interceptor;

import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.ph.repo.provider.PackageRemoteRepoSettingsProvider;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Interceptor that applies pre-defined package remote repository settings during the creation of remote repositories.
 * It checks for applicable settings providers and applies their configurations to the new remote repository.
 *
 * @author Dor Shriki
 */
@Component
public class PackageRemoteRepoConfigurationInterceptor implements RepoConfigChangeInterceptor {

    private final List<PackageRemoteRepoSettingsProvider> settingsProviders;

    @Autowired
    public PackageRemoteRepoConfigurationInterceptor(List<PackageRemoteRepoSettingsProvider> settingsProviders) {
        this.settingsProviders = settingsProviders;
    }

    @Override
    public void onBeforeSave(RepoConfigChanges repoConfigChanges) {
        repoConfigChanges.filterBy(RepoConfigChange.ConfigAction.REPO_CREATE ,RepoDetailsType.REMOTE)
                .forEach(change -> {
                    RemoteRepoConfig remoteConfig = (RemoteRepoConfig) change.getNewRepoConfig();
                    settingsProviders.stream()
                            .filter(provider -> shouldApplyProvider(provider, remoteConfig))
                            .forEach(provider -> provider.applySettings(remoteConfig));
                });
    }

    private boolean shouldApplyProvider(PackageRemoteRepoSettingsProvider provider, RemoteRepoConfig remoteConfig) {
        return remoteConfig != null &&
                provider.isPackageTypeSupported(remoteConfig) &&
                provider.isApplicable(remoteConfig);
    }
}
