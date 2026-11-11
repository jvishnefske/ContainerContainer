package org.artifactory.storage.db.locks.service;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class AutoDbLocksServiceTest {

    @Mock
    private JFrogTableBasedDbLocksService tableBasedDbLocksService;
    @Mock
    private NativeDbLocksService nativeDbLocksService;
    @Mock
    private ArtifactoryServersCommonService artifactoryServersCommonService;
    @Mock
    private CentralConfigService centralConfigService;
    @InjectMocks
    private AutoDbLocksService autoDbLocksService;

    private AutoCloseable mocks;
    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testAcquireLockNotAllServersSupportNativeLocksUseTableBased() throws TimeoutException {
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        when(configDescriptor.isForceNativeDbLocksMechanismInAutoMode()).thenReturn(false);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        when(nativeDbLocksService.getSupportedVersion()).thenReturn(
                ArtifactoryServiceVersionProvider.v7600.get().getVersion());

        ArtifactoryServer artifactoryServer1 = mock(ArtifactoryServer.class);
        ArtifactoryServer artifactoryServer2 = mock(ArtifactoryServer.class);
        when(artifactoryServer1.getArtifactoryVersion()).thenReturn(
                ArtifactoryServiceVersionProvider.v7590m001.get().getVersion());
        when(artifactoryServer2.getArtifactoryVersion()).thenReturn(
                ArtifactoryServiceVersionProvider.v770.get().getVersion());
        when(artifactoryServersCommonService.getAllArtifactoryServers()).thenReturn(
                List.of(artifactoryServer1, artifactoryServer2));

        autoDbLocksService.acquireLock("category", "key", "owner", 1L, TimeUnit.SECONDS);

        verify(tableBasedDbLocksService).acquireLock("category", "key", "owner", 1L, TimeUnit.SECONDS);
    }

    @Test
    void testAcquireLockAllServersSupportNativeLocksUseNative() throws TimeoutException {
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        when(configDescriptor.isForceNativeDbLocksMechanismInAutoMode()).thenReturn(false);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        when(nativeDbLocksService.getSupportedVersion()).thenReturn(
                ArtifactoryServiceVersionProvider.v7600.get().getVersion());

        ArtifactoryServer artifactoryServer1 = mock(ArtifactoryServer.class);
        ArtifactoryServer artifactoryServer2 = mock(ArtifactoryServer.class);
        when(artifactoryServer1.getArtifactoryVersion()).thenReturn(
                ArtifactoryServiceVersionProvider.v7600.get().getVersion());
        when(artifactoryServer2.getArtifactoryVersion()).thenReturn(
                ArtifactoryServiceVersionProvider.v7630.get().getVersion());
        when(artifactoryServersCommonService.getAllArtifactoryServers()).thenReturn(
                List.of(artifactoryServer1, artifactoryServer2));

        autoDbLocksService.acquireLock("category", "key", "owner", 1L, TimeUnit.SECONDS);

        verify(nativeDbLocksService).acquireLock("category", "key", "owner", 1L, TimeUnit.SECONDS);
    }

    @Test
    void testForceNativeInAutoModeOnAndOff() throws TimeoutException {
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        when(configDescriptor.isForceNativeDbLocksMechanismInAutoMode()).thenReturn(true);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        when(nativeDbLocksService.getSupportedVersion()).thenReturn(
                ArtifactoryServiceVersionProvider.v7600.get().getVersion());

        ArtifactoryServer artifactoryServer1 = mock(ArtifactoryServer.class);
        ArtifactoryServer artifactoryServer2 = mock(ArtifactoryServer.class);
        when(artifactoryServer1.getArtifactoryVersion()).thenReturn(
                ArtifactoryServiceVersionProvider.v7590m001.get().getVersion());
        when(artifactoryServer2.getArtifactoryVersion()).thenReturn(
                ArtifactoryServiceVersionProvider.v770.get().getVersion());
        when(artifactoryServersCommonService.getAllArtifactoryServers()).thenReturn(
                List.of(artifactoryServer1, artifactoryServer2));

        autoDbLocksService.acquireLock("category", "key", "owner", 1L, TimeUnit.SECONDS);

        verify(nativeDbLocksService).acquireLock("category", "key", "owner", 1L, TimeUnit.SECONDS);

        configDescriptor = mock(CentralConfigDescriptor.class);
        when(configDescriptor.isForceNativeDbLocksMechanismInAutoMode()).thenReturn(false);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        autoDbLocksService.acquireLock("category", "key", "owner", 1L, TimeUnit.SECONDS);

        verify(tableBasedDbLocksService).acquireLock("category", "key", "owner", 1L, TimeUnit.SECONDS);
    }
}