package org.artifactory.repo.service;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.db.fs.model.DbFsFile;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.MutableVfsFolder;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ZapServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private LocalCacheRepo mockCacheRepo;

    @Mock
    private RemoteRepo mockRemoteRepo;

    @Mock
    private LocalCacheRepoDescriptor mockDescriptor;

    @Mock
    private CacheExpiry cacheExpiry;

    private ZapServiceImpl zapService;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        zapService = new ZapServiceImpl();

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(ZapService.class)).thenReturn(zapService);
        when(context.beanForType(CacheExpiry.class)).thenReturn(cacheExpiry);
        when(mockCacheRepo.getRemoteRepo()).thenReturn(mockRemoteRepo);

        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    public void testZapEmptyFolder() {
        // Arrange
        RepoPath repoPath = new RepoPathImpl("repoKey", "path");
        prepareFolder(repoPath);

        int itemsZapped = zapService.zap(repoPath, mockCacheRepo, System::currentTimeMillis);

        assertEquals(1, itemsZapped);
        verify(mockRemoteRepo, times(1)).removeFromCaches(repoPath.getPath(), true);
    }

    @Test
    public void testZapFolderWithItems() {
        RepoPath repoPath = new RepoPathImpl("repoKey", "path");
        VfsFolder<?> vfsFolder = prepareFolder(repoPath);

        VfsFile<?> file1 = new DbFsFile(null, 1, new FileInfoImpl(new RepoPathImpl("key1", "path1")));
        VfsFile<?> file2 = new DbFsFile(null, 2, new FileInfoImpl(new RepoPathImpl("key2", "path2")));
        VfsFile<?> file3 = new DbFsFile(null, 3, new FileInfoImpl(new RepoPathImpl("key3", "path3")));
        List<VfsItem> children = Arrays.asList(file1, file2, file3);
        when(vfsFolder.getImmutableChildren()).thenReturn(children);
        when(cacheExpiry.isExpirable(any(), any(), eq("path1"))).thenReturn(true);
        when(cacheExpiry.isExpirable(any(), any(), eq("path2"))).thenReturn(true);
        when(cacheExpiry.isExpirable(any(), any(), eq("path3"))).thenReturn(false);

        when(mockCacheRepo.getMutableFile(file1.getRepoPath())).thenReturn(mock(MutableVfsFile.class));
        when(mockCacheRepo.getMutableFile(file2.getRepoPath())).thenReturn(mock(MutableVfsFile.class));

        int itemsZapped = zapService.zap(repoPath, mockCacheRepo, () -> 12893L);

        assertEquals(3, itemsZapped);
        verify(mockRemoteRepo, times(1)).removeFromCaches(repoPath.getPath(), true);
    }

    private VfsFolder<?> prepareFolder(RepoPath repoPath) {
        VfsFolder<?> vfsFolder = mock(VfsFolder.class);
        when(vfsFolder.getRepoPath()).thenReturn(repoPath);
        when(mockCacheRepo.getImmutableFsItem(repoPath)).thenReturn(vfsFolder);
        when(mockCacheRepo.getDescriptor()).thenReturn(mockDescriptor);
        when(mockDescriptor.getRetrievalCachePeriodMillis()).thenReturn(1000L);
        MutableVfsFolder mutableFolder = mock(MutableVfsFolder.class);
        when(mockCacheRepo.getMutableFolder(repoPath)).thenReturn(mutableFolder);
        when(vfsFolder.getPath()).thenReturn(repoPath.getPath());
        return vfsFolder;
    }

    @Test
    public void testZapWithNullFsItem() {
        RepoPath repoPath = mock(RepoPath.class);
        when(mockCacheRepo.getImmutableFsItem(repoPath)).thenReturn(null);

        int itemsZapped = zapService.zap(repoPath, mockCacheRepo, () -> 128342493L);

        assertEquals(0, itemsZapped);
        verify(mockRemoteRepo, never()).removeFromCaches(anyString(), anyBoolean());
    }
}
