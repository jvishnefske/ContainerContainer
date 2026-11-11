package org.artifactory.probes.service;

import jakarta.ws.rs.ServiceUnavailableException;
import org.artifactory.probes.model.ReadinessStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.artifactory.common.ConstantValues.readinessInternalExternalStatusSeparationEnabled;
import static org.artifactory.state.ArtifactoryServerState.OFFLINE;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

/**
 * @author Mor Merhav
 */
public class ProbesServiceImplStatusSeparationFlagDisabledTest extends ProbesServiceImplTest {

    @Override
    protected void mockInternalExternalStatusSeparationFeatureFlag() {
        when(systemProperties.getBooleanProperty(readinessInternalExternalStatusSeparationEnabled)).thenReturn(
                false);
    }

    @Override
    @Test
    public void testCheckReadinessStuck() {
        mockReadinessStuck(true);
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(null));
        assertThrows("Readiness status is not available", ServiceUnavailableException.class,
                () -> probesService.checkReadinessStatus(true));
    }

    @Override
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

        assertThrows(ServiceUnavailableException.class, () -> probesService.checkReadinessStatus(false));
    }

    @Override
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

        assertThrows(ServiceUnavailableException.class, () -> probesService.checkReadinessStatus(true));
    }

    @Override
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

        assertThrows(ServiceUnavailableException.class,
                () -> probesService.checkReadinessStatus(false));
    }

    @Override
    @Test
    public void testCheckReadinessStatusNull() {
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(null));
        assertThrows(ServiceUnavailableException.class, () -> probesService.checkReadinessStatus(false));
    }

    @Override
    @Test
    public void testCheckReadinessStatusNullWithNoCache() {
        when(artifactoryServers.getCurrentMember().getServerState()).thenReturn(OFFLINE);
        assertThrows(ServiceUnavailableException.class, () -> probesService.checkReadinessStatus(true));
    }

    @Override
    @Test
    public void testCheckReadinessStatusStuck_NoErrors_readinessUnavailable() {
        mockReadinessStuck(true);
        ReadinessStatus readinessStatus = createOkReadinessStatus();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));

        assertThrows(ServiceUnavailableException.class,
                () -> probesService.checkReadinessStatus(true));
    }

    @Override
    @Test
    public void testCheckReadinessStuckWithNonNullStatus() {
        ReadinessStatus readinessStatus = ReadinessStatus.builder()
                .running(false)
                .build();
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(readinessStatus));
        mockReadinessStuck(true);

        assertThrows(ServiceUnavailableException.class,
                () -> probesService.checkReadinessStatus(true));
    }

    @Override
    @Test
    public void testCheckReadinessWithStuckAndNullStatus() {
        mockReadinessStuck(true);
        ReflectionTestUtils.setField(probesService, "readinessStatusRef", new AtomicReference<>(null));

        assertThrows(ServiceUnavailableException.class, () -> probesService.checkReadinessStatus(true));
    }

    @Override
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

        assertThrows(ServiceUnavailableException.class, () -> probesService.checkReadinessStatus(false));
    }
}