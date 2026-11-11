package org.artifactory.repo.propagation;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.repo.RepoConfigReload;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;
import org.artifactory.util.CollectionUtils;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

/**
 * Class filters propagated repo actions based on the next assumptions:
 * 1. Skip create repo actions if a repo is in the repo config cache (means repo creation has been already handled by
 * repo cache sync logic) OR if repo does not exist in DB (means event is outdated)
 * 2. Skip update repo actions if a repo key is present in the cache and a modification date is not changed OR if a
 * repo does not exist in DB. IMPORTANT: Filter does not check that an updated repo is present in the repo config
 * cache since create action might be lost, and we may lose a repo update too because of this check. Reload
 * listeners also be designed to handle the case properly.
 * 3. Skip delete repo actions if a repo does not exist in the repo config cache (means already deleted or never
 * created) and exists in DB (outdated event).
 */
@Slf4j
@NotThreadSafe
public class RepoConfigPropagationModelFilter {
    private final RepositoryConfigStorageService storageService;
    private final Map<String, BasicRepoConfig> allCachedBasicRepoConfigs;
    private final int keysSizeToUseAllInstanceOfSync;
    private Set<String> allRepoKeysFromDb;

    public RepoConfigPropagationModelFilter(Map<String, BasicRepoConfig> allCachedBasicRepoConfigs,
            RepositoryConfigStorageService storageService, int keysSizeToUseAllInstanceOfSync) {
        this.allCachedBasicRepoConfigs = allCachedBasicRepoConfigs;
        this.storageService = storageService;
        this.keysSizeToUseAllInstanceOfSync = keysSizeToUseAllInstanceOfSync;
    }

    public void filter(@Nonnull RepoConfigPropagationModel model) {
        Set<RepoConfigReload> reposToCreate = handleReposCreateUpdate(model.getRepoConfigCreateOrUpdate());
        Set<RepoConfigReload> reposDelete = handleReposDelete(model.getRepoConfigDelete());
        model.setRepoConfigCreateOrUpdate(reposToCreate);
        model.setRepoConfigDelete(reposDelete);
    }

    private Set<RepoConfigReload> handleReposCreateUpdate(Set<RepoConfigReload> repoCreateUpdate) {
        if (CollectionUtils.isNullOrEmpty(repoCreateUpdate)) {
            log.trace("There is no create/update repos propagation");
            return Set.of();
        }

        // remove values that are in the cache, since they already exist in app
        if (MapUtils.isNotEmpty(allCachedBasicRepoConfigs)) {
            repoCreateUpdate = repoCreateUpdate.stream()
                    .filter(this::isRepoCreatedOrUpdated)
                    .collect(toSet());

            if (repoCreateUpdate.isEmpty()) {
                log.debug("All created/updated repos exist in repo cache, nothing to reload");
                return Set.of();
            }
        }

        // keep values only if they are present in DB
        Set<String> repoKeys = repoCreateUpdate.stream()
                .map(RepoConfigReload::getRepoKey)
                .collect(toSet());

        Set<String> actualRepoKeys = getRepoKeysFromDb(repoKeys);
        if (log.isTraceEnabled()) {
            log.trace("Propagated repo keys to create/update: {}", actualRepoKeys.stream()
                    .collect(Collectors.joining(",")));
        }

        return repoCreateUpdate.stream()
                .filter(reload -> actualRepoKeys.contains(reload.getRepoKey()))
                .collect(toSet());
    }

    private Set<RepoConfigReload> handleReposDelete(Set<RepoConfigReload> reposToDelete) {
        if (CollectionUtils.isNullOrEmpty(reposToDelete)) {
            log.trace("There is no delete repos propagation");
            return Set.of();
        }

        // keep values that are in the cache, all others keys do not matter since instance don't know about it
        if (MapUtils.isNotEmpty(allCachedBasicRepoConfigs)) {
            reposToDelete = reposToDelete.stream()
                    .filter(repoReload -> allCachedBasicRepoConfigs.containsKey(repoReload.getRepoKey()))
                    .collect(toSet());

            if (reposToDelete.isEmpty()) {
                log.debug("Propagated deleted repo keys do not exist in repo cache, nothing to reload");
                return Set.of();
            }
        }

        // check that DB does not have keys to delete
        Set<String> repoKeys = reposToDelete.stream()
                .map(RepoConfigReload::getRepoKey)
                .collect(toSet());

        Set<String> actualRepoKeys = getRepoKeysFromDb(repoKeys);
        repoKeys.removeAll(actualRepoKeys);

        if (log.isTraceEnabled()) {
            log.trace("Propagated repo keys to delete: {}", repoKeys.stream()
                    .collect(Collectors.joining(",")));
        }

        return reposToDelete.stream()
                .filter(reload -> repoKeys.contains(reload.getRepoKey()))
                .collect(toSet());
    }

    private Set<String> getRepoKeysFromDb(Set<String> repoKeys) {
        if (allRepoKeysFromDb != null) {
            return repoKeys.stream()
                    .filter(allRepoKeysFromDb::contains)
                    .collect(Collectors.toSet());
        }

        if (repoKeys.size() <= keysSizeToUseAllInstanceOfSync) {
            return storageService.findKeysFromSet(repoKeys);
        }

        allRepoKeysFromDb = storageService.getAllRepoKeys();
        return repoKeys.stream()
                .filter(allRepoKeysFromDb::contains)
                .collect(Collectors.toSet());
    }

    /**
     * Check if repo is created or updated by the following logic:
     * 1. If repo is not in the cache, it is either created or updated after a missing created event - return true
     * 2. If repo is in the cache, but modification date from event is different, it is updated - return true
     * 3. Otherwise, return false
     */
    private boolean isRepoCreatedOrUpdated(RepoConfigReload repoReload) {
        boolean modified = true;
        if (allCachedBasicRepoConfigs.containsKey(repoReload.getRepoKey())) {
            long modificationDate =
                    allCachedBasicRepoConfigs.get(repoReload.getRepoKey()).getModification();
            modified = repoReload.getModification() != modificationDate;
        }
        log.trace("Process repo create/update action for repo key {}. Is modified: {}", repoReload.getRepoKey(),
                modified);
        return modified;
    }
}