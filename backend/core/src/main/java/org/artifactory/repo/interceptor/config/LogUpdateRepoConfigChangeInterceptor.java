package org.artifactory.repo.interceptor.config;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.async.Async;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.util.DiffEntry;
import org.artifactory.util.RepoConfigDiffUtil;
import org.jfrog.common.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;

@Component
@Slf4j
public class LogUpdateRepoConfigChangeInterceptor implements RepoConfigChangeInterceptor {

    /**
     * Logs asynchronously the repository configurations changes on repository update
     *
     * @param repoConfigChanges repository configuration changes
     */
    @Async
    @Override
    public void onAfterSave(RepoConfigChanges repoConfigChanges) {
        List<RepoConfigChange> repoConfigChangesByAction = repoConfigChanges.filterBy(REPO_UPDATE);
        repoConfigChangesByAction.forEach(this::logRepoConfigChange);
    }

    @VisibleForTesting
    void logRepoConfigChange(RepoConfigChange repoConfigChange) {
        RepoConfig newRepoConfig = repoConfigChange.getNewRepoConfig();
        RepoConfig oldRepoConfig = repoConfigChange.getOldRepoConfig();
        if (newRepoConfig == null || oldRepoConfig == null) {
            log.warn("Illegal repo config change detected. oldRepoChange: '{}', newRepoConfig: '{}'",
                    oldRepoConfig, newRepoConfig);
            return;
        }
        try {
            List<DiffEntry> repoConfigDiffs = RepoConfigDiffUtil.maskedDiff(oldRepoConfig, newRepoConfig);
            if (CollectionUtils.isNotEmpty(repoConfigDiffs)) {
                log.info("Repository '{}' configuration has been updated. Changes: {}", newRepoConfig.getKey(),
                        JsonUtils.getInstance().valueToString(repoConfigDiffs));
                if (isRepoChangedToBlackedOut(oldRepoConfig, newRepoConfig)) {
                    log.info("Repository '{}' has been blacked out", newRepoConfig.getKey());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to calculate repository '{}' configuration diffs", newRepoConfig.getKey(), e);
        }
    }

    private boolean isRepoChangedToBlackedOut(RepoConfig oldRepoConfig, RepoConfig newRepoConfig) {
        if (oldRepoConfig instanceof RealRepoConfig oldRealRepoConfig &&
                newRepoConfig instanceof RealRepoConfig newRealRepoConfig) {
            return !oldRealRepoConfig.getRepoTypeConfig().isBlackedOut() &&
                    newRealRepoConfig.getRepoTypeConfig().isBlackedOut();
        }
        return false;
    }
}