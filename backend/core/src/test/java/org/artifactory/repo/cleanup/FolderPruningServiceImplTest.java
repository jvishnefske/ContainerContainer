package org.artifactory.repo.cleanup;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.mime.MavenNaming;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.model.fs.FolderInfoImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.schedule.TaskService;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.folderPruningQuietPeriodSecs;
import static org.artifactory.common.ConstantValues.trashcanFolderPruningEnabled;
import static org.artifactory.repo.trash.TrashService.TRASH_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

public class FolderPruningServiceImplTest extends ArtifactoryHomeBoundTest {
    private static final String LIBS_RELEASES_LOCAL = "libs-releases-local";

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private FileService fileService;

    @Mock
    private ArtifactoryContext contextMock;

    @Mock
    private ItemInfo itemInfo;

    @Mock
    private TaskService taskService;

    @Mock
    private MeterRegistry meterRegistry;

    private FolderPruningServiceImpl folderPruningService;

    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        mocks = MockitoAnnotations.openMocks(this);
        folderPruningService = new FolderPruningServiceImpl(repositoryService, taskService, fileService, meterRegistry);
        ArtifactoryContextThreadBinder.bind(contextMock);
        when(contextMock.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        homeStub.setProperty(folderPruningQuietPeriodSecs, "0");
        homeStub.setProperty(trashcanFolderPruningEnabled, "true");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testInit() {
        when(taskService.startTask(any(), anyBoolean())).thenReturn("id");
        when(meterRegistry.gauge(any(), any(Object.class), any())).thenReturn(null);
        folderPruningService.init();
    }

    @Test
    public void testDontAddSameParentTwiceTest() {

        RepoPath fileRepoPath1 = new RepoPathImpl(LIBS_RELEASES_LOCAL, "radiohead/kid_a.txt");
        RepoPath fileRepoPath2 = new RepoPathImpl(LIBS_RELEASES_LOCAL, "radiohead/amnesiac.txt");

        when(repositoryService.getItemInfo(fileRepoPath1.getParent())).thenReturn(itemInfo);
        when(itemInfo.isFolder()).thenReturn(true);
        folderPruningService.prune(fileRepoPath1.getParent());
        folderPruningService.prune(fileRepoPath2.getParent());
        folderPruningService.doPrune();
        verify(repositoryService, times(1)).getItemInfo(fileRepoPath1.getParent());
    }

    @Test
    public void testAddDifferentParentTest() {

        RepoPath fileRepoPath1 = new RepoPathImpl(LIBS_RELEASES_LOCAL, "radiohead/kid_a.txt");
        RepoPath fileRepoPath2 = new RepoPathImpl(LIBS_RELEASES_LOCAL, "tool/10000_days.txt");

        when(repositoryService.getItemInfo(fileRepoPath1.getParent())).thenReturn(itemInfo);
        when(repositoryService.getItemInfo(fileRepoPath2.getParent())).thenReturn(itemInfo);
        when(itemInfo.isFolder()).thenReturn(true);
        folderPruningService.prune(fileRepoPath1.getParent());
        folderPruningService.prune(fileRepoPath2.getParent());
        folderPruningService.doPrune();
        verify(repositoryService, times(1)).getItemInfo(fileRepoPath1.getParent());
        verify(repositoryService, times(1)).getItemInfo(fileRepoPath2.getParent());
    }

    @Test
    public void testSendingFolderThatContainsChildTest() {

        RepoPath fileRepoPath1 = new RepoPathImpl(LIBS_RELEASES_LOCAL, "radiohead/kid_a.txt");
        List<ItemInfo> itemInfoList = new ArrayList<>();
        itemInfoList.add(new FolderInfoImpl(fileRepoPath1));

        when(repositoryService.getItemInfo(fileRepoPath1.getParent())).thenReturn(itemInfo);
        when(itemInfo.isFolder()).thenReturn(true);
        when(fileService.loadChildren(any(), eq(2L))).thenReturn(itemInfoList);
        folderPruningService.prune(itemInfoList.get(0).getRepoPath());
        folderPruningService.doPrune();
        verify(repositoryService, times(0)).getItemInfo(fileRepoPath1.getParent());
    }

    @Test
    public void testItemNotFoundRuntimeExceptionTest() {
        RepoPath fileRepoPath1 = new RepoPathImpl(LIBS_RELEASES_LOCAL, "radiohead/kid_a.txt");
        RepoPath fileRepoPath2 = new RepoPathImpl(LIBS_RELEASES_LOCAL, "tool/10000_days.txt");

        when(repositoryService.getItemInfo(fileRepoPath1.getParent())).thenThrow(
                new ItemNotFoundRuntimeException("not found"));
        when(repositoryService.getItemInfo(fileRepoPath2.getParent())).thenReturn(itemInfo);
        when(itemInfo.isFolder()).thenThrow(new ItemNotFoundRuntimeException("not found"));
        folderPruningService.prune(fileRepoPath1.getParent());
        folderPruningService.prune(fileRepoPath2.getParent());
        folderPruningService.doPrune();
        verify(repositoryService, times(1)).getItemInfo(fileRepoPath1.getParent());
        verify(repositoryService, times(1)).getItemInfo(fileRepoPath2.getParent());
    }

    @Test
    public void testTryToPruneFile() {
        RepoPath fileRepoPath1 = new RepoPathImpl(LIBS_RELEASES_LOCAL, "tool/kid_a.txt");

        when(repositoryService.getItemInfo(fileRepoPath1.getParent())).thenReturn(itemInfo);
        when(itemInfo.isFolder()).thenReturn(false);
        folderPruningService.prune(fileRepoPath1.getParent());
        folderPruningService.doPrune();
        verify(repositoryService, times(1)).getItemInfo(fileRepoPath1.getParent());
    }

    @Test
    public void testPruneSingleRepoPathThrowExceptionTest() {
        RepoPath fileRepoPath1 = new RepoPathImpl(LIBS_RELEASES_LOCAL, "tool/kid_a.txt");

        when(repositoryService.getItemInfo(fileRepoPath1.getParent())).thenReturn(itemInfo);
        when(itemInfo.isFolder()).thenReturn(true);
        when(fileService.loadChildren(any(), eq(2L))).thenThrow(new ItemNotFoundRuntimeException("not found"));
        folderPruningService.prune(fileRepoPath1.getParent());
        folderPruningService.doPrune();
        verify(repositoryService, times(0)).getItemInfo(fileRepoPath1.getParent());
    }

    @Test
    public void testPruneEmptyFolder() {
        RepoPath emptyFolderPath = new RepoPathImpl(LIBS_RELEASES_LOCAL, "empty/folder");

        when(fileService.loadChildren(emptyFolderPath, 2L)).thenReturn(Collections.emptyList());
        when(repositoryService.getItemInfo(emptyFolderPath)).thenReturn(itemInfo);
        when(itemInfo.isFolder()).thenReturn(true);

        folderPruningService.prune(emptyFolderPath);
        assertEquals(1, folderPruningService.getQueueSize());
        folderPruningService.doPrune();

        verify(fileService, times(1)).loadChildren(emptyFolderPath, 2L);
        verify(repositoryService, times(1)).undeploy(
                eq(emptyFolderPath), eq(true), any(DeleteContext.class));
    }

    @Test
    public void testPruneFolderWithOnlyMavenMetadata() {
        RepoPath folderPath = new RepoPathImpl(LIBS_RELEASES_LOCAL, "maven/folder");
        RepoPath mavenMetadataPath =
                new RepoPathImpl(LIBS_RELEASES_LOCAL, "maven/folder/" + MavenNaming.MAVEN_METADATA_NAME);

        List<ItemInfo> mavenMetadataOnly = new ArrayList<>();
        ItemInfo mavenMetadataItem = new FileInfoImpl(mavenMetadataPath);
        mavenMetadataOnly.add(mavenMetadataItem);

        when(fileService.loadChildren(folderPath, 2L)).thenReturn(mavenMetadataOnly);

        folderPruningService.prune(folderPath);
        folderPruningService.doPrune();

        verify(fileService, times(1)).loadChildren(folderPath, 2L);
        verify(repositoryService, times(1)).undeploy(
                eq(folderPath), eq(true), any(DeleteContext.class));
    }

    @Test
    public void testDontPruneFolderWithMultipleChildren() {
        RepoPath folderPath = new RepoPathImpl(LIBS_RELEASES_LOCAL, "full/folder");
        RepoPath child1Path = new RepoPathImpl(LIBS_RELEASES_LOCAL, "full/folder/file1.txt");
        RepoPath child2Path = new RepoPathImpl(LIBS_RELEASES_LOCAL, "full/folder/file2.txt");

        List<ItemInfo> multipleChildren = new ArrayList<>();
        multipleChildren.add(new FileInfoImpl(child1Path));
        multipleChildren.add(new FileInfoImpl(child2Path));

        when(fileService.loadChildren(folderPath, 2L)).thenReturn(multipleChildren);

        folderPruningService.prune(folderPath);
        folderPruningService.doPrune();

        verify(fileService, times(1)).loadChildren(folderPath, 2L);
        verify(repositoryService, times(0)).undeploy(any(), any(Boolean.class), any(Boolean.class));
    }

    @Test
    public void testDontPruneFolderWithNonMavenMetadataChild() {
        RepoPath folderPath = new RepoPathImpl(LIBS_RELEASES_LOCAL, "single/folder");
        RepoPath childPath = new RepoPathImpl(LIBS_RELEASES_LOCAL, "single/folder/regular-file.txt");

        List<ItemInfo> singleNonMavenChild = new ArrayList<>();
        singleNonMavenChild.add(new FileInfoImpl(childPath));

        when(fileService.loadChildren(folderPath, 2L)).thenReturn(singleNonMavenChild);

        folderPruningService.prune(folderPath);
        folderPruningService.doPrune();

        verify(fileService, times(1)).loadChildren(folderPath, 2L);
        verify(repositoryService, times(0)).undeploy(any(), any(Boolean.class), any(Boolean.class));
    }

    @Test
    public void testTrashFolderPruningWithMultipleChildrenWhenEnabled() {
        RepoPath trashFolderPath = new RepoPathImpl(TRASH_KEY, "some-repo/parent/folder");
        RepoPath child1Path = new RepoPathImpl(TRASH_KEY, "some-repo/parent/folder/subfolder1");
        RepoPath child2Path = new RepoPathImpl(TRASH_KEY, "some-repo/parent/folder/subfolder2");

        setupTrashFolderWithChildren(trashFolderPath, child1Path, child2Path);

        folderPruningService.prune(trashFolderPath);
        folderPruningService.doPrune();

        verify(repositoryService, never()).undeploy(eq(trashFolderPath), any(Boolean.class), any(Boolean.class));
        verify(repositoryService, times(1)).getChildrenStream(trashFolderPath);
        verify(repositoryService, times(1)).getItemInfo(child1Path);
        verify(repositoryService, times(1)).getItemInfo(child2Path);
    }

    @Test
    public void testTrashFolderPruningWithMixedChildrenWhenEnabled() {
        RepoPath trashFolderPath = new RepoPathImpl(TRASH_KEY, "some-repo/parent/folder");
        RepoPath childFilePath = new RepoPathImpl(TRASH_KEY, "some-repo/parent/folder/file.txt");
        RepoPath childFolderPath = new RepoPathImpl(TRASH_KEY, "some-repo/parent/folder/subfolder");

        List<ItemInfo> mixedChildren = new ArrayList<>();
        mixedChildren.add(new FileInfoImpl(childFilePath));
        mixedChildren.add(new FolderInfoImpl(childFolderPath));

        when(fileService.loadChildren(trashFolderPath, 2L)).thenReturn(mixedChildren);
        mockFolderInfo(trashFolderPath);
        mockFolderInfo(childFolderPath);

        when(repositoryService.getChildrenStream(trashFolderPath))
                .thenReturn(Stream.of(new FileInfoImpl(childFilePath), new FolderInfoImpl(childFolderPath)));

        folderPruningService.prune(trashFolderPath);
        folderPruningService.doPrune();

        verify(repositoryService, never()).undeploy(eq(trashFolderPath), any(Boolean.class), any(Boolean.class));
        verify(repositoryService, times(1)).getChildrenStream(trashFolderPath);
        verify(repositoryService, times(1)).getItemInfo(childFolderPath);
        verify(repositoryService, never()).getItemInfo(childFilePath);
    }

    @Test
    public void testTrashFolderPruningWhenDisabled() {
        homeStub.setProperty(trashcanFolderPruningEnabled, "false");

        RepoPath trashFolderPath = new RepoPathImpl(TRASH_KEY, "some-repo/parent/folder");
        RepoPath child1Path = new RepoPathImpl(TRASH_KEY, "some-repo/parent/folder/subfolder1");
        RepoPath child2Path = new RepoPathImpl(TRASH_KEY, "some-repo/parent/folder/subfolder2");

        setupTrashFolderWithChildren(trashFolderPath, child1Path, child2Path);

        folderPruningService.prune(trashFolderPath);
        folderPruningService.doPrune();

        verify(repositoryService, never()).undeploy(eq(trashFolderPath), any(Boolean.class), any(Boolean.class));
        verify(repositoryService, never()).getChildrenStream(any());
    }

    @Test
    public void testNonTrashFolderWithMultipleChildrenIgnoresTrashcanPruning() {
        RepoPath regularFolderPath = new RepoPathImpl(LIBS_RELEASES_LOCAL, "parent/folder");
        RepoPath child1Path = new RepoPathImpl(LIBS_RELEASES_LOCAL, "parent/folder/subfolder1");
        RepoPath child2Path = new RepoPathImpl(LIBS_RELEASES_LOCAL, "parent/folder/subfolder2");

        List<ItemInfo> multipleChildren = new ArrayList<>();
        multipleChildren.add(new FolderInfoImpl(child1Path));
        multipleChildren.add(new FolderInfoImpl(child2Path));

        when(fileService.loadChildren(regularFolderPath, 2L)).thenReturn(multipleChildren);

        folderPruningService.prune(regularFolderPath);
        folderPruningService.doPrune();

        verify(repositoryService, never()).undeploy(eq(regularFolderPath), any(Boolean.class), any(Boolean.class));
        verify(repositoryService, never()).getChildrenStream(any());
    }

    @Test
    public void testTrashFolderWithEmptyChildrenStillGetsPruned() {
        RepoPath emptyTrashFolderPath = new RepoPathImpl(TRASH_KEY, "some-repo/empty/folder");

        when(fileService.loadChildren(emptyTrashFolderPath, 2L)).thenReturn(Collections.emptyList());
        when(repositoryService.getItemInfo(emptyTrashFolderPath)).thenReturn(itemInfo);
        when(itemInfo.isFolder()).thenReturn(true);

        folderPruningService.prune(emptyTrashFolderPath);
        folderPruningService.doPrune();

        verify(repositoryService, times(1)).undeploy(
                eq(emptyTrashFolderPath), eq(true), any(DeleteContext.class));
        verify(repositoryService, never()).getChildrenStream(any());
    }

    @Test
    public void testTrashFolderWithOnlyMavenMetadataStillGetsPruned() {
        RepoPath trashFolderPath = new RepoPathImpl(TRASH_KEY, "some-repo/maven/folder");
        RepoPath mavenMetadataPath =
                new RepoPathImpl(TRASH_KEY, "some-repo/maven/folder/" + MavenNaming.MAVEN_METADATA_NAME);

        List<ItemInfo> mavenMetadataOnly = new ArrayList<>();
        mavenMetadataOnly.add(new FileInfoImpl(mavenMetadataPath));

        when(fileService.loadChildren(trashFolderPath, 2L)).thenReturn(mavenMetadataOnly);
        mockFolderInfo(trashFolderPath);

        folderPruningService.prune(trashFolderPath);
        folderPruningService.doPrune();

        verify(repositoryService, times(1)).undeploy(
                eq(trashFolderPath), eq(true), any(DeleteContext.class));
        verify(repositoryService, never()).getChildrenStream(any());
    }

    private void setupTrashFolderWithChildren(RepoPath parentPath, RepoPath... childPaths) {
        List<ItemInfo> childFolders = Arrays.stream(childPaths)
                .map(childPath -> {
                    mockFolderInfo(childPath);
                    return new FolderInfoImpl(childPath);
                })
                .collect(Collectors.toList());
        when(fileService.loadChildren(parentPath, 2L)).thenReturn(childFolders);
        mockFolderInfo(parentPath);
        Stream<ItemInfo> childStream = childFolders.stream();
        when(repositoryService.getChildrenStream(parentPath)).thenReturn(childStream);
    }

    private void mockFolderInfo(RepoPath folderPath) {
        ItemInfo folderInfo = mockFolderItemInfo();
        when(repositoryService.getItemInfo(folderPath)).thenReturn(folderInfo);
    }

    private ItemInfo mockFolderItemInfo() {
        ItemInfo folderInfo = mock(ItemInfo.class);
        when(folderInfo.isFolder()).thenReturn(true);
        return folderInfo;
    }
}