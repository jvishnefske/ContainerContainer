package org.artifactory.repo.service.mover;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LayoutsCoreAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.storage.FolderSummaryInfo;
import org.artifactory.api.security.SecurityService;
import org.artifactory.md.Properties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsItem;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.artifactory.projects.ProjectsUtils.PROJ_LIMIT_MSG;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author andreik
 */
public class RepoPathMoverTest {

    @Mock(answer = CALLS_REAL_METHODS)
    private RepoPathMover repoPathMover;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private AolService aolService;
    @Mock
    private SecurityService securityService;

    private final String SOURCE_REPO = "source";
    private final RepoPath SOURCE_FILE = new RepoPathImpl(SOURCE_REPO, "/file.ext", false);
    private final String TARGET_REPO = "target";
    private final RepoPath TARGET_PATH = new RepoPathImpl(TARGET_REPO, "/file.ext", false);
    private final long TEST_SIZE = 100L;

    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContext context = Mockito.mock(ArtifactoryContext.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);
        repoPathMover.setAddonsManager(addonsManager);
        repoPathMover.setRepositoryService(repositoryService);
        repoPathMover.setProjectsService(projectsService);
        repoPathMover.initStorageQuotaValidator();
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(securityService.currentUsername()).thenReturn("cool-user");
    }

    @AfterMethod
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    public void testCopyOrMove() {
        MoverConfig moverConfig = getMoverConfig(true, false);
        prepRepoService(moverConfig, false);
        when(projectsService.getProjectQuotaSizeByRepository(TARGET_REPO)).thenReturn(0L);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();

        repoPathMover.moveOrCopy(statusHolder, moverConfig);

        verify(repoPathMover).handleMoveOrCopy(any(), any(), any(), any());
        assertEquals(statusHolder.getWarnings().size(), 0);
        assertEquals(statusHolder.getErrors().size(), 0);
    }

    @Test
    public void testCopyOrMoveWithProjectsWarningOnly() {
        MoverConfig moverConfig = getMoverConfig(true, false);
        prepRepoService(moverConfig, false);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        when(projectsService.getProjectQuotaSizeByRepository(TARGET_REPO)).thenReturn(TEST_SIZE + 1L);
        doAnswer(invocationOnMock -> {
            MoveMultiStatusHolder status = (MoveMultiStatusHolder) invocationOnMock.getArguments()[1];
            status.warn(PROJ_LIMIT_MSG, HttpStatus.SC_REQUEST_TOO_LONG,
                    LoggerFactory.getLogger(ProjectsService.class));
            return null;
        }).when(projectsService).assertProjectStorageQuota(TARGET_REPO, statusHolder, TEST_SIZE);

        repoPathMover.moveOrCopy(statusHolder, moverConfig);

        verify(repoPathMover).handleMoveOrCopy(any(), any(), any(), any());
        assertEquals(statusHolder.getWarnings().size(), 1);
        assertEquals(statusHolder.getErrors().size(), 0);
    }

    @Test
    public void testCopyOrMoveWithProjectWithUnlimitedQuota() {
        MoverConfig moverConfig = getMoverConfig(true, false);
        prepRepoService(moverConfig, false);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        when(projectsService.getProjectQuotaSizeByRepository(TARGET_REPO))
                .thenReturn(ProjectsService.UNLIMITED_STORAGE);

        repoPathMover.moveOrCopy(statusHolder, moverConfig);

        verify(repoPathMover).handleMoveOrCopy(any(), any(), any(), any());
        verify(repositoryService, never()).getItemInfo(any());
        assertEquals(statusHolder.getWarnings().size(), 0);
        assertEquals(statusHolder.getErrors().size(), 0);
    }

    @Test
    public void testDeleteToTrashCan() {
        MoverConfig moverConfig = getMoverConfig(true, true);
        prepRepoService(moverConfig, true);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();

        repoPathMover.moveOrCopy(statusHolder, moverConfig);

        verify(repoPathMover).handleMoveOrCopy(any(), any(), any(), any());
        verify(projectsService, never()).getProjectQuotaSizeByRepository(anyString());
        verify(repositoryService, never()).getItemInfo(any());
        assertEquals(statusHolder.getWarnings().size(), 0);
        assertEquals(statusHolder.getErrors().size(), 0);
    }

    @Test
    public void testProjectQuotaWillExceedDenyCopyOrMove() {
        MoverConfig moverConfig = getMoverConfig(false, false);
        prepRepoService(moverConfig, false);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        when(projectsService.getProjectQuotaSizeByRepository(TARGET_REPO)).thenReturn(TEST_SIZE + 1L);
        doAnswer(invocationOnMock -> {
            MoveMultiStatusHolder status = (MoveMultiStatusHolder) invocationOnMock.getArguments()[1];
            status.error(PROJ_LIMIT_MSG, HttpStatus.SC_REQUEST_TOO_LONG,
                    LoggerFactory.getLogger(ProjectsService.class));
            return null;
        }).when(projectsService).assertProjectStorageQuota(TARGET_REPO, statusHolder, TEST_SIZE);

        repoPathMover.moveOrCopy(statusHolder, moverConfig);

        verify(repoPathMover, never()).handleMoveOrCopy(any(), any(), any(), any());
        verify(addonsManager, never()).addonByType(LayoutsCoreAddon.class);
        assertEquals(statusHolder.getWarnings().size(), 0);
        assertEquals(statusHolder.getErrors().size(), 1);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp =
            "Target repository.* is a cache repository.* to cache repositories is not allowed.*")
    public void testCopyOrMoveToCache() {
        MoverConfig moverConfig = getMoverConfig(true, false);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        prepRepoService(moverConfig, false, true);
        repoPathMover.moveOrCopy(statusHolder, moverConfig);
        verify(repoPathMover, never()).handleMoveOrCopy(any(), any(), any(), any());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp =
            "Target repository.* is a remote repository.* to remote repositories is not allowed.*")
    public void testCopyOrMoveToRemote() {
        when(repositoryService.isRemoteRepoExist(any())).thenReturn(true);
        MoverConfig moverConfig = getMoverConfig(true, false);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        prepRepoService(moverConfig, false, false);
        repoPathMover.moveOrCopy(statusHolder, moverConfig);
        verify(repoPathMover, never()).handleMoveOrCopy(any(), any(), any(), any());
    }

    @Test
    public void testCopyOrMoveToRemoteSystemUser() {
        when(securityService.currentUsername()).thenReturn(SecurityService.USER_SYSTEM);
        when(repositoryService.isRemoteRepoExist(any())).thenReturn(true);
        MoverConfig moverConfig = getMoverConfig(true, false);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        prepRepoService(moverConfig, false, false);
        repoPathMover.moveOrCopy(statusHolder, moverConfig);
        verify(repoPathMover, times(1)).handleMoveOrCopy(any(), any(), any(), any());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp =
            "Target repository.* is a virtual repository.* to virtual repositories is not allowed.*")
    public void testCopyOrMoveToVirtual() {
        when(repositoryService.isVirtualRepoExist(any())).thenReturn(true);
        MoverConfig moverConfig = getMoverConfig(true, false);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        prepRepoService(moverConfig, false, false);
        repoPathMover.moveOrCopy(statusHolder, moverConfig);
        verify(repoPathMover, never()).handleMoveOrCopy(any(), any(), any(), any());
    }

    @Test
    public void testHandleSamePath() {
        when(repositoryService.isRemoteRepoExist(any())).thenReturn(true);
        MoverConfig moverConfig = new MoverConfigBuilder(SOURCE_FILE, SOURCE_FILE)
                .copy(false).dryRun(false).pruneEmptyFolders(true)
                .properties(mock(Properties.class)).suppressLayouts(true).build();
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        prepRepoService(moverConfig, false, false);
        repoPathMover.moveOrCopy(statusHolder, moverConfig);
        verify(repoPathMover, never()).handleMoveOrCopy(any(), any(), any(), any());
        assertEquals(statusHolder.getErrors().size(), 1);
        assertEquals(statusHolder.getErrors().getFirst().getStatusCode(), 400);
    }

    @Test
    public void testCopyOrMoveToVirtualSystemUser() {
        when(securityService.currentUsername()).thenReturn(SecurityService.USER_SYSTEM);
        when(repositoryService.isVirtualRepoExist(any())).thenReturn(true);
        MoverConfig moverConfig = getMoverConfig(true, false);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        prepRepoService(moverConfig, false, false);
        repoPathMover.moveOrCopy(statusHolder, moverConfig);
        verify(repoPathMover, times(1)).handleMoveOrCopy(any(), any(), any(), any());
    }

    @Test
    public void testMoveOrCopyWithNullSourceItem() {
        MoverConfig moverConfig = getMoverConfig(true, false);
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        
        // Setup repository service to return null for sourceItem
        StoringRepo<?,?> storingRepo = mock(StoringRepo.class);
        when(storingRepo.getImmutableFsItem(moverConfig.getFromRepoPath())).thenReturn(null);
        when(repositoryService.storingRepositoryByKey(any())).thenReturn(storingRepo);
        
        repoPathMover.moveOrCopy(statusHolder, moverConfig);
        assertEquals(statusHolder.getWarnings().getFirst().getStatusCode(), 400);
        assertEquals(statusHolder.getWarnings().getFirst().getMessage(),
                "Could not find item at " + moverConfig.getFromRepoPath());
    }

    private MoverConfig getMoverConfig(boolean suppressLayouts, boolean toTrashCan) {
        MoverConfigBuilder moverConfigBuilder;
        if (toTrashCan) {
            moverConfigBuilder =
                    new MoverConfigBuilder(SOURCE_FILE, new RepoPathImpl(TRASH_REPO_KEY, "/file.ext", false))
                            .copy(false).dryRun(false).pruneEmptyFolders(true)
                            .properties(mock(Properties.class));
        } else {
            moverConfigBuilder = new MoverConfigBuilder(SOURCE_FILE, TARGET_PATH)
                    .copy(false).dryRun(false).pruneEmptyFolders(true)
                    .properties(mock(Properties.class));
        }
        if (suppressLayouts) {
            return moverConfigBuilder.suppressLayouts(true).build();
        }
        return moverConfigBuilder.build();
    }

    private void prepRepoService(MoverConfig moverConfig, boolean toTrashCan) {
        prepRepoService(moverConfig, toTrashCan, false);
    }

    private void prepRepoService(MoverConfig moverConfig, boolean toTrashCan, boolean isCache) {
        StoringRepo storingRepo = mock(StoringRepo.class);
        when(storingRepo.getImmutableFsItem(moverConfig.getFromRepoPath())).thenReturn(mock(VfsItem.class));
        when(repositoryService.storingRepositoryByKey(any())).thenReturn(storingRepo);
        DbLocalRepo dbLocalRepo = mock(DbLocalRepo.class);
        when(dbLocalRepo.isCache()).thenReturn(isCache);
        if (toTrashCan) {
            when(repositoryService.getRepoRepoPath(new RepoPathImpl(TRASH_REPO_KEY, "/file.ext", false)))
                    .thenReturn(new RepoRepoPath<>(dbLocalRepo, new RepoPathImpl(TRASH_REPO_KEY, "/file.ext", false)));
        } else {
            when(repositoryService.getRepoRepoPath(TARGET_PATH))
                    .thenReturn(new RepoRepoPath<>(dbLocalRepo, TARGET_PATH));
        }
        FolderSummaryInfo folderSummaryInfoMock = mock(FolderSummaryInfo.class);
        when(folderSummaryInfoMock.getFolderSize()).thenReturn(TEST_SIZE);
        when(repositoryService.getArtifactCountAndSize(any())).thenReturn(folderSummaryInfoMock);
        FileInfoImpl fileInfo = new FileInfoImpl(moverConfig.getFromRepoPath());
        fileInfo.setSize(100);
        when(repositoryService.getItemInfo(SOURCE_FILE)).thenReturn(fileInfo);
        when(repositoryService.getFileInfo(any())).thenReturn(fileInfo);
    }
}