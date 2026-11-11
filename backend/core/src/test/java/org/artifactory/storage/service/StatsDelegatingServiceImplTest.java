package org.artifactory.storage.service;

import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.storage.db.fs.entity.Stat;
import org.artifactory.storage.db.fs.service.AbstractStatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class StatsDelegatingServiceImplTest {

    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private HttpRepo httpRepo;
    @Mock
    private RemoteRepoConfig remoteRepoConfig;
    @Mock
    private RemoteRepositoryRepoTypeConfig repoTypeConfig;

    private AutoCloseable mocks;
    private StatsDelegatingServiceImpl service;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        service = new StatsDelegatingServiceImpl();
        ReflectionTestUtils.setField(service, "repoService", internalRepositoryService);
    }

    @AfterEach
    public void cleanup() throws Exception {
        mocks.close();
    }

    @Test
    void testGetRemoteRepoKey() {
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getUrl()).thenReturn("http://artifactory/repoKey");
        assertEquals("repoKey", service.getRemoteRepoKey(remoteRepoConfig));
    }

    @Test
    void testGetRemoteRepoKeyThrowsException() {
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getUrl()).thenReturn(null);
        assertNull(service.getRemoteRepoKey(remoteRepoConfig));
    }

    @Test
    void testGetRemoteRepoKeyReturnsNull() {
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getUrl()).thenReturn("http://artifactory/");
        assertNull(service.getRemoteRepoKey(remoteRepoConfig));
    }

    @Test
    void onTraversingEndTest() {
        ConcurrentMap<String, Map<String, Collection<AbstractStatsService.StatsEvent>>> delegationQueue =
                new ConcurrentHashMap<>();
        delegationQueue.put("key", createSingleRepoKeyEventMap());
        ReflectionTestUtils.setField(service, "delegationQueue", delegationQueue);
        when(internalRepositoryService.remoteRepositoryByKey(any())).thenReturn(httpRepo);
        when(httpRepo.getRepoConfig()).thenReturn(remoteRepoConfig);
        when(remoteRepoConfig.isSmart()).thenReturn(true);
        when(remoteRepoConfig.isStatsSyncEnabled()).thenReturn(true);
        when(httpRepo.isOffline()).thenReturn(false);
        when(httpRepo.getUrl()).thenReturn("https://hello.com");
        when(internalRepositoryService.remoteRepositoryByKey(anyString())).thenReturn(httpRepo);
        assertEquals(1, delegationQueue.size());
        service.onTraversingEnd();
        assertEquals(0, delegationQueue.size());
    }

    @Test
    void onTraversingEndSkippedTest() {
        ConcurrentMap<String, Map<String, Collection<AbstractStatsService.StatsEvent>>> delegationQueue =
                new ConcurrentHashMap<>();
        delegationQueue.put("key", createSingleRepoKeyEventMap());
        ReflectionTestUtils.setField(service, "delegationQueue", delegationQueue);
        when(internalRepositoryService.remoteRepositoryByKey(any())).thenReturn(httpRepo);
        when(httpRepo.getRepoConfig()).thenReturn(remoteRepoConfig);
        when(remoteRepoConfig.isSmart()).thenReturn(true);
        when(remoteRepoConfig.isStatsSyncEnabled()).thenReturn(true);
        when(httpRepo.isOffline()).thenReturn(true);
        when(httpRepo.getUrl()).thenReturn("https://hello.com");
        when(internalRepositoryService.remoteRepositoryByKey(anyString())).thenReturn(httpRepo);
        service.onTraversingEnd();
    }

    @Test
    void onTraversingEndTestWithEmptyEventsList() {
        ConcurrentMap<String, Map<String, Collection<AbstractStatsService.StatsEvent>>> delegationQueue =
                new ConcurrentHashMap<>();
        Map<String, Collection<AbstractStatsService.StatsEvent>> singleRepoKeyEvents = new HashMap<>();
        singleRepoKeyEvents.put("repoKey",
                List.of());
        delegationQueue.put("key", singleRepoKeyEvents);
        ReflectionTestUtils.setField(service, "delegationQueue", delegationQueue);
        when(internalRepositoryService.remoteRepositoryByKey(any())).thenReturn(httpRepo);
        when(httpRepo.getRepoConfig()).thenReturn(remoteRepoConfig);
        when(remoteRepoConfig.isSmart()).thenReturn(true);
        when(remoteRepoConfig.isStatsSyncEnabled()).thenReturn(true);
        when(httpRepo.isOffline()).thenReturn(false);
        when(httpRepo.getUrl()).thenReturn("https://hello.com");
        when(internalRepositoryService.remoteRepositoryByKey(anyString())).thenReturn(httpRepo);
        assertEquals(1, delegationQueue.size());
        service.onTraversingEnd();
        assertEquals(0, delegationQueue.size());
    }


    @Test
    void testRemoteRepoConfigIsNull() {
        when(internalRepositoryService.remoteRepositoryByKey(any())).thenReturn(null);
        boolean result = service.filterEvents(createSingleRepoKeyEventMap());
        assertFalse(result);
    }

    @Test
    void testGetContentSynchronisationIsNull() {
        when(internalRepositoryService.remoteRepositoryByKey(any())).thenReturn(httpRepo);
        when(httpRepo.getRepoConfig()).thenReturn(remoteRepoConfig);
        when(remoteRepoConfig.isSmart()).thenReturn(false);
        boolean result = service.filterEvents(createSingleRepoKeyEventMap());
        assertFalse(result);
    }

    @Test
    void testRemoteRequest() throws IOException {
        HttpRequestBase request = new HttpPut();
        when(httpRepo.executeMethod(request)).thenThrow(IOException.class);
        assertNull(service.request(request, httpRepo));
    }

    @ParameterizedTest
    @MethodSource("provideEventsMap")
    void testFilterEvents(Map<String, Collection<AbstractStatsService.StatsEvent>> events, boolean isOffline,
            boolean isContentSynchronisationEnabled, boolean isGetStatisticsEnabled, boolean result) {
        when(httpRepo.getRepoConfig()).thenReturn(remoteRepoConfig);
        when(httpRepo.isOffline()).thenReturn(isOffline);
        when(remoteRepoConfig.isSmart()).thenReturn(isContentSynchronisationEnabled);
        when(remoteRepoConfig.isStatsSyncEnabled()).thenReturn(isGetStatisticsEnabled);
        when(internalRepositoryService.remoteRepositoryByKey(anyString())).thenReturn(httpRepo);
        assertEquals(result, service.filterEvents(events));
    }

    @Test
    void testProcessStatsWithNullRemoteRepoConfig() {
        AbstractStatsService.StatsEvent statsEvent = mock(AbstractStatsService.StatsEvent.class);
        Stat stats = mock(Stat.class);
        String repoPath = "path/repoKey";
        when(statsEvent.getRepoPath()).thenReturn(RepoPathFactory.create(repoPath));
        when(internalRepositoryService.getRepositoryConfigByKey(anyString())).thenReturn(null);
        service.processStats(statsEvent, 1L, stats);
        verify(internalRepositoryService).getRemoteRepoConfigByKey(anyString());
        // Ensure no events are queued
        assertTrue(service.getStatsEvents().isEmpty());
    }

    @Test
    void testProcessStatsWithNonSmartRepo() {
        AbstractStatsService.StatsEvent statsEvent = mock(AbstractStatsService.StatsEvent.class);
        Stat stats = mock(Stat.class);
        String repoPath = "path/repoKey";
        when(statsEvent.getRepoPath()).thenReturn(RepoPathFactory.create(repoPath));
        when(internalRepositoryService.getRemoteRepoConfigByKey(anyString())).thenReturn(remoteRepoConfig);
        when(remoteRepoConfig.isSmart()).thenReturn(false);
        service.processStats(statsEvent, 1L, stats);
        verify(remoteRepoConfig).isSmart();
        // Ensure no events are queued
        assertTrue(service.getStatsEvents().isEmpty());
    }


    @Test
    void testProcessStatsWithSmartRepoAndValidTargetRepoKey() {
        AbstractStatsService.StatsEvent statsEvent = mock(AbstractStatsService.StatsEvent.class);
        Stat stats = mock(Stat.class);
        String repoPath = "path/repoKey";
        when(statsEvent.getRepoPath()).thenReturn(RepoPathFactory.create(repoPath));
        when(internalRepositoryService.getRemoteRepoConfigByKey(anyString())).thenReturn(remoteRepoConfig);
        when(remoteRepoConfig.isSmart()).thenReturn(true);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(remoteRepoConfig.getRepoTypeConfig().getUrl()).thenReturn("http://artifactory/repoKey");
        service.processStats(statsEvent, 1L, stats);
        verify(remoteRepoConfig).isSmart();
    }

    private static Stream<Arguments> provideEventsMap() {
        return Stream.of(
                Arguments.of(Map.of(), false, true, true, false),
                Arguments.of(createSingleRepoKeyEventMap(), true, true, true, false),
                Arguments.of(createSingleRepoKeyEventMap(), false, true, true, true),
                Arguments.of(createSingleRepoKeyEventMap(), false, false, true, false),
                Arguments.of(createSingleRepoKeyEventMap(), false, true, false, false)
        );
    }

    private static Map<String, Collection<AbstractStatsService.StatsEvent>> createSingleRepoKeyEventMap() {
        Map<String, Collection<AbstractStatsService.StatsEvent>> singleRepoKeyEvents = new HashMap<>();
        singleRepoKeyEvents.put("repoKey",
                List.of(new AbstractStatsService.StatsEvent(RepoPathFactory.create("repoKey/cool"), null)));
        return singleRepoKeyEvents;
    }
}