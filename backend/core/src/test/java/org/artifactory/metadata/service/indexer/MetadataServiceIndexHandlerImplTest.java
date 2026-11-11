package org.artifactory.metadata.service.indexer;

import com.jfrog.artifactory.packageindexed.DeletePackageIndexEventOuterClass;
import com.jfrog.artifactory.packageindexed.PackageIndexEventOuterClass;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.jfbus.JfbusEventingService.JfbusOperationMode;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.MetadataEntityFacade;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.metadata.service.PackagesEventService;
import org.artifactory.model.RepoConfig;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.db.event.service.metadata.model.MutableMetadataEntityBOM;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.metadata.client.model.MetadataPackage;
import org.jfrog.metadata.client.model.MetadataVersionRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.jfbus.JfbusConstants.PACKAGE_UPDATED_EVENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataServiceIndexHandlerImplTest extends ArtifactoryHomeBoundJUnitTest {

    private MetadataServiceIndexHandlerImpl metadataServiceIndexHandler;
    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private MetadataEntityFacade metadataEntityFacade;
    @Mock
    private LeadArtifactFacade leadArtifactFacade;
    @Mock
    private MetadataEventService metadataEventService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ArtifactoryTracer artifactoryTracer;
    @Mock
    private PackagesEventService packagesEventService;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        metadataServiceIndexHandler = Mockito.spy(new MetadataServiceIndexHandlerImpl(packagesEventService));
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(InternalMetadataServiceIndexHandler.class))
                .thenReturn(metadataServiceIndexHandler);
        when(context.beanForType(ArtifactoryTracer.class))
                .thenReturn(artifactoryTracer);
        metadataServiceIndexHandler.setMetadataEventService(metadataEventService);
        metadataServiceIndexHandler.setRepositoryService(repositoryService);
        metadataServiceIndexHandler.setMetadataEntityFacade(metadataEntityFacade);
        metadataServiceIndexHandler.setLeadArtifactFacade(leadArtifactFacade);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        metadataServiceIndexHandler.destroy();
        closeable.close();
    }

    @Test
    void testJfbusOperationMode() {
        setRealTimeIndexingProp(false);
        getBound().setProperty(ConstantValues.jfbusOperationMode, "OFF");
        JfbusOperationMode mode = metadataServiceIndexHandler.jfbusOperationMode();
        assertEquals(JfbusOperationMode.OFF, mode);

        getBound().setProperty(ConstantValues.jfbusOperationMode, "SHADOW");
        mode = metadataServiceIndexHandler.jfbusOperationMode();
        assertEquals(JfbusOperationMode.SHADOW, mode);

        getBound().setProperty(ConstantValues.jfbusOperationMode, "ACTIVE");
        mode = metadataServiceIndexHandler.jfbusOperationMode();
        assertEquals(JfbusOperationMode.ACTIVE, mode);
    }

    @Test
    void testIsPackageTypeSupportedWhenFeatureIsOff() {
        setRealTimeIndexingProp(false);
        Assertions.assertFalse(metadataServiceIndexHandler.shouldUseRealTimeIndex("repo/some/path"));
    }

    @Test
    void testShouldUseRealTimeIndexWithEmptyPath() {
        setRealTimeIndexingProp(true);
        Assertions.assertFalse(metadataServiceIndexHandler.shouldUseRealTimeIndex(""));
    }

    @Test
    void testShouldUseRealTimeIndexWithDocker() {
        setRealTimeIndexingProp(true);
        mockRepoAndPackageType("repo", RepoType.Docker);
        Assertions.assertTrue(metadataServiceIndexHandler.shouldUseRealTimeIndex("repo/some/path"));
    }

    @ParameterizedTest
    @MethodSource("provideSupportedRealTimeIndexingRepoTypes")
    void testShouldUseRealTimeIndex(RepoType repoType) {
        setRealTimeIndexingProp(true);
        mockRepoAndPackageType("repo", repoType);
        Assertions.assertTrue(metadataServiceIndexHandler.shouldUseRealTimeIndex("repo/some/path"));
    }

    @Test
    void testShouldUseRealTimeIndexWithGeneric() {
        setRealTimeIndexingProp(true);
        mockRepoAndPackageType("generic-local", RepoType.Generic);
        Assertions.assertFalse(metadataServiceIndexHandler.shouldUseRealTimeIndex("generic-local"));
    }

    @Test
    void testShouldUseRealTimeIndexingNegative() {
        setRealTimeIndexingProp(true);
        mockRepoAndPackageType("generic-local", RepoType.Generic);
        Assertions.assertFalse(metadataServiceIndexHandler
                .isRealTimeIdxOrJfbusEnabled(RepoPathFactory.create("generic-local")));

        RepoPath manifestPath = RepoPathFactory.create("docker-local/image/tag/manifest.json");
        when(leadArtifactFacade.isLeadArtifact(manifestPath)).thenReturn(false);
        mockRepoAndPackageType("docker-local", RepoType.Docker);
        Assertions.assertFalse(metadataServiceIndexHandler.isRealTimeIdxOrJfbusEnabled(manifestPath));
    }

    @Test
    void testShouldUseRealTimeIndexingPositive() {
        setRealTimeIndexingProp(true);
        RepoPath manifestPath = RepoPathFactory.create("docker-local/image/tag/manifest.json");
        when(leadArtifactFacade.isLeadArtifact(manifestPath)).thenReturn(true);
        mockRepoAndPackageType("docker-local", RepoType.Docker);

        Assertions.assertTrue(metadataServiceIndexHandler.isRealTimeIdxOrJfbusEnabled(manifestPath));
    }

    @Test
    void testDeletePackageAsync() {
        setRealTimeIndexingProp(true);
        mockRepoAndPackageType("docker-local", RepoType.Docker);
        RepoPath manifestPath = RepoPathFactory.create("docker-local/image/tag/manifest.json");
        when(leadArtifactFacade.isLeadArtifact(manifestPath)).thenReturn(true);

        ItemInfo itemInfo = Mockito.mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(manifestPath);

        metadataServiceIndexHandler.deletePackageAsync(itemInfo, null);
        Mockito.verify(metadataServiceIndexHandler).submitPackageDeletionAfterCommit(itemInfo, null);
    }

    @Test
    void submitPackageDeletionAfterCommit_shouldPassConvertedPropertiesToBomCreation_whenGivenNonEmptyPropsMap() {
        setJfbusModeAndExcludedPackages("ACTIVE", "");
        mockRepoAndPackageType("docker-local", RepoType.Docker);
        RepoPath repoPath = RepoPathFactory.create("docker-local/alpine/1.0/manifest.json");
        when(metadataEntityFacade.createPathDeletedEntity(repoPath)).thenReturn(
                createPathDeletedEntity("docker-local", "alpine/1.0/manifest.json", "docker", 0L));

        Map<String, Collection<String>> propsMap = Map.of("build.name", List.of("my-build"));
        ItemInfo itemInfo = Mockito.mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(repoPath);
        when(itemInfo.getName()).thenReturn("manifest.json");

        metadataServiceIndexHandler.submitPackageDeletionAfterCommit(itemInfo, propsMap);

        ArgumentCaptor<Properties> propertiesCaptor = ArgumentCaptor.forClass(Properties.class);
        verify(metadataEntityFacade).createMutableMetadataEntityBOM(
                eq(itemInfo),
                propertiesCaptor.capture(),
                any()
        );
        Properties capturedProperties = propertiesCaptor.getValue();
        assertFalse(capturedProperties.isEmpty());
        assertTrue(capturedProperties.containsKey("build.name"));
        assertEquals(Set.of("my-build"), capturedProperties.get("build.name"));
    }

    @Test
    void testIndexPackageAsyncWithJfbusNotification() throws Exception {
        setJfbusModeAndExcludedPackages("ACTIVE", "");
        setRealTimeIndexingProp(false); // Disable real-time indexing to test only JFBus path
        mockRepoAndPackageType("docker-local", RepoType.Docker);

        RepoPath manifestPath = RepoPathFactory.create("docker-local/alpine/1.0/manifest.json");
        ItemInfo itemInfo = Mockito.mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(manifestPath);

        Properties properties = Mockito.mock(Properties.class);

        when(leadArtifactFacade.isLeadArtifact(manifestPath)).thenReturn(true);

        MutableMetadataEntityBOM bom = Mockito.mock(MutableMetadataEntityBOM.class);
        when(metadataEntityFacade.createMutableMetadataEntityBOM(eq(itemInfo), eq(properties), any()))
                .thenReturn(Optional.of(bom));

        MetadataPackage metadataPackage = Mockito.mock(MetadataPackage.class);
        when(metadataPackage.getName()).thenReturn("alpine");
        when(metadataPackage.getPackageType()).thenReturn("docker");
        when(metadataPackage.getPkgid()).thenReturn("docker://alpine");
        when(metadataEntityFacade.metadataBomToMdsPackage(bom, manifestPath))
                .thenReturn(metadataPackage);
        when(metadataEntityFacade.getStatsIfNeeded(itemInfo)).thenReturn(null);

        metadataServiceIndexHandler.indexPackageAsync(itemInfo, properties);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PackageIndexEventOuterClass.PackageIndexEvent> eventCaptor =
                ArgumentCaptor.forClass(PackageIndexEventOuterClass.PackageIndexEvent.class);
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);

        verify(packagesEventService, times(1)).sendPackageIndexEvent(
                keyCaptor.capture(),
                eventCaptor.capture(),
                headersCaptor.capture()
        );

        assertEquals("alpine", keyCaptor.getValue());
        assertNotNull(eventCaptor.getValue());
        assertEquals("docker", headersCaptor.getValue().get("repo-package-type"));
    }

    @Test
    void testNotifyPropertyChange_sendsJfbusEventWithUpdatedHeader() throws Exception {
        setJfbusModeAndExcludedPackages("ACTIVE", "");
        setRealTimeIndexingProp(false);
        mockRepoAndPackageType("docker-local", RepoType.Docker);

        RepoPath manifestPath = RepoPathFactory.create("docker-local/alpine/1.0/manifest.json");
        ItemInfo itemInfo = Mockito.mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(manifestPath);
        when(itemInfo.isFolder()).thenReturn(false);
        Properties properties = Mockito.mock(Properties.class);

        when(repositoryService.getItemInfo(manifestPath)).thenReturn(itemInfo);
        when(leadArtifactFacade.isLeadArtifact(manifestPath)).thenReturn(true);

        MutableMetadataEntityBOM bom = Mockito.mock(MutableMetadataEntityBOM.class);
        when(metadataEntityFacade.createMutableMetadataEntityBOM(eq(itemInfo), eq(properties), any()))
                .thenReturn(Optional.of(bom));

        MetadataPackage metadataPackage = Mockito.mock(MetadataPackage.class);
        when(metadataPackage.getName()).thenReturn("alpine");
        when(metadataPackage.getPackageType()).thenReturn("docker");
        when(metadataPackage.getPkgid()).thenReturn("docker://alpine");
        when(metadataEntityFacade.metadataBomToMdsPackage(bom, manifestPath))
                .thenReturn(metadataPackage);
        when(metadataEntityFacade.getStatsIfNeeded(itemInfo)).thenReturn(null);

        metadataServiceIndexHandler.notifyPropertyChange(manifestPath, properties);

        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(packagesEventService, times(1)).sendPackageIndexEvent(
                eq("alpine"),
                any(PackageIndexEventOuterClass.PackageIndexEvent.class),
                headersCaptor.capture()
        );
        assertEquals(PACKAGE_UPDATED_EVENT_TYPE, headersCaptor.getValue().get("type"));
        assertEquals("docker", headersCaptor.getValue().get("repo-package-type"));
    }

    @Test
    void testIndexPackageAsyncWithJfbusOff() throws Exception {
        // Setup: JFBus disabled
        setJfbusModeAndExcludedPackages("OFF", "");
        mockRepoAndPackageType("docker-local", RepoType.Docker);

        RepoPath manifestPath = RepoPathFactory.create("docker-local/alpine/1.0/manifest.json");

        // Mock isRealTimeIdxOrJfbusEnabled to return true even though JFBus is OFF
        Mockito.doReturn(true).when(metadataServiceIndexHandler)
                .isRealTimeIdxOrJfbusEnabled(manifestPath);

        ItemInfo itemInfo = Mockito.mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(manifestPath);
        Properties properties = Mockito.mock(Properties.class);

        MutableMetadataEntityBOM bom = Mockito.mock(MutableMetadataEntityBOM.class);
        when(metadataEntityFacade.createMutableMetadataEntityBOM(eq(itemInfo), eq(properties), any()))
                .thenReturn(Optional.of(bom));

        MetadataPackage metadataPackage = Mockito.mock(MetadataPackage.class);
        when(metadataPackage.getName()).thenReturn("alpine");
        when(metadataPackage.getPackageType()).thenReturn("docker");
        when(metadataPackage.getPkgid()).thenReturn("docker://alpine");
        when(metadataEntityFacade.metadataBomToMdsPackage(bom, manifestPath))
                .thenReturn(metadataPackage);
        when(metadataEntityFacade.getStatsIfNeeded(itemInfo)).thenReturn(null);

        metadataServiceIndexHandler.indexPackageAsync(itemInfo, properties);

        // Verify: JFBus sendPackageIndexEvent should NOT be called because JFBus mode is OFF
        verify(packagesEventService, never()).sendPackageIndexEvent(
                any(String.class),
                any(PackageIndexEventOuterClass.PackageIndexEvent.class),
                any(Map.class)
        );
    }

    @Test
    void testIndexPackageAsyncWhenBomCreationFails() throws Exception {
        setJfbusModeAndExcludedPackages("ACTIVE", "");
        setRealTimeIndexingProp(false);
        mockRepoAndPackageType("docker-local", RepoType.Docker);

        RepoPath manifestPath = RepoPathFactory.create("docker-local/alpine/1.0/manifest.json");
        ItemInfo itemInfo = Mockito.mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(manifestPath);
        Properties properties = Mockito.mock(Properties.class);

        when(leadArtifactFacade.isLeadArtifact(manifestPath)).thenReturn(true);

        when(metadataEntityFacade.createMutableMetadataEntityBOM(eq(itemInfo), eq(properties), any()))
                .thenReturn(Optional.empty());

        metadataServiceIndexHandler.indexPackageAsync(itemInfo, properties);

        verify(packagesEventService, never()).sendPackageIndexEvent(
                any(String.class),
                any(PackageIndexEventOuterClass.PackageIndexEvent.class),
                any(Map.class)
        );
    }

    private void setJfbusModeAndExcludedPackages(String mode, String excludedPackageTypes) {
        getBound().setProperty(ConstantValues.jfbusOperationMode.getPropertyName(), mode);
        getBound().setProperty(ConstantValues.jfbusExcludedPackageTypes.getPropertyName(), excludedPackageTypes);
    }

    private void mockRepoAndPackageType(String repoKey, RepoType repoType) {
        RepoConfig repoConfig = Mockito.mock(RepoConfig.class);
        when(repoConfig.getPackageType()).thenReturn(repoType);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repositoryService.repoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
    }

    private void setRealTimeIndexingProp(boolean enabled) {
        getBound().setProperty(ConstantValues.metadataRealTimeIndexingEnable, String.valueOf(enabled));
        metadataServiceIndexHandler.init();
    }

    @ParameterizedTest
    @MethodSource("provideSubmitPackageDeletionAfterCommitTestCases")
    void testSubmitPackageDeletionAfterCommit(
            String testName,
            String jfbusMode,
            String repoKey,
            String repoPath,
            boolean mockRepoConfig,
            MetadataVersionRepo pathDeletedEntity,
            RuntimeException exceptionToThrow,
            boolean shouldCallJfbus,
            String expectedRepoName,
            String expectedLeadFilePath,
            String expectedPackageType,
            Long expectedDownloadCount) throws Exception {

        setJfbusModeAndExcludedPackages(jfbusMode, "");

        if (mockRepoConfig) {
            mockRepoAndPackageType(repoKey, RepoType.Docker);
        } else {
            when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(null);
        }

        RepoPath testRepoPath = RepoPathFactory.create(repoPath);

        if (exceptionToThrow != null) {
            when(metadataEntityFacade.createPathDeletedEntity(testRepoPath))
                    .thenThrow(exceptionToThrow);
        } else if (pathDeletedEntity != null) {
            when(metadataEntityFacade.createPathDeletedEntity(testRepoPath))
                    .thenReturn(pathDeletedEntity);
        }

        ItemInfo itemInfo = Mockito.mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(testRepoPath);
        if (shouldCallJfbus && expectedRepoName != null) {
            when(itemInfo.getName()).thenReturn(expectedRepoName);
        }
        metadataServiceIndexHandler.submitPackageDeletionAfterCommit(itemInfo, null);

        if (!shouldCallJfbus) {
            verify(packagesEventService, never()).sendPackageIndexEvent(
                    any(String.class),
                    any(PackageIndexEventOuterClass.PackageIndexEvent.class),
                    any(Map.class)
            );
        } else {
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<PackageIndexEventOuterClass.PackageIndexEvent> eventCaptor =
                    ArgumentCaptor.forClass(PackageIndexEventOuterClass.PackageIndexEvent.class);
            ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);

            verify(packagesEventService, times(1)).sendPackageIndexEvent(
                    keyCaptor.capture(),
                    eventCaptor.capture(),
                    headersCaptor.capture()
            );

            // Assert: Verify the captured arguments
            assertEquals(expectedRepoName, keyCaptor.getValue());
            assertNotNull(eventCaptor.getValue());
            assertTrue(eventCaptor.getValue().hasDeletePackageIndexEvent());

            PackageIndexEventOuterClass.PackageIndexEvent capturedEvent = eventCaptor.getValue();
            DeletePackageIndexEventOuterClass.DeletePackageIndexEvent deleteEvent =
                    capturedEvent.getDeletePackageIndexEvent();
            assertEquals(expectedRepoName, capturedEvent.getRepoName());
            assertEquals(expectedLeadFilePath, capturedEvent.getLeadFilePath());

            if (expectedPackageType != null) {
                assertEquals(expectedPackageType, capturedEvent.getRepoPackageType());
            }

            if (expectedDownloadCount != null) {
                assertEquals(expectedDownloadCount.longValue(), deleteEvent.getDownloadCount());
            }

            // Headers should be empty for delete events (BOM not mocked, so itemInfo.getName() used)
            assertTrue(headersCaptor.getValue().isEmpty());
        }
    }

    @Test
    void submitPackageDeletionAfterCommit_shouldUseBomNameAndPackageTypeHeaders_whenBomIsPresent()
            throws Exception {
        setJfbusModeAndExcludedPackages("ACTIVE", "");
        mockRepoAndPackageType("docker-local", RepoType.Docker);
        RepoPath repoPath = RepoPathFactory.create("docker-local/alpine/1.0/manifest.json");
        when(metadataEntityFacade.createPathDeletedEntity(repoPath))
                .thenReturn(createPathDeletedEntity("docker-local", "alpine/1.0/manifest.json", "docker", 0L));

        MutableMetadataEntityBOM bom = Mockito.mock(MutableMetadataEntityBOM.class);
        when(bom.getName()).thenReturn("alpine");
        when(bom.getPackageType()).thenReturn("docker");
        when(metadataEntityFacade.createMutableMetadataEntityBOM(any(), any(), any()))
                .thenReturn(Optional.of(bom));

        ItemInfo itemInfo = Mockito.mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(repoPath);

        metadataServiceIndexHandler.submitPackageDeletionAfterCommit(itemInfo, null);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(packagesEventService, times(1)).sendPackageIndexEvent(
                keyCaptor.capture(),
                any(PackageIndexEventOuterClass.PackageIndexEvent.class),
                headersCaptor.capture()
        );
        assertEquals("alpine", keyCaptor.getValue());
        assertEquals("docker", headersCaptor.getValue().get("repo-package-type"));
        assertEquals(1, headersCaptor.getValue().size());
    }

    private static Stream<Arguments> provideSubmitPackageDeletionAfterCommitTestCases() {
        return Stream.of(
                // Test case 1: JFBus mode is OFF - should not call JFBus
                Arguments.of(
                        "JFBus mode is OFF",
                        "OFF", // jfbusMode
                        "docker-local", // repoKey
                        "docker-local/alpine/1.0/manifest.json", // repoPath
                        true, // mockRepoConfig
                        null, // pathDeletedEntity (not needed)
                        null, // exceptionToThrow
                        false, // shouldCallJfbus
                        null, // expectedRepoName (not checked)
                        null, // expectedLeadFilePath (not checked)
                        null, // expectedPackageType (not checked)
                        null  // expectedDownloadCount (not checked)
                ),

                // Test case 2: Success with all fields populated
                Arguments.of(
                        "Success with all fields",
                        "ACTIVE", // jfbusMode
                        "docker-local", // repoKey
                        "docker-local/alpine/1.0/manifest.json", // repoPath
                        true, // mockRepoConfig
                        createPathDeletedEntity("docker-local", "alpine/1.0/manifest.json", "docker", 100L),
                        null, // exceptionToThrow
                        true, // shouldCallJfbus
                        "docker-local", // expectedRepoName
                        "alpine/1.0/manifest.json", // expectedLeadFilePath
                        "docker", // expectedPackageType
                        100L  // expectedDownloadCount
                ),

                // Test case 3: Success with null optional fields
                Arguments.of(
                        "Success with null optional fields",
                        "SHADOW", // jfbusMode
                        "docker-local", // repoKey
                        "docker-local/alpine/1.0/manifest.json", // repoPath
                        true, // mockRepoConfig
                        createPathDeletedEntity("docker-local", "alpine/1.0/manifest.json", null, null),
                        null, // exceptionToThrow
                        true, // shouldCallJfbus
                        "docker-local", // expectedRepoName
                        "alpine/1.0/manifest.json", // expectedLeadFilePath
                        "", // expectedPackageType (default empty for proto)
                        0L  // expectedDownloadCount (default 0 for proto)
                ),

                // Test case 4: Exception thrown by MetadataEntityFacade
                Arguments.of(
                        "MetadataEntityFacade throws exception",
                        "ACTIVE", // jfbusMode
                        "docker-local", // repoKey
                        "docker-local/alpine/1.0/manifest.json", // repoPath
                        true, // mockRepoConfig
                        null, // pathDeletedEntity (not used)
                        new RuntimeException("Failed to create path deleted entity"), // exceptionToThrow
                        false, // shouldCallJfbus
                        null, // expectedRepoName (not checked)
                        null, // expectedLeadFilePath (not checked)
                        null, // expectedPackageType (not checked)
                        null  // expectedDownloadCount (not checked)
                ),

                // Test case 5: Repository config is null
                Arguments.of(
                        "Repository config is null",
                        "ACTIVE", // jfbusMode
                        "deleted-repo", // repoKey
                        "deleted-repo/alpine/1.0/manifest.json", // repoPath
                        false, // mockRepoConfig (don't mock, return null)
                        null, // pathDeletedEntity (not needed)
                        null, // exceptionToThrow
                        false, // shouldCallJfbus
                        null, // expectedRepoName (not checked)
                        null, // expectedLeadFilePath (not checked)
                        null, // expectedPackageType (not checked)
                        null  // expectedDownloadCount (not checked)
                ),

                // Test case 6: Docker package type with SHADOW mode
                Arguments.of(
                        "Docker package type with SHADOW mode",
                        "SHADOW", // jfbusMode
                        "docker-local", // repoKey
                        "docker-local/test/1.0/artifact", // repoPath
                        true, // mockRepoConfig
                        createPathDeletedEntity("docker-local", "test/1.0/artifact", "docker", null),
                        null, // exceptionToThrow
                        true, // shouldCallJfbus
                        "docker-local", // expectedRepoName
                        "test/1.0/artifact", // expectedLeadFilePath
                        "docker", // expectedPackageType
                        0L  // expectedDownloadCount (default 0 for proto when null)
                )
        );
    }

    private static MetadataVersionRepo createPathDeletedEntity(
            String name, String leadFilePath, String type, Long downloadCount) {
        MetadataVersionRepo entity = new MetadataVersionRepo();
        entity.setName(name);
        entity.setLeadFilePath(leadFilePath);
        entity.setType(type);
        entity.setDownloadCount(downloadCount);
        return entity;
    }

    private static Stream<RepoType> provideSupportedRealTimeIndexingRepoTypes() {
        return Stream.of(RepoType.Docker, RepoType.Npm, RepoType.Gradle, RepoType.Maven, RepoType.SBT, RepoType.Ivy,
                RepoType.P2, RepoType.Pypi, RepoType.NuGet, RepoType.Go, RepoType.OCI, RepoType.Debian,
                RepoType.Opkg, RepoType.HelmOCI, RepoType.HuggingFaceML, RepoType.Conda, RepoType.MachineLearning,
                RepoType.Terraform, RepoType.Pub, RepoType.Ansible, RepoType.Bower, RepoType.CRAN, RepoType.Alpine,
                RepoType.BazelModules, RepoType.JetBrainsPlugins, RepoType.NimModel, RepoType.Gems, RepoType.Puppet,
                RepoType.Swift, RepoType.Hex, RepoType.Conan, RepoType.Chef, RepoType.Skills,
                RepoType.Composer, RepoType.LuaRocks);
    }

}