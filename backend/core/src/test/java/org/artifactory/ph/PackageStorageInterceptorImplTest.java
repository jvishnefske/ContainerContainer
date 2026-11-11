package org.artifactory.ph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.jfrog.ph.acl.exception.PackageCancelException;
import com.jfrog.ph.acl.exception.PackageVerificationException;
import com.jfrog.ph.acl.listener.artifact.PackageArtifactEventListener;
import com.jfrog.ph.acl.listener.artifact.PackageArtifactEventListenerContext;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import org.apache.http.client.methods.HttpRequestBase;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.rest.restmodel.JsonUtil;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.event.EventHintType;
import org.artifactory.exception.CancelException;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.HeadersMultiMap;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.storage.db.fs.model.DbMutableFile;
import org.artifactory.util.StringInputStream;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.event.EventHintType.PackageIndexExtraInfo;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@Test
public class PackageStorageInterceptorImplTest {

    private static final RepoPath LOCAL_REPO_PATH = RepoPathFactory.create("local_repo1", "a/b/c/f1");
    private static final RepoPath REMOTE_REPO_PATH = RepoPathFactory.create("remote_repo1", "a/b/c/f1");

    private static final RepoPath REMOTE_CACHE_PATH = RepoPathFactory.create("remote_repo-cache", "a/b/c/f1");

    private PackageStorageInterceptorImpl interceptor;

    @Spy
    private PackageArtifactEventListener packagesListener;
    @Spy
    private PackageSpecificationAdapter packageSpecificationAdapter;
    @Spy
    private InternalRepositoryService repositoryService;
    @Mock
    private DbMutableFile file;
    @Mock
    private LocalRepoConfig localRepoConfig;
    @Mock
    private RemoteRepoConfig remoteRepoConfig;
    @Mock
    private LocalCacheRepoConfig localRepoConfigCache;
    private MutableStatusHolder statusHolder;
    @Mock
    private Properties properties;
    @Mock
    private InterceptorMoveCopyContext interceptorMoveCopyContext;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = openMocks(this);
        interceptor =
                new PackageStorageInterceptorImpl(packagesListener, repositoryService, packageSpecificationAdapter);
        setup(file, LOCAL_REPO_PATH);
        when(localRepoConfig.getPackageType()).thenReturn(RepoType.CRAN);
        when(remoteRepoConfig.getPackageType()).thenReturn(RepoType.CRAN);
        when(localRepoConfigCache.getPackageType()).thenReturn(RepoType.CRAN);
        when(localRepoConfigCache.isCache()).thenReturn(true);
        when(repositoryService.getRepositoryConfigByKey(LOCAL_REPO_PATH.getRepoKey())).thenReturn(localRepoConfig);
        when(repositoryService.getRepositoryConfigByKey(REMOTE_REPO_PATH.getRepoKey())).thenReturn(remoteRepoConfig);
        when(repositoryService.getRepositoryConfigByKey(REMOTE_CACHE_PATH.getRepoKey())).thenReturn(
                localRepoConfigCache);
        when(packageSpecificationAdapter.isSupportedPackage(anyString())).thenReturn(true);
        statusHolder = new BasicStatusHolder();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    void setup(VfsFile<?> file, RepoPath repoPath) {
        when(file.length()).thenReturn(Long.MAX_VALUE);
        when(file.getSha1()).thenReturn("sha1");
        when(file.getSha2()).thenReturn("sha2");
        when(file.getMd5()).thenReturn("md5");
        when(file.isFolder()).thenReturn(false);
        when(file.isFile()).thenReturn(true);
        when(file.getRepoKey()).thenReturn(repoPath.getRepoKey());
        when(file.getRepoPath()).thenReturn(repoPath);
        when(file.getPath()).thenReturn(repoPath.getPath());
        FileInfo fileInfo = Mockito.mock(MutableFileInfo.class);
        when(file.getInfo()).thenReturn(fileInfo);
        when(fileInfo.getLastModified()).thenReturn(System.currentTimeMillis());
        when(fileInfo.getCreated()).thenReturn(System.currentTimeMillis());
        when(fileInfo.getLastUpdated()).thenReturn(System.currentTimeMillis());
        when(fileInfo.getModifiedBy()).thenReturn("System");
        when(fileInfo.getCreatedBy()).thenReturn("System");


        when(properties.keys()).thenReturn(LinkedHashMultiset.create(Set.of()));
        doAnswer(invocation -> properties).when(file).getProperties();

        when(file.getInfo()).thenReturn(fileInfo);

        when(fileInfo.getSha1()).thenReturn("sha1");
        when(fileInfo.getSha2()).thenReturn("sha2");
        when(fileInfo.getMd5()).thenReturn("md5");
        when(fileInfo.getRepoPath()).thenReturn(repoPath);
        when(fileInfo.getCreated()).thenReturn(Long.MAX_VALUE);
        when(fileInfo.getRepoKey()).thenReturn(repoPath.getRepoKey());
        when(fileInfo.getLastModified()).thenReturn(Long.MAX_VALUE);
        when(fileInfo.getName()).thenReturn(repoPath.getName());
        when(fileInfo.getRelPath()).thenReturn(repoPath.getPath());
        when(fileInfo.getModifiedBy()).thenReturn("admin");
        when(fileInfo.getLastUpdated()).thenReturn(Long.MAX_VALUE);
        when(fileInfo.getCreatedBy()).thenReturn("admin");
    }

    @Test
    public void testNoRepository() throws PackageCancelException {
        when(repositoryService.getRepositoryConfigByKey(LOCAL_REPO_PATH.getRepoKey())).thenReturn(null);

        interceptor.afterCreate(file, statusHolder, null);

        verify(packagesListener, never()).afterCreate(any(PackageArtifact.class));
    }

    @Test
    public void testUnsupportedPackageType() throws PackageCancelException {

        when(localRepoConfig.getPackageType()).thenReturn(RepoType.Distribution);

        interceptor.afterCreate(file, statusHolder, null);

        verify(packagesListener, never()).afterCreate(any(PackageArtifact.class));
    }

    @Test
    public void testAfterCreate() throws PackageCancelException {

        interceptor.afterCreate(file, statusHolder, mock(InterceptorCreateContext.class));

        ArgumentCaptor<PackageArtifact> artifact = ArgumentCaptor.forClass(PackageArtifact.class);

        verify(packagesListener, times(1)).afterCreate(artifact.capture());

        assertEquals(artifact.getValue().getRepoKey(), LOCAL_REPO_PATH.getRepoKey());

    }

    @Test
    public void testAfterCreateWithRemoteCache() throws PackageVerificationException {
        DbMutableFile mutableFile = mock(DbMutableFile.class);
        setup(mutableFile, REMOTE_CACHE_PATH);
        ArgumentCaptor<PackageArtifact> artifact = ArgumentCaptor.forClass(PackageArtifact.class);
        when(packageSpecificationAdapter.shouldVerifyRemoteArtifact(any())).thenReturn(true);
        interceptor.afterCreate(mutableFile, statusHolder, mock(InterceptorCreateContext.class));
        verify(packageSpecificationAdapter, times(1)).verifyRemoteArtifactNotCorrupted(artifact.capture(), any());
        assertEquals(artifact.getValue().getRepoKey(), REMOTE_CACHE_PATH.getRepoKey());
    }

    @Test
    public void testAfterCreateWithRemoteCacheNotVerify() throws PackageVerificationException {
        DbMutableFile mutableFile = mock(DbMutableFile.class);
        setup(mutableFile, REMOTE_CACHE_PATH);
        ArgumentCaptor<PackageArtifact> artifact = ArgumentCaptor.forClass(PackageArtifact.class);
        when(packageSpecificationAdapter.shouldVerifyRemoteArtifact(any())).thenReturn(false);
        interceptor.afterCreate(mutableFile, statusHolder, mock(InterceptorCreateContext.class));
        verify(packageSpecificationAdapter, times(0)).verifyRemoteArtifactNotCorrupted(artifact.capture(), any());
    }

    @Test
    public void testAfterCreateWithRemoteCacheNotVfsFile() throws PackageVerificationException {
        VfsItem vfsItem = mock(VfsItem.class);
        when(vfsItem.getRepoKey()).thenReturn(REMOTE_CACHE_PATH.getRepoKey());
        when(vfsItem.getPath()).thenReturn(REMOTE_CACHE_PATH.getPath());
        ArgumentCaptor<PackageArtifact> artifact = ArgumentCaptor.forClass(PackageArtifact.class);
        when(packageSpecificationAdapter.shouldVerifyRemoteArtifact(any())).thenReturn(true);
        interceptor.afterCreate(vfsItem, statusHolder, mock(InterceptorCreateContext.class));
        verify(packageSpecificationAdapter, times(0)).verifyRemoteArtifactNotCorrupted(artifact.capture(), any());
    }

    @Test
    public void testAfterCreateCatchVerificationException() throws PackageVerificationException {
        DbMutableFile mutableFile = mock(DbMutableFile.class);
        when(mutableFile.getRepoPath()).thenReturn(RepoPathFactory.create("repo", "path"));
        setup(mutableFile, REMOTE_CACHE_PATH);
        ArgumentCaptor<PackageArtifact> artifact = ArgumentCaptor.forClass(PackageArtifact.class);
        when(packageSpecificationAdapter.shouldVerifyRemoteArtifact(any())).thenReturn(true);
        doThrow(new PackageVerificationException("error")).when(packageSpecificationAdapter)
                .verifyRemoteArtifactNotCorrupted(any(), any());
        CancelException exception = Assertions.assertThrows(CancelException.class,
                () -> interceptor.afterCreate(mutableFile, statusHolder, mock(InterceptorCreateContext.class)));
        Assertions.assertEquals("error", exception.getMessage());
        verify(packageSpecificationAdapter, times(1)).shouldVerifyRemoteArtifact(any());
        verify(packageSpecificationAdapter, times(1)).verifyRemoteArtifactNotCorrupted(artifact.capture(), any());
        assertEquals(artifact.getValue().getRepoKey(), REMOTE_CACHE_PATH.getRepoKey());
    }

    @Test
    public void testAfterCreateCatchPackageCancelException() throws PackageCancelException {
        doThrow(PackageCancelException.class).when(packagesListener).afterCreate(any(PackageArtifact.class));
        Assertions.assertThrows(CancelException.class,
                () -> interceptor.afterCreate(file, statusHolder, mock(InterceptorCreateContext.class)));
    }

    @Test
    public void testBeforeDelete() throws IOException {

        RepoPath path = RepoPathFactory.create(LOCAL_REPO_PATH.getRepoKey(), LOCAL_REPO_PATH.getPath());
        EventLogHintManager.put(PackageIndexExtraInfo, path,
                JsonUtil.jsonToString(Map.of("k0", "v0", "k1", "v1")));

        doAnswer(invocation -> {
            PackageArtifactEventListenerContext context = invocation.getArgument(2);
            context.setEventExtraInfo(Map.of("k1", "v11", "k2", 2));
            return null;
        }).when(packagesListener)
                .beforeDelete(eq(LOCAL_REPO_PATH.getRepoKey()), eq(LOCAL_REPO_PATH.getPath()),
                        any(PackageArtifactEventListenerContext.class));
        DeleteContext deleteContext = mock(DeleteContext.class);
        interceptor.beforeDelete(file, statusHolder, deleteContext);
        String hintValue = EventHintType.getValue(EventLogHintManager.getHints(path), PackageIndexExtraInfo);
        Map<String, Object> extraInfo =
                JsonUtil.mapDataToModel(new StringInputStream(hintValue), new TypeReference<>() {
                });

        assertEquals(3, extraInfo.size());
        assertEquals("v0", extraInfo.get("k0"));
        assertEquals("v11", extraInfo.get("k1"));
        assertEquals(2, extraInfo.get("k2"));
        verify(deleteContext, times(0)).isTriggeredByMove();
    }

    @Test
    public void testAfterDelete() {

        interceptor.afterDelete(file, statusHolder, mock(DeleteContext.class));

        ArgumentCaptor<PackageArtifact> artifact = ArgumentCaptor.forClass(PackageArtifact.class);

        verify(packagesListener, times(1)).afterDelete(artifact.capture());

        assertEquals(artifact.getValue().getRepoKey(), LOCAL_REPO_PATH.getRepoKey());

    }

