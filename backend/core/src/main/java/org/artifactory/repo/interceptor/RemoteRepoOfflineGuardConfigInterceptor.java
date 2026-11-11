package org.artifactory.repo.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;

/**
 * Wires the remote-repo create/update path into {@link RemoteRepoOfflineGuardService}:
 * <ul>
 *     <li>{@code onBeforeSave} (create): reject any new remote repo whose URL matches a configured substring while
 *     the guard is in an enforcing mode ({@code PFW3_NEW} / {@code PFW3_ALL}).</li>
 *     <li>{@code onBeforeSave} (update): reject any URL change to a still-matching URL in enforcing modes, and
 *     reject any admin attempt to flip a guard-offlined remote repo back to online while triggers still match.</li>
 *     <li>{@code onAfterSave}: when a remote repo's URL changes, notify the guard so it can drop audit headers
 *     tied to the old URL. The repo's offline state is not changed automatically — admin must re-online manually.</li>
 * </ul>
 *
 * @author Hussein Alshqerat
 */
@Slf4j
@Component
public class RemoteRepoOfflineGuardConfigInterceptor implements RepoConfigChangeInterceptor {

    private final RemoteRepoOfflineGuardService offlineGuardService;

    @Autowired
    public RemoteRepoOfflineGuardConfigInterceptor(RemoteRepoOfflineGuardService offlineGuardService) {
        this.offlineGuardService = offlineGuardService;
    }

    @Override
    public void onBeforeSave(RepoConfigChanges repoConfigChanges) {
        for (RepoConfigChange change : repoConfigChanges.filterBy(REPO_CREATE, RepoDetailsType.REMOTE)) {
            rejectCreateIfUrlBlocked(change);
        }
        for (RepoConfigChange change : repoConfigChanges.filterBy(REPO_UPDATE, RepoDetailsType.REMOTE)) {
            rejectUpdateIfUrlBlocked(change);
            rejectReonlineIfGuardStillMatches(change);
        }
    }

    @Override
    public void onAfterSave(RepoConfigChanges repoConfigChanges) {
        for (RepoConfigChange change : repoConfigChanges.filterBy(REPO_UPDATE, RepoDetailsType.REMOTE)) {
            notifyGuardOnUrlChange(change);
        }
    }

    private void rejectCreateIfUrlBlocked(RepoConfigChange change) {
        RemoteRepositoryRepoTypeConfig newType = remoteTypeConfig(change.getNewRepoConfig());
        if (newType == null) {
            return;
        }
        if (offlineGuardService.isCreateBlocked(change.getRepoKey(), newType.getUrl())) {
            throw new IllegalArgumentException("This repository cannot be created because its upstream traffic is "
                    + "being channeled through an unsupported integration." +
                    " Contact JFrog support for further discussion.");
        }
    }

    private void rejectUpdateIfUrlBlocked(RepoConfigChange change) {
        RemoteRepositoryRepoTypeConfig oldType = remoteTypeConfig(change.getOldRepoConfig());
        RemoteRepositoryRepoTypeConfig newType = remoteTypeConfig(change.getNewRepoConfig());
        if (oldType == null || newType == null) {
            return;
        }
        if (offlineGuardService.isUpdateUrlBlocked(change.getRepoKey(), oldType.getUrl(), newType.getUrl())) {
            throw new IllegalArgumentException("This repository's URL cannot be changed because the new URL routes "
                    + "through an unsupported integration. Contact JFrog support for further discussion.");
        }
    }

    private void rejectReonlineIfGuardStillMatches(RepoConfigChange change) {
        RemoteRepositoryRepoTypeConfig oldType = remoteTypeConfig(change.getOldRepoConfig());
        RemoteRepositoryRepoTypeConfig newType = remoteTypeConfig(change.getNewRepoConfig());
        if (oldType == null || newType == null) {
            return;
        }
        if (!oldType.isOffline() || newType.isOffline()) {
            return;
        }
        String repoKey = change.getRepoKey();
        if (offlineGuardService.isReonlineBlocked(repoKey, newType.getUrl())) {
            throw new IllegalArgumentException("This repository cannot be set online because its upstream " +
                    "traffic is being channeled through an unsupported integration." +
                    " Contact JFrog support for further discussion."
            );
        }
    }

    private void notifyGuardOnUrlChange(RepoConfigChange change) {
        RemoteRepositoryRepoTypeConfig oldType = remoteTypeConfig(change.getOldRepoConfig());
        RemoteRepositoryRepoTypeConfig newType = remoteTypeConfig(change.getNewRepoConfig());
        if (oldType == null || newType == null) {
            return;
        }
        String newUrl = newType.getUrl();
        if (Objects.equals(oldType.getUrl(), newUrl)) {
            return;
        }
        try {
            offlineGuardService.onRemoteRepoUrlChanged(change.getRepoKey(), newUrl);
        } catch (Exception e) {
            log.warn("Offline guard URL-change hook failed for repo '{}'", change.getRepoKey(), e);
        }
    }

    private static RemoteRepositoryRepoTypeConfig remoteTypeConfig(RepoConfig repoConfig) {
        return (repoConfig instanceof RemoteRepoConfig remote) ? remote.getRepoTypeConfig() : null;
    }
}
