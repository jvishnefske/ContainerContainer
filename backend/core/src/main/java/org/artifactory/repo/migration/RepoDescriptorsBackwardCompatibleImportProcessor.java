package org.artifactory.repo.migration;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.config.RepoConfigBackwardsCompatibleImportProcessor;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.converter.RepositoriesConfigSubConverter;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapper;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Migrator for all kind of repo descriptors - local, remote, virtual, federated, release bundles.
 */
@Slf4j
public class RepoDescriptorsBackwardCompatibleImportProcessor {
    private final Consumer<CentralConfigDescriptor> persistCentralConfigFunction;
    private final Set<RepositoriesConfigSubConverter> subConverters;
    private final RepoConfigBackwardsCompatibleImportProcessor repoConfigBackwardsCompatibleProcessor;
    private final RepositoryConfigMapper repositoryConfigMapper;
    private final ArtifactoryContext artifactoryContext;

    public RepoDescriptorsBackwardCompatibleImportProcessor(
            Consumer<CentralConfigDescriptor> persistCentralConfigFunction,
            Set<RepositoriesConfigSubConverter> subConverters,
            RepoConfigBackwardsCompatibleImportProcessor repoConfigBackwardsCompatibleProcessor,
            ArtifactoryContext artifactoryContext) {
        this.persistCentralConfigFunction = persistCentralConfigFunction;
        this.subConverters = subConverters;
        this.repoConfigBackwardsCompatibleProcessor = repoConfigBackwardsCompatibleProcessor;
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
    public boolean doImportIfNeeded(MutableCentralConfigDescriptor centralConfigDescriptor) {
        try {
            int repoDescriptorsCount = ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(centralConfigDescriptor);
            if (repoDescriptorsCount == 0) {
                log.info("Repository descriptors backward compatible import has been skipped");
                return false;
            }
            log.info("Repository descriptors backward compatible import has been started");
            // interceptors do required permutations with repo descriptors
            persistCentralConfigFunction.accept(centralConfigDescriptor);
            List<RepoConfig> repoConfigs =
                    ConfigDescriptorRepoUtils.mapRepoDescriptorsToRepoConfigs(centralConfigDescriptor, subConverters,
                            repositoryConfigMapper);
            if (repoConfigs.isEmpty()) {
                throw new IllegalStateException(
                        "There is no repository descriptors after mapping from repo descriptors to repo configs");
            }

            Authentication originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
            try {
                SecurityContextHolder.getContext().setAuthentication(new SystemAuthenticationToken());
                repoConfigBackwardsCompatibleProcessor.importRepoConfigs(repoConfigs, true, false);
            } finally {
                SecurityContextHolder.getContext().setAuthentication(originalAuthentication);
            }
            verifyMigratedRepoConfigCount(repoDescriptorsCount);
            cleanupConfigDescriptorRepositories(centralConfigDescriptor);
            repoConfigBackwardsCompatibleProcessor.reloadRepoConfigs();
            artifactoryContext.beanForType(AddonsManager.class).addonByType(HaAddon.class)
                    .propagateSystemConfigReload();
            log.info("Repository descriptors backward compatible import has finished successfully");
            return true;
        } catch (Throwable th) {
            log.error("Exception occurs during repository descriptors backward compatible import. Reason: " +
                    th.getMessage(), th);
            throw th;
        }
    }

    @VisibleForTesting
    void verifyMigratedRepoConfigCount(int repoDescriptorsCount) {
        int migratedReposCount = repoConfigBackwardsCompatibleProcessor.countAll();
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
}