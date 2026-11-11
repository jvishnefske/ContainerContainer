/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.db;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import jakarta.annotation.Nonnull;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.filteredresources.FilteredResourcesAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.repo.exception.maven.BadPomException;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoBaseDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.fs.ZipEntryRepoResource;
import org.artifactory.io.ZipResourceStreamHandle;
import org.artifactory.io.checksum.policy.ChecksumPolicyException;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.SaveResourceContext;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.MutableRepoResourceInfo;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.resource.ResourceWithChecksumStreamHandle;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.fs.model.DbFsFile;
import org.artifactory.storage.db.fs.model.DbMutableFile;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.VfsItemProvider;
import org.artifactory.storage.fs.VfsItemProviderFactory;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.util.RepoPathUtils;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.Assertions;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the save resource process.
 *
 * @author Yossi Shaul
 * @see org.artifactory.repo.db.DbStoringRepoMixin#saveResource(org.artifactory.repo.SaveResourceContext)
 */
@Test
public class DbStoringRepoMixinSaveResourceTest {

    private MemoryAppender memoryAppender;

    @BeforeMethod
    public void setup() {
        memoryAppender = MemoryAppenderUtil.createMemoryAppender(DbStoringRepoMixin.class, Level.DEBUG);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
        stopMemoryAppender(memoryAppender);
    }

    @Test(expectedExceptions = StorageException.class, expectedExceptionsMessageRegExp = ".*parent.*")
    public void failToSaveRootFolder() throws IOException, RepoRejectException {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        RepoResource repoResourceMock = mock(RepoResource.class);
        when(repoResourceMock.getRepoPath()).thenReturn(RepoPathUtils.repoRootPath("repo"));
        repo.saveResource(new SaveResourceContext.Builder(repoResourceMock, (InputStream) null).build());
    }

    private DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> createStoringRepo() {
        LocalRepoDescriptor repoDescriptor = new LocalRepoDescriptor();
        repoDescriptor.setKey("repo");
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repo", RepoType.Generic);
        DbStoringRepoMixin<?, ?> dbStoringRepoMixin = mock(DbStoringRepoMixin.class);
        return spy(new DbStoringRepoMixin<>(repoDescriptor, localRepoConfig, dbStoringRepoMixin));
    }

    private void setRepositoryService(DbStoringRepoMixin repoMixin, RepositoryService repoService) {
        ReflectionTestUtils.setField(repoMixin, "repositoryService", repoService);
    }

    @Test
    public void testVerifyChecksumWhenChecksumPolicyIsNull() {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        setRepositoryService(repo, repositoryService);

        //exception will be thrown due to policy=null
        assertThrows(ChecksumPolicyException.class, () -> repo.verifyChecksum(null));
    }

    @Test
    public void testValidateJarWhenJarInputIsNull() {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        setRepositoryService(repo, repositoryService);

        MutableVfsFile mutableVfsFile = mock(MutableVfsFile.class);
        when(mutableVfsFile.getStream()).thenReturn(null);

        //exception will be thrown due to jarInputStream=null
        assertThrows(RepoRejectException.class,
                () -> repo.validateJar(mutableVfsFile, RepoPathFactory.create("repo", "path")));
    }


    @Test(dataProvider = "valid_jars")
    public void testValidateJar(String resource) {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        setRepositoryService(repo, repositoryService);

        MutableVfsFile mutableVfsFile = mock(MutableVfsFile.class);
        InputStream inputStream = ResourceUtils.getResource(resource);
        when(mutableVfsFile.getStream()).thenReturn(inputStream);

        assertDoesNotThrow(() -> {
            repo.validateJar(mutableVfsFile, RepoPathFactory.create("repo", "path"));
        });
    }

    @Test(dataProvider = "invalid_jars")
    public void testValidateJarInvalidInput(String resource) {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        setRepositoryService(repo, repositoryService);

        MutableVfsFile mutableVfsFile = mock(MutableVfsFile.class);
        InputStream inputStream = ResourceUtils.getResource(resource);
        when(mutableVfsFile.getStream()).thenReturn(inputStream);

        assertThrows(RepoRejectException.class,
                () -> repo.validateJar(mutableVfsFile, RepoPathFactory.create("repo", "path")));
    }

    @Test
    void updateResourceWithActualBinaryData() {
        // Given
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        RepoResource res = mock(RepoResource.class);
        MutableRepoResourceInfo info = new FileInfoImpl(repoPath);
        when(res.getInfo()).thenReturn(info);
        when(res.getRepoPath()).thenReturn(repoPath);
        ChecksumsInfo checksumInfo = prepareChecksums(repo);
        // When
        repo.updateResourceWithActualBinaryData(res, repo.getMutableFile(repoPath));
        // Then
        assertNotNull(res.getInfo());
        assertEquals(res.getInfo().getChecksumsInfo(), checksumInfo);
    }

    @Test
    public void testGetResourceStreamHandleSimpleStream() throws IOException {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        RequestContext requestContext = mock(RequestContext.class);
        RepoResource res = mock(RepoResource.class);
        when(res.getRepoPath()).thenReturn(RepoPathFactory.create("repo", "path"));
        ChecksumsInfo checksumInfo = prepareChecksums(repo);

        prepareRequest(requestContext, false);
        prepareStats(repo);
        ResourceStreamHandle resourceStreamHandle = repo.getResourceStreamHandle(requestContext, res);
        assertTrue(resourceStreamHandle instanceof ResourceWithChecksumStreamHandle);
        assertTrue(((ResourceWithChecksumStreamHandle) resourceStreamHandle).getChecksumsInfo().isPresent());
        assertEquals(((ResourceWithChecksumStreamHandle) resourceStreamHandle).getChecksumsInfo().get(), checksumInfo);
    }

    @Test
    public void testGetResourceStreamHandleZipStream() throws IOException {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        RequestContext requestContext = mock(RequestContext.class);
        ZipEntryRepoResource res = mock(ZipEntryRepoResource.class);
        when(res.getRepoPath()).thenReturn(RepoPathFactory.create("repo", "path"));
        FileInfo fileInfo = spy(new FileInfoImpl(RepoPathFactory.create("repo", "path")));
        ChecksumsInfo checksumInfo = new ChecksumsInfo();
        when(fileInfo.getChecksumsInfo()).thenReturn(checksumInfo);

        when(res.getInfo()).thenReturn(fileInfo);
        prepareRequest(requestContext, true);
        prepareStats(repo);
        prepareVfsFile(repo, fileInfo);
        AddonsManager addonsManager = prepareAddonManager(repo);
        FilteredResourcesAddon filteredResourcesAddon = mock(FilteredResourcesAddon.class);
        ZipResourceStreamHandle zipResourceStreamHandle = mock(ZipResourceStreamHandle.class);
        when(filteredResourcesAddon.getZipResourceHandle(any(), any(), any())).thenReturn(zipResourceStreamHandle);
        when(addonsManager.addonByType(FilteredResourcesAddon.class)).thenReturn(filteredResourcesAddon);

        ResourceStreamHandle resourceStreamHandle = repo.getResourceStreamHandle(requestContext, res);
        assertTrue(resourceStreamHandle instanceof ResourceWithChecksumStreamHandle);
        assertEquals(resourceStreamHandle, zipResourceStreamHandle);
    }

    @Test
    public void testGetMutableItem() {
        VfsItem<org.artifactory.fs.ItemInfo> vfsItem = mock(VfsItem.class);
        when(vfsItem.isFile()).thenReturn(true);
        ItemInfo originalItemInfo = mock(ItemInfo.class);

        VfsItemProvider provider = mockVfsItemProvider(vfsItem, originalItemInfo);

        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        repo.init(true);
        repo.getMutableItem(vfsItem, originalItemInfo, null);

        verify(provider).getOrCreateMutableFsItem(true);

        reset(provider);
        when(vfsItem.isFile()).thenReturn(false);
        repo.getMutableItem(vfsItem, originalItemInfo, null);
        verify(provider).getOrCreateMutableFsItem(false);
    }

    @Test(dataProvider = "provideExceptionsValues")
    public void printLogWithRelevantLevel(Throwable ioCause, String expectedMessage, Level expectedLevel) {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        RepoResource res = mock(RepoResource.class);
        when(res.getRepoPath()).thenReturn(RepoPathFactory.create("repo", "path"));
        repo.printLogWithRelevantLevel(res, ioCause);
        Assertions.assertEquals(1, memoryAppender.search(expectedMessage, expectedLevel).size());
    }

    @Test
    public void testGetResolvedResourceMetadataNull() throws IOException {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        FileResource mockMetadataResource = mock(FileResource.class);
        assertNull(repo.getResolvedResource(mockMetadataResource, null));
    }

    @Test
    public void testGetResolvedResourceVersioningNull() throws IOException {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        FileResource mockMetadataResource = mock(FileResource.class);
        Metadata mockMetadata = mock(Metadata.class);
        when(mockMetadata.getVersioning()).thenReturn(null);

        assertNull(repo.getResolvedResource(mockMetadataResource, mockMetadata));
    }

    @Test
    public void testGetResolvedResourceVersioningNotNull() throws IOException {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        FileResource mockMetadataResource = mock(FileResource.class);
        Metadata mockMetadata = mock(Metadata.class);
        Versioning mockVersioning = mock(Versioning.class);
        when(mockMetadata.getVersioning()).thenReturn(mockVersioning);
        when(mockVersioning.getSnapshotVersions()).thenReturn(Collections.emptyList());

        assertNull(repo.getResolvedResource(mockMetadataResource, mockMetadata));
    }

