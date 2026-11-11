package org.artifactory.repo.cache;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoConfigReload;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RepoConfigCacheDiffCalculator {

    @Nullable
    public static RepoConfigPropagationModel calculateCacheDiff(Map<String, BasicRepoConfig> oldBasicRepoConfigs,
            Map<String, BasicRepoConfig> newBasicRepoConfigs) {
        Set<RepoConfigReload> repoConfigDelete = new HashSet<>();
        Set<RepoConfigReload> repoConfigCreateOrUpdate = new HashSet<>();
        collectRepoConfigUpdateOrDelete(oldBasicRepoConfigs, newBasicRepoConfigs, repoConfigDelete,
                repoConfigCreateOrUpdate);
        collectRepoConfigCreate(oldBasicRepoConfigs, newBasicRepoConfigs, repoConfigCreateOrUpdate);
        if (repoConfigCreateOrUpdate.isEmpty() && repoConfigDelete.isEmpty()) {
            return null;
        }
        return new RepoConfigPropagationModel(repoConfigCreateOrUpdate, repoConfigDelete, false);
    }

    private static void collectRepoConfigCreate(Map<String, BasicRepoConfig> oldDbBasicRepoConfigs,
            Map<String, BasicRepoConfig> newBasicRepoConfigs,
            Set<RepoConfigReload> repoConfigCreateOrUpdate) {
        Set<String> newRepoKeys = new HashSet<>(newBasicRepoConfigs.keySet());
        newRepoKeys.removeAll(oldDbBasicRepoConfigs.keySet());
        newRepoKeys.forEach(newRepoKey -> {
            BasicRepoConfig newRepoConfig = newBasicRepoConfigs.get(newRepoKey);
            repoConfigCreateOrUpdate.add(
                    new RepoConfigReload(newRepoKey, newRepoConfig.getRepoType(), newRepoConfig.getPackageType(),
                            RepoConfigChange.ConfigAction.REPO_CREATE, newRepoConfig.getModification()));
            log.debug("Repo {} is not cached, but exists in the DB. Cache will be reloaded", newRepoKey);
        });
    }

    private static void collectRepoConfigUpdateOrDelete(Map<String, BasicRepoConfig> oldDbBasicRepoConfigs,
            Map<String, BasicRepoConfig> newBasicRepoConfigs,
            Set<RepoConfigReload> repoConfigDelete,
            Set<RepoConfigReload> repoConfigCreateOrUpdate) {
        oldDbBasicRepoConfigs.forEach((key, basicRepoConfig) -> {
            BasicRepoConfig oldBasicRepoConfig = oldDbBasicRepoConfigs.get(key);
            if (newBasicRepoConfigs.containsKey(key)) {
                Long dbModificationDate = newBasicRepoConfigs.get(key).getModification();
                if (dbModificationDate == null) {
                    log.warn("Modification date for repo {} should not be null", key);
                    return;
                }
                if (dbModificationDate.longValue() != basicRepoConfig.getModification()) {
                    repoConfigCreateOrUpdate.add(new RepoConfigReload(key, oldBasicRepoConfig.getRepoType(),
                            oldBasicRepoConfig.getPackageType(), RepoConfigChange.ConfigAction.REPO_UPDATE,
                            dbModificationDate));
                    log.debug("Modification date for repo {} is changed. Cache will be reloaded", key);
                }
            } else {
                repoConfigDelete.add(new RepoConfigReload(key, oldBasicRepoConfig.getRepoType(),
                        oldBasicRepoConfig.getPackageType(), RepoConfigChange.ConfigAction.REPO_DELETE,
                        basicRepoConfig.getModification()));
                log.debug("Repo {} does not exist in DB but still exists in cache. Cache will be reloaded", key);
            }
        });
    }
}
