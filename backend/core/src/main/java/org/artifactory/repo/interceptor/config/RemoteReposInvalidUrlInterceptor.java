package org.artifactory.repo.interceptor.config;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.modle.AllowedRemoteUrlsConfig;
import org.artifactory.repo.remote.interceptor.RemoteRepoInterceptor;
import org.artifactory.service.ProhibitedUrlsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;

/**
 * Intercepts repository configuration changes and runtime downloads to validate remote URLs against the allowed
 * remote URLs allowlist.
 *
 * @author Yuval Ohayon
 */
@Slf4j
@Component
public class RemoteReposInvalidUrlInterceptor implements RepoConfigChangeInterceptor, RemoteRepoInterceptor {

    private ProhibitedUrlsService prohibitedUrlsService;

    @Autowired
    public void setProhibitedUrlsService(ProhibitedUrlsService prohibitedUrlsService) {
        this.prohibitedUrlsService = prohibitedUrlsService;
    }

    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        mutableRepoConfigChanges.getOrDefault(RepoDetailsType.REMOTE, List.of()).stream()
                .filter(this::isCreateOrUpdate)
                .filter(this::isRelevantChange)
                .map(this::extractRemoteRepoConfig)
                .filter(Objects::nonNull)
                .forEach(this::validateRemoteRepoUrl);
    }

    @Override
    public boolean isRemoteDownloadAllowed(RemoteRepoConfig repoConfig, RepoPath remoteRepoPath) {
        return shouldAllowUrl(repoConfig);
    }

    private void validateRemoteRepoUrl(RemoteRepoConfig remoteRepoConfig) {
        if (!shouldAllowUrl(remoteRepoConfig)) {
            String url = remoteRepoConfig.getRepoTypeConfig().getUrl();
            throw new ConfigurationException(String.format(
                    "The URL '%s' isn’t valid for '%s' repositories. Enter a valid URL for the repository '%s'.",
                    url, remoteRepoConfig.getPackageType(), remoteRepoConfig.getKey()));
        }
    }

    /**
     * Shared validation logic for both config-time and runtime checks.
     * Returns {@code true} if the URL is allowed, {@code false} if it should be blocked.
     */
    private boolean shouldAllowUrl(RemoteRepoConfig repoConfig) {
        AllowedRemoteUrlsConfig config = prohibitedUrlsService.getAllowedRemoteUrlsConfig();
        if (config == null) {
            return true;
        }
        String url = repoConfig.getRepoTypeConfig() != null ? repoConfig.getRepoTypeConfig().getUrl() : null;
        if (url == null) {
            return true;
        }
        RepoType packageType = repoConfig.getPackageType();
        if (config.isUrlAllowed(url, packageType)) {
            return true;
        }
        if (isDryRun()) {
            log.debug("[DRY-RUN] URL '{}' is not allowed for package type '{}' in repo '{}'",
                    url, packageType, repoConfig.getKey());
            return true;
        }
        log.error("URL '{}' is not allowed for package type '{}' in repo '{}'",
                url, packageType, repoConfig.getKey());
        return false;
    }

    @VisibleForTesting
    boolean isDryRun() {
        return ConstantValues.allowedRemoteUrlsDryRun.getBoolean();
    }

    /**
     * Returns true if the URL changed or the repo is being taken out of offline mode.
     * Avoids running validation on unrelated config edits.
     */
    @VisibleForTesting
    boolean isRelevantChange(RepoConfigChange change) {
        return isUrlChange(change) || isBeingTakenOnline(change);
    }

    @VisibleForTesting
    boolean isUrlChange(RepoConfigChange change) {
        RemoteRepoConfig oldConfig = (RemoteRepoConfig) change.getOldRepoConfig();
        RemoteRepoConfig newConfig = (RemoteRepoConfig) change.getNewRepoConfig();
        if (oldConfig == null || newConfig == null) {
            return true;
        }
        String oldUrl = oldConfig.getRepoTypeConfig() != null ? oldConfig.getRepoTypeConfig().getUrl() : null;
        String newUrl = newConfig.getRepoTypeConfig() != null ? newConfig.getRepoTypeConfig().getUrl() : null;
        return !Objects.equals(oldUrl, newUrl);
    }

    @VisibleForTesting
    boolean isBeingTakenOnline(RepoConfigChange change) {
        RemoteRepoConfig oldConfig = (RemoteRepoConfig) change.getOldRepoConfig();
        RemoteRepoConfig newConfig = (RemoteRepoConfig) change.getNewRepoConfig();
        if (oldConfig == null || newConfig == null) {
            return false;
        }
        boolean wasOffline = oldConfig.getRepoTypeConfig() != null && oldConfig.getRepoTypeConfig().isOffline();
        boolean isNowOnline = newConfig.getRepoTypeConfig() != null && !newConfig.getRepoTypeConfig().isOffline();
        return wasOffline && isNowOnline;
    }

    @Nullable
    private RemoteRepoConfig extractRemoteRepoConfig(RepoConfigChange repoConfigChange) {
        return (RemoteRepoConfig) repoConfigChange.getNewRepoConfig();
    }

    private boolean isCreateOrUpdate(RepoConfigChange change) {
        return REPO_CREATE.equals(change.getConfigAction()) || REPO_UPDATE.equals(change.getConfigAction());
    }
}