    @Test
    public void testAfterMove() {
        RepoPath targetPath = RepoPathFactory.create("repo2", LOCAL_REPO_PATH.getPath());
        VfsFile<?> targetFile = mock(VfsFile.class);
        setup(targetFile, targetPath);
        LocalRepoConfig targetRepoConfig = mock(LocalRepoConfig.class);
        when(targetRepoConfig.getPackageType()).thenReturn(RepoType.CRAN);
        when(repositoryService.getRepositoryConfigByKey(targetPath.getRepoKey())).thenReturn(targetRepoConfig);
        interceptor.afterMove(file, targetFile, statusHolder, properties, interceptorMoveCopyContext);
        ArgumentCaptor<PackageArtifact> source = ArgumentCaptor.forClass(PackageArtifact.class);
        ArgumentCaptor<PackageArtifact> target = ArgumentCaptor.forClass(PackageArtifact.class);
        verify(packagesListener, times(1)).afterMove(source.capture(), target.capture());
        assertEquals(source.getValue().getRepoKey(), LOCAL_REPO_PATH.getRepoKey());
        assertEquals(target.getValue().getRepoKey(), targetPath.getRepoKey());
    }

    @Test
    public void testAfterCopy() {
        RepoPath targetPath = RepoPathFactory.create("repo2", LOCAL_REPO_PATH.getPath());
        VfsFile<?> targetFile = mock(VfsFile.class);
        setup(targetFile, targetPath);

        LocalRepoConfig targetRepoConfig = mock(LocalRepoConfig.class);
        when(targetRepoConfig.getPackageType()).thenReturn(RepoType.CRAN);
        when(repositoryService.getRepositoryConfigByKey(targetPath.getRepoKey())).thenReturn(targetRepoConfig);
        interceptor.afterCopy(file, targetFile, statusHolder, properties, interceptorMoveCopyContext);
        ArgumentCaptor<PackageArtifact> source = ArgumentCaptor.forClass(PackageArtifact.class);
        ArgumentCaptor<PackageArtifact> target = ArgumentCaptor.forClass(PackageArtifact.class);
        verify(packagesListener, times(1)).afterCopy(source.capture(), target.capture());
        assertEquals(source.getValue().getRepoKey(), LOCAL_REPO_PATH.getRepoKey());
        assertEquals(target.getValue().getRepoKey(), targetPath.getRepoKey());
    }

    @Test
    public void testAfterCopyWithTargetIsNotSupported() {
        RepoPath targetPath = RepoPathFactory.create("repoNotSupported", LOCAL_REPO_PATH.getPath());
        VfsFile<?> targetFile = mock(VfsFile.class);
        setup(targetFile, targetPath);
        RepoConfig targetRepoConfig = mock(RepoConfig.class);
        when(targetRepoConfig.getPackageType()).thenReturn(RepoType.ReleaseBundles);
        when(packageSpecificationAdapter.isSupportedPackage("repoNotSupported")).thenReturn(false);
        interceptor.afterCopy(file, targetFile, statusHolder, properties, interceptorMoveCopyContext);
        ArgumentCaptor<PackageArtifact> source = ArgumentCaptor.forClass(PackageArtifact.class);
        ArgumentCaptor<PackageArtifact> target = ArgumentCaptor.forClass(PackageArtifact.class);
        verify(packagesListener, times(0)).afterCopy(source.capture(), target.capture());
    }

    @Test
    public void testCheckCopyMove() {
        assertEquals(ALLOW, interceptor.checkCopyMove(file, file.getRepoPath(), statusHolder));
        assertEquals(ALLOW, interceptor.checkCopyMove(file, file.getRepoPath(), statusHolder));
        when(packageSpecificationAdapter.isSupportedPackage(anyString())).thenReturn(false);
        assertEquals(ALLOW, interceptor.checkCopyMove(file, file.getRepoPath(), statusHolder));
    }

    @Test
    public void testCheckCopyMoveReject() throws PackageCancelException {
        RepoPath targetRepoPath = RepoPathFactory.create("target", "targetPath");
        doThrow(PackageCancelException.class).when(packagesListener)
                .isCopyOrMoveAllowed(any(PackageArtifact.class), eq(targetRepoPath.getRepoKey()),
                        eq(targetRepoPath.getPath()));
        assertEquals(REJECT, interceptor.checkCopyMove(file, targetRepoPath, statusHolder));
        verify(packageSpecificationAdapter, times(1)).isSupportedPackage(targetRepoPath.getRepoKey());
    }

    @Test
    public void testAfterPropertyCreate() {
        interceptor.afterPropertyCreate(file, statusHolder, "k1", "v1", "v2");

        ArgumentCaptor<PackageArtifact> artifact = ArgumentCaptor.forClass(PackageArtifact.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> delete = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String[]> values = ArgumentCaptor.forClass(String[].class);

        verify(packagesListener, times(1)).afterPropertyChange(artifact.capture(), key.capture(), delete.capture(),
                values.capture());

        assertEquals(artifact.getValue().getRepoKey(), LOCAL_REPO_PATH.getRepoKey());
        assertEquals("k1", key.getValue());
        assertEquals(Boolean.FALSE, delete.getValue());
        Set<String> expected = values.getAllValues().stream()
                .map(Arrays::asList)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        assertEquals(expected, Set.of("v1", "v2"));
    }

    @Test
    public void testAfterPropertyDelete() {
        interceptor.afterPropertyDelete(file, statusHolder, "k1");

        ArgumentCaptor<PackageArtifact> artifact = ArgumentCaptor.forClass(PackageArtifact.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> delete = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<String[]> values = ArgumentCaptor.forClass(String[].class);

        verify(packagesListener, times(1)).afterPropertyChange(artifact.capture(), key.capture(), delete.capture(),
                values.capture());

        assertEquals(artifact.getValue().getRepoKey(), LOCAL_REPO_PATH.getRepoKey());
        assertEquals("k1", key.getValue());
        assertEquals(Boolean.TRUE, delete.getValue());
        Set<String> expected = values.getAllValues().stream()
                .map(Arrays::asList)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        assertEquals(expected, Set.of());
    }

    @Test
    public void testAfterRemoteDownload() throws RepoRejectException {
        HttpRepo repo = mock(HttpRepo.class);
        HttpRepoDescriptor repoDescriptor = mock(HttpRepoDescriptor.class);
        when(repo.isLocal()).thenReturn(false);
        when(repo.isCache()).thenReturn(false);
        when(repo.getDescriptor()).thenReturn(repoDescriptor);
        when(repoDescriptor.getType()).thenReturn(RepoType.CRAN);
        when(repositoryService.remoteRepositoryByKey(REMOTE_REPO_PATH.getRepoKey())).thenReturn(repo);
        RepoResource repoResource = mock(RepoResource.class);
        RepoResourceInfo info = mock(RepoResourceInfo.class);
        when(info.getRepoPath()).thenReturn(REMOTE_REPO_PATH);
        when(info.getLastModified()).thenReturn(Long.MAX_VALUE);
        when(info.getSize()).thenReturn(Long.MAX_VALUE);
        when(info.getSha1()).thenReturn("sha1");
        when(info.getSha2()).thenReturn("sha2");
        when(info.getMd5()).thenReturn("md5");
        when(info.getName()).thenReturn(REMOTE_REPO_PATH.getName());
        when(repoResource.getInfo()).thenReturn(info);
        when(repoResource.getRepoKey()).thenReturn(REMOTE_REPO_PATH.getRepoKey());

        interceptor.afterRemoteDownload(repoResource);

        ArgumentCaptor<PackageArtifact> artifact = ArgumentCaptor.forClass(PackageArtifact.class);

        verify(packagesListener, times(1)).afterRemoteDownload(artifact.capture());

        assertEquals(artifact.getValue().getRepoKey(), REMOTE_REPO_PATH.getRepoKey());
    }

    @Test
    public void testBeforeCopyFiles() {
        interceptor.beforeCopyFiles(mock(BulkCopyMoveContext.class));
        verify(repositoryService, never()).getRepositoryConfigByKey(anyString());
    }

    @Test
    public void testBeforeMoveFiles() {
        interceptor.beforeMoveFiles(mock(BulkCopyMoveContext.class));
        verify(repositoryService, never()).getRepositoryConfigByKey(anyString());
    }

    @Test
    public void testAssertDeletePathAllowed() {
        interceptor.assertDeletePathAllowed(LOCAL_REPO_PATH, statusHolder);
        verify(repositoryService, never()).getRepositoryConfigByKey(anyString());
    }

    @Test
    public void testAssertDeleteRepoAllowed() {
        interceptor.assertDeleteRepoAllowed(LOCAL_REPO_PATH.getRepoKey(), statusHolder);
        verify(repositoryService, never()).getRepositoryConfigByKey(anyString());
    }

    @Test
    void testBeforeRemoteHttpMethodExecution() {
        HttpRequestBase httpRequestBase = mock(HttpRequestBase.class);
        HeadersMultiMap headers = spy(HeadersMultiMap.class);
        HttpRepo repo = mock(HttpRepo.class);
        Multimap<String, String> dummyMap =
                TreeMultimap.create(String.CASE_INSENSITIVE_ORDER, Comparator.reverseOrder());

        when(repositoryService.remoteRepositoryByKey(REMOTE_REPO_PATH.getRepoKey())).thenReturn(repo);
        when(headers.getMultiHeaders()).thenReturn(dummyMap);

        interceptor.beforeRemoteHttpMethodExecution(httpRequestBase, null, headers, REMOTE_REPO_PATH);
        verify(packagesListener, times(1)).beforeRemoteHttpMethodExecution(httpRequestBase, null, dummyMap,
                PackageType.CRAN, REMOTE_REPO_PATH.getRepoKey(), REMOTE_REPO_PATH.getPath());
    }

    @Test
    void testBeforeCreate() throws PackageCancelException {
        interceptor.beforeCreate(file, statusHolder);
        verify(repositoryService, times(2)).getRepositoryConfigByKey(anyString());
        verify(packagesListener).beforeCreate(any(PackageArtifact.class), anyMap());
    }

    @Test
    void testCheckDirCopyMove_shouldReturnAllow_whenFolderIsSupported() throws PackageCancelException {
        // Arrange
        VfsItem folder = mock(VfsItem.class);
        when(folder.isFolder()).thenReturn(true);
        RepoPath targetRepoPath = RepoPathFactory.create("target_repo", "target/path");

        // Act
        CopyMoveCheckResult result = interceptor.checkDirCopyMove(folder, targetRepoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
        verify(packagesListener, times(1)).isDirCopyOrMoveAllowed(
                eq(targetRepoPath.getRepoKey()), eq(targetRepoPath.getPath()));
    }

    @Test
    void testCheckDirCopyMove_shouldReturnReject_whenPackageCancelExceptionIsThrown()
            throws PackageCancelException {
        // Arrange
        VfsItem folder = mock(VfsItem.class);
        when(folder.isFolder()).thenReturn(true);
        RepoPath targetRepoPath = RepoPathFactory.create("target_repo", "target/path");
        doThrow(new PackageCancelException(400, "Not allowed", "Not allowed"))
                .when(packagesListener).isDirCopyOrMoveAllowed(
                        eq(targetRepoPath.getRepoKey()), eq(targetRepoPath.getPath()));

        // Act
        CopyMoveCheckResult result = interceptor.checkDirCopyMove(folder, targetRepoPath, statusHolder);

        // Assert
        assertEquals(REJECT, result);
    }

    @Test
    void testCheckDirCopyMove_shouldReturnAllow_whenNotFolder() {
        // Arrange
        when(file.isFolder()).thenReturn(false);
        RepoPath targetRepoPath = RepoPathFactory.create("target_repo", "target/path");

        // Act
        CopyMoveCheckResult result = interceptor.checkDirCopyMove(file, targetRepoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
    }

    @Test
    void testCheckDirCopyMove_shouldReturnAllow_whenPackageTypeNotSupported() {
        // Arrange
        VfsItem folder = mock(VfsItem.class);
        when(folder.isFolder()).thenReturn(true);
        when(folder.getRepoKey()).thenReturn("unsupported_repo");
        RepoPath targetRepoPath = RepoPathFactory.create("target_repo", "target/path");
        when(packageSpecificationAdapter.isSupportedPackage("target_repo")).thenReturn(false);

        // Act
        CopyMoveCheckResult result = interceptor.checkDirCopyMove(folder, targetRepoPath, statusHolder);

        // Assert
        assertEquals(ALLOW, result);
    }
}