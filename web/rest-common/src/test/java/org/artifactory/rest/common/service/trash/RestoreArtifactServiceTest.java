package org.artifactory.rest.common.service.trash;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.rest.common.model.trash.RestoreArtifact;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class RestoreArtifactServiceTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private TrashService trashService;
    @Mock
    private HaAddon haAddon;
    @Mock
    private ConflictsGuard conflictsGuard;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactoryRestRequest<?> artifactoryRestRequest;
    @Mock
    private ItemInfo itemInfo;
    @Mock
    private ItemInfo targetItemInfo;
    private RestoreArtifactService restoreArtifactService;
    private AutoCloseable autoCloseable;
    private MoveMultiStatusHolder statusHolder;
    private RestResponse<?> restResponse;

    @BeforeEach
    void setup() {
        autoCloseable = openMocks(this);
        statusHolder = new MoveMultiStatusHolder();
        restResponse = new ArtifactoryRestResponse<>();
        when(artifactoryRestRequest.isUiRestCall()).thenReturn(true);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.getConflictsGuard("delete_restore_folder")).thenReturn(conflictsGuard);
        restoreArtifactService =
                new RestoreArtifactService(trashService, internalRepositoryService, addonsManager);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void clear() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testRestoreFolderWhileDeleteInProgress() throws InterruptedException {
        RestoreArtifact restoreArtifact = new RestoreArtifact();
        restoreArtifact.setRepoKey("cool-repo");
        restoreArtifact.setPath("cool-folder");
        restoreArtifact.setTargetRepoKey("cool-repo");
        restoreArtifact.setTargetPath("cool-folder");
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-folder")))
                .thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-folder"));
        when(itemInfo.isFolder()).thenReturn(true);
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(false);
        restoreArtifactService.execute(artifactoryRestRequest, restResponse);
        assertEquals("{\"warn\":\"Can't restore the folder: cool-repo/cool-folder, " +
                        "there is a running delete/restore process on the same folder.\"}",
                restResponse.getEntity().toString());
    }

    @Test
    void testRestoreEvidenceBlocked() throws InterruptedException {
        RestoreArtifact restoreArtifact = new RestoreArtifact();
        restoreArtifact.setRepoKey("cool-repo");
        restoreArtifact.setPath(".evidence");
        restoreArtifact.setTargetRepoKey("cool-repo");
        restoreArtifact.setTargetPath(".evidence");
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        restoreArtifactService.execute(artifactoryRestRequest, restResponse);
        assertEquals("{\"error\":\"Disallow direct restoration of artifacts from .evidence directory.: .evidence.\"}",
                restResponse.getEntity().toString());
    }

    @Test
    void testRestoreFolderWhileTheTargetInRestoreProgress() throws InterruptedException {
        RestoreArtifact restoreArtifact = new RestoreArtifact();
        restoreArtifact.setRepoKey("auto-trashcan");
        restoreArtifact.setPath("cool-repo/cool-folder");
        restoreArtifact.setTargetRepoKey("cool-repo");
        restoreArtifact.setTargetPath("cool-folder");
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder")))
                .thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder"));
        when(itemInfo.isFolder()).thenReturn(true);
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-folder")))
                .thenReturn(targetItemInfo);
        when(targetItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-folder"));
        when(targetItemInfo.isFolder()).thenReturn(true);
        when(conflictsGuard.tryToLock("auto-trashcan/cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(false);
        restoreArtifactService.execute(artifactoryRestRequest, restResponse);
        assertEquals("{\"warn\":\"Can't restore the folder: auto-trashcan/cool-repo/cool-folder, " +
                        "there is a running delete/restore process on the target folder.\"}",
                restResponse.getEntity().toString());
    }

    @Test
    void testRestoreFileWhileTheTargetIsFolderAndInRestoreProgress() throws InterruptedException {
        RestoreArtifact restoreArtifact = new RestoreArtifact();
        restoreArtifact.setRepoKey("auto-trashcan");
        restoreArtifact.setPath("cool-repo/cool-folder/cool-file");
        restoreArtifact.setTargetRepoKey("cool-repo");
        restoreArtifact.setTargetPath("cool-folder");
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder/cool-file")))
                .thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder/cool-file"));
        when(itemInfo.isFolder()).thenReturn(false);
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-folder")))
                .thenReturn(targetItemInfo);
        when(targetItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-folder"));
        when(targetItemInfo.isFolder()).thenReturn(true);
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(false);
        restoreArtifactService.execute(artifactoryRestRequest, restResponse);
        assertEquals("{\"warn\":\"Can't restore file: auto-trashcan/cool-repo/cool-folder/cool-file," +
                        " there is a running delete/restore process on the target folder.\"}",
                restResponse.getEntity().toString());
    }

    @Test
    void testRestoreFileWhileTheTargetIsNotExist() {
        RestoreArtifact restoreArtifact = new RestoreArtifact();
        restoreArtifact.setRepoKey("auto-trashcan");
        restoreArtifact.setPath("cool-repo/cool-folder/cool-file");
        restoreArtifact.setTargetRepoKey("cool-repo");
        restoreArtifact.setTargetPath("cool-folder");
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder/cool-file")))
                .thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder/cool-file"));
        when(itemInfo.isFolder()).thenReturn(false);
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-folder")))
                .thenReturn(null);
        when(targetItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-folder"));
        when(trashService.restoreBulk(any(), any(), any(), eq(1000))).thenReturn(statusHolder);
        restoreArtifactService.execute(artifactoryRestRequest, restResponse);
        assertEquals("{\"info\":\"Successfully restored trash items\"}",
                restResponse.getEntity().toString());
    }

    @Test
    void testRestoreFolderSuccessfully() throws InterruptedException {
        RestoreArtifact restoreArtifact = new RestoreArtifact();
        restoreArtifact.setRepoKey("auto-trashcan");
        restoreArtifact.setPath("cool-repo/cool-folder");
        restoreArtifact.setTargetRepoKey("cool-repo");
        restoreArtifact.setTargetPath("cool-folder");
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder")))
                .thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder"));
        when(itemInfo.isFolder()).thenReturn(true);
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-folder")))
                .thenReturn(targetItemInfo);
        when(targetItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-folder"));
        when(targetItemInfo.isFolder()).thenReturn(true);
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-folder")))
                .thenReturn(targetItemInfo);
        when(conflictsGuard.tryToLock("auto-trashcan/cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);
        when(trashService.restoreBulk(any(), any(), any(), eq(1000))).thenReturn(statusHolder);
        restoreArtifactService.execute(artifactoryRestRequest, restResponse);
        assertEquals("{\"info\":\"Successfully restored trash items\"}", restResponse.getEntity().toString());
    }

    @Test
    void testRestoreFileSuccessfully() throws InterruptedException {
        RestoreArtifact restoreArtifact = new RestoreArtifact();
        restoreArtifact.setRepoKey("auto-trashcan");
        restoreArtifact.setPath("cool-repo/cool-file");
        restoreArtifact.setTargetRepoKey("cool-repo");
        restoreArtifact.setTargetPath("cool-folder");
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-file")))
                .thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-file"));
        when(itemInfo.isFolder()).thenReturn(false);
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-folder")))
                .thenReturn(targetItemInfo);
        when(targetItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-folder"));
        when(targetItemInfo.isFolder()).thenReturn(true);
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-folder")))
                .thenReturn(targetItemInfo);
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);
        when(trashService.restoreBulk(any(), any(), any(), eq(1000))).thenReturn(statusHolder);
        restoreArtifactService.execute(artifactoryRestRequest, restResponse);
        assertEquals("{\"info\":\"Successfully restored trash items\"}", restResponse.getEntity().toString());
    }

    @Test
    void testRestoreFileWhileTheTargetFolderInDeleteProcess() throws InterruptedException {
        RestoreArtifact restoreArtifact = new RestoreArtifact();
        restoreArtifact.setRepoKey("auto-trashcan");
        restoreArtifact.setPath("cool-repo/cool-folder/cool-file");
        restoreArtifact.setTargetRepoKey("cool-repo");
        restoreArtifact.setTargetPath("cool-folder");
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder/cool-file")))
                .thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder/cool-file"));
        when(itemInfo.isFolder()).thenReturn(false);
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-folder")))
                .thenReturn(targetItemInfo);
        when(targetItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-folder"));
        when(targetItemInfo.isFolder()).thenReturn(true);
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(false);
        when(trashService.restoreBulk(any(), any(), any(), eq(1000))).thenReturn(statusHolder);
        restoreArtifactService.execute(artifactoryRestRequest, restResponse);
        assertEquals("{\"warn\":\"Can't restore file: auto-trashcan/cool-repo/cool-folder/cool-file," +
                        " there is a running delete/restore process on the target folder.\"}",
                restResponse.getEntity().toString());
    }

    @Test
    void testRestoreFolderWhileTheParentFolderInRestoreProgress() {
        RestoreArtifact restoreArtifact = new RestoreArtifact();
        restoreArtifact.setRepoKey("auto-trashcan");
        restoreArtifact.setPath("cool-repo/cool-folder/folder");
        restoreArtifact.setTargetRepoKey("cool-repo");
        restoreArtifact.setTargetPath("cool-folder/folder");
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder/folder")))
                .thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(
                RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder/folder"));
        when(itemInfo.isFolder()).thenReturn(true);
        when((RestoreArtifact) artifactoryRestRequest.getImodel()).thenReturn(restoreArtifact);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-folder")))
                .thenReturn(targetItemInfo);
        when(targetItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-folder"));
        when(targetItemInfo.isFolder()).thenReturn(true);
        when(conflictsGuard.isLocked("auto-trashcan/cool-repo/cool-folder/")).thenReturn(true);
        restoreArtifactService.execute(artifactoryRestRequest, restResponse);
        assertEquals(
                "{\"warn\":\"Can't restore the folder: auto-trashcan/cool-repo/cool-folder/folder, there is a running" +
                        " delete/restore process on the same folder.\"}",
                restResponse.getEntity().toString());
    }
}