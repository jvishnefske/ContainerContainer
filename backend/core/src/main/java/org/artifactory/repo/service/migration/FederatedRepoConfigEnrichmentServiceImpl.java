package org.artifactory.repo.service.migration;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.FederatedRepoConfigEnrichmentService;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.FederationConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapper;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dedicated service for the one-shot enrichment (migration) of federated repository configs. It uses the repository
 * config storage service for persistence and {@link InternalRepositoryConfigService} for the in-memory cache rebuild,
 * keeping this migration logic out of the repository config CRUD services.
 */
@Slf4j
@Service
public class FederatedRepoConfigEnrichmentServiceImpl implements FederatedRepoConfigEnrichmentService {

    private final RepositoryConfigStorageService storageService;
    private final InternalRepositoryConfigService repositoryConfigService;
    private final RepositoryConfigMapper repositoryConfigMapper;

    @Autowired
    public FederatedRepoConfigEnrichmentServiceImpl(RepositoryConfigStorageService storageService,
            InternalRepositoryConfigService repositoryConfigService) {
        this.storageService = storageService;
        this.repositoryConfigService = repositoryConfigService;
        this.repositoryConfigMapper = new RepositoryConfigMapperImpl();
    }

    @Override
    public void enrichColdFederatedReposBlobs() {
        long now = System.currentTimeMillis();
        Set<DbRepositoryConfig> coldEnrichedRepos = storageService
                .findRepositoriesByDetailsType(RepoDetailsType.FEDERATED.typeNameLowercase()).stream()
                .flatMap(dbConfig -> toFederatedRepoConfig(dbConfig)
                        .filter(this::isColdFederatedRepo)
                        .map(repoConfig -> {
                            // Copy the legacy federation data into the new baseConfig.federationConfig
                            List<FederatedMember> legacyMembers = repoConfig.getRepoTypeConfig().getFederatedMembers();
                            repoConfig.setFederatedMembers(new ArrayList<>(legacyMembers));
                            repoConfig.setModificationDate(repoConfig.getRepoTypeConfig().getModificationDate());
                            // Bump the row timestamp so RepoConfigCache.syncRepoConfigKeys invalidates stale
                            // entries on other HA nodes and so bulkUpdate's non-zero-modification assertion passes.
                            repoConfig.setModification(now);
                            DbRepositoryConfig enriched =
                                    repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repoConfig);
                            enriched.setRevision(dbConfig.getRevision());
                            return enriched;
                        })
                        .stream())
                .collect(Collectors.toSet());
        if (coldEnrichedRepos.isEmpty()) {
            log.info("No cold federated repositories to enrich; federation members are already in the new location");
            return;
        }
        log.info("Enriching {} cold federated repositories: copying federation members into the new config location",
                coldEnrichedRepos.size());
        // Fast path: bulkUpdateRepositoryConfigs delegates straight to repositoryConfigDao.bulkUpdate (no interceptors,
        // no HA propagation). The repos already exist, so no root folders are created.
        storageService.bulkUpdateRepositoryConfigs(coldEnrichedRepos, Set.of());
        // rebuildRepoConfigCache() is node-local. HA peers pick up the change via the modified-timestamp bump above:
        // RepoConfigCache.syncRepoConfigKeys detects the newer modified value and re-fetches the affected rows.
        repositoryConfigService.rebuildRepoConfigCache();
    }

    private Optional<FederatedRepoConfig> toFederatedRepoConfig(DbRepositoryConfig dbRepositoryConfig) {
        Object repositoryConfig = repositoryConfigMapper.dbInfoBlobToRepositoryConfig(dbRepositoryConfig);
        if (repositoryConfig instanceof FederatedRepoConfig federatedRepoConfig) {
            return Optional.of(federatedRepoConfig);
        }
        String actualConfigType = repositoryConfig == null ? "null" : repositoryConfig.getClass().getSimpleName();
        log.warn("Skipping repository '{}' with details type '{}' during federated enrichment: expected {}, got {}",
                dbRepositoryConfig.getKey(), dbRepositoryConfig.getType(),
                FederatedRepoConfig.class.getSimpleName(), actualConfigType);
        return Optional.empty();
    }

    /**
     * A federated repo is "cold" when its members live only in the legacy {@code repoTypeConfig} location and the new
     * {@code baseConfig.federationConfig} location is still empty. Warm repos (already dual-written) and member-less
     * repos are skipped, making {@link #enrichColdFederatedReposBlobs()} idempotent.
     */
    private boolean isColdFederatedRepo(FederatedRepoConfig repoConfig) {
        FederationConfig federationConfig = repoConfig.getBaseConfig().getFederationConfig();
        List<FederatedMember> newLocationMembers = federationConfig != null ? federationConfig.getMembers() : null;
        boolean newLocationEmpty = newLocationMembers == null || newLocationMembers.isEmpty();
        List<FederatedMember> legacyMembers = repoConfig.getRepoTypeConfig().getFederatedMembers();
        boolean legacyHasMembers = legacyMembers != null && !legacyMembers.isEmpty();
        return newLocationEmpty && legacyHasMembers;
    }
}
