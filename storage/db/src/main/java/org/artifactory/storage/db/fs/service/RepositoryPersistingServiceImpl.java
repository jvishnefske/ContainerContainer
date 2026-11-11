package org.artifactory.storage.db.fs.service;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.AggregatedReposConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.spring.Reloadable;
import org.jfrog.common.FileUtils;
import org.jfrog.common.JsonUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service responsible for holding a 'dirty' flag for repository configurations and persisting the
 * configurations to a backup file which later on can be used for bootstrapping Artifactory repositories
 *
 * @author shayb
 * @see org.artifactory.storage.jobs.RepositoryConfigPersistingJob
 * @see org.artifactory.storage.db.repo.service.RepositoryConfigStorageServiceImpl
 */
@Slf4j
@Service
@Reloadable(beanClass = RepositoryPersistingService.class)
public class RepositoryPersistingServiceImpl implements RepositoryPersistingService {
    private InternalRepositoryConfigService repositoryConfigService;
    private final AtomicBoolean dirtyRepositories = new AtomicBoolean(true);

    @Override
    public void init() {
        this.repositoryConfigService = ContextHelper.get().beanForType(InternalRepositoryConfigService.class);
    }

    @Override
    public void persist() {
        if (dirtyRepositories.getAndSet(false)) {
            Collection<RepoConfig> allRepositoryConfigs = repositoryConfigService.getAllRepoConfigsSkipCache();
            log.debug("Persisting {} repository configurations", allRepositoryConfigs.size());
            AggregatedReposConfig aggregatedReposConfig = new AggregatedReposConfig(allRepositoryConfigs);
            String aggregatedReposConfigJson = JsonUtils.getInstance().writeValueAsString(aggregatedReposConfig);
            File artifactoryRepoConfigLatestFile = ArtifactoryHome.get().getArtifactoryRepoConfigLatestFile();
            try {
                FileUtils.writeContentToRollingFile(
                        aggregatedReposConfigJson, artifactoryRepoConfigLatestFile,
                        ConstantValues.repoConfigNumBackups.getInt());
            } catch (IOException e) {
                log.debug("Failed to persist: {}", artifactoryRepoConfigLatestFile.getName(), e);
                log.error("Failed to persist: {}. Error message: {}", artifactoryRepoConfigLatestFile.getName(),
                        e.getMessage());
            }
        }
    }

    @Override
    public void markDirty() {
        dirtyRepositories.set(true);
    }
}
