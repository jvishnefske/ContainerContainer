package org.artifactory.schedule.monitor;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicStatusLine;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.AssertionsKt.fail;
import static org.mockito.ArgumentMatchers.any;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;


/**
 * @author Alexei Vainshtein
 */
class OnlineMonitorServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String TRACE = "trace";
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    InternalRepositoryService repositoryService;
    private AutoCloseable mocks;
    private final List<CloseableHttpClient> testHttpClients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (CloseableHttpClient client : testHttpClients) {
            try {
                client.close();
            } catch (IOException ignored) {
                // best effort cleanup of test clients
            }
        }
        testHttpClients.clear();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    private CloseableHttpClient newSpiedTestHttpClient() {
        return newSpiedTestHttpClient(HttpClients.createDefault());
    }

    private CloseableHttpClient newSpiedTestHttpClient(CloseableHttpClient base) {
        CloseableHttpClient client = spy(base);
        testHttpClients.add(client);
        return client;
    }

    @Test
    void testAddOfflineRepo() {
        OnlineMonitorServiceImpl onlineMonitorService = spy(new OnlineMonitorServiceImpl(repositoryService));
        RemoteRepo<?, ?> repo = prepareRemoteRepo();
        when(repositoryService.remoteRepositoryByKey(repo.getKey())).thenReturn(repo);
        addOfflineRepo(onlineMonitorService, repo);
    }

    @Test
    void testAddOfflineRepoTwice() {
        OnlineMonitorServiceImpl onlineMonitorService = spy(new OnlineMonitorServiceImpl(repositoryService));
        RemoteRepo<?, ?> repo = prepareRemoteRepo();
        when(repositoryService.remoteRepositoryByKey(repo.getKey())).thenReturn(repo);
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        ScheduledFuture oldFuture = mock(ScheduledFuture.class);
        doAnswer(invocation -> {
            OnlineMonitorServiceImpl.OnlineMonitorRunnable argument =
                    invocation.getArgument(0, OnlineMonitorServiceImpl.OnlineMonitorRunnable.class);
            assertEquals(100, argument.calculateNextOnlineCheckDelay(repo));
            return oldFuture;
        }).when(scheduledExecutorService)
                .schedule(any(Runnable.class), eq(0L), eq(TimeUnit.MILLISECONDS));

        onlineMonitorService.onlineMonitorScheduler = scheduledExecutorService;
        onlineMonitorService.createOfflineRepoMonitor(repo);
        onlineMonitorService.createOfflineRepoMonitor(repo);
        verify(scheduledExecutorService, times(1))
                .schedule(any(Runnable.class), eq(0L), eq(TimeUnit.MILLISECONDS));
        assertTrue(onlineMonitorService.offlineReposFutures.containsKey(repo.getKey()));
        assertEquals(onlineMonitorService.offlineReposFutures.get(repo.getKey()), oldFuture);
    }

    @Test
    @Disabled
    void testAddOfflineRepoTwiceSimultaneously() {
        CountDownLatch latch = new CountDownLatch(1);
        int threads = 70;
        ExecutorService service = Executors.newFixedThreadPool(threads);
        OnlineMonitorServiceImpl onlineMonitorService = spy(new OnlineMonitorServiceImpl(repositoryService));
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        RemoteRepo<?, ?> repo = prepareRemoteRepo();
        when(repositoryService.remoteRepositoryByKey(repo.getKey())).thenReturn(repo);
        ScheduledExecutorService scheduledExecutorService = prepareExecutor(onlineMonitorService, scheduledFuture);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(service.submit(() -> {
                latch.await();
                onlineMonitorService.createOfflineRepoMonitor(repo);
                return true;
            }));
        }
        latch.countDown();
        try {
            futures.forEach(future -> {
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    fail("Future failed with: " + e.getMessage(), e);
                }
            });
        } finally {
            service.shutdown();
        }
        verify(scheduledExecutorService, atLeast(2))
                .schedule(any(Runnable.class), eq(0L), eq(TimeUnit.MILLISECONDS));
        assertTrue(onlineMonitorService.offlineReposFutures.containsKey(repo.getKey()));
        assertEquals(onlineMonitorService.offlineReposFutures.get(repo.getKey()), scheduledFuture);
        verify(scheduledFuture, atLeast(1)).cancel(true);
    }

    @Test
    void testOnlineMonitorScheduler() {
        OnlineMonitorServiceImpl onlineMonitorService = new OnlineMonitorServiceImpl(repositoryService);
        try {
            onlineMonitorService.initOnlineMonitorScheduler();
            assertEquals(
                    ((ScheduledThreadPoolExecutor) onlineMonitorService.onlineMonitorScheduler).getMaximumPoolSize(),
                    ConstantValues.onlineMonitorThreadsCount.getInt());
            assertTrue(((ScheduledThreadPoolExecutor) onlineMonitorService.onlineMonitorScheduler)
                    .allowsCoreThreadTimeOut());
            assertTrue(((ScheduledThreadPoolExecutor) onlineMonitorService.onlineMonitorScheduler)
                    .getRemoveOnCancelPolicy());
            assertEquals(60, ((ScheduledThreadPoolExecutor) onlineMonitorService.onlineMonitorScheduler)
                    .getKeepAliveTime(TimeUnit.SECONDS));
        } finally {
            onlineMonitorService.destroy();
            assert onlineMonitorService.onlineMonitorScheduler != null;
            assertTrue(onlineMonitorService.onlineMonitorScheduler.isShutdown());
        }
    }

    @ParameterizedTest
    @MethodSource("statusProvider")
    void testIsResourceUnavailable(int statusCode, boolean isUnavailable) {
        OnlineMonitorServiceImpl onlineMonitorService = new OnlineMonitorServiceImpl(repositoryService);
        BasicStatusLine basicStatusLine = mock(BasicStatusLine.class);
        when(basicStatusLine.getStatusCode()).thenReturn(statusCode);
        assertEquals(onlineMonitorService.isResourceUnavailable(basicStatusLine), isUnavailable);
    }

    @Test
    void testCalculateNextOnlineCheckDelay() {
        RemoteRepo<?, ?> repo = prepareRemoteRepo();
        OnlineMonitorServiceImpl onlineMonitorService = new OnlineMonitorServiceImpl(repositoryService);
        when(repositoryService.remoteRepositoryByKey(repo.getKey())).thenReturn(repo);
        OnlineMonitorServiceImpl.OnlineMonitorRunnable runnable =
                onlineMonitorService.createRunnable(repo.getKey());
        assertEquals(100, runnable.calculateNextOnlineCheckDelay(repo));
        assertEquals(100, runnable.calculateNextOnlineCheckDelay(repo));
        assertEquals(100, runnable.calculateNextOnlineCheckDelay(repo));
        assertEquals(155, runnable.calculateNextOnlineCheckDelay(repo));
        assertEquals(547, runnable.calculateNextOnlineCheckDelay(repo));
        assertEquals(1933, runnable.calculateNextOnlineCheckDelay(repo));
        assertEquals(6823, runnable.calculateNextOnlineCheckDelay(repo));
        assertEquals(24082, runnable.calculateNextOnlineCheckDelay(repo));
        assertEquals(84998, runnable.calculateNextOnlineCheckDelay(repo));
        assertEquals(300000, runnable.calculateNextOnlineCheckDelay(repo));
        assertEquals(300000, runnable.calculateNextOnlineCheckDelay(repo));
    }

    @ParameterizedTest
    @MethodSource("onlineProvider")
    void testCheckOnline(int statusCode, boolean isOffline) throws IOException {
        RemoteRepo<?, ?> repo = prepareRemoteRepo();
        OnlineMonitorServiceImpl onlineMonitorService = new OnlineMonitorServiceImpl(repositoryService);
        when(repositoryService.remoteRepositoryByKey(repo.getKey())).thenReturn(repo);
        OnlineMonitorServiceImpl.OnlineMonitorRunnable runnable =
                onlineMonitorService.createRunnable(repo.getKey());
        prepareCheckOnline(statusCode, repo);
        assertEquals(runnable.checkOnline(repo), !isOffline);
    }

    @Test
    void testCheckOnlineWithException() throws IOException {
        RemoteRepo<?, ?> repo = prepareRemoteRepo();
        OnlineMonitorServiceImpl onlineMonitorService = new OnlineMonitorServiceImpl(repositoryService);
        when(repositoryService.remoteRepositoryByKey(repo.getKey())).thenReturn(repo);
        OnlineMonitorServiceImpl.OnlineMonitorRunnable runnable =
                onlineMonitorService.createRunnable(repo.getKey());
        when(repo.getUrl()).thenReturn("https://this.url");
        CloseableHttpClient closeableHttpClient = newSpiedTestHttpClient();
        doThrow(new IOException("nope")).when(closeableHttpClient).execute(any(HttpGet.class));
        when(repo.getHttpClient()).thenReturn(closeableHttpClient);
        assertFalse(runnable.checkOnline(repo));
    }

    @Test
    void testStopMonitorForRepo() {
        OnlineMonitorServiceImpl onlineMonitorService = spy(new OnlineMonitorServiceImpl(repositoryService));
        RemoteRepo repo = prepareRemoteRepo();
        when(repositoryService.remoteRepositoryByKey(repo.getKey())).thenReturn(repo);
        addOfflineRepo(onlineMonitorService, repo);
        ScheduledFuture scheduledFuture = onlineMonitorService.offlineReposFutures.get("key");
        onlineMonitorService.stopMonitorForRepo(repo.getKey());
        verify(scheduledFuture, times(1)).cancel(true);
        verify(onlineMonitorService, times(1)).removeRepoFromOfflineMap(repo.getKey());
    }

    @Test
    void testIsRemoteRepoMonitored() {
        OnlineMonitorServiceImpl onlineMonitorService = new OnlineMonitorServiceImpl(repositoryService);
        RemoteRepo<?, ?> repo = prepareRemoteRepo();
        when(repositoryService.remoteRepositoryByKey(repo.getKey())).thenReturn(repo);
        assertFalse(onlineMonitorService.isRemoteRepoMonitored(repo.getKey()));
        addOfflineRepo(onlineMonitorService, repo);
        assertTrue(onlineMonitorService.isRemoteRepoMonitored(repo.getKey()));
        onlineMonitorService.removeRepoFromOfflineMap(repo.getKey());
        assertFalse(onlineMonitorService.isRemoteRepoMonitored(repo.getKey()));
    }

    @Test
    void testRunSuccess() throws IOException {
        var traceId = "online-monitor-key";
        RemoteRepo<?, ?> repo = prepareRemoteRepo();
        when(repo.isAssumedOffline()).thenReturn(true);
        OnlineMonitorServiceImpl onlineMonitorService = spy(new OnlineMonitorServiceImpl(repositoryService));
        when(repositoryService.remoteRepositoryByKey(repo.getKey())).thenReturn(repo);
        OnlineMonitorServiceImpl.OnlineMonitorRunnable runnable =
                onlineMonitorService.createRunnable(repo.getKey());
        prepareCheckOnline(202, repo);
        runnable.run();
        verify(repo, times(1)).resetAssumedOffline();
        verify(onlineMonitorService, times(1)).removeRepoFromOfflineMap(repo.getKey());
    }

    @Test
    void testRunFailed() throws IOException {
        OnlineMonitorServiceImpl onlineMonitorService = spy(new OnlineMonitorServiceImpl(repositoryService));
        RemoteRepo<?, ?> repo = prepareRemoteRepo();
        when(repositoryService.remoteRepositoryByKey(repo.getKey())).thenReturn(repo);
        ScheduledFuture newFuture = mock(ScheduledFuture.class);
        ScheduledFuture oldFuture = mock(ScheduledFuture.class);
        onlineMonitorService.offlineReposFutures.put(repo.getKey(), oldFuture);
        prepareExecutor(100L, onlineMonitorService, newFuture);
        when(repo.isAssumedOffline()).thenReturn(false);

        OnlineMonitorServiceImpl.OnlineMonitorRunnable runnable = onlineMonitorService.createRunnable(repo.getKey());
        prepareCheckOnline(502, repo);
        runnable.run();
        verify(repo, times(1)).setAssumedOffline();
        verify(onlineMonitorService, times(0)).removeRepoFromOfflineMap(repo.getKey());
    }

    @Test
    void testRunWhenRemoteRepoDoesNotExist() {
        OnlineMonitorServiceImpl onlineMonitorService = spy(new OnlineMonitorServiceImpl(repositoryService));
        String repoKey = "nonexistent-repo";
        when(repositoryService.remoteRepositoryByKey(repoKey)).thenReturn(null);
        OnlineMonitorServiceImpl.OnlineMonitorRunnable runnable =
                onlineMonitorService.createRunnable(repoKey);

        runnable.run();

        verify(onlineMonitorService, times(1)).removeRepoFromOfflineMap(repoKey);
        verify(repositoryService, times(1)).remoteRepositoryByKey(repoKey);
    }

    @Test
    void checkOnlineProbeDisablesAutomaticRedirectsAndPreservesClientDefaults() throws IOException {
        RemoteRepo<?, ?> repo = prepareRemoteRepo();
        when(repo.getUrl()).thenReturn("https://this.url");
        CloseableHttpClient base = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom().setSocketTimeout(7777).build())
                .build();
        CloseableHttpClient closeableHttpClient = newSpiedTestHttpClient(base);
        ArgumentCaptor<HttpGet> getCaptor = ArgumentCaptor.forClass(HttpGet.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        BasicStatusLine statusLine = mock(BasicStatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        doReturn(response).when(closeableHttpClient).execute(getCaptor.capture());
        when(repo.getHttpClient()).thenReturn(closeableHttpClient);

        OnlineMonitorServiceImpl onlineMonitorService = new OnlineMonitorServiceImpl(repositoryService);
        OnlineMonitorServiceImpl.OnlineMonitorRunnable runnable =
                onlineMonitorService.createRunnable(repo.getKey());
        assertTrue(runnable.checkOnline(repo));

        RequestConfig requestConfig = getCaptor.getValue().getConfig();
        assertNotNull(requestConfig);
        assertFalse(requestConfig.isRedirectsEnabled());
        // The client's default socket timeout must be preserved (proves copyOrCreateConfig path was taken,
        // not a fresh RequestConfig.custom() build).
        assertEquals(7777, requestConfig.getSocketTimeout());
    }

    private void addOfflineRepo(OnlineMonitorServiceImpl onlineMonitorService, RemoteRepo<?, ?> repo) {
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        ScheduledExecutorService scheduledExecutorService =
                prepareExecutor(onlineMonitorService, scheduledFuture);
        onlineMonitorService.createOfflineRepoMonitor(repo);
        verify(scheduledExecutorService, times(1))
                .schedule(any(Runnable.class), eq(0L), eq(TimeUnit.MILLISECONDS));
        assertTrue(onlineMonitorService.offlineReposFutures.containsKey(repo.getKey()));
        assertEquals(onlineMonitorService.offlineReposFutures.get(repo.getKey()), scheduledFuture);
    }

    private ScheduledExecutorService prepareExecutor(OnlineMonitorServiceImpl onlineMonitorService,
            ScheduledFuture scheduledFuture) {
        return prepareExecutor(0, onlineMonitorService, scheduledFuture);
    }

    private ScheduledExecutorService prepareExecutor(long initDelay, OnlineMonitorServiceImpl onlineMonitorService,
            ScheduledFuture scheduledFuture) {
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            OnlineMonitorServiceImpl.OnlineMonitorRunnable argument =
                    invocation.getArgument(0, OnlineMonitorServiceImpl.OnlineMonitorRunnable.class);
            assertEquals(100, argument.calculateNextOnlineCheckDelay(
                    repositoryService.remoteRepositoryByKey(argument.getRepoKey())));
            return scheduledFuture;
        }).when(scheduledExecutorService)
                .schedule(any(Runnable.class), eq(initDelay), eq(TimeUnit.MILLISECONDS));

        onlineMonitorService.onlineMonitorScheduler = scheduledExecutorService;
        return scheduledExecutorService;
    }

    private RemoteRepo prepareRemoteRepo() {
        RemoteRepo repo = mock(RemoteRepo.class);
        when(repo.getKey()).thenReturn("key");
        when(repo.getAssumedOfflinePeriodSecs()).thenReturn(300L);
        return repo;
    }

    private void prepareCheckOnline(int statusCode, RemoteRepo<?, ?> repo) throws IOException {
        when(repo.getUrl()).thenReturn("https://this.url");
        CloseableHttpClient closeableHttpClient = newSpiedTestHttpClient();
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        BasicStatusLine statusLine = mock(BasicStatusLine.class);
        when(statusLine.getReasonPhrase()).thenReturn("Reason");
        when(statusLine.getStatusCode()).thenReturn(statusCode);
        when(response.getStatusLine()).thenReturn(statusLine);
        doReturn(response).when(closeableHttpClient).execute(any(HttpGet.class));
        when(repo.getHttpClient()).thenReturn(closeableHttpClient);
    }

    static Stream<Arguments> statusProvider() {
        return Stream.of(
                Arguments.of(503, true),
                Arguments.of(502, true),
                Arguments.of(504, true),
                Arguments.of(505, true),
                Arguments.of(400, false),
                Arguments.of(500, false),
                Arguments.of(200, false),
                Arguments.of(202, false),
                Arguments.of(201, false),
                Arguments.of(301, false),
                Arguments.of(306, false)
        );
    }

    static Stream<Arguments> onlineProvider() {
        return Stream.of(
                Arguments.of(503, true),
                Arguments.of(502, true),
                Arguments.of(504, true),
                Arguments.of(505, true),
                Arguments.of(400, false),
                Arguments.of(500, false),
                Arguments.of(200, false),
                Arguments.of(202, false),
                Arguments.of(201, false),
                Arguments.of(301, false),
                Arguments.of(306, false)
        );
    }
}