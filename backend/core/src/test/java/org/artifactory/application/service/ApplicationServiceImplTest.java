package org.artifactory.application.service;

import com.jfrog.apptrust.client.ApptrustClient;
import com.jfrog.apptrust.client.exception.PackageLinkCreationException;
import com.jfrog.apptrust.client.packages.PackagesClient;
import com.jfrog.apptrust.v1.packages.PackageVersionCreatedRequest;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.application.exception.ApplicationServiceDisableException;
import org.artifactory.application.exception.NotifyExistsPackageException;
import org.artifactory.application.exception.NotifyPackageCreatedException;
import org.artifactory.application.model.ApplicationServiceSBOM;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.access.AccessService;
import org.artifactory.storage.db.event.service.metadata.model.MetadataEvent;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.common.ServiceId;
import org.jfrog.metadata.client.model.MetadataPackage;
import org.jfrog.metadata.client.model.MetadataVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    private ApplicationServiceImpl applicationServiceImpl;

    @Mock
    private AccessService accessService;
    @Mock
    private ArtifactoryServersCommonService artifactoryServersCommonService;
    @Mock
    private ApptrustClient apptrustClient;
    @Mock
    private PackagesClient descriptorClient;
    @Mock
    private ArtifactoryContext context;

    AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        applicationServiceImpl = spy(new ApplicationServiceImpl());
        applicationServiceImpl.setAccessService(accessService);
        applicationServiceImpl.setArtifactoryServersCommonService(artifactoryServersCommonService);
        when(apptrustClient.packagesClient()).thenReturn(descriptorClient);
        doReturn(apptrustClient).when(applicationServiceImpl).createApplicationLifecycleClient();
        applicationServiceImpl.init();
        homeStub.setProperty(ConstantValues.applicationServiceFeatureEnabled, "true");
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void tearDown() throws Exception {
        closeable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @MethodSource("provideMetadataVersion")
    void createApplicationServiceSBOM(List<MetadataVersion> metadataVersionList) {
        MetadataEvent metadataEvent = mock(MetadataEvent.class);
        MetadataPackage metadataPackage = mock(MetadataPackage.class);
        when(metadataEvent.getApplicationKey()).thenReturn("appKey");
        when(metadataPackage.getPackageType()).thenReturn("npm");
        when(metadataPackage.getName()).thenReturn("packageName");
        when(metadataPackage.getPkgid()).thenReturn("gav://@package://name");
        when(metadataPackage.getVersions()).thenReturn(metadataVersionList);

        Optional<ApplicationServiceSBOM>
                result = applicationServiceImpl.createApplicationServiceSBOM(metadataEvent, metadataPackage);

        assertTrue(result.isPresent());
        Assertions.assertEquals("appKey", result.get().applicationKey());
        Assertions.assertEquals("gav", result.get().packageType());
        Assertions.assertEquals("@package://name", result.get().packageName());
        String version = metadataVersionList.isEmpty() ? null : metadataVersionList.getFirst().getName();
        Assertions.assertEquals(version, result.get().packageVersion());
    }

    @Test
    void createApplicationServiceSBOMWithoutApplicationKey() {
        MetadataEvent metadataEvent = mock(MetadataEvent.class);
        MetadataPackage metadataPackage = mock(MetadataPackage.class);
        when(metadataEvent.getApplicationKey()).thenReturn(null);

        Optional<ApplicationServiceSBOM>
                result = applicationServiceImpl.createApplicationServiceSBOM(metadataEvent, metadataPackage);

        assertFalse(result.isPresent());
    }

    @Test
    void isApplicationServiceEnabled() {
        when(apptrustClient.ping()).thenReturn(true);
        assertNotNull(applicationServiceImpl.getApptrustClient());
        assertTrue(applicationServiceImpl.isApplicationServiceEnabled());
    }

    @Test
    void isApplicationServiceEnabledApplicationServiceFeatureEnabledIsFalse() {
        homeStub.setProperty(ConstantValues.applicationServiceFeatureEnabled, "false");
        when(apptrustClient.ping()).thenReturn(true);
        assertNotNull(applicationServiceImpl.getApptrustClient());
        assertFalse(applicationServiceImpl.isApplicationServiceEnabled());
        homeStub.setProperty(ConstantValues.applicationServiceFeatureEnabled, "true");
    }

    @Test
    void isApplicationServiceEnabledWithNullClient() {
        applicationServiceImpl.destroy();
        assertNull(applicationServiceImpl.getApptrustClient());
        assertFalse(applicationServiceImpl.isApplicationServiceEnabled());
    }

    @Test
    void isApplicationServiceEnabledWithDisableAppService() {
        when(apptrustClient.ping()).thenReturn(false);
        assertNotNull(applicationServiceImpl.getApptrustClient());
        assertFalse(applicationServiceImpl.isApplicationServiceEnabled());
    }

    @Test
    void createApplicationLifecycleClient() {
        reset(applicationServiceImpl);

        ArtifactoryServer artifactoryServer = mock(ArtifactoryServer.class);
        when(artifactoryServer.getServerId()).thenReturn("id");
        when(artifactoryServersCommonService.getArtifactoryServer(any())).thenReturn(artifactoryServer);

        ServiceId serviceId = mock(ServiceId.class);
        when(serviceId.getFormattedName()).thenReturn("id");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);

        applicationServiceImpl.init();
        assertNotNull(applicationServiceImpl.getApptrustClient());
    }

    @Test
    void notifyPackageCreated() {
        ApplicationServiceSBOM applicationServiceSBOM =
                new ApplicationServiceSBOM("key", "type", "name", "version", new HashMap<>());
        doReturn(true).when(applicationServiceImpl).isApplicationServiceEnabled();
        doNothing().when(descriptorClient).notifyPackageCreated(any());

        applicationServiceImpl.notifyPackageCreated(applicationServiceSBOM);

        PackageVersionCreatedRequest packageVersionCreatedRequest = PackageVersionCreatedRequest.newBuilder()
                .setPackageName(applicationServiceSBOM.packageName())
                .setPackageVersion(applicationServiceSBOM.packageVersion())
                .setPackageType(applicationServiceSBOM.packageType())
                .setApplicationKey(applicationServiceSBOM.applicationKey())
                .build();
        verify(descriptorClient, times(1)).notifyPackageCreated(packageVersionCreatedRequest);
    }

    @Test
    void notifyPackageCreatedWithDisableApplicationService() {
        ApplicationServiceSBOM applicationServiceSBOM =
                new ApplicationServiceSBOM("key", "type", "name", "version", new HashMap<>());
        doReturn(false).when(applicationServiceImpl).isApplicationServiceEnabled();
        assertThrows(ApplicationServiceDisableException.class,
                () -> applicationServiceImpl.notifyPackageCreated(applicationServiceSBOM));
    }

    @Test
    void notifyPackageCreatedWithPackageAlreadyExists() {
        ApplicationServiceSBOM applicationServiceSBOM =
                new ApplicationServiceSBOM("key", "type", "name", "version", new HashMap<>());
        doReturn(true).when(applicationServiceImpl).isApplicationServiceEnabled();
        doThrow(new PackageLinkCreationException(new Exception("UNKNOWN: already exists"))).when(
                descriptorClient).notifyPackageCreated(any());
        assertThrows(NotifyExistsPackageException.class,
                () -> applicationServiceImpl.notifyPackageCreated(applicationServiceSBOM));
    }

    @Test
    void notifyPackageCreatedWithNotifyException() {
        ApplicationServiceSBOM applicationServiceSBOM =
                new ApplicationServiceSBOM("key", "type", "name", "version", new HashMap<>());
        doReturn(true).when(applicationServiceImpl).isApplicationServiceEnabled();
        doThrow(new PackageLinkCreationException(new Exception("UNKNOWN: app key isn't exists"))).when(
                descriptorClient).notifyPackageCreated(any());
        assertThrows(NotifyPackageCreatedException.class,
                () -> applicationServiceImpl.notifyPackageCreated(applicationServiceSBOM));
    }

    @Test
    void destroy() throws IOException {
        applicationServiceImpl.destroy();
        verify(apptrustClient, times(1)).close();
        assertNull(applicationServiceImpl.getApptrustClient());
    }

    @Test
    void destroyWithApplicationClientNull() throws IOException {
        reset(applicationServiceImpl);
        doReturn(null).when(applicationServiceImpl).createApplicationLifecycleClient();
        applicationServiceImpl.init();
        applicationServiceImpl.destroy();
        verify(apptrustClient, times(0)).close();
    }

    @Test
    void destroyWithCloseException() throws IOException {
        doThrow(new IOException()).when(apptrustClient).close();
        applicationServiceImpl.destroy();
        verify(apptrustClient, times(1)).close();
        assertNull(applicationServiceImpl.getApptrustClient());
    }

    private static Stream<Arguments> provideMetadataVersion() {
        MetadataVersion metadataVersion = new MetadataVersion();
        metadataVersion.setName("1.0.0");
        return Stream.of(Arguments.of(List.of(metadataVersion)),
                Arguments.of(List.of()));
    }
}