package org.artifactory.repo.service.migration;

import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedRepoConfigEnrichmentServiceImplTest {

    private RepositoryConfigMapperImpl repositoryConfigMapper;
    private RepositoryConfigStorageService storageService;
    private InternalRepositoryConfigService repositoryConfigService;
    private FederatedRepoConfigEnrichmentServiceImpl enrichmentService;

    @BeforeEach
    void setup() {
        repositoryConfigMapper = new RepositoryConfigMapperImpl();
        storageService = mock(RepositoryConfigStorageService.class);
        repositoryConfigService = mock(InternalRepositoryConfigService.class);
        enrichmentService = new FederatedRepoConfigEnrichmentServiceImpl(storageService, repositoryConfigService);
    }

    @Test
    void enrichColdFederatedReposCopiesLegacyMembersToNewLocationAndSkipsWarmAndNonFederated() {
        // cold: federation members only in the legacy repoTypeConfig location
        FederatedRepoConfig coldRepo = FederatedRepoConfig.create("cold-fed", Generic);
        FederatedMember legacyMember = new FederatedMember();
        legacyMember.setUrl("http://other:8081/artifactory/cold-fed");
        coldRepo.getRepoTypeConfig().getFederatedMembers().add(legacyMember);

        // warm: already dual-written to both locations
        FederatedRepoConfig warmRepo = FederatedRepoConfig.create("warm-fed", Generic);
        FederatedMember warmMember = new FederatedMember();
        warmMember.setUrl("http://other:8081/artifactory/warm-fed");
        warmRepo.setFederatedMembers(new ArrayList<>(List.of(warmMember)));

        // non-federated: must be untouched (defensive in-memory filter still skips it if the DB returns it)
        LocalRepoConfig localRepo = LocalRepoConfig.create("local-repo", Generic);

        when(storageService.findRepositoriesByDetailsType("federated"))
                .thenReturn(Stream.of(coldRepo, warmRepo, localRepo)
                        .map(repositoryConfigMapper::repositoryConfigToDbRepositoryConfig).toList());

        enrichmentService.enrichColdFederatedReposBlobs();

        ArgumentCaptor<Set<DbRepositoryConfig>> captor = ArgumentCaptor.forClass(Set.class);
        verify(storageService).bulkUpdateRepositoryConfigs(captor.capture(), any());
        Set<DbRepositoryConfig> updated = captor.getValue();

        // only the cold federated repo is written
        assertEquals(1, updated.size());
        DbRepositoryConfig updatedDb = updated.iterator().next();
        assertEquals("cold-fed", updatedDb.getKey());

        // the enriched blob now carries the members in the new baseConfig.federationConfig location
        FederatedRepoConfig enriched =
                (FederatedRepoConfig) repositoryConfigMapper.dbInfoBlobToRepositoryConfig(updatedDb);
        assertEquals(1, enriched.getBaseConfig().getFederationConfig().getMembers().size());
        // and the modification timestamp was bumped so the bulkUpdate non-zero assertion / HA sync works
        assertTrue(updatedDb.getModified() > 0);
        // the in-memory cache is rebuilt after the write
        verify(repositoryConfigService).rebuildRepoConfigCache();
    }

    @Test
    void enrichColdFederatedReposIsIdempotentWhenAllWarm() {
        FederatedRepoConfig warmRepo = FederatedRepoConfig.create("warm-fed", Generic);
        FederatedMember warmMember = new FederatedMember();
        warmMember.setUrl("http://other:8081/artifactory/warm-fed");
        warmRepo.setFederatedMembers(new ArrayList<>(List.of(warmMember)));

        when(storageService.findRepositoriesByDetailsType("federated")).thenReturn(
                List.of(repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(warmRepo)));

        enrichmentService.enrichColdFederatedReposBlobs();

        // nothing cold -> no write and no cache rebuild
        verify(storageService, never()).bulkUpdateRepositoryConfigs(any(), any());
        verify(repositoryConfigService, never()).rebuildRepoConfigCache();
    }
}
