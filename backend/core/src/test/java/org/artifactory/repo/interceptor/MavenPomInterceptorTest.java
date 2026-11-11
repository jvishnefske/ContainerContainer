package org.artifactory.repo.interceptor;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MavenPomInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private StoringRepo storingRepo;
    @Mock
    private LocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepo;
    @Mock
    private VfsFile vfsFile;
    @Mock
    private MutableVfsFile mutableVfsFile;
    @Mock
    private MetadataServiceIndexHandler metadataServiceIndexHandler;

    private MavenPomInterceptor interceptor;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        interceptor = new MavenPomInterceptor();
        interceptor.setRepositoryService(repositoryService);
        interceptor.setMetadataServiceIndexHandler(metadataServiceIndexHandler);
        when(artifactoryContext.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void checkCopyMove_shouldReject_whenBadPomException() {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/repo.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setKey("repoKey");
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(localRepo);
        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getRepoPath()).thenReturn(repoPath);
        when(vfsFile.getStream()).thenReturn(ResourceUtils.getResource("/maven/stam.pom"));
        when(localRepo.getItemModuleInfo("path/to/repo.pom")).thenReturn(mock(ModuleInfo.class));

        // Act
        CopyMoveCheckResult result = interceptor.checkCopyMove(vfsFile, repoPath, statusHolder);

        // Assert
        assertEquals(REJECT, result);
        assertTrue(statusHolder.isError());
    }

    @Test
    void checkCopyMove_shouldAllow_whenTargetRepoIsNull() {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/repo.pom");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(null);

        // Act
        CopyMoveCheckResult result = interceptor.checkCopyMove(vfsFile, repoPath, statusHolder);

        // Assert
        assertEquals(REJECT, result);
    }

    @Test
    void checkCopyMove_shouldAllow_whenTargetRepoIsNotLocalRepo() {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/repo.pom");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);

        // Act
        CopyMoveCheckResult result = interceptor.checkCopyMove(vfsFile, repoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
    }

    @Test
    void checkCopyMove_shouldAllow_whenTargetRepoIsNotMaven() {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/repo.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Generic);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(localRepo);

        // Act
        CopyMoveCheckResult result = interceptor.checkCopyMove(vfsFile, repoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
    }

    @Test
    void checkCopyMove_shouldAllow_whenSourceItemIsNotFile() {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/repo.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(localRepo);
        when(vfsFile.isFile()).thenReturn(false);

        // Act
        CopyMoveCheckResult result = interceptor.checkCopyMove(vfsFile, repoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file.jar", "file.txt", "file.xml"})
    void checkCopyMove_shouldAllow_whenSourcePathIsNotPom(String fileName) {
        // Arrange
        RepoPath sourceRepoPath = RepoPathFactory.create("sourceRepo", "path/to/" + fileName);
        RepoPath targetRepoPath = RepoPathFactory.create("repoKey", "path/to/repo.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(localRepo);
        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getRepoPath()).thenReturn(sourceRepoPath);

        // Act
        CopyMoveCheckResult result = interceptor.checkCopyMove(vfsFile, targetRepoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
    }

    @Test
    void checkCopyMove_shouldAllow_whenTargetPathIsNotPom() {
        // Arrange
        RepoPath sourceRepoPath = RepoPathFactory.create("sourceRepo", "path/to/repo.pom");
        RepoPath targetRepoPath = RepoPathFactory.create("repoKey", "path/to/file.jar");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(localRepo);
        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getRepoPath()).thenReturn(sourceRepoPath);

        // Act
        CopyMoveCheckResult result = interceptor.checkCopyMove(vfsFile, targetRepoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
    }

    @Test
    void checkCopyMove_shouldAllow_whenSuppressPomConsistencyChecksIsTrue() {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/repo.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(true);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(localRepo);
        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getRepoPath()).thenReturn(repoPath);

        // Act
        CopyMoveCheckResult result = interceptor.checkCopyMove(vfsFile, repoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
    }

    @Test
    void checkCopyMove_shouldReject_whenIOExceptionOccurs() throws IOException {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/repo.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InputStream inputStream = mock(InputStream.class);

        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(localRepo);
        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getRepoPath()).thenReturn(repoPath);
        when(vfsFile.getStream()).thenReturn(inputStream);
        when(localRepo.getItemModuleInfo("path/to/repo.pom")).thenReturn(mock(ModuleInfo.class));
        when(inputStream.read(any(byte[].class))).thenThrow(new IOException("Stream error"));

        // Act
        CopyMoveCheckResult result = interceptor.checkCopyMove(vfsFile, repoPath, statusHolder);

        // Assert
        assertEquals(REJECT, result);
        assertTrue(statusHolder.isError());
    }

    @Test
    void afterCreate_shouldDoNothing_whenItemIsNotFile() {
        // Arrange
        VfsItem folder = mock(VfsItem.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(folder.isFile()).thenReturn(false);

        // Act
        interceptor.afterCreate(folder, statusHolder, ctx);

        // Assert
        assertFalse(statusHolder.isError());
    }

    @Test
    void afterCreate_shouldDoNothing_whenFileNameIsNotPom() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.jar");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        assertFalse(statusHolder.isError());
    }

    @Test
    void afterCreate_shouldDoNothing_whenStoringRepoIsNull() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(null);

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        assertFalse(statusHolder.isError());
    }

    @Test
    void afterCreate_shouldDoNothing_whenStoringRepoIsNotReal() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(false);

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        assertFalse(statusHolder.isError());
    }

    @Test
    void afterCreate_shouldDoNothing_whenRepoIsNotMaven() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Generic);

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        assertFalse(statusHolder.isError());
    }

    @Test
    void afterCreate_shouldThrowRuntimeException_whenBadPomException() throws IOException {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/repo.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(vfsFile.getPath()).thenReturn("path/to/repo.pom");
        when(vfsFile.getRepoPath()).thenReturn(repoPath);
        when(vfsFile.getStream()).thenReturn(ResourceUtils.getResource("/maven/stam.pom"));
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);
        when(storingRepo.getItemModuleInfo("path/to/repo.pom")).thenReturn(mock(ModuleInfo.class));

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> interceptor.afterCreate(vfsFile, statusHolder, ctx));
    }

    @Test
    void afterCreate_shouldThrowCancelException_whenEOFException() throws IOException {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/repo.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        InputStream inputStream = mock(InputStream.class);

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(vfsFile.getPath()).thenReturn("path/to/repo.pom");
        when(vfsFile.getRepoPath()).thenReturn(repoPath);
        when(vfsFile.getStream()).thenReturn(inputStream);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);
        when(storingRepo.getItemModuleInfo("path/to/repo.pom")).thenReturn(mock(ModuleInfo.class));
        when(inputStream.read(any(byte[].class))).thenThrow(new EOFException("Unexpected end of file"));

        // Act & Assert
        assertThrows(org.artifactory.exception.CancelException.class,
                () -> interceptor.afterCreate(vfsFile, statusHolder, ctx));
    }

    @Test
    void afterCreate_shouldMarkAsMavenPlugin_whenPomIsMavenPlugin() throws IOException {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey",
                "maven/maven-null-plugin/1.4/maven-null-plugin-1.4.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        Properties properties = new PropertiesImpl();

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("maven-null-plugin-1.4.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(vfsFile.getPath()).thenReturn("maven/maven-null-plugin/1.4/maven-null-plugin-1.4.pom");
        when(vfsFile.getRepoPath()).thenReturn(repoPath);
        when(vfsFile.getStream()).thenReturn(
                ResourceUtils.getResource("/org/artifactory/maven/maven-null-plugin.pom"));
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);
        when(storingRepo.getItemModuleInfo(
                "maven/maven-null-plugin/1.4/maven-null-plugin-1.4.pom")).thenReturn(mock(ModuleInfo.class));
        when(storingRepo.getMutableFile(repoPath)).thenReturn(mutableVfsFile);
        when(mutableVfsFile.getProperties()).thenReturn(properties);

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        assertFalse(statusHolder.isError());
        verify(mutableVfsFile).setProperties(properties);
        assertTrue(properties.containsKey(PropertiesService.MAVEN_PLUGIN_PROPERTY_NAME));
        assertEquals("true", properties.getFirst(PropertiesService.MAVEN_PLUGIN_PROPERTY_NAME));
    }

    @Test
    void afterCreate_shouldNotMarkAsMavenPlugin_whenPomIsNotMavenPlugin() throws IOException {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey",
                "org/yourpit/yourpit/1.0.0-alpha2/yourpit-1.0.0-alpha2.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("yourpit-1.0.0-alpha2.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(vfsFile.getPath()).thenReturn("org/yourpit/yourpit/1.0.0-alpha2/yourpit-1.0.0-alpha2.pom");
        when(vfsFile.getRepoPath()).thenReturn(repoPath);
        when(vfsFile.getStream()).thenReturn(
                ResourceUtils.getResource("/org/artifactory/maven/yourpit-1.0.0-alpha2.pom"));
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);
        when(storingRepo.getItemModuleInfo(
                "org/yourpit/yourpit/1.0.0-alpha2/yourpit-1.0.0-alpha2.pom")).thenReturn(mock(ModuleInfo.class));

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        assertFalse(statusHolder.isError());
        verify(storingRepo, never()).getMutableFile(repoPath);
    }

    @Test
    void afterImport_shouldCallAfterCreate() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.jar");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");

        // Act
        interceptor.afterImport(vfsFile, statusHolder);

        // Assert
        assertFalse(statusHolder.isError());
    }

    @Test
    void afterCreate_shouldCallIndexPackageAsync_whenValidPomIsCreated() throws IOException {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey",
                "org/yourpit/yourpit/1.0.0-alpha2/yourpit-1.0.0-alpha2.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        FileInfo fileInfo = mock(FileInfo.class);
        Properties properties = new PropertiesImpl();

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("yourpit-1.0.0-alpha2.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(vfsFile.getPath()).thenReturn("org/yourpit/yourpit/1.0.0-alpha2/yourpit-1.0.0-alpha2.pom");
        when(vfsFile.getRepoPath()).thenReturn(repoPath);
        when(vfsFile.getStream()).thenReturn(
                ResourceUtils.getResource("/org/artifactory/maven/yourpit-1.0.0-alpha2.pom"));
        when(vfsFile.getInfo()).thenReturn(fileInfo);
        when(vfsFile.getProperties()).thenReturn(properties);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);
        when(storingRepo.getItemModuleInfo(
                "org/yourpit/yourpit/1.0.0-alpha2/yourpit-1.0.0-alpha2.pom")).thenReturn(mock(ModuleInfo.class));

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        assertFalse(statusHolder.isError());
        verify(metadataServiceIndexHandler).indexPackageAsync(fileInfo, properties);
    }

    @Test
    void afterCreate_shouldCallIndexPackageAsync_whenValidMavenPluginPomIsCreated() throws IOException {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey",
                "maven/maven-null-plugin/1.4/maven-null-plugin-1.4.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        descriptor.setSuppressPomConsistencyChecks(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        FileInfo fileInfo = mock(FileInfo.class);
        Properties properties = new PropertiesImpl();

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("maven-null-plugin-1.4.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(vfsFile.getPath()).thenReturn("maven/maven-null-plugin/1.4/maven-null-plugin-1.4.pom");
        when(vfsFile.getRepoPath()).thenReturn(repoPath);
        when(vfsFile.getStream()).thenReturn(
                ResourceUtils.getResource("/org/artifactory/maven/maven-null-plugin.pom"));
        when(vfsFile.getInfo()).thenReturn(fileInfo);
        when(vfsFile.getProperties()).thenReturn(properties);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);
        when(storingRepo.getItemModuleInfo(
                "maven/maven-null-plugin/1.4/maven-null-plugin-1.4.pom")).thenReturn(mock(ModuleInfo.class));
        when(storingRepo.getMutableFile(repoPath)).thenReturn(mutableVfsFile);
        when(mutableVfsFile.getProperties()).thenReturn(properties);

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        assertFalse(statusHolder.isError());
        verify(metadataServiceIndexHandler).indexPackageAsync(fileInfo, properties);
    }

    @Test
    void afterCreate_shouldNotCallIndexPackageAsync_whenItemIsNotFile() {
        // Arrange
        VfsItem folder = mock(VfsItem.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        ItemInfo itemInfo = mock(ItemInfo.class);

        when(folder.isFile()).thenReturn(false);
        when(folder.getInfo()).thenReturn(itemInfo);

        // Act
        interceptor.afterCreate(folder, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCreate_shouldNotCallIndexPackageAsync_whenFileNameIsNotPom() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        FileInfo fileInfo = mock(FileInfo.class);

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.jar");
        when(vfsFile.getInfo()).thenReturn(fileInfo);

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCreate_shouldNotCallIndexPackageAsync_whenStoringRepoIsNull() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        FileInfo fileInfo = mock(FileInfo.class);

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(vfsFile.getInfo()).thenReturn(fileInfo);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(null);

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCreate_shouldNotCallIndexPackageAsync_whenStoringRepoIsNotReal() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        FileInfo fileInfo = mock(FileInfo.class);

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(vfsFile.getInfo()).thenReturn(fileInfo);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(false);

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCreate_shouldNotCallIndexPackageAsync_whenRepoIsNotMaven() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Generic);
        FileInfo fileInfo = mock(FileInfo.class);

        when(vfsFile.isFile()).thenReturn(true);
        when(vfsFile.getName()).thenReturn("file.pom");
        when(vfsFile.getRepoKey()).thenReturn("repoKey");
        when(vfsFile.getInfo()).thenReturn(fileInfo);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);

        // Act
        interceptor.afterCreate(vfsFile, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterMove_shouldCallIndexPackageAsync_whenValidPomIsMoved() {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/file.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        VfsItem sourceItem = mock(VfsItem.class);
        VfsItem targetItem = mock(VfsItem.class);
        ItemInfo sourceItemInfo = mock(ItemInfo.class);
        ItemInfo targetItemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();

        when(sourceItem.getInfo()).thenReturn(sourceItemInfo);
        when(targetItem.getName()).thenReturn("file.pom");
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(targetItem.getRepoPath()).thenReturn(repoPath);
        when(targetItem.getInfo()).thenReturn(targetItemInfo);
        when(targetItem.getProperties()).thenReturn(properties);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);

        // Act
        interceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler).indexPackageAsync(targetItemInfo, properties);
    }

    @Test
    void afterMove_shouldNotCallIndexPackageAsync_whenTargetItemNameIsNotPom() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        VfsItem sourceItem = mock(VfsItem.class);
        VfsItem targetItem = mock(VfsItem.class);
        ItemInfo sourceItemInfo = mock(ItemInfo.class);
        ItemInfo targetItemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();

        when(sourceItem.getInfo()).thenReturn(sourceItemInfo);
        when(targetItem.getName()).thenReturn("file.jar");
        when(targetItem.getInfo()).thenReturn(targetItemInfo);

        // Act
        interceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterMove_shouldNotCallIndexPackageAsync_whenStoringRepoIsNull() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        VfsItem sourceItem = mock(VfsItem.class);
        VfsItem targetItem = mock(VfsItem.class);
        ItemInfo sourceItemInfo = mock(ItemInfo.class);
        ItemInfo targetItemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();

        when(sourceItem.getInfo()).thenReturn(sourceItemInfo);
        when(targetItem.getName()).thenReturn("file.pom");
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(targetItem.getInfo()).thenReturn(targetItemInfo);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(null);

        // Act
        interceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterMove_shouldNotCallIndexPackageAsync_whenStoringRepoIsNotReal() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        VfsItem sourceItem = mock(VfsItem.class);
        VfsItem targetItem = mock(VfsItem.class);
        ItemInfo sourceItemInfo = mock(ItemInfo.class);
        ItemInfo targetItemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();

        when(sourceItem.getInfo()).thenReturn(sourceItemInfo);
        when(targetItem.getName()).thenReturn("file.pom");
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(targetItem.getInfo()).thenReturn(targetItemInfo);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(false);

        // Act
        interceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterMove_shouldNotCallIndexPackageAsync_whenRepoIsNotMaven() {
        // Arrange
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Generic);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        VfsItem sourceItem = mock(VfsItem.class);
        VfsItem targetItem = mock(VfsItem.class);
        ItemInfo sourceItemInfo = mock(ItemInfo.class);
        ItemInfo targetItemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();

        when(sourceItem.getInfo()).thenReturn(sourceItemInfo);
        when(targetItem.getName()).thenReturn("file.pom");
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(targetItem.getInfo()).thenReturn(targetItemInfo);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);

        // Act
        interceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCopy_shouldCallIndexPackageAsync_whenValidPomIsCopied() {
        // Arrange
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/file.pom");
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Maven);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        VfsItem sourceItem = mock(VfsItem.class);
        VfsItem targetItem = mock(VfsItem.class);
        ItemInfo sourceItemInfo = mock(ItemInfo.class);
        ItemInfo targetItemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();

        when(sourceItem.getInfo()).thenReturn(sourceItemInfo);
        when(targetItem.getName()).thenReturn("file.pom");
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(targetItem.getRepoPath()).thenReturn(repoPath);
        when(targetItem.getInfo()).thenReturn(targetItemInfo);
        when(targetItem.getProperties()).thenReturn(properties);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);

        // Act
        interceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler).indexPackageAsync(targetItemInfo, properties);
    }

    @Test
    void afterCopy_shouldNotCallIndexPackageAsync_whenTargetItemNameIsNotPom() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        VfsItem sourceItem = mock(VfsItem.class);
        VfsItem targetItem = mock(VfsItem.class);
        ItemInfo sourceItemInfo = mock(ItemInfo.class);
        ItemInfo targetItemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();

        when(sourceItem.getInfo()).thenReturn(sourceItemInfo);
        when(targetItem.getName()).thenReturn("file.jar");
        when(targetItem.getInfo()).thenReturn(targetItemInfo);

        // Act
        interceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCopy_shouldNotCallIndexPackageAsync_whenStoringRepoIsNull() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        VfsItem sourceItem = mock(VfsItem.class);
        VfsItem targetItem = mock(VfsItem.class);
        ItemInfo sourceItemInfo = mock(ItemInfo.class);
        ItemInfo targetItemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();

        when(sourceItem.getInfo()).thenReturn(sourceItemInfo);
        when(targetItem.getName()).thenReturn("file.pom");
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(targetItem.getInfo()).thenReturn(targetItemInfo);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(null);

        // Act
        interceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCopy_shouldNotCallIndexPackageAsync_whenStoringRepoIsNotReal() {
        // Arrange
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        VfsItem sourceItem = mock(VfsItem.class);
        VfsItem targetItem = mock(VfsItem.class);
        ItemInfo sourceItemInfo = mock(ItemInfo.class);
        ItemInfo targetItemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();

        when(sourceItem.getInfo()).thenReturn(sourceItemInfo);
        when(targetItem.getName()).thenReturn("file.pom");
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(targetItem.getInfo()).thenReturn(targetItemInfo);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(false);

        // Act
        interceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCopy_shouldNotCallIndexPackageAsync_whenRepoIsNotMaven() {
        // Arrange
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setType(RepoType.Generic);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        VfsItem sourceItem = mock(VfsItem.class);
        VfsItem targetItem = mock(VfsItem.class);
        ItemInfo sourceItemInfo = mock(ItemInfo.class);
        ItemInfo targetItemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();

        when(sourceItem.getInfo()).thenReturn(sourceItemInfo);
        when(targetItem.getName()).thenReturn("file.pom");
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(targetItem.getInfo()).thenReturn(targetItemInfo);
        when(repositoryService.storingRepositoryByKey("repoKey")).thenReturn(storingRepo);
        when(storingRepo.isReal()).thenReturn(true);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey("repoKey")).thenReturn(descriptor);

        // Act
        interceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }
}