    @Test
    public void testGetResolvedResourceVersioningNotNullNotEmpty() throws IOException {
        DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo = createStoringRepo();
        FileResource mockMetadataResource = mock(FileResource.class);
        Metadata mockMetadata = mock(Metadata.class);
        Versioning mockVersioning = mock(Versioning.class);
        when(mockMetadata.getVersioning()).thenReturn(mockVersioning);
        SnapshotVersion mockSnapshotVersion = mock(SnapshotVersion.class);
        when(mockVersioning.getSnapshotVersions()).thenReturn(Collections.singletonList(mockSnapshotVersion));
        when(mockVersioning.getSnapshotVersions()).thenReturn(Collections.singletonList(mockSnapshotVersion));
        when(mockMetadataResource.getPath()).thenReturn("example/path");

        assertNotNull(repo.getResolvedResource(mockMetadataResource, mockMetadata));
    }

    private static void stopMemoryAppender(MemoryAppender memoryAppender) {
        memoryAppender.stop();
        ((Logger) LoggerFactory.getLogger(DbStoringRepoMixin.class)).detachAppender(memoryAppender);
    }

    private VfsItemProvider mockVfsItemProvider(VfsItem<ItemInfo> vfsItem, ItemInfo originalItemInfo) {
        VfsItemProviderFactory factory = mockFactory();
        VfsItemProvider provider = mock(VfsItemProvider.class);
        when(factory.createMutableItemProvider(any(), eq(vfsItem), any(), any(), eq(originalItemInfo), eq(null)))
                .thenReturn(provider);
        return provider;
    }

    private VfsItemProviderFactory mockFactory() {
        ArtifactoryContext context = mockContext();
        VfsItemProviderFactory factory = mock(VfsItemProviderFactory.class);
        when(context.beanForType(VfsItemProviderFactory.class)).thenReturn(factory);
        return factory;
    }

    private ArtifactoryContext mockContext() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        return context;
    }

    private void prepareStats(DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo) {
        StatsService statsService = mock(StatsService.class);
        ReflectionTestUtils.setField(repo, "statsService", statsService);
    }

    private AddonsManager prepareAddonManager(DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo) {
        AddonsManager addonsManager = mock(AddonsManager.class);
        ReflectionTestUtils.setField(repo, "addonsManager", addonsManager);
        return addonsManager;
    }

    @Nonnull
    private ChecksumsInfo prepareChecksums(DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo) {
        FileInfo fileInfo = spy(new FileInfoImpl(RepoPathFactory.create("repo", "path")));
        ChecksumsInfo checksumInfo = new ChecksumsInfo();
        when(fileInfo.getChecksumsInfo()).thenReturn(checksumInfo);
        prepareVfsFile(repo, fileInfo);
        return checksumInfo;
    }

    private void prepareVfsFile(DbStoringRepoMixin<RepoBaseDescriptor, RepoConfig> repo, FileInfo fileInfo) {
        DbFsFile vfsFile = mock(DbFsFile.class);
        when(vfsFile.getInfo()).thenReturn(fileInfo);
        doReturn(vfsFile).when(repo).getImmutableFile(any(RepoPathImpl.class));

        DbMutableFile mutableVfsFile = mock(DbMutableFile.class);
        when(mutableVfsFile.getInfo()).thenReturn((MutableFileInfo) fileInfo);
        doReturn(mutableVfsFile).when(repo).getMutableFile(any(RepoPathImpl.class));
    }

    private void prepareRequest(RequestContext requestContext, boolean isZip) {
        Request request = mock(Request.class);
        when(requestContext.getRequest()).thenReturn(request);
        when(request.isZipResourceRequest()).thenReturn(isZip);
    }

    @DataProvider
    public static Object[][] valid_jars() {
        return new Object[][] {
                {"/maven/real.jar"},
                {"/maven/real_executable.jar"}
        };
    }

    @DataProvider
    public static Object[][] invalid_jars() {
        return new Object[][] {
                {"/maven/corrupted.jar"}
        };
    }

    @DataProvider
    public static Object[][] provideExceptionsValues() {
        return new Object[][] {
                {new EOFException(
                        "message"), "IO error while trying to save resource repo:path: java.io.EOFException: " +
                        "message", Level.DEBUG},
                {new BadPomException(
                        "message"), "IO error while trying to save resource repo:path: org.artifactory.api.repo" +
                        ".exception.maven.BadPomException: message", Level.WARN},
                {new Exception(
                        "message"), "IO error while trying to save resource repo:path: java.lang.Exception: " +
                        "message", Level.ERROR},
        };
    }
}