package org.artifactory.repo;

import org.artifactory.model.RepoConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for creating {@link RepoConfigChangesReload} instances by the following logic:
 * Input consists of keys of: Created, updated and deleted repo config keys from change event.
 * - Deleted repo configs should still exist in cache and are fetched from it
 * - Updated repo configs should exist in DB. Old instances are fetched from cache and new
 * instances are fetched from DB. Any repo config that does not exist in DB is skipped. If the old repo
 * config is not found in the cache, it is considered as a missed creation, and a dummy change is used with the new
 * repo config as the old one since we don't want to miss the reload event.
 * - Created repo configs should not exist in cache and so are only fetched from DB
 @author Mor Merhav
 */
public class RepoConfigChangesReloadFactory {

    private final Function<Set<String>, Collection<RepoConfig>> getRepoConfigsFromCache;
    private final Function<Set<String>, Set<RepoConfig>> getRepoConfigsFromDb;

    public RepoConfigChangesReloadFactory(Function<Set<String>, Collection<RepoConfig>> getRepoConfigsFromCache,
            Function<Set<String>, Set<RepoConfig>> getRepoConfigsFromDb) {
        this.getRepoConfigsFromCache = getRepoConfigsFromCache;
        this.getRepoConfigsFromDb = getRepoConfigsFromDb;
    }

    public RepoConfigChangesReload create(Set<String> createdRepoKeys, Set<String> updatedRepoKeys,
            Set<String> deletedRepoKeys) {
        Set<RepoConfigChangeReload> repoConfigDeleteChanges = getRepoConfigDeleteChanges(deletedRepoKeys);
        Set<RepoConfigChangeReload> repoConfigUpdateChanges = getRepoConfigUpdateChanges(updatedRepoKeys);
        Set<RepoConfigChangeReload> repoConfigCreateChanges = getRepoConfigCreateChanges(createdRepoKeys);
        Set<RepoConfigChangeReload> allChanges = new HashSet<>();
        allChanges.addAll(repoConfigDeleteChanges);
        allChanges.addAll(repoConfigUpdateChanges);
        allChanges.addAll(repoConfigCreateChanges);
        return new RepoConfigChangesReload(allChanges);
    }

    private Set<RepoConfigChangeReload> getRepoConfigDeleteChanges(Set<String> deletedRepoKeys) {
        Set<RepoConfig> deletedRepoConfigs = new HashSet<>(getRepoConfigsFromCache.apply(deletedRepoKeys));
        return deletedRepoConfigs.stream().map(RepoConfigChangesReloadFactory::toDeleteChangeReload).collect(
                Collectors.toSet());
    }

    private static RepoConfigChangeReload toDeleteChangeReload(RepoConfig repoConfig) {
        return new RepoConfigChangeReload(RepoConfigChange.deleteOf(repoConfig));
    }

    private Set<RepoConfigChangeReload> getRepoConfigUpdateChanges(Set<String> createdOrUpdatedRepoKeys) {
        Collection<RepoConfig> oldRepoConfigs = getRepoConfigsFromCache.apply(createdOrUpdatedRepoKeys);
        List<RepoConfig> newRepoConfigs = new ArrayList<>(getRepoConfigsFromDb.apply(createdOrUpdatedRepoKeys));

        Set<RepoConfigChangeReload> repoConfigUpdateChanges = new HashSet<>();
        for (RepoConfig newRepoConfig : newRepoConfigs) {
            RepoConfig oldRepoConfig = findRepoConfig(newRepoConfig.getKey(), oldRepoConfigs);
            RepoConfigChangeReload updateChangeReload = toUpdateChangeReload(oldRepoConfig, newRepoConfig);
            repoConfigUpdateChanges.add(updateChangeReload);
        }
        return repoConfigUpdateChanges;
    }

    private RepoConfig findRepoConfig(String repoKey, Collection<RepoConfig> repoConfigs) {
        return repoConfigs.stream().filter(repoConfig -> repoConfig.getKey().equals(repoKey)).findFirst().orElse(null);
    }

    /**
     * Create an update change reload instance from the old and new repo configs
     * Important - Missed creation event handling - In case the old repo config is not found in the cache, it is
     * considered as a missed creation and the new repo config is used since we don't want to miss the reload event
     */
    private RepoConfigChangeReload toUpdateChangeReload(RepoConfig oldConfig, RepoConfig newConfig) {
        if (oldConfig == null) {
            oldConfig = newConfig;
        }
        return new RepoConfigChangeReload(RepoConfigChange.updateOf(oldConfig, newConfig));
    }

    private Set<RepoConfigChangeReload> getRepoConfigCreateChanges(Set<String> createdRepoKeys) {
        Set<RepoConfig> createdRepoConfigs = getRepoConfigsFromDb.apply(createdRepoKeys);
        return createdRepoConfigs.stream().map(RepoConfigChangesReloadFactory::toCreateChangeReload)
                .collect(Collectors.toSet());
    }

    private static RepoConfigChangeReload toCreateChangeReload(RepoConfig repoConfig) {
        return new RepoConfigChangeReload(RepoConfigChange.createOf(repoConfig));
    }
}