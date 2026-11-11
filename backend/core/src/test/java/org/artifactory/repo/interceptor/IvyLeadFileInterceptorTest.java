package org.artifactory.repo.interceptor;

import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IvyLeadFileInterceptorTest {

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private MetadataServiceIndexHandler metadataServiceIndexHandler;
    @Mock
    private VfsItem<? extends ItemInfo> vfsItem;
    @Mock
    private RepoConfig repoConfig;
    @Mock
    private FileInfo fileInfo;
    @Mock
    private Properties properties;

    private IvyLeadFileInterceptor interceptor;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        interceptor = new IvyLeadFileInterceptor();
        interceptor.setRepositoryService(repositoryService);
        interceptor.setMetadataServiceIndexHandler(metadataServiceIndexHandler);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void afterCreate_shouldDoNothing_whenItemIsNotFile() {
        // Arrange
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsItem.isFile()).thenReturn(false);

        // Act
        interceptor.afterCreate(vfsItem, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCreate_shouldDoNothing_whenRepoConfigIsNull() {
        // Arrange
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsItem.isFile()).thenReturn(true);
        when(vfsItem.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(null);

        // Act
        interceptor.afterCreate(vfsItem, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCreate_shouldDoNothing_whenRepoConfigIsNotReal() {
        // Arrange
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsItem.isFile()).thenReturn(true);
        when(vfsItem.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(false);

        // Act
        interceptor.afterCreate(vfsItem, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maven", "Generic", "Docker", "Npm"})
    void afterCreate_shouldDoNothing_whenRepoTypeIsNotIvyOrSbt(String repoTypeName) {
        // Arrange
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext ctx = new InterceptorCreateContext();
        RepoType repoType = RepoType.valueOf(repoTypeName);

        when(vfsItem.isFile()).thenReturn(true);
        when(vfsItem.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(repoType);

        // Act
        interceptor.afterCreate(vfsItem, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"file.jar", "artifact.txt", "package.zip", "stam.xml"})
    void afterCreate_shouldDoNothing_whenFileNameIsNotIvyFileName(String fileName) {
        // Arrange
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsItem.isFile()).thenReturn(true);
        when(vfsItem.getRepoKey()).thenReturn("repoKey");
        when(vfsItem.getName()).thenReturn(fileName);
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Ivy);

        // Act
        interceptor.afterCreate(vfsItem, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"module.pom", "artifact-1.0.pom", "ivy-1.2.3.pom"})
    void afterCreate_shouldDoNothing_whenFileNameIsPom(String fileName) {
        // Arrange
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsItem.isFile()).thenReturn(true);
        when(vfsItem.getRepoKey()).thenReturn("repoKey");
        when(vfsItem.getName()).thenReturn(fileName);
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Ivy);

        // Act
        interceptor.afterCreate(vfsItem, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @ParameterizedTest
    @MethodSource("ivyFileNameProvider")
    void afterCreate_shouldIndexPackage_whenIvyRepoAndIvyFileName(String fileName) {
        // Arrange
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsItem.isFile()).thenReturn(true);
        when(vfsItem.getRepoKey()).thenReturn("repoKey");
        when(vfsItem.getName()).thenReturn(fileName);
        when(vfsItem.getInfo()).thenReturn(fileInfo);
        when(vfsItem.getProperties()).thenReturn(properties);
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Ivy);

        // Act
        interceptor.afterCreate(vfsItem, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler).indexPackageAsync(fileInfo, properties);
    }

    @ParameterizedTest
    @MethodSource("ivyFileNameProvider")
    void afterCreate_shouldIndexPackage_whenSbtRepoAndIvyFileName(String fileName) {
        // Arrange
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext ctx = new InterceptorCreateContext();

        when(vfsItem.isFile()).thenReturn(true);
        when(vfsItem.getRepoKey()).thenReturn("repoKey");
        when(vfsItem.getName()).thenReturn(fileName);
        when(vfsItem.getInfo()).thenReturn(fileInfo);
        when(vfsItem.getProperties()).thenReturn(properties);
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(RepoType.SBT);

        // Act
        interceptor.afterCreate(vfsItem, statusHolder, ctx);

        // Assert
        verify(metadataServiceIndexHandler).indexPackageAsync(fileInfo, properties);
    }

    @Test
    void afterMove_shouldDoNothing_whenTargetItemIsNotFile() {
        // Arrange
        VfsItem<? extends ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<? extends ItemInfo> targetItem = mock(VfsItem.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();

        when(targetItem.isFile()).thenReturn(false);

        // Act
        interceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterMove_shouldDoNothing_whenRepoConfigIsNull() {
        // Arrange
        VfsItem<? extends ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<? extends ItemInfo> targetItem = mock(VfsItem.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();

        when(targetItem.isFile()).thenReturn(true);
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(null);

        // Act
        interceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterMove_shouldDoNothing_whenRepoConfigIsNotReal() {
        // Arrange
        VfsItem<? extends ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<? extends ItemInfo> targetItem = mock(VfsItem.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();

        when(targetItem.isFile()).thenReturn(true);
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(false);

        // Act
        interceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maven", "Generic", "Docker", "Npm"})
    void afterMove_shouldDoNothing_whenRepoTypeIsNotIvyOrSbt(String repoTypeName) {
        // Arrange
        VfsItem<? extends ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<? extends ItemInfo> targetItem = mock(VfsItem.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        RepoType repoType = RepoType.valueOf(repoTypeName);

        when(targetItem.isFile()).thenReturn(true);
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(repoType);

        // Act
        interceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @ParameterizedTest
    @MethodSource("ivyFileNameProvider")
    void afterMove_shouldIndexPackage_whenIvyRepoAndIvyFileName(String fileName) {
        // Arrange
        VfsItem<? extends ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<? extends ItemInfo> targetItem = mock(VfsItem.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();

        when(targetItem.isFile()).thenReturn(true);
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(targetItem.getName()).thenReturn(fileName);
        when(targetItem.getInfo()).thenReturn(fileInfo);
        when(targetItem.getProperties()).thenReturn(properties);
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Ivy);

        // Act
        interceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler).indexPackageAsync(fileInfo, properties);
    }

    @Test
    void afterCopy_shouldDoNothing_whenTargetItemIsNotFile() {
        // Arrange
        VfsItem<? extends ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<? extends ItemInfo> targetItem = mock(VfsItem.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();

        when(targetItem.isFile()).thenReturn(false);

        // Act
        interceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCopy_shouldDoNothing_whenRepoConfigIsNull() {
        // Arrange
        VfsItem<? extends ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<? extends ItemInfo> targetItem = mock(VfsItem.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();

        when(targetItem.isFile()).thenReturn(true);
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(null);

        // Act
        interceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @Test
    void afterCopy_shouldDoNothing_whenRepoConfigIsNotReal() {
        // Arrange
        VfsItem<? extends ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<? extends ItemInfo> targetItem = mock(VfsItem.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();

        when(targetItem.isFile()).thenReturn(true);
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(false);

        // Act
        interceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maven", "Generic", "Docker", "Npm"})
    void afterCopy_shouldDoNothing_whenRepoTypeIsNotIvyOrSbt(String repoTypeName) {
        // Arrange
        VfsItem<? extends ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<? extends ItemInfo> targetItem = mock(VfsItem.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();
        RepoType repoType = RepoType.valueOf(repoTypeName);

        when(targetItem.isFile()).thenReturn(true);
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(repoType);

        // Act
        interceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler, never()).indexPackageAsync(any(), any());
    }

    @ParameterizedTest
    @MethodSource("ivyFileNameProvider")
    void afterCopy_shouldIndexPackage_whenIvyRepoAndIvyFileName(String fileName) {
        // Arrange
        VfsItem<? extends ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<? extends ItemInfo> targetItem = mock(VfsItem.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        Properties properties = mock(Properties.class);
        InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext();

        when(targetItem.isFile()).thenReturn(true);
        when(targetItem.getRepoKey()).thenReturn("repoKey");
        when(targetItem.getName()).thenReturn(fileName);
        when(targetItem.getInfo()).thenReturn(fileInfo);
        when(targetItem.getProperties()).thenReturn(properties);
        when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(repoConfig);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Ivy);

        // Act
        interceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);

        // Assert
        verify(metadataServiceIndexHandler).indexPackageAsync(fileInfo, properties);
    }

    private static Stream<Arguments> ivyFileNameProvider() {
        return Stream.of(
                // Exact match - standard Ivy file name
                arguments("ivy.xml"),
                // Starts with "ivy-" and ends with ".xml" - versioned Ivy file
                arguments("ivy-1.2.3.xml"),
                // Ends with ".ivy" - alternative Ivy file extension
                arguments("module.ivy"),
                // Ends with "-ivy.xml" - organization-ivy.xml pattern
                arguments("org.module-ivy.xml")
        );
    }
}