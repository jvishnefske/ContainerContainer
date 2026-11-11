package org.artifactory.metadata.service;

import com.jfrog.artifactory.eventapi.stats.StatsEntryOuterClass;
import com.jfrog.artifactory.eventapi.stats.StatsEventOuterClass;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.fs.model.MetadataStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsEventConverterTest {

    private static final List<String> NO_EXCLUSIONS = List.of();
    private static final List<String> DEFAULT_EXCLUSIONS =
            List.of("Generic", "ReleaseBundles", "PipeInfo", "BuildInfo", "Support");

    @Mock
    private LeadArtifactFacade leadArtifactFacade;
    @Mock
    private RepositoryService repositoryService;

    @Test
    void toStatsEvents_mapsLeadArtifactFields() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx/manifest.json");
        when(leadArtifactFacade.isPackageConsumptionLead(repoPath)).thenReturn(true);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.getRepoConfigByKey("docker-local")).thenReturn(repoConfig);

        List<StatsEventOuterClass.StatsEvent> events = StatsEventConverter.toStatsEvents(
                Map.of(repoPath, new MetadataStats(3L, 100L, 50L, false)),
                leadArtifactFacade, repositoryService, NO_EXCLUSIONS);

        assertEquals(1, events.size());
        StatsEntryOuterClass.StatsEntry entry = events.get(0).getEntries(0);
        assertEquals("stats.v1", events.get(0).getSchemaVersion());
        assertFalse(events.get(0).getEventId().isBlank());
        assertEquals("docker-local", entry.getRepoName());
        assertEquals("docker-local/library/nginx/manifest.json", entry.getLeadFilePath());
        assertEquals("docker", entry.getRepoPackageType());
        assertEquals(3L, entry.getAdditionalDownloads());
        assertEquals(0L, entry.getAdditionalFollowers());
        assertEquals(100L, entry.getLastDownloaded());
        assertEquals(50L, entry.getRemoteLastDownloaded());
        assertFalse(entry.getForce());
    }

    @Test
    void toStatsEvents_skipsNonLeadArtifactsAndZeroDownloads() {
        RepoPath nonLead = RepoPathFactory.create("docker-local", "sha256/abc");
        RepoPath zeroDownloads = RepoPathFactory.create("docker-local", "library/nginx/layer.tgz");
        when(leadArtifactFacade.isPackageConsumptionLead(nonLead)).thenReturn(false);
        when(leadArtifactFacade.isPackageConsumptionLead(zeroDownloads)).thenReturn(true);

        List<StatsEventOuterClass.StatsEvent> events = StatsEventConverter.toStatsEvents(
                Map.of(
                        nonLead, new MetadataStats(5L, 1L, 0L, false),
                        zeroDownloads, new MetadataStats(0L, 1L, 0L, false)),
                leadArtifactFacade, repositoryService, NO_EXCLUSIONS);

        assertTrue(events.isEmpty());
    }

    @Test
    void toStatsEvents_chunksEntriesPerRepoAtOneThousand() {
        Map<RepoPath, MetadataStats> pathToStats = new LinkedHashMap<>();
        IntStream.range(0, 1001).forEach(i -> {
            RepoPath repoPath = RepoPathFactory.create("docker-local", "pkg-" + i + "/manifest.json");
            pathToStats.put(repoPath, new MetadataStats(1L, 1L, 0L, false));
        });
        when(leadArtifactFacade.isPackageConsumptionLead(any())).thenReturn(true);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.getRepoConfigByKey("docker-local")).thenReturn(repoConfig);

        List<StatsEventOuterClass.StatsEvent> events = StatsEventConverter.toStatsEvents(
                pathToStats, leadArtifactFacade, repositoryService, NO_EXCLUSIONS);

        assertEquals(2, events.size());
        assertEquals(1000, events.get(0).getEntriesCount());
        assertEquals(1, events.get(1).getEntriesCount());
    }

    @Test
    void toStatsEvents_resolvesRemoteCacheRepoKey() {
        RepoPath repoPath = RepoPathFactory.create("docker-remote-cache", "library/nginx/manifest.json");
        when(leadArtifactFacade.isPackageConsumptionLead(repoPath)).thenReturn(true);
        org.artifactory.model.LocalCacheRepoConfig cacheConfig =
                mock(org.artifactory.model.LocalCacheRepoConfig.class);
        org.artifactory.model.RemoteRepoConfig remoteConfig = mock(org.artifactory.model.RemoteRepoConfig.class);
        when(remoteConfig.getKey()).thenReturn("docker-remote");
        when(cacheConfig.getRemoteRepoConfig()).thenReturn(remoteConfig);
        when(repositoryService.getRepoConfigByKey("docker-remote-cache")).thenReturn(cacheConfig);

        List<StatsEventOuterClass.StatsEvent> events = StatsEventConverter.toStatsEvents(
                Map.of(repoPath, new MetadataStats(1L, 1L, 0L, true)),
                leadArtifactFacade, repositoryService, NO_EXCLUSIONS);

        assertEquals(1, events.size());
        assertEquals("docker-remote", events.get(0).getEntries(0).getRepoName());
        assertEquals("docker-remote/library/nginx/manifest.json", events.get(0).getEntries(0).getLeadFilePath());
        assertTrue(events.get(0).getEntries(0).getForce());
    }

    @Test
    void toStatsEvents_skipsReposWithExcludedPackageType() {
        RepoPath genericPath = RepoPathFactory.create("generic-local", "blob.bin");
        RepoPath npmPath = RepoPathFactory.create("npm-local", "lodash/-/lodash-4.17.21.tgz");
        when(leadArtifactFacade.isPackageConsumptionLead(genericPath)).thenReturn(true);
        when(leadArtifactFacade.isPackageConsumptionLead(npmPath)).thenReturn(true);
        RepoConfig genericConfig = mock(RepoConfig.class);
        when(genericConfig.getPackageType()).thenReturn(RepoType.Generic);
        RepoConfig npmConfig = mock(RepoConfig.class);
        when(npmConfig.getPackageType()).thenReturn(RepoType.Npm);
        when(repositoryService.getRepoConfigByKey("generic-local")).thenReturn(genericConfig);
        when(repositoryService.getRepoConfigByKey("npm-local")).thenReturn(npmConfig);

        Map<RepoPath, MetadataStats> batch = Map.of(
                genericPath, new MetadataStats(5L, 1L, 0L, false),
                npmPath, new MetadataStats(3L, 1L, 0L, false));

        List<StatsEventOuterClass.StatsEvent> events = StatsEventConverter.toStatsEvents(
                batch, leadArtifactFacade, repositoryService, DEFAULT_EXCLUSIONS);

        assertEquals(1, events.size(), "Generic repo must be excluded; only npm-local should produce an event");
        assertEquals("npm-local", events.get(0).getEntries(0).getRepoName());
    }
}
