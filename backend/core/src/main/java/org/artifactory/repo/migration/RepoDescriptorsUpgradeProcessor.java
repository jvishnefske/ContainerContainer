package org.artifactory.repo.migration;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.converter.RepositoriesConfigSubConverter;
import org.artifactory.repo.service.RepoConfigBootstrapStorageHelper;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapper;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.spring.StorageContextHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Migrator for all kind of repo descriptors - local, remote, virtual, federated, release bundles.
 */
@Slf4j
public class RepoDescriptorsUpgradeProcessor {
    private final ConfigsService configsService;
    private final Consumer<CentralConfigDescriptor> persistCentralConfigFunction;
    private final Set<RepositoriesConfigSubConverter> subConverters;
    private final RepoConfigBootstrapStorageHelper repoConfigUpgradeService;
    private final RepositoryConfigMapper repositoryConfigMapper;
    private final ArtifactoryContext artifactoryContext;

    public RepoDescriptorsUpgradeProcessor(Consumer<CentralConfigDescriptor> persistCentralConfigFunction,
            ConfigsService configsService,
            Set<RepositoriesConfigSubConverter> subConverters,
            RepoConfigBootstrapStorageHelper repoConfigUpgradeService,
            ArtifactoryContext artifactoryContext) {
        this.persistCentralConfigFunction = persistCentralConfigFunction;
        this.configsService = configsService;
        this.subConverters = subConverters;
        this.repoConfigUpgradeService = repoConfigUpgradeService;
        this.artifactoryContext = artifactoryContext;
        this.repositoryConfigMapper = new RepositoryConfigMapperImpl();
    }

    /**
     * Migrate repo descriptors from central config descriptor into a DB.
     * NOTE: Method should be is called in scope of HA_INIT lock, additional synchronization is no needed.
     *
     * @param centralConfigDescriptor - descriptor that contains (or not) repo descriptors from the
     *                                artifactory.config.xml
     * @return - true if repositories were migrated, false if nothing to migrate.
     */
    public boolean doUpgradeIfNeeded(MutableCentralConfigDescriptor centralConfigDescriptor) {
        int repoDescriptorsCount =
                ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(centralConfigDescriptor);
        if (repoDescriptorsCount == 0) {
            log.info("Repository descriptors upgrade has been skipped");
            return false;
        }
        log.info("Creating central config descriptor backup...");
        configsService.backupConfig(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE, System.currentTimeMillis());
        log.info("Central config descriptor backup has been created");
        return dbService().invokeInTransaction("RepoDescriptorsUpgradeProcessor.doUpgradeIfNeeded",
                () -> executeUpgradeUnderTransaction(centralConfigDescriptor));
    }

    private boolean executeUpgradeUnderTransaction(MutableCentralConfigDescriptor centralConfigDescriptor) {
        try {
            int repoDescriptorsCount =
                    ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(centralConfigDescriptor);
            log.info("Repository descriptors upgrade has been started");
            // interceptors do required permutations
            persistCentralConfigFunction.accept(centralConfigDescriptor);
            Set<RepoConfig> repoConfigs =
                    new HashSet<>(ConfigDescriptorRepoUtils.mapRepoDescriptorsToRepoConfigs(centralConfigDescriptor,
                            subConverters,
                            repositoryConfigMapper));
            if (repoConfigs.isEmpty()) {
                throw new IllegalStateException(
                        "There is no repository descriptors after mapping from repo descriptors to repo configs");
            }
            Authentication originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
            try {
                SecurityContextHolder.getContext().setAuthentication(new SystemAuthenticationToken());
                deleteExistingRepoConfigs();
                repoConfigUpgradeService.bulkInsertRepositoryConfigs(repoConfigs, true);
            } finally {
                SecurityContextHolder.getContext().setAuthentication(originalAuthentication);
            }
            verifyMigratedRepoConfigCount(repoDescriptorsCount);
            cleanupConfigDescriptorRepositories(centralConfigDescriptor);
            repoConfigUpgradeService.reloadRepoConfigurations();
            artifactoryContext.beanForType(AddonsManager.class).addonByType(HaAddon.class)
                    .propagateSystemConfigReload();
            log.info("Repository descriptors upgrade has finished successfully");
            return true;
        } catch (Throwable th) {
            log.error("Exception occurs during repository descriptors upgrade. Reason: " + th.getMessage(), th);
            throw th;
        }
    }

    @VisibleForTesting
    void deleteExistingRepoConfigs() {
        int repoConfigCount = repoConfigUpgradeService.countAll();
        if (repoConfigCount == 0) {
            log.info("Repo configs do not exist. Skip cleanup step");
            return;
        }
        int deletedRepositories = repoConfigUpgradeService.deleteAll();
        repoConfigUpgradeService.reloadRepoConfigurations();
        log.info("{} existing repo configs were cleaned up", deletedRepositories);
    }

    @VisibleForTesting
    void verifyMigratedRepoConfigCount(int repoDescriptorsCount) {
        int migratedReposCount = repoConfigUpgradeService.countAll();
        log.info("Repositories count before migration: {}. Total repositories count after migration: {}",
                repoDescriptorsCount, migratedReposCount);
        // less than because repo config interceptors may add system repos like artifactory-build-info etc
        if (migratedReposCount < repoDescriptorsCount) {
            throw new IllegalStateException(
                    "Migrated repositories count is less than repositories count in the system");
        }
    }

    @VisibleForTesting
    void cleanupConfigDescriptorRepositories(MutableCentralConfigDescriptor centralConfigDescriptor) {
        ConfigDescriptorRepoUtils.cleanupConfigDescriptorRepositories(centralConfigDescriptor);
    }

    private InternalDbService dbService() {
        return StorageContextHelper.get().beanForType(InternalDbService.class);
    }
}