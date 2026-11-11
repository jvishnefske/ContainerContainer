package org.artifactory.storage.db.fs.model;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.MutableFolderInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.storage.fs.MutableVfsFolder;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.lock.FsItemsVault;
import org.artifactory.storage.fs.lock.LockingHelper;
import org.artifactory.storage.fs.lock.SessionLockEntry;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.session.StorageSession;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.artifactory.common.ConstantValues.invokeBeforeAndAfterCreateInterceptorsForNewItem;
import static org.artifactory.common.ConstantValues.repoRootAtomicCreation;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbFsItemProviderTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private SecurityService securityService;
    @Mock
    private RepositoryService repositoryService;
    private AutoCloseable closable;

    private DbFsItemProvider dbFsItemProvider;
    @Mock
    private StoringRepo storingRepo;
    @Mock
    private FsItemsVault fileVault;
    @Mock
    private FsItemsVault folderVault;
    @Mock
    private StorageSession storageSession;
    @Mock
    private SessionLockEntry sessionLockEntry;
    @Mock
    private StorageInterceptors storageInterceptors;
    @Mock
    private FileService fileService;
    @Mock
    private FolderInfo folderInfo;
    private DbFsItemProvider spyDbFsItemProvider;
    private String originalRepoRootAtomicCreation;
    private String originalInvokeInterceptors;

    @BeforeEach
    void setUp() {
        closable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        originalRepoRootAtomicCreation = repoRootAtomicCreation.getString();
        originalInvokeInterceptors = invokeBeforeAndAfterCreateInterceptorsForNewItem.getString();
        RepoPath testRepoPath = RepoPathFactory.create("test-repo", "test/path");
        when(storingRepo.getKey()).thenReturn("test-repo");
        when(artifactoryContext.beanForType(FileService.class)).thenReturn(fileService);
        dbFsItemProvider = new DbFsItemProvider(storingRepo, testRepoPath, fileVault, folderVault);
        spyDbFsItemProvider = spy(dbFsItemProvider);
    }

    @AfterEach
    void tearDown() throws Exception {
        getBound().setProperty(repoRootAtomicCreation, originalRepoRootAtomicCreation);
        getBound().setProperty(invokeBeforeAndAfterCreateInterceptorsForNewItem, originalInvokeInterceptors);
        closable.close();
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    @Test
    void testConfigureFolderInfoUser() {
        MutableFolderInfo folderInfo = Mockito.mock(MutableFolderInfo.class);
        RepoPath repoPath = RepoPathFactory.create("repo", "path/to/folder");
        when(folderInfo.getRepoPath()).thenReturn(repoPath);
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(securityService.currentUsername()).thenReturn("testUser");

        dbFsItemProvider.configureFolderInfo(folderInfo);

        verify(folderInfo).setCreatedBy("testUser");
        verify(folderInfo).setModifiedBy("testUser");
    }

    @Test
    void testConfigureFolderInfoForTrashcan() {
        MutableFolderInfo folderInfo = Mockito.mock(MutableFolderInfo.class);
        folderInfo.setCreated(1L);
        MutableFolderInfo originFolderInfo = Mockito.mock(MutableFolderInfo.class);
        RepoPath repoPath = RepoPathFactory.create("auto-trashcan", "repo/path/to/folder");
        when(folderInfo.getRepoPath()).thenReturn(repoPath);
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(securityService.currentUsername()).thenReturn("testUser");
        when(repositoryService.exists(any(RepoPath.class))).thenReturn(true);
        when(repositoryService.getFolderInfo(any(RepoPath.class))).thenReturn(originFolderInfo);
        when(originFolderInfo.getCreated()).thenReturn(123456789L);
        dbFsItemProvider.configureFolderInfo(folderInfo);

        verify(folderInfo).setCreatedBy("testUser");
        verify(folderInfo).setModifiedBy("testUser");
        verify(folderInfo).setCreated(123456789L);
    }

    @Test
    void testConfigureFolderInfoForTrashcanWhenRepoNotFound() {
        MutableFolderInfo folderInfo = Mockito.mock(MutableFolderInfo.class);
        folderInfo.setCreated(1L);
        RepoPath repoPath = RepoPathFactory.create("auto-trashcan", "repo/path/to/folder");
        when(folderInfo.getRepoPath()).thenReturn(repoPath);
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(securityService.currentUsername()).thenReturn("testUser");
        when(repositoryService.exists(any(RepoPath.class))).thenReturn(false);

        dbFsItemProvider.configureFolderInfo(folderInfo);

        verify(folderInfo).setCreatedBy("testUser");
        verify(folderInfo).setModifiedBy("testUser");
        verify(folderInfo).setCreated(1L);
    }

    @Test
    void testGetOrCreateMutableFsItemNewItemCallsInterceptors() {
        // Given: No existing item in session
        RepoPath repoPath = RepoPathFactory.create("test-repo", "test/path");

        try (
                MockedStatic<LockingHelper> lockingHelperMock = mockStatic(LockingHelper.class);
                MockedStatic<StorageSessionHolder> sessionHolderMock = mockStatic(StorageSessionHolder.class)
        ) {

            // Mock session
            sessionHolderMock.when(StorageSessionHolder::getSession).thenReturn(storageSession);

            // Mock no existing item
            lockingHelperMock.when(() -> LockingHelper.getIfWriteLockedByMe(repoPath))
                    .thenReturn(null);

            // Mock vault operations
            when(fileVault.getLock(repoPath)).thenReturn(mock(org.artifactory.storage.fs.lock.LockEntryId.class));
            when(LockingHelper.writeLock(any())).thenReturn(sessionLockEntry);

            // Mock the creation process
            MutableVfsFolder mockItem = mock(MutableVfsFolder.class);
            when(mockItem.isNew()).thenReturn(true);
            when(mockItem.isFolder()).thenReturn(true);
            when(mockItem.getInfo()).thenReturn(folderInfo);
            when(folderInfo.getRepoPath()).thenReturn(repoPath);

            // Mock the fetchMutableVfsItem to return null (item doesn't exist)
            doReturn(null).when(spyDbFsItemProvider).fetchMutableVfsItem(any(), any());

            // Mock createNewMutableItem
            doReturn(mockItem).when(spyDbFsItemProvider).createNewMutableItem(any(), any());

            // Mock StorageInterceptors
            when(artifactoryContext.beanForType(StorageInterceptors.class)).thenReturn(storageInterceptors);

            // Mock FileService for MinimalInfo.resolve()
            when(fileService.isFile(any())).thenReturn(false);
            when(fileService.isFolder(any())).thenReturn(false);

            // When: Get or create mutable item
            MutableVfsItem result = spyDbFsItemProvider.getOrCreateMutableFsItem(true);

            // Then: Interceptors should be called for new items
            assertNotNull(result);
            verify(storageInterceptors, times(1)).beforeCreate(any(), any());
            verify(storageInterceptors, times(1)).afterCreate(any(), any(), any());
        }
    }

    @Test
    void testGetOrCreateMutableFsItemExistingItemIsNewCallsInterceptors() {
        RepoPath repoPath = RepoPathFactory.create("test-repo", "test/path");
        try (MockedStatic<LockingHelper> lockingHelperMock = mockStatic(LockingHelper.class)) {
            MutableVfsFolder existingItem = mock(MutableVfsFolder.class);
            lockingHelperMock.when(() -> LockingHelper.getIfWriteLockedByMe(repoPath))
                    .thenReturn(existingItem);
            MutableVfsItem result = spyDbFsItemProvider.getOrCreateMutableFsItem(true);
            assertSame(existingItem, result);
            verify(storageInterceptors, never()).beforeCreate(any(), any());
            verify(storageInterceptors, never()).afterCreate(any(), any(), any());
        }
    }

    @Test
    void testGetOrCreateMutableFsItemRestoreScenarioCallsInterceptors() {
        // Given: Restore scenario - item exists but needs to be recreated
        RepoPath repoPath = RepoPathFactory.create("test-repo", "test/path");
        try (
                MockedStatic<LockingHelper> lockingHelperMock = mockStatic(LockingHelper.class);
                MockedStatic<StorageSessionHolder> sessionHolderMock = mockStatic(StorageSessionHolder.class)) {

            // Mock session
            sessionHolderMock.when(StorageSessionHolder::getSession).thenReturn(storageSession);

            // Mock no existing item initially (restore scenario)
            lockingHelperMock.when(() -> LockingHelper.getIfWriteLockedByMe(repoPath))
                    .thenReturn(null);

            // Mock vault operations
            when(fileVault.getLock(repoPath)).thenReturn(mock(org.artifactory.storage.fs.lock.LockEntryId.class));
            when(LockingHelper.writeLock(any())).thenReturn(sessionLockEntry);

            // Mock the creation process
            MutableVfsFolder mockItem = mock(MutableVfsFolder.class);
            when(mockItem.isNew()).thenReturn(true);
            when(mockItem.isFolder()).thenReturn(true);
            when(mockItem.getInfo()).thenReturn(folderInfo);
            when(folderInfo.getRepoPath()).thenReturn(repoPath);

            // Mock the fetchMutableVfsItem to return null (item doesn't exist)
            doReturn(null).when(spyDbFsItemProvider).fetchMutableVfsItem(any(), any());

            // Mock createNewMutableItem
            doReturn(mockItem).when(spyDbFsItemProvider).createNewMutableItem(any(), any());

            // Mock StorageInterceptors
            when(artifactoryContext.beanForType(StorageInterceptors.class)).thenReturn(storageInterceptors);

            // When: Get or create mutable item during restore
            MutableVfsItem result = spyDbFsItemProvider.getOrCreateMutableFsItem(true);

            // Then: Interceptors should be called for restored items
            assertNotNull(result);
            assertTrue(result.isNew());
            verify(storageInterceptors, times(1)).beforeCreate(any(), any());
            verify(storageInterceptors, times(1)).afterCreate(any(), any(), any());
        }
    }

    @Test
    void testGetOrCreateMutableFsItemFileItemDoesNotCallInterceptors() {
        // Given: File item (not folder)
        RepoPath repoPath = RepoPathFactory.create("test-repo", "test/file.txt");

        try (
                MockedStatic<LockingHelper> lockingHelperMock = mockStatic(LockingHelper.class);
                MockedStatic<StorageSessionHolder> sessionHolderMock = mockStatic(StorageSessionHolder.class)
        ) {

            // Mock session
            sessionHolderMock.when(StorageSessionHolder::getSession).thenReturn(storageSession);

            // Mock no existing item
            lockingHelperMock.when(() -> LockingHelper.getIfWriteLockedByMe(repoPath))
                    .thenReturn(null);

            // Mock vault operations
            when(fileVault.getLock(repoPath)).thenReturn(mock(org.artifactory.storage.fs.lock.LockEntryId.class));
            when(LockingHelper.writeLock(any())).thenReturn(sessionLockEntry);

            // Mock the creation process
            MutableVfsItem mockItem = mock(MutableVfsItem.class);
            when(mockItem.isNew()).thenReturn(true);
            when(mockItem.isFolder()).thenReturn(false); // File, not folder

            // Mock the fetchMutableVfsItem to return null (item doesn't exist)
            doReturn(null).when(spyDbFsItemProvider).fetchMutableVfsItem(any(), any());

            // Mock createNewMutableItem
            doReturn(mockItem).when(spyDbFsItemProvider).createNewMutableItem(any(), any());

            // When: Get or create mutable file
            MutableVfsItem result = spyDbFsItemProvider.getOrCreateMutableFsItem(true);

            // Then: Interceptors should NOT be called for files
            assertNotNull(result);
            verify(storageInterceptors, never()).beforeCreate(any(), any());
            verify(storageInterceptors, never()).afterCreate(any(), any(), any());
        }
    }

    @Test
    void testCreateAncestors_shouldReturnEarly_whenParentIsRootAndAtomicCreationEnabled() throws Exception {
        // Given: A repo path whose parent is the root folder (depth=1), and the root doesn't exist in db
        // Feature flag enabled means root folders are created atomically with repo config
        getBound().setProperty(repoRootAtomicCreation, "true");
        RepoPath fileInRoot = RepoPathFactory.create("test-repo", "file.txt");

        try (MockedStatic<LockingHelper> lockingHelperMock = mockStatic(LockingHelper.class)) {
            // Mock: Parent is not locked
            lockingHelperMock.when(() -> LockingHelper.getIfWriteLockedByMe(fileInRoot.getParent()))
                    .thenReturn(null);

            // Mock: Parent doesn't exist in db (MinimalInfo.resolve returns non-existent)
            when(fileService.isFile(fileInRoot.getParent())).thenReturn(false);
            when(fileService.isFolder(fileInRoot.getParent())).thenReturn(false);

            // Create provider with the file-in-root path
            DbFsItemProvider providerForFileInRoot =
                    new DbFsItemProvider(storingRepo, fileInRoot, fileVault, folderVault);
            DbFsItemProvider spyProvider = spy(providerForFileInRoot);

            // When: createAncestors is called
            // Then: It should return early without throwing (root folders are assumed to exist atomically)
            assertDoesNotThrow(() -> spyProvider.createAncestors());

            // Verify handleAncestors was not called (early return because parent is root)
            verify(spyProvider, never()).handleAncestors(any());
        }
    }

    @Test
    void testCreateAncestors_shouldCallHandleAncestors_whenParentIsRootAndAtomicCreationDisabled() throws Exception {
        // Given: A repo path whose parent is the root folder (depth=1), but atomic creation is DISABLED
        // When feature flag is disabled, we should NOT early return - root needs to be created lazily
        getBound().setProperty(repoRootAtomicCreation, "false");
        RepoPath fileInRoot = RepoPathFactory.create("test-repo", "file.txt");

        try (MockedStatic<LockingHelper> lockingHelperMock = mockStatic(LockingHelper.class)) {
            // Mock: Parent is not locked
            lockingHelperMock.when(() -> LockingHelper.getIfWriteLockedByMe(fileInRoot.getParent()))
                    .thenReturn(null);

            // Mock: Parent doesn't exist in db
            when(fileService.isFile(fileInRoot.getParent())).thenReturn(null);

            // Mock: areFiles for batch resolution in handleAncestors (for root folder)
            // Since ancestors list will be empty for root parent, this won't be called
            // But we need to verify handleAncestors was actually invoked
            when(fileService.areFiles(any())).thenReturn(java.util.Map.of());

            // Create provider with the file-in-root path
            DbFsItemProvider providerForFileInRoot =
                    new DbFsItemProvider(storingRepo, fileInRoot, fileVault, folderVault);
            DbFsItemProvider spyProvider = spy(providerForFileInRoot);

            // When: createAncestors is called
            spyProvider.createAncestors();

            // Then: handleAncestors should be called (no early return because feature is disabled)
            // When parent is root and feature is disabled, handleAncestors is called with empty list
            verify(spyProvider).handleAncestors(any());
        }
    }

    @Test
    void testCreateAncestors_shouldCallHandleAncestors_whenParentIsNotRootAndDoesNotExist() throws Exception {
        // Given: A repo path whose parent is NOT root (nested path), and the parent doesn't exist in db
        RepoPath nestedFile = RepoPathFactory.create("test-repo", "folder/subfolder/file.txt");

        try (MockedStatic<LockingHelper> lockingHelperMock = mockStatic(LockingHelper.class)) {
            // Mock: Parent is not locked
            lockingHelperMock.when(() -> LockingHelper.getIfWriteLockedByMe(any()))
                    .thenReturn(null);

            // Mock: All paths don't exist in db
            // MinimalInfo.resolve uses fileService.isFile() which returns:
            // - null for non-existent paths
            // - true for files
            // - false for folders
            when(fileService.isFile(any())).thenReturn(null);

            // Mock: areFiles for batch resolution in handleAncestors
            // Returns null for each ancestor to indicate they don't exist
            when(fileService.areFiles(any())).thenAnswer(invocation -> {
                java.util.Collection<RepoPath> paths = invocation.getArgument(0);
                java.util.Map<RepoPath, Boolean> result = new java.util.HashMap<>();
                for (RepoPath path : paths) {
                    result.put(path, null); // null means not exist
                }
                return result;
            });

            // Mock: LockingHelper.getLockEntry returns a lock entry with mutable item.
            // This causes the code to skip folder creation (already locked),
            // but still proves handleAncestors was called.
            MutableVfsFolder mockFolder = mock(MutableVfsFolder.class);
            lockingHelperMock.when(() -> LockingHelper.getLockEntry(any())).thenReturn(sessionLockEntry);
            when(sessionLockEntry.getMutableFsItem()).thenReturn(mockFolder);

            // Create provider with the nested path
            DbFsItemProvider providerForNestedFile =
                    new DbFsItemProvider(storingRepo, nestedFile, fileVault, folderVault);

            // When: createAncestors is called
            providerForNestedFile.createAncestors();

            // Then: areFiles should be called (proves handleAncestors was reached)
            // This happens because parent is NOT root, so we don't return early
            verify(fileService).areFiles(any());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "true, true, true, true",
            "true, true, false, false",
            "true, false, true, false",
            "true, false, false, false",
            "false, true, true, false",
            "false, true, false, false",
            "false, false, true, false",
            "false, false, false, false"
    })
    void testGetOrCreateMutableFsItemExistingNewItemCallsInterceptors(boolean isFolder, boolean isNew,
            boolean featureFlag, boolean shouldRun) {
        // Given: fetchMutableVfsItem returns an existing item that is new
        RepoPath repoPath = RepoPathFactory.create("test-repo", "test/path");

        try (
                MockedStatic<LockingHelper> lockingHelperMock = mockStatic(LockingHelper.class);
                MockedStatic<StorageSessionHolder> sessionHolderMock = mockStatic(StorageSessionHolder.class)
        ) {

            // Mock session
            sessionHolderMock.when(StorageSessionHolder::getSession).thenReturn(storageSession);

            // Mock no existing item in session initially
            lockingHelperMock.when(() -> LockingHelper.getIfWriteLockedByMe(repoPath))
                    .thenReturn(null);

            // Mock vault operations
            when(fileVault.getLock(repoPath)).thenReturn(mock(org.artifactory.storage.fs.lock.LockEntryId.class));
            when(LockingHelper.writeLock(any())).thenReturn(sessionLockEntry);

            // Mock fetchMutableVfsItem to return an existing item that is new
            MutableVfsFolder existingNewItem = mock(MutableVfsFolder.class);
            getBound().setProperty(invokeBeforeAndAfterCreateInterceptorsForNewItem, String.valueOf(featureFlag));
            when(existingNewItem.isNew()).thenReturn(isNew);
            when(existingNewItem.isFolder()).thenReturn(isFolder);
            when(existingNewItem.getInfo()).thenReturn(folderInfo);
            when(folderInfo.getRepoPath()).thenReturn(repoPath);

            doReturn(existingNewItem).when(spyDbFsItemProvider).fetchMutableVfsItem(any(), any());

            // Mock StorageInterceptors
            when(artifactoryContext.beanForType(StorageInterceptors.class)).thenReturn(storageInterceptors);

            // Mock FileService for MinimalInfo.resolve()
            when(fileService.isFile(any())).thenReturn(false);
            when(fileService.isFolder(any())).thenReturn(false);

            // When: Get or create mutable item
            MutableVfsItem result = spyDbFsItemProvider.getOrCreateMutableFsItem(true);

            // Then: Interceptors should be called for existing new items
            assertNotNull(result);
            assertSame(existingNewItem, result);
            if (shouldRun) {
                verify(storageInterceptors, times(1)).beforeCreate(any(), any());
                verify(storageInterceptors, times(1)).afterCreate(any(), any(), any());
            } else {
                verify(storageInterceptors, never()).beforeCreate(any(), any());
                verify(storageInterceptors, never()).afterCreate(any(), any(), any());
            }
        }
    }
}
