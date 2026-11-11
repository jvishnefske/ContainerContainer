package org.artifactory.repo.cleanup;

import ch.qos.logback.classic.Level;
import com.jfrog.test.log.MemoryAppender;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.search.ArtifactSearchAddon;
import org.artifactory.addon.search.SearchAddonImpl;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.storage.service.StatsServiceImpl;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Stream;

import static com.jfrog.test.log.MemoryAppenderUtil.createMemoryAppender;
import static org.artifactory.common.ConstantValues.ArtifactCleanupJobLogAllRepoItemsToBeCleaned;
import static org.artifactory.common.ConstantValues.artifactsCleanupJobSkipReposWithRemoteReplication;
import static org.artifactory.common.ConstantValues.artifactsCleanupJobSortByUpdated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("ALL")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ArtifactCleanupServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    private static final String LOCAL_CACHE_REPO_KEY = "remote-repo-cache";
    private static final String REMOTE_REPO_KEY = "remote-repo";

    @Mock
    private StatsServiceImpl statsService;

    @Mock
    private InternalRepositoryService repositoryService;

    @Mock
    private CentralConfigService centralConfigService;

    @Mock
    private AddonsManager addonsManager;

    @InjectMocks
    private ArtifactCleanupServiceImpl artifactCleanupService;

    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void unbind() throws Exception {
        mocks.close();
        ReflectionTestUtils.setField(artifactCleanupService, "logAllRepoItemsToBeCleaned",
                Boolean.parseBoolean(ArtifactCleanupJobLogAllRepoItemsToBeCleaned.getDefValue()));
        ReflectionTestUtils.setField(artifactCleanupService, "skipReposWithRemoteReplication",
                Boolean.parseBoolean(artifactsCleanupJobSkipReposWithRemoteReplication.getDefValue()));
        homeStub.setProperty(artifactsCleanupJobSortByUpdated, artifactsCleanupJobSortByUpdated.getDefValue());
    }

    @ParameterizedTest
    @MethodSource("cleanupsCombinations")
    void testCleanupArtifacts(int cleanupBulkSize, int cleanupMaxItems) {
        RepoPathImpl path = new RepoPathImpl(LOCAL_CACHE_REPO_KEY, "path");
        SearchAddonImpl searchAddon = prepareSearchAddonToRetrieveArtifactsForClean();

        when(searchAddon.streamArtifactsItemInfoNotDownloadedSince(any(), any(),
                eq(cleanupBulkSize), eq(false), eq(LOCAL_CACHE_REPO_KEY)))
                .thenReturn(streamItems(path, cleanupBulkSize),
                        streamItems(path, cleanupBulkSize),
                        streamItems(path, cleanupBulkSize));
        if (cleanupMaxItems % cleanupBulkSize != 0) {
            when(searchAddon.streamArtifactsItemInfoNotDownloadedSince(any(), any(),
                    eq(cleanupMaxItems % cleanupBulkSize), eq(false), eq(LOCAL_CACHE_REPO_KEY)))
                    .thenReturn(streamItems(path, cleanupMaxItems % cleanupBulkSize));
        }

        when(repositoryService.undeploy(path, false, true)).thenReturn(new BasicStatusHolder());

        ReflectionTestUtils.setField(artifactCleanupService, "cleanupBulkSize", cleanupBulkSize);
        ReflectionTestUtils.setField(artifactCleanupService, "cleanupMaxItems", cleanupMaxItems);
        ReflectionTestUtils.setField(artifactCleanupService, "jobDuration", 100000);
        artifactCleanupService.clean();

        verify(repositoryService, times(cleanupMaxItems)).undeploy(path, false, true);
        verify(searchAddon, times((int) Math.ceil((double) cleanupMaxItems / cleanupBulkSize)))
                .streamArtifactsItemInfoNotDownloadedSince(any(), any(), any(), eq(false), eq(LOCAL_CACHE_REPO_KEY));
    }

    @Test
    void testCleanupArtifactsWillDoNothingAfterJobDurationPassed() {
        ReflectionTestUtils.setField(artifactCleanupService, "cleanupBulkSize", 1000);
        ReflectionTestUtils.setField(artifactCleanupService, "cleanupMaxItems", 2000);
        ReflectionTestUtils.setField(artifactCleanupService, "jobDuration", 0);

        LocalCacheRepoConfig localCacheRepoConfig = mock(LocalCacheRepoConfig.class);
        when(repositoryService.getCachedRepoConfigs()).thenReturn(List.of(localCacheRepoConfig));

        artifactCleanupService.clean();
        verify(repositoryService, times(0)).localFederatedOrCachedRepositoryByKey(any());
    }

    @Test
    void testCleanupArtifactsLoggingAllFailedArtifacts() {
        RepoPathImpl path = new RepoPathImpl(LOCAL_CACHE_REPO_KEY, "path");

        SearchAddonImpl searchAddon = prepareSearchAddonToRetrieveArtifactsForClean();

        when(searchAddon.streamArtifactsItemInfoNotDownloadedSince(any(), any(),
                eq(10), eq(false), eq(LOCAL_CACHE_REPO_KEY)))
                .thenReturn(streamItems(path, 10),
                        streamItems(path, 10),
                        streamItems(path, 10));

        BasicStatusHolder basicStatusHolder = new BasicStatusHolder();
        basicStatusHolder.error("", null);
        when(repositoryService.undeploy(path, false, true)).thenReturn(basicStatusHolder);

        ReflectionTestUtils.setField(artifactCleanupService, "cleanupBulkSize", 10);
        ReflectionTestUtils.setField(artifactCleanupService, "cleanupMaxItems", 20);
        ReflectionTestUtils.setField(artifactCleanupService, "jobDuration", 3000);

        MemoryAppender appender = createMemoryAppender(ArtifactCleanupServiceImpl.class, Level.WARN);

        artifactCleanupService.clean();

        assertEquals(1, appender.search("artifact(s) were NOT cleaned", Level.WARN).size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testLoggingOfAllRepoItemsAndUndeployments(boolean logAllRepoItems) {
        RepoPathImpl path = new RepoPathImpl(LOCAL_CACHE_REPO_KEY, "path");
        SearchAddonImpl searchAddon = prepareSearchAddonToRetrieveArtifactsForClean();
        when(repositoryService.undeploy(path, false, true))
                .thenReturn(new BasicStatusHolder());

        when(searchAddon.streamArtifactsItemInfoNotDownloadedSince(any(), any(),
                eq(10), eq(false), eq(LOCAL_CACHE_REPO_KEY)))
                .thenReturn(streamItems(path, 10),
                        streamItems(path, 10),
                        streamItems(path, 10));
        when(searchAddon.streamArtifactsItemInfoNotDownloadedSince(any(), any(),
                eq(null), eq(false), eq(LOCAL_CACHE_REPO_KEY)))
                .thenReturn(streamItems(path, 50));

        ReflectionTestUtils.setField(artifactCleanupService, "cleanupBulkSize", 10);
        ReflectionTestUtils.setField(artifactCleanupService, "cleanupMaxItems", 20);
        ReflectionTestUtils.setField(artifactCleanupService, "jobDuration", 3000);
        ReflectionTestUtils.setField(artifactCleanupService, "logAllRepoItemsToBeCleaned", logAllRepoItems);

        MemoryAppender appender = createMemoryAppender(ArtifactCleanupServiceImpl.class, Level.TRACE);

        artifactCleanupService.clean();

        assertEquals(20, appender.search("Undeploying artifact", Level.TRACE).size());
        if (logAllRepoItems) {
            assertTrue(appender.contains("Found the following list of artifacts (size: 50)", Level.TRACE));
        } else {
            assertTrue(appender.contains("not logging artifacts to be cleaned from repository", Level.TRACE));
        }
    }

    @ParameterizedTest
    @MethodSource("remoteReplicationConfigurationSource")
    void testCleanupArtifactsWithRemoteReplicationConfiguration(String remoteReplicationRepoKey,
            boolean replicationEnabled, boolean cleanExpected) {
        RepoPathImpl path = new RepoPathImpl(LOCAL_CACHE_REPO_KEY, "path");
        homeStub.setProperty(artifactsCleanupJobSkipReposWithRemoteReplication, String.valueOf(true));
        SearchAddonImpl searchAddon = prepareSearchAddonToRetrieveArtifactsForClean();

        RemoteReplicationDescriptor remoteReplication = mock(RemoteReplicationDescriptor.class);
        when(remoteReplication.getRepoKey()).thenReturn(remoteReplicationRepoKey);
        when(remoteReplication.isEnabled()).thenReturn(replicationEnabled);
        MutableCentralConfigDescriptor descriptor = mock(MutableCentralConfigDescriptor.class);
        when(descriptor.getRemoteReplication(remoteReplicationRepoKey)).thenReturn(remoteReplication);
        when(centralConfigService.getMutableDescriptor()).thenReturn(descriptor);

        when(searchAddon.streamArtifactsItemInfoNotDownloadedSince(any(), any(),
                eq(10), eq(false), eq(LOCAL_CACHE_REPO_KEY)))
                .thenReturn(streamItems(path, 10));

        ReflectionTestUtils.setField(artifactCleanupService, "cleanupBulkSize", 10);
        ReflectionTestUtils.setField(artifactCleanupService, "cleanupMaxItems", 20);
        ReflectionTestUtils.setField(artifactCleanupService, "jobDuration", 3000);
        ReflectionTestUtils.setField(artifactCleanupService, "skipReposWithRemoteReplication", true);

        artifactCleanupService.clean();

        if (cleanExpected) {
            verify(repositoryService, times(10)).undeploy(path, false, true);
        } else {
            verify(repositoryService, times(0)).undeploy(path, false, true);
        }
    }

    @Test
    void testSortingByLastUpdatedEnabled() {
        RepoPathImpl path = new RepoPathImpl(LOCAL_CACHE_REPO_KEY, "path");
        homeStub.setProperty(artifactsCleanupJobSortByUpdated, String.valueOf(true));
        SearchAddonImpl searchAddon = prepareSearchAddonToRetrieveArtifactsForClean();
        when(repositoryService.undeploy(path, false, true)).thenReturn(new BasicStatusHolder());

        when(searchAddon.streamArtifactsItemInfoNotDownloadedSince(any(), any(),
                eq(10), eq(true), eq(LOCAL_CACHE_REPO_KEY)))
                .thenReturn(streamItems(path, 10));

        ReflectionTestUtils.setField(artifactCleanupService, "cleanupBulkSize", 10);
        ReflectionTestUtils.setField(artifactCleanupService, "cleanupMaxItems", 10);
        ReflectionTestUtils.setField(artifactCleanupService, "jobDuration", 3000);

        artifactCleanupService.clean();

        verify(searchAddon, times(1))
                .streamArtifactsItemInfoNotDownloadedSince(any(), any(), eq(10), eq(true), eq(LOCAL_CACHE_REPO_KEY));
        verify(repositoryService, times(10)).undeploy(path, false, true);
    }

    private SearchAddonImpl prepareSearchAddonToRetrieveArtifactsForClean() {
        LocalCacheRepoConfig localCacheRepoConfig = mock(LocalCacheRepoConfig.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
        when(remoteRepoConfig.getKey()).thenReturn(REMOTE_REPO_KEY);
        when(remoteRepoConfig.getRepoTypeConfig().getUnusedArtifactsCleanupPeriodHours()).thenReturn(1);
        when(localCacheRepoConfig.getRemoteRepoConfig()).thenReturn(remoteRepoConfig);
        when(localCacheRepoConfig.getKey()).thenReturn(LOCAL_CACHE_REPO_KEY);
        List<LocalCacheRepoConfig> localCacheRepoConfigs = List.of(localCacheRepoConfig);
        when(repositoryService.getCachedRepoConfigs()).thenReturn(localCacheRepoConfigs);

        LocalRepo localRepo = mock(DbLocalRepo.class);
        when(localRepo.isCache()).thenReturn(true);
        when(repositoryService.localFederatedOrCachedRepositoryByKey(LOCAL_CACHE_REPO_KEY)).thenReturn(localRepo);
        when(repositoryService.getLocalFederatedAndCachedRepositories()).thenReturn(List.of(localRepo));
        SearchAddonImpl searchAddon = mock(SearchAddonImpl.class);
        when(addonsManager.addonByType(ArtifactSearchAddon.class)).thenReturn(searchAddon);
        return searchAddon;
    }

    private static Stream<ItemInfo> streamItems(RepoPathImpl path, int limit) {
        FileInfoImpl itemInfo = new FileInfoImpl(path);
        itemInfo.setCreated(0);
        return Stream.of(Stream.generate(() -> itemInfo).limit(limit).toArray(ItemInfo[]::new));
    }

    private static Stream<Arguments> cleanupsCombinations() {
        return Stream.of(
                Arguments.of(2, 3),
                Arguments.of(3, 7),
                Arguments.of(5, 10),
                Arguments.of(15, 40)
        );
    }

    private static Stream<Arguments> remoteReplicationConfigurationSource() {
        return Stream.of(
                Arguments.of(REMOTE_REPO_KEY, true, false),
                Arguments.of(REMOTE_REPO_KEY, false, true),
                Arguments.of("non-related-repo", true, true),
                Arguments.of("non-related-repo", false, true)
        );
    }
}
