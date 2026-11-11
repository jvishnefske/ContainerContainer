package org.artifactory.probes.service;

import com.jfrog.sysconf.SysConfig;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import lombok.Builder;
import lombok.ToString;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.metrics.framework.MetricsFacade;
import org.artifactory.probes.model.ReadinessStatus;
import org.artifactory.probes.tomcat.ServerReadinessMonitor;
import org.artifactory.schedule.TaskService;
import org.artifactory.security.access.AccessService;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.mockito.Answers;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Boolean.FALSE;
import static org.artifactory.common.ConstantValues.qrlLowPriorityAdditionalList;
import static org.artifactory.common.ConstantValues.qrlLowPriorityDefaultList;
import static org.artifactory.common.ConstantValues.readinessInternalExternalStatusSeparationEnabled;
import static org.artifactory.probes.service.ProbesServiceImpl.DEFAULT_LIVENESS_FAIL_READINESS_DURATION;
import static org.artifactory.probes.service.ProbesServiceImpl.LIVENESS_FAIL_READINESS_DURATION_KEY_NAME;
import static org.artifactory.state.ArtifactoryServerState.CONVERTING;
import static org.artifactory.state.ArtifactoryServerState.OFFLINE;
import static org.artifactory.state.ArtifactoryServerState.RUNNING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * @author Andrey Tashlitsky
 */
public class ProbesServiceImplTest {

    @Mock
    private ArtifactoryServer artifactoryServer;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    ArtifactorySystemProperties systemProperties;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private SysConfig sysConfig;
    @Mock
    private File dataDir;
    @Mock
    private File logDir;
    @Mock
    private StorageService storageService;
    @Mock
    private InternalDbService internalDbService;
    @Mock
    private AccessService accessService;
    @Mock
    private TaskService taskService;
    @Mock
    private MetricsFacade metricsFacade;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    protected ArtifactoryServersCommonService artifactoryServers;

    private AutoCloseable mocks;
    protected ProbesServiceImpl probesService;

    @BeforeMethod
    public void beforeMethod() {
        mocks = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        mockArtifactoryHome();
        probesService = createProbesService();
        setupAllOkMocks();
    }

    @AfterMethod
    public void afterMethod() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    public void testCheckReadinessStuck() {
        mockReadinessStuck(true);
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(null));
        assertThrows("Readiness status is not available", InternalServerErrorException.class,
                () -> probesService.checkReadinessStatus(true));
    }

    @Test
    public void testCheckReadinessCachedStatusReady() {
        ReadinessStatus readyStatus = getReadyStatus();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readyStatus));
        probesService.checkReadinessStatus(false);
    }

    @Test(dataProvider = "testCheckReadinessStatusNotReadyDataProvider")
    public void testCheckReadinessStatusNotReady(Args args) {
        mockServerRunningState(args.running);
        mockServerOffline(args.offline);
        mockCanWriteToFileSystem(args.canWrite);
        mockTomcatReady(args.tomcatReady);
        mockDbHealth(args.dbHealthy);
        mockStorageServiceReady(args.storageReady);
        mockAccessServiceReady(args.accessReady);
        mockLastSuccessfulReadiness(1000L);

        assertThrows(args.expectedException, () -> probesService.checkReadinessStatus(true));
    }

    @Test
    public void testCheckReadinessStatusNull() {
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(null));
        assertThrows(InternalServerErrorException.class, () -> probesService.checkReadinessStatus(false));
    }

    @Test(dataProvider = "testCheckReadinessStatusNotReadyDataProvider")
    public void testCheckReadinessCachedStatusNotReady(Args args) {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(args.running)
                .offline(args.offline)
                .canWrite(args.canWrite)
                .tomcatReady(args.tomcatReady)
                .storageServiceReady(args.storageReady)
                .internalDbServiceHealthy(args.dbHealthy)
                .accessServiceReady(args.accessReady)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));

        assertThrows(args.expectedException, () -> probesService.checkReadinessStatus(false));
    }

    @Test
    public void testCheckLivenessStatusReadinessNull() {
        assertThrows(ServiceUnavailableException.class, () -> probesService.checkLivenessStatus());
    }

    @Test
    public void testCheckLivenessStatusLivenessFailReadinessDisabled() {
        mockLivenessDependsOnReadiness(false);
        probesService.checkLivenessStatus();
    }

    @Test
    public void testCheckLivenessStatusReadinessReady() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(true)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .tomcatReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));

        probesService.checkLivenessStatus();
    }

    @Test
    public void testCheckLivenessStatusReadinessNotReady() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockReadinessFailureLimitPassed(true);
        assertThrows(ServiceUnavailableException.class, () -> probesService.checkLivenessStatus());
    }

    @Test
    public void testCheckLivenessStatusReadinessStuckFailureLimitPassed() {
        probesService.triggerReadinessCheck();
        mockReadinessStuck(true);
        mockReadinessFailureLimitPassed(true);
        assertThrows(ServiceUnavailableException.class, () -> probesService.checkLivenessStatus());
    }

    @Test
    public void testServerReadinessMonitorThrowsException() {
        probesService = createProbesService(() -> {
            throw new RuntimeException("Thrown by test");
        });
        probesService.triggerReadinessCheck();
    }

    @Test
    void testTaskIsScheduledOnContextCreated() {
        probesService.onContextCreated();
        verify(taskService).startTask(any(), eq(FALSE));
    }

    @Test
    void testIsReady() {
        assertFalse(probesService.isReady());
        probesService.triggerReadinessCheck();
        assertTrue(probesService.isReady());
        probesService = createProbesService(() -> false);
        assertFalse(probesService.isReady());
    }

    @Test
    void testIsReadyNotReady() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        assertFalse(probesService.isReady());
    }

    @Test
    public void testCheckLivenessStatusReadinessNotReadyFailureLimitNotPassed() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockReadinessFailureLimitPassed(false);
        probesService.checkLivenessStatus();
    }

    @Test
    public void testCheckLivenessStatusReadinessStuckFailureLimitNotPassed() {
        probesService.triggerReadinessCheck();
        mockReadinessStuck(true);
        mockReadinessFailureLimitPassed(false);
        probesService.checkLivenessStatus();
    }

    @Test
    public void testCheckReadinessStatusNotReadyWithCache() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .tomcatReady(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockLastSuccessfulReadiness(1000L);

        assertThrows(InternalServerErrorException.class,
                () -> probesService.checkReadinessStatus(false));
    }

    @Test
    public void testCheckReadinessStuckWithNonNullStatus() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockReadinessStuck(true);

        assertThrows(InternalServerErrorException.class,
                () -> probesService.checkReadinessStatus(true));
    }

    @Test
    public void testTriggerReadinessCheckSuccessUpdatesLastSuccessfulReadiness() {
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        assertTrue(status.isReady());

        @SuppressWarnings("unchecked")
        long lastSuccessful = ((AtomicLong)
                ReflectionTestUtils.getField(probesService, "lastSuccessfulReadinessMillis")).get();
        assertTrue(lastSuccessful > 0);
        assertTrue(getLastReadinessRunMillis() > 0);
    }

    @Test
    public void testTriggerReadinessCheckFailureDoesNotUpdateLastSuccessfulReadiness() {
        mockServerRunningState(false);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);
        mockLastSuccessfulReadiness(1000L);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        long lastSuccessful = ((AtomicLong)
                ReflectionTestUtils.getField(probesService, "lastSuccessfulReadinessMillis")).get();
        assertEquals(lastSuccessful, 1000L);
        assertTrue(getLastReadinessRunMillis() > 0,
                "lastReadinessRunMillis must be updated even when readiness is not ready");
    }

    @Test
    public void testCheckCanWriteDataDirFails() {
        when(artifactoryHome.getDataDir()).thenReturn(dataDir);
        when(dataDir.canWrite()).thenReturn(false);
        when(artifactoryHome.getLogDir()).thenReturn(logDir);
        when(logDir.canWrite()).thenReturn(true);
        mockServerRunningState(true);
        mockServerOffline(false);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // When data dir can't write, readiness should fail
        assertFalse(status.isReady());
    }

    @Test
    public void testCheckCanWriteLogDirFails() {
        when(artifactoryHome.getDataDir()).thenReturn(dataDir);
        when(dataDir.canWrite()).thenReturn(true);
        when(artifactoryHome.getLogDir()).thenReturn(logDir);
        when(logDir.canWrite()).thenReturn(false);
        mockServerRunningState(true);
        mockServerOffline(false);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // When log dir can't write, readiness should fail
        assertFalse(status.isReady());
    }

    @Test
    public void testServerStateConverting() {
        when(artifactoryServers.getCurrentMember()).thenReturn(artifactoryServer);
        when(artifactoryServer.getServerState()).thenReturn(CONVERTING);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // When server is in CONVERTING state (not RUNNING), readiness should fail
        assertFalse(status.isReady());
    }

    @Test
    public void testCheckReadinessStatusTransitionFromReadyToNotReady() {
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();
        @SuppressWarnings("unchecked")
        ReadinessStatus firstStatus = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        assertTrue(firstStatus.isReady());

        mockServerRunningState(false);
        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus secondStatus = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        assertFalse(secondStatus.isReady());
    }

    @Test
    public void testCheckLivenessStatusBoundaryElapsedTimeEqualsFailureLimit() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));

        when(sysConfig.getInt(LIVENESS_FAIL_READINESS_DURATION_KEY_NAME,
                DEFAULT_LIVENESS_FAIL_READINESS_DURATION)).thenReturn(2);
        probesService.setTimeSupplier(() -> 3000L);
        mockLastSuccessfulReadiness(1000L);

        probesService.checkLivenessStatus();
    }

    @Test
    public void testCheckLivenessStatusBoundaryElapsedTimeJustAfterFailureLimit() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));

        when(sysConfig.getInt(LIVENESS_FAIL_READINESS_DURATION_KEY_NAME,
                DEFAULT_LIVENESS_FAIL_READINESS_DURATION)).thenReturn(1);
        probesService.setTimeSupplier(() -> 3000L);
        mockLastSuccessfulReadiness(1000L);

        assertThrows(ServiceUnavailableException.class, () -> probesService.checkLivenessStatus());
    }

    @Test
    public void testServerOfflineFalseState() {
        mockServerOffline(false);
        mockServerRunningState(true);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // When server is online, readiness should succeed
        assertTrue(status.isReady());
    }

    @Test
    public void testServerOfflineTrueState() {
        mockServerOffline(true);
        mockServerRunningState(true);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // When server is offline, readiness should fail
        assertFalse(status.isReady());
    }

    @Test
    public void testCheckLivenessStatusReadinessStuckWithReadyStatus() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(true)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockReadinessStuck(true, 1000L);

        // Should throw even with ready status if stuck
        assertThrows(ServiceUnavailableException.class, () -> probesService.checkLivenessStatus());
    }

    @Test
    public void testCheckReadinessStatusWithNoCacheTriggersNewCheck() {
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        // First call with noCache=true should trigger a readiness check
        probesService.checkReadinessStatus(true);

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        assertTrue(status.isReady());
    }

    @Test
    public void testIsReadyLogsMessageWhenStatusNull() {
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(null));
        assertFalse(probesService.isReady());
    }

    @Test
    public void testIsReadyLogsMessageWhenNotReady() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        assertFalse(probesService.isReady());
    }

    @Test
    public void testServerRunningStateExceptionReturnsfalse() {
        when(artifactoryServers.getCurrentMember()).thenThrow(new RuntimeException("Test exception"));

        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // Should fail readiness when running check throws exception
        assertFalse(status.isReady());
    }

    @Test
    public void testOfflineCheckExceptionReturnsTrue() {
        mockServerRunningState(true);
        when(artifactoryContext.isOffline()).thenThrow(new RuntimeException("Test exception"));
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // Should fail readiness when offline check throws exception
        assertFalse(status.isReady());
    }

    @Test
    public void testCanWriteCheckException() {
        mockServerRunningState(true);
        mockServerOffline(false);
        when(artifactoryHome.getDataDir()).thenThrow(new RuntimeException("Test exception"));
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // Should fail readiness when can write check throws exception
        assertFalse(status.isReady());
    }

    @Test
    public void testStorageServiceException() {
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        doThrow(new RuntimeException("Test exception")).when(storageService).ping(anyBoolean());
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // Should fail readiness when storage service throws exception
        assertFalse(status.isReady());
    }

    @Test
    public void testInternalDbServiceException() {
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        doThrow(new RuntimeException("Test exception")).when(internalDbService).health();
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // Should fail readiness when db service throws exception
        assertFalse(status.isReady());
    }

    @Test
    public void testAccessServiceException() {
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        doThrow(new RuntimeException("Test exception")).when(accessService).readiness();

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // Should fail readiness when access service throws exception
        assertFalse(status.isReady());
    }

    @Test
    public void testTomcatReadinessException() {
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        ServerReadinessMonitor serverReadinessMonitor = mock(ServerReadinessMonitor.class);
        when(serverReadinessMonitor.isReady()).thenThrow(new RuntimeException("Test exception"));
        ReflectionTestUtils.setField(probesService, "serverReadinessMonitor", serverReadinessMonitor);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // When Tomcat throws exception, it returns true by default
        assertTrue(status.isReady());
    }

    @Test
    public void testHandleReadinessFailureWithNullStatusAndFeatureFlagDisabled() {
        mockInternalExternalStatusSeparationFeatureFlagDisabled();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(null));

        assertThrows(ServiceUnavailableException.class, () -> probesService.checkReadinessStatus(false));
    }

    @Test
    public void testHandleReadinessFailureWithNullStatusAndFeatureFlagEnabled() {
        mockInternalExternalStatusSeparationFeatureFlagEnabled();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(null));

        assertThrows(InternalServerErrorException.class, () -> probesService.checkReadinessStatus(false));
    }

    @Test
    public void testHandleReadinessFailureWithFailedStatusAndFeatureFlagDisabled() {
        mockInternalExternalStatusSeparationFeatureFlagDisabled();
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockLastSuccessfulReadiness(1000L);

        assertThrows(ServiceUnavailableException.class, () -> probesService.checkReadinessStatus(false));
    }

    @Test
    public void testHandleReadinessFailureWithFailedStatusAndFeatureFlagEnabled() {
        mockInternalExternalStatusSeparationFeatureFlagEnabled();
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockLastSuccessfulReadiness(1000L);

        assertThrows(InternalServerErrorException.class, () -> probesService.checkReadinessStatus(false));
    }

    @Test
    public void testCheckLivenessStatusWithMultipleInternalFailures() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(true)
                .canWrite(false)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockReadinessFailureLimitPassed(true);

        assertThrows(ServiceUnavailableException.class, () -> probesService.checkLivenessStatus());
    }

    @Test
    public void testTriggerReadinessCheckWithAllComponentsHealthy() {
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        assertTrue(status != null && status.isReady());
    }

    @Test
    public void testCheckReadinessWithStuckAndNullStatus() {
        mockReadinessStuck(true);
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(null));

        assertThrows(InternalServerErrorException.class, () -> probesService.checkReadinessStatus(true));
    }

    @Test
    public void testIsReadyReturnsTrue() {
        ReadinessStatus readyStatus = getReadyStatus();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readyStatus));

        assertTrue(probesService.isReady());
    }

    @Test
    public void testCheckLivenessStatusWithReadinessFailureLimitEdgeCase() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));

        when(sysConfig.getInt(LIVENESS_FAIL_READINESS_DURATION_KEY_NAME,
                DEFAULT_LIVENESS_FAIL_READINESS_DURATION)).thenReturn(2);
        probesService.setTimeSupplier(() -> 4000L);
        mockLastSuccessfulReadiness(1000L);

        assertThrows(ServiceUnavailableException.class, () -> probesService.checkLivenessStatus());
    }

    @Test
    public void testStorageServicePingWithExceptionScenario() {
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        doThrow(new RuntimeException("Storage error")).when(storageService).ping(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        assertFalse(status.isReady());
    }

    @Test
    public void testMultipleReadinessCheckCalls() {
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();
        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        assertTrue(status.isReady());
    }

    @Test(dataProvider = "readinessStuckTimingProvider")
    public void testReadinessStuckTiming(boolean flagEnabled, Class<? extends RuntimeException> expectedException,
            ReadinessStuckTimingScenario scenario) {
        if (flagEnabled) {
            mockInternalExternalStatusSeparationFeatureFlagEnabled();
        } else {
            mockInternalExternalStatusSeparationFeatureFlagDisabled();
        }
        mockLastReadinessRun(scenario.lastReadinessRunMillis);
        mockLastSuccessfulReadiness(scenario.lastSuccessfulReadinessMillis);
        mockReadinessTimeoutThreshold(1);
        probesService.setTimeSupplier(() -> scenario.currentTimeMillis);
        ReflectionTestUtils.setField(probesService, "readinessStatusRef",
                new AtomicReference<>(scenario.cachedStatus));

        String context = " [flag=" + flagEnabled + ", scenario=" + scenario.description + "]";
        RuntimeException thrown = expectThrows(expectedException, () -> probesService.checkReadinessStatus(false));
        String message = thrown.getMessage();
        assertNotNull(message, "Expected non-null exception message" + context);
        if (scenario.expectStuckMessage) {
            assertTrue(message.contains("stuck"),
                    "Expected stuck message but was: " + message + context);
        } else {
            assertFalse(message.contains("stuck"),
                    "Expected message not to mention stuck but was: " + message + context);
        }
        if (scenario.expectStatusUnavailableMessage) {
            assertTrue(message.contains("Readiness status is not available"),
                    "Expected status-unavailable message but was: " + message + context);
        }
    }

    @DataProvider
    public static Object[][] readinessStuckTimingProvider() {
        ReadinessStatus readyStatus = createOkReadinessStatus();
        ReadinessStuckTimingScenario neverRanWithNullStatus = ReadinessStuckTimingScenario.builder()
                .description("job never ran + null cached status")
                .lastReadinessRunMillis(0L).lastSuccessfulReadinessMillis(0L).currentTimeMillis(3000L)
                .cachedStatus(null).expectStuckMessage(false).expectStatusUnavailableMessage(true).build();
        ReadinessStuckTimingScenario ranButStuckNeverSucceeded = ReadinessStuckTimingScenario.builder()
                .description("job ran but is now stuck + never succeeded + null cached status")
                .lastReadinessRunMillis(1000L).lastSuccessfulReadinessMillis(0L).currentTimeMillis(3000L)
                .cachedStatus(null).expectStuckMessage(true).expectStatusUnavailableMessage(false).build();
        ReadinessStuckTimingScenario ranWithinThresholdNeverSucceeded = ReadinessStuckTimingScenario.builder()
                .description("job ran within threshold + never succeeded + null cached status")
                .lastReadinessRunMillis(1000L).lastSuccessfulReadinessMillis(0L).currentTimeMillis(1000L)
                .cachedStatus(null).expectStuckMessage(false).expectStatusUnavailableMessage(false).build();
        ReadinessStuckTimingScenario stuckAfterPreviousSuccessWithReadyStatus = ReadinessStuckTimingScenario.builder()
                .description("job stuck after a previous success + ready cached status")
                .lastReadinessRunMillis(1000L).lastSuccessfulReadinessMillis(2900L).currentTimeMillis(3000L)
                .cachedStatus(readyStatus).expectStuckMessage(true).expectStatusUnavailableMessage(false).build();
        ReadinessStuckTimingScenario neverRanAfterPreviousSuccessNullStatus = ReadinessStuckTimingScenario.builder()
                .description("job has not run since a previous success + null cached status")
                .lastReadinessRunMillis(0L).lastSuccessfulReadinessMillis(2900L).currentTimeMillis(3000L)
                .cachedStatus(null).expectStuckMessage(false).expectStatusUnavailableMessage(false).build();
        return new Object[][] {
                {true, InternalServerErrorException.class, neverRanWithNullStatus},
                {false, ServiceUnavailableException.class, neverRanWithNullStatus},
                {true, InternalServerErrorException.class, ranButStuckNeverSucceeded},
                {false, ServiceUnavailableException.class, ranButStuckNeverSucceeded},
                {true, InternalServerErrorException.class, ranWithinThresholdNeverSucceeded},
                {false, ServiceUnavailableException.class, ranWithinThresholdNeverSucceeded},
                {true, InternalServerErrorException.class, stuckAfterPreviousSuccessWithReadyStatus},
                {false, ServiceUnavailableException.class, stuckAfterPreviousSuccessWithReadyStatus},
                {true, InternalServerErrorException.class, neverRanAfterPreviousSuccessNullStatus},
                {false, ServiceUnavailableException.class, neverRanAfterPreviousSuccessNullStatus},
        };
    }

    @Test
    public void testCheckReadinessStatusNullWithNoCache() {
        when(artifactoryServers.getCurrentMember().getServerState()).thenReturn(OFFLINE);
        assertThrows(InternalServerErrorException.class, () -> probesService.checkReadinessStatus(true));
    }

    @Test
    public void testCheckReadinessStatusStuck_NoErrors_readinessUnavailable() {
        mockReadinessStuck(true);
        ReadinessStatus readinessStatus = createOkReadinessStatus();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));

        assertThrows(InternalServerErrorException.class,
                () -> probesService.checkReadinessStatus(true));
    }

    @Test(dataProvider = "stuckExceptionClassProvider")
    public void testCheckReadinessStuckDoesNotLeakStaleStatus(boolean flagEnabled,
            Class<? extends RuntimeException> expectedException) {
        if (flagEnabled) {
            mockInternalExternalStatusSeparationFeatureFlagEnabled();
        } else {
            mockInternalExternalStatusSeparationFeatureFlagDisabled();
        }
        // Stale status carries a recognizable error message ("Artifactory is not running" via NOT_RUNNING enum)
        ReadinessStatus staleStatus = ReadinessStatus.builder()
                .running(false)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(staleStatus));
        mockReadinessStuck(true);

        RuntimeException thrown = expectThrows(expectedException,
                () -> probesService.checkReadinessStatus(true));
        String message = thrown.getMessage();
        assertNotNull(message, "Expected non-null exception message");
        assertTrue(message.contains("stuck"),
                "Expected exception message to mention 'stuck' but was: " + message);
        assertFalse(message.contains("Artifactory is not running"),
                "Expected exception message to NOT leak stale status text but was: " + message);
    }

    @DataProvider
    public static Object[][] stuckExceptionClassProvider() {
        return new Object[][] {
                {true, InternalServerErrorException.class},
                {false, ServiceUnavailableException.class}
        };
    }

    @Test
    public void testCheckOfflineWithContextException() {
        mockServerRunningState(true);
        when(artifactoryContext.isOffline()).thenThrow(new RuntimeException("Context offline check failed"));
        mockCanWriteToFileSystem(true);
        mockTomcatReady(true);
        mockDbHealth(true);
        mockStorageServiceReady(true);
        mockAccessServiceReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        // Exception causes offline to be true (returns true)
        assertFalse(status.isReady());
    }

    @Test
    public void testCheckAllComponentsFailSimultaneously() {
        when(artifactoryServers.getCurrentMember()).thenThrow(new RuntimeException("Running check failed"));
        when(artifactoryContext.isOffline()).thenThrow(new RuntimeException("Offline check failed"));
        when(artifactoryHome.getDataDir()).thenThrow(new RuntimeException("Data dir failed"));
        doThrow(new RuntimeException("DB check failed")).when(internalDbService).health();
        doThrow(new RuntimeException("Storage check failed")).when(storageService).ping(anyBoolean());
        doThrow(new RuntimeException("Access check failed")).when(accessService).readiness();
        mockTomcatReady(true);

        probesService.triggerReadinessCheck();

        @SuppressWarnings("unchecked")
        ReadinessStatus status = ((AtomicReference<ReadinessStatus>)
                ReflectionTestUtils.getField(probesService, "readinessStatusRef")).get();
        assertFalse(status.isReady());
    }

    @Test
    public void testGetReadinessStatusMsgFormat() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockLastSuccessfulReadiness(1000L);

        assertThrows(InternalServerErrorException.class, () -> probesService.checkReadinessStatus(false));
    }

    @Test
    public void testCheckLivenessWhenReadinessStuckButWithinLimit() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(true)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));

        mockReadinessStuck(true, 1000L);
        when(sysConfig.getInt(LIVENESS_FAIL_READINESS_DURATION_KEY_NAME,
                DEFAULT_LIVENESS_FAIL_READINESS_DURATION)).thenReturn(10);

        // Should not throw because failure limit hasn't passed
        probesService.checkLivenessStatus();
    }

    @Test
    public void testCheckLivenessStatusFailsWhenReadinessNeverSucceeded() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .accessServiceReady(true)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockReadinessStuck(false, 0L);

        ServiceUnavailableException thrown = expectThrows(ServiceUnavailableException.class,
                () -> probesService.checkLivenessStatus());
        assertEquals(thrown.getMessage(), "Liveness failed: readiness state has not been reached yet");
    }

    @Test
    public void testCheckLivenessStatusFailsWhenStuckAndNeverSucceeded() {
        ReadinessStatus readinessStatus = getReadyStatus();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockReadinessStuck(true, 0L);

        ServiceUnavailableException thrown = expectThrows(ServiceUnavailableException.class,
                () -> probesService.checkLivenessStatus());
        assertEquals(thrown.getMessage(), "Liveness failed: readiness state has not been reached yet");
    }

    protected static ReadinessStatus createOkReadinessStatus() {
        return ReadinessStatus.builder()
                .running(true)
                .offline(false)
                .canWrite(true)
                .storageServiceReady(true)
                .internalDbServiceHealthy(true)
                .tomcatReady(true)
                .accessServiceReady(true)
                .build();
    }

    protected void mockInternalExternalStatusSeparationFeatureFlagDisabled() {
        when(systemProperties.getBooleanProperty(readinessInternalExternalStatusSeparationEnabled)).thenReturn(false);
    }

    protected void mockInternalExternalStatusSeparationFeatureFlagEnabled() {
        when(systemProperties.getBooleanProperty(readinessInternalExternalStatusSeparationEnabled)).thenReturn(true);
    }

    private void mockArtifactoryHome() {
        ArtifactoryHome.bind(artifactoryHome);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(systemProperties);
    }

    private void setupAllOkMocks() {
        mockReadinessStuck(false);
        mockLivenessDependsOnReadiness(true);
        mockArtifactorySystemProperties();
        mockServerRunningState(true);
        mockServerOffline(false);
        mockCanWriteToFileSystem(true);
        when(artifactoryHome.getLogDir()).thenReturn(logDir);
        when(logDir.canWrite()).thenReturn(true);

        doNothing().when(storageService).ping(true);
        doNothing().when(internalDbService).health();
        doNothing().when(accessService).readiness();
    }

    protected void mockAccessServiceReady(boolean accessReady) {
        if (accessReady) {
            doNothing().when(accessService).readiness();
        } else {
            doThrow(new RuntimeException("Thrown by test")).when(accessService).readiness();
        }
    }

    protected static ReadinessStatus getReadyStatus() {
        return ReadinessStatus.builder()
                .running(true)
                .offline(false)
                .canWrite(true)
                .tomcatReady(true)
                .internalDbServiceHealthy(true)
                .storageServiceReady(true)
                .accessServiceReady(true)
                .build();
    }

    private void mockReadinessFailureLimitPassed(boolean failureLimitPassed) {
        if (failureLimitPassed) {
            mockReadinessFailureLimit(1);
            probesService.setTimeSupplier(() -> 3000L);
            mockLastSuccessfulReadiness(1000L);
        } else {
            mockReadinessFailureLimit(0);
            probesService.setTimeSupplier(() -> 1000L);
            mockLastSuccessfulReadiness(1000L);
        }
    }

    private void mockReadinessFailureLimit(int failureLimitSecs) {
        when(sysConfig.getInt(LIVENESS_FAIL_READINESS_DURATION_KEY_NAME,
                DEFAULT_LIVENESS_FAIL_READINESS_DURATION)).thenReturn(failureLimitSecs);
    }

    /**
     * Mocks all state required for the "readiness stuck" scenarios: last run timestamp, timeout
     * threshold, time supplier, and last successful readiness time.
     *
     * @param stuck               {@code true} to put the service in a stuck state; {@code false} to put it
     *                            in a not-stuck state.
     * @param lastSuccessfulMillis value to assign to {@code lastSuccessfulReadinessMillis}. Pass {@code 0L}
     *                            to simulate "readiness has never been reached".
     */
    protected void mockReadinessStuck(boolean stuck, long lastSuccessfulMillis) {
        mockReadinessTimeoutThreshold(1);
        mockLastReadinessRun(1000L);
        mockLastSuccessfulReadiness(lastSuccessfulMillis);
        if (stuck) {
            probesService.setTimeSupplier(() -> 3000L);
        } else {
            probesService.setTimeSupplier(() -> 1000L);
        }
    }

    /**
     * Convenience overload that sets {@code lastSuccessfulReadinessMillis} to {@code 1000L}, representing
     * a service that has been ready before. Prefer {@link #mockReadinessStuck(boolean, long)} when the test
     * depends on whether readiness has ever been reached.
     */
    protected void mockReadinessStuck(boolean stuck) {
        mockReadinessStuck(stuck, 1000L);
    }

    protected void mockLastReadinessRun(long lastReadinessRunMillis) {
        ReflectionTestUtils.setField(probesService, "lastReadinessRunMillis", new AtomicLong(lastReadinessRunMillis));
    }

    protected void mockLastSuccessfulReadiness(long lastSuccessfulReadinessMillis) {
        ReflectionTestUtils.setField(probesService, "lastSuccessfulReadinessMillis",
                new AtomicLong(lastSuccessfulReadinessMillis));
    }

    private long getLastReadinessRunMillis() {
        return ((AtomicLong) ReflectionTestUtils.getField(probesService, "lastReadinessRunMillis")).get();
    }

    private void mockArtifactorySystemProperties() {
        when(systemProperties.getProperty(qrlLowPriorityDefaultList)).thenReturn("");
        when(systemProperties.getProperty(qrlLowPriorityAdditionalList)).thenReturn("");
        mockInternalExternalStatusSeparationFeatureFlag();
    }

    protected void mockInternalExternalStatusSeparationFeatureFlag() {
        when(systemProperties.getBooleanProperty(readinessInternalExternalStatusSeparationEnabled)).thenReturn(
                Boolean.parseBoolean(readinessInternalExternalStatusSeparationEnabled.getDefValue()));
    }

    protected void mockReadinessTimeoutThreshold(int timeoutThresholdSecs) {
        ReflectionTestUtils.setField(probesService, "readinessThresholdTimeoutMillis", timeoutThresholdSecs);
    }

    private void mockLivenessDependsOnReadiness(boolean value) {
        ReflectionTestUtils.setField(probesService, "livenessDependentOnReadiness", value);
    }

    protected void mockCanWriteToFileSystem(boolean canWrite) {
        when(artifactoryHome.getDataDir()).thenReturn(dataDir);
        when(dataDir.canWrite()).thenReturn(canWrite);
    }

    protected void mockServerOffline(boolean offline) {
        when(artifactoryContext.isOffline()).thenReturn(offline);
    }

    protected void mockServerRunningState(boolean running) {
        ArtifactoryServerState state;
        if (running) {
            state = RUNNING;
        } else {
            state = OFFLINE;
        }
        when(artifactoryServers.getCurrentMember()).thenReturn(artifactoryServer);
        when(artifactoryServer.getServerState()).thenReturn(state);
    }

    protected void mockTomcatReady(boolean tomcatReady) {
        ServerReadinessMonitor serverReadinessMonitor = mock(ServerReadinessMonitor.class);
        when(serverReadinessMonitor.isReady()).thenReturn(tomcatReady);
        ReflectionTestUtils.setField(probesService, "serverReadinessMonitor", serverReadinessMonitor);
    }

    protected void mockDbHealth(boolean dbHealthy) {
        if (dbHealthy) {
            doNothing().when(internalDbService).health();
        } else {
            doThrow(new RuntimeException()).when(internalDbService).health();
        }
    }

    protected void mockStorageServiceReady(boolean ready) {
        if (ready) {
            doNothing().when(storageService).ping(anyBoolean());
        } else {
            doThrow(new RuntimeException("Thrown by test")).when(storageService).ping(anyBoolean());
        }
    }

    private ProbesServiceImpl createProbesService() {
        return createProbesService(() -> true);
    }

    private ProbesServiceImpl createProbesService(ServerReadinessMonitor readinessMonitor) {
        return new ProbesServiceImpl(artifactoryServers, storageService, internalDbService, accessService,
                taskService, readinessMonitor, metricsFacade);
    }

    private static Args.ArgsBuilder args() {
        return Args.builder();
    }

    @Builder
    static class ReadinessStuckTimingScenario {
        private final String description;
        private final long lastReadinessRunMillis;
        private final long lastSuccessfulReadinessMillis;
        private final long currentTimeMillis;
        private final ReadinessStatus cachedStatus;
        private final boolean expectStuckMessage;
        private final boolean expectStatusUnavailableMessage;
    }

    @ToString
    @Builder
    public static class Args {

        // Default values allow positive path
        @Builder.Default
        protected boolean tomcatReady = true;
        @Builder.Default
        protected boolean running = true;
        @Builder.Default
        protected boolean offline = false;
        @Builder.Default
        protected boolean canWrite = true;
        @Builder.Default
        protected boolean dbHealthy = true;
        @Builder.Default
        protected boolean storageReady = true;
        @Builder.Default
        protected boolean accessReady = true;
        protected Class<? extends Exception> expectedException;
    }

    @DataProvider
    public static Object[][] testTriggerReadinessChecksLastSuccessfulReadinessZeroDataProvider() {
        return new Object[][] {
                // serverState, offline, canWriteDataDir, canWriteLogDir
                {RUNNING, false, true, false},
                {RUNNING, false, false, true},
                {RUNNING, false, false, false},
                {RUNNING, true, true, true},
                {RUNNING, true, true, false},
                {RUNNING, true, false, true},
                {RUNNING, true, false, false},
                {CONVERTING, false, true, true},
                {CONVERTING, false, true, false},
                {CONVERTING, false, false, true},
                {CONVERTING, false, false, false},
                {CONVERTING, true, true, true},
                {CONVERTING, true, true, false},
                {CONVERTING, true, false, true},
                {CONVERTING, true, false, false}
        };
    }

    @DataProvider
    public static Object[][] noCacheParamProvider() {
        return new Object[][] {{true}, {false}};
    }

    @DataProvider
    public static Object[][] testCheckReadinessStatusNotReadyDataProvider() {
        return new Object[][] {
                //#region Internal failure combinations
                //#region Single internal failure combinations
                {args().tomcatReady(false).expectedException(getInternalException()).build()},
                {args().running(false).expectedException(getInternalException()).build()},
                {args().offline(true).expectedException(getInternalException()).build()},
                {args().canWrite(false).expectedException(getInternalException()).build()},
                {args().dbHealthy(false).expectedException(getInternalException()).build()},
                //#endregion
                //#region Two internal failures combinations
                {args().tomcatReady(false).running(false).expectedException(getInternalException()).build()},
                {args().tomcatReady(false).offline(true).expectedException(getInternalException()).build()},
                {args().tomcatReady(false).canWrite(false).expectedException(getInternalException()).build()},
                {args().tomcatReady(false).dbHealthy(false).expectedException(getInternalException()).build()},
                {args().running(false).offline(true).expectedException(getInternalException()).build()},
                {args().running(false).canWrite(false).expectedException(getInternalException()).build()},
                {args().running(false).dbHealthy(false).expectedException(getInternalException()).build()},
                {args().offline(true).canWrite(false).expectedException(getInternalException()).build()},
                {args().offline(true).dbHealthy(false).expectedException(getInternalException()).build()},
                //#endregion
                //#region Three internal failure combinations
                {args().tomcatReady(false).running(false).offline(true)
                        .expectedException(getInternalException()).build()},
                {args().tomcatReady(false).running(false).canWrite(false)
                        .expectedException(getInternalException()).build()},
                {args().tomcatReady(false).offline(true).canWrite(false)
                        .expectedException(getInternalException()).build()},
                {args().running(false).offline(true).canWrite(false).expectedException(getInternalException()).build()},
                //#endregion
                //#endregion

                //#region External failure combinations
                //#region Single external failure combinations
                {args().storageReady(false).expectedException(getExternalException()).build()},
                {args().accessReady(false).expectedException(getExternalException()).build()},
                //#endregion
                //#region Two external failures combinations
                {args().storageReady(false).accessReady(false).expectedException(getExternalException()).build()},
                //#endregion
                //#endregion
        };
    }

    private static Class<ServiceUnavailableException> getExternalException() {
        return ServiceUnavailableException.class;
    }

    private static Class<InternalServerErrorException> getInternalException() {
        return InternalServerErrorException.class;
    }
}

