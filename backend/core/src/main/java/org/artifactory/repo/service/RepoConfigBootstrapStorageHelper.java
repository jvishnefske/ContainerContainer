package org.artifactory.repo.service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapper;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.artifactory.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;

@Slf4j
public class RepoConfigBootstrapStorageHelper {
    private final RepositoryConfigStorageService storageService;
    private final Set<RepoConfigChangeInterceptor> interceptors;
    private final Runnable reloadRepoConfigurationsFunction;
    private final RepoConfigChangesMapFunction repoConfigChangesMapFunction;
    private final RepositoryConfigMapper repositoryConfigMapper = new RepositoryConfigMapperImpl();

    public RepoConfigBootstrapStorageHelper(RepositoryConfigStorageService storageService,
            Set<RepoConfigChangeInterceptor> interceptors,
            Runnable reloadRepoConfigurationsFunction,
            RepoConfigChangesMapFunction repoConfigChangesMapFunction) {
        this.storageService = storageService;
        this.interceptors = interceptors;
        this.reloadRepoConfigurationsFunction = reloadRepoConfigurationsFunction;
        this.repoConfigChangesMapFunction = repoConfigChangesMapFunction;
    }

    public void bulkInsertRepositoryConfigs(Set<? extends RepoConfig> repositoryConfigs, boolean isUpgrade) {
        if (CollectionUtils.isNullOrEmpty(repositoryConfigs)) {
            return;
        }
        if (ContextHelper.get().isReposImportInProgress()) {
            log.info("Cannot insert repository configs, import is still in progress");
            return;
        }
        if (isUpgrade) {
            Map<RepoDetailsType, List<RepoConfigChange>> typeToChanges = repositoryConfigs.stream()
                    .map(repo -> new RepoConfigChange(repo.getKey(), REPO_UPDATE, repo, repo))
                    .collect(groupingBy(change -> change.getNewRepoConfig().getRepoType()));

            interceptors.forEach(listener -> listener.onBeforeSave(new RepoConfigChanges(typeToChanges)));
            Set<DbRepositoryConfig> reposToCreate = filterReposFromChanges(typeToChanges, REPO_CREATE);
            reposToCreate.addAll(filterReposFromChanges(typeToChanges, REPO_UPDATE));
            Set<String> rootFoldersToCreate = ConstantValues.repoRootAtomicCreation.getBoolean()
                    ? RepoConfigRootFolderKeyExtractor.extractRootFolderKeys(repositoryConfigs)
                    : Set.of();
            storageService.bulkInsertRepositoryConfigs(reposToCreate, rootFoldersToCreate);
        } else {
            Map<RepoDetailsType, List<RepoConfigChange>> typeToChanges =
                    repoConfigChangesMapFunction.apply(repositoryConfigs);
            RepoConfigChanges repoConfigChanges = new RepoConfigChanges(typeToChanges);

            interceptors.forEach(listener -> listener.onBeforeSave(repoConfigChanges));
            Set<DbRepositoryConfig> reposToCreate = filterReposFromChanges(typeToChanges, REPO_CREATE);
            Set<String> rootFoldersToCreate = ConstantValues.repoRootAtomicCreation.getBoolean()
                    ? RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForInsert(repoConfigChanges)
                    : Set.of();
            storageService.bulkInsertRepositoryConfigs(reposToCreate, rootFoldersToCreate);
        }
    }

    public void reloadRepoConfigurations() {
        reloadRepoConfigurationsFunction.run();
    }

    public int countAll() {
        return storageService.countAll();
    }

    public int deleteAll() {
        return storageService.deleteAll();
    }

    public void bulkUpdateRepositoryConfigs(Set<? extends RepoConfig> repositoryConfigs) {
        if (CollectionUtils.isNullOrEmpty(repositoryConfigs)) {
            return;
        }
        if (ContextHelper.get().isReposImportInProgress()) {
            log.info("Cannot insert repository configs, import is still in progress");
            return;
        }

        Map<RepoDetailsType, List<RepoConfigChange>> typeToChangesMap =
                repoConfigChangesMapFunction.apply(repositoryConfigs);
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(typeToChangesMap);

        interceptors.forEach(listener -> listener.onBeforeSave(repoConfigChanges));
        Set<DbRepositoryConfig> reposToUpdate = filterReposFromChanges(typeToChangesMap, REPO_UPDATE);
        Set<String> rootFoldersToCreate = ConstantValues.repoRootAtomicCreation.getBoolean()
                ? RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForUpdate(repoConfigChanges)
                : Set.of();
        storageService.bulkUpdateRepositoryConfigs(reposToUpdate, rootFoldersToCreate);
    }

    @Nonnull
    private Set<DbRepositoryConfig> filterReposFromChanges(Map<RepoDetailsType, List<RepoConfigChange>> typeToChanges,
            RepoConfigChange.ConfigAction configAction) {
        return typeToChanges.values().stream()
                .flatMap(Collection::stream)
                .filter(change -> configAction == change.getConfigAction())
                .map(change -> {
                    long timestamp = System.currentTimeMillis();
                    if (RepoConfigChange.ConfigAction.REPO_CREATE.equals(change.getConfigAction())) {
                        change.getNewRepoConfig().setCreation(timestamp);
                    }
                    change.getNewRepoConfig().setModification(timestamp);
                    return repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(change.getNewRepoConfig());
                })
                .collect(toSet());
    }
}
