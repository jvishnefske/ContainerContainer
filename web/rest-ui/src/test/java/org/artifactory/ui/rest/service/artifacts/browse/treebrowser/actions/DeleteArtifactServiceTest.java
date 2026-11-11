package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.DeleteArtifact;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.repo.webdav.WebdavStatus.SC_BAD_REQUEST;
import static org.artifactory.repo.webdav.WebdavStatus.SC_CONFLICT;
import static org.artifactory.repo.webdav.WebdavStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class DeleteArtifactServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String INTERNAL_METADATA_PATH = ".jfrog/locks/victim.txt/lock";

    @InjectMocks
    private DeleteArtifactService deleteArtifactService;
    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private HaAddon haAddon;
    @Mock
    private ConflictsGuard<Object> conflictsGuard;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactoryRestRequest<?> artifactoryRestRequest;
    @Mock
    private ItemInfo trashedItemInfo;
    @Mock
    private ItemInfo itemInfo;
    private AutoCloseable autoCloseable;
    private BasicStatusHolder statusHolder;
    private ArtifactoryRestResponse<?> restResponse;

    @BeforeEach
    void setup() {
        autoCloseable = openMocks(this);
        statusHolder = new BasicStatusHolder();
        restResponse = new ArtifactoryRestResponse<>();
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.getConflictsGuard("delete_restore_folder")).thenReturn(conflictsGuard);
        setStringSystemProperty(ConstantValues.trashcanRemoveFolderOnOverwrite, "FALSE");
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void clear() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testDeleteFileSuccessfully() {
        generateArtifactForDeletion(false, "cool-repo", "cool-artifact");
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create("cool-repo", "cool-artifact")))
                .thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-artifact"));
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-artifact"))).thenThrow(
                ItemNotFoundRuntimeException.class);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        verify(internalRepositoryService, times(1))
                .undeployMultiTransaction(RepoPathFactory.create("cool-repo", "cool-artifact"));
        assertEquals(SC_OK, restResponse.getResponseCode());
    }

    @Test
    void testSkipDeleteFileThatAlreadyExistInTheTrashcan() {
        generateArtifactForDeletion(false, "cool-repo", "cool-file");
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create("cool-repo", "cool-file"))).thenReturn(itemInfo);
        when(itemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-file"));
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-file"))).thenReturn(trashedItemInfo);
        when(trashedItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-file"));

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals(SC_CONFLICT, restResponse.getResponseCode());
        assertEquals("{\"feedbackMsg\":{\"warn\":\"The trash can contains items with the same path." +
                        "\\nOverridden items cannot be restored.\"},\"is_override\":true}",
                restResponse.getEntity());
        verify(internalRepositoryService, times(0))
                .undeployMultiTransaction(RepoPathFactory.create("auto-trashcan", "cool-repo/cool-file"));
    }

    @Test
    void testDeleteFileThatAlreadyExistInTheTrashcan() {
        generateArtifactForDeletion(true, "cool-repo", "cool-artifact");
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        when(itemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-artifact"));
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create("cool-repo", "cool-artifact"))).thenReturn(itemInfo);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-artifact"))).thenReturn(trashedItemInfo);
        when(trashedItemInfo.getRepoPath()).thenReturn(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-artifact"));

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        verify(internalRepositoryService, times(1))
                .undeployMultiTransaction(RepoPathFactory.create("auto-trashcan", "cool-repo/cool-artifact"));
        assertEquals(SC_OK, restResponse.getResponseCode());
    }

    @Test
    void testSkipDeleteFolderThatAlreadyExistInTheTrashcan() throws InterruptedException {
        boolean folder = true;
        generateArtifactForDeletion(false, "cool-repo", "cool-folder");
        mockItemInfo(RepoPathFactory.create("cool-repo", "cool-folder"), folder);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-folder"))).thenReturn(trashedItemInfo);
        when(trashedItemInfo.isFolder()).thenReturn(true);
        when(trashedItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-folder"));
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals(SC_CONFLICT, restResponse.getResponseCode());
        assertEquals("{\"feedbackMsg\":{\"warn\":\"The trash can contains items with the same path." +
                        "\\nOverridden items cannot be restored.\"},\"is_override\":true}",
                restResponse.getEntity());
        verify(internalRepositoryService, times(0))
                .undeployMultiTransaction(RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder"));
    }

    @Test
    void testForceDeleteFolderThatAlreadyExistInTheTrashcan() throws InterruptedException {
        setStringSystemProperty(ConstantValues.trashcanRemoveFolderOnOverwrite, "TRUE");
        boolean folder = true;
        generateArtifactForDeletion(true, "cool-repo", "cool-folder");
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        mockItemInfo(RepoPathFactory.create("cool-repo", "cool-folder"), folder);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-folder"))).thenReturn(trashedItemInfo);
        when(trashedItemInfo.isFolder()).thenReturn(true);
        when(trashedItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-folder"));
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);
        when(conflictsGuard.tryToLock("auto-trashcan/cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        verify(internalRepositoryService, times(1))
                .undeployMultiTransaction(RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder"));
        assertEquals(SC_OK, restResponse.getResponseCode());
    }

    @Test
    void testForceDeleteFolderThatAlreadyExistInTheTrashcanWhenRemoveFolderOnOverwriteFalse()
            throws InterruptedException {
        boolean folder = true;
        generateArtifactForDeletion(true, "cool-repo", "cool-folder");
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        mockItemInfo(RepoPathFactory.create("cool-repo", "cool-folder"), folder);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-folder"))).thenReturn(trashedItemInfo);
        when(trashedItemInfo.isFolder()).thenReturn(true);
        when(trashedItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-folder"));
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);
        when(conflictsGuard.tryToLock("auto-trashcan/cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);
        deleteArtifactService.execute(artifactoryRestRequest, restResponse);
        verify(internalRepositoryService, times(0))
                .undeployMultiTransaction(RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder"));
        verify(internalRepositoryService, times(1))
                .undeployMultiTransaction(RepoPathFactory.create("cool-repo", "cool-folder"));
        assertEquals(SC_OK, restResponse.getResponseCode());
    }

    @Test
    void testDeleteFolderWhileFileExistInTheTrashcanWithTheSameName() throws InterruptedException {
        generateArtifactForDeletion(true, "cool-repo", "cool-folder");
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        when(itemInfo.getRepoPath()).thenReturn(RepoPathFactory.create("cool-repo", "cool-folder"));
        when(itemInfo.isFolder()).thenReturn(true);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create("cool-repo", "cool-folder"))).thenReturn(itemInfo);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-folder"))).thenReturn(trashedItemInfo);
        when(trashedItemInfo.isFolder()).thenReturn(false);
        when(trashedItemInfo.getRepoPath()).thenReturn(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-artifact"));
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        verify(internalRepositoryService, times(1))
                .undeployMultiTransaction(RepoPathFactory.create("auto-trashcan", "cool-repo/cool-artifact"));
        assertEquals(SC_OK, restResponse.getResponseCode());
    }

    @Test
    void testDeleteFolderWhileRestoreInProgress() throws InterruptedException {
        boolean folder = true;
        generateArtifactForDeletion(true, "cool-repo", "cool-folder");
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        mockItemInfo(RepoPathFactory.create("cool-repo", "cool-folder"), folder);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-folder"))).thenReturn(trashedItemInfo);
        when(trashedItemInfo.isFolder()).thenReturn(true);
        when(trashedItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool-folder"));
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);
        when(conflictsGuard.tryToLock("auto-trashcan/cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(false);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        verify(internalRepositoryService, times(0))
                .undeployMultiTransaction(RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder"));
        assertEquals(
                "{\"feedbackMsg\":{\"warn\":\"Can't override trashed item: auto-trashcan/cool-repo/cool-folder, there" +
                        " is running delete/restore process on the same trashed folder.\"},\"is_override\":false}",
                restResponse.getEntity().toString());
        assertEquals(SC_CONFLICT, restResponse.getResponseCode());
    }

    @Test
    void testDeleteFileWhileRestoringFolderWithTheSameName() throws InterruptedException {
        boolean folder = true;
        generateArtifactForDeletion(true, "cool-repo", "cool");
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        mockItemInfo(RepoPathFactory.create("cool-repo", "cool"), folder);
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool"))).thenReturn(trashedItemInfo);
        when(trashedItemInfo.isFolder()).thenReturn(true);
        when(trashedItemInfo.getRepoPath()).thenReturn(RepoPathFactory.create(TRASH_REPO_KEY, "cool-repo/cool"));
        when(conflictsGuard.tryToLock("cool-repo/cool", 0, TimeUnit.SECONDS)).thenReturn(true);
        when(conflictsGuard.tryToLock("auto-trashcan/cool-repo/cool", 0, TimeUnit.SECONDS)).thenReturn(false);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        verify(internalRepositoryService, times(0))
                .undeployMultiTransaction(RepoPathFactory.create("auto-trashcan", "cool-repo/cool-folder"));
        assertEquals(
                "{\"feedbackMsg\":{\"warn\":\"Can't override trashed item: auto-trashcan/cool-repo/cool, there is " +
                        "running delete/restore process on the same trashed folder.\"},\"is_override\":false}",
                restResponse.getEntity().toString());
        assertEquals(SC_CONFLICT, restResponse.getResponseCode());
    }

    @Test
    void testDeleteFolderWhileDeletionInProgressOnTheSameFile() throws InterruptedException {
        boolean folder = true;
        generateArtifactForDeletion(false, "cool-repo", "cool-folder");
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        mockItemInfo(RepoPathFactory.create("cool-repo", "cool-folder"), folder);
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(false);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals(restResponse.getEntity().toString(),
                "{\"feedbackMsg\":{\"warn\":\"Can't delete folder: cool-repo/cool-folder, there is a running " +
                        "delete/restore process on the same folder.\"},\"is_override\":false}");
        assertEquals(SC_CONFLICT, restResponse.getResponseCode());
    }

    @Test
    void testDeleteItemThatNotExistsFailed() {
        generateArtifactForDeletion(true, "cool-repo", "cool-item");
        when(internalRepositoryService.getItemInfo(
                RepoPathFactory.create("cool-repo", "cool-item"))).thenThrow(ItemNotFoundRuntimeException.class);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals(SC_BAD_REQUEST, restResponse.getResponseCode());
    }

    @Test
    void testDeleteFileThatNotExistsFailed() {
        generateArtifactForDeletion(false, "cool-repo", "cool");
        when(internalRepositoryService.undeployMultiTransaction(any())).thenThrow(ItemNotFoundRuntimeException.class);
        mockItemInfo(RepoPathFactory.create("cool-repo", "cool-folder"));

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals("{\"error\":\"item not found.\"}", restResponse.getEntity().toString());
        assertEquals(SC_BAD_REQUEST, restResponse.getResponseCode());
    }

    @Test
    void testDeleteFolderThatNotExistsFailed() throws InterruptedException {
        boolean folder = true;
        generateArtifactForDeletion(true, "cool-repo", "cool-folder");
        when(internalRepositoryService.undeployMultiTransaction(any())).thenThrow(ItemNotFoundRuntimeException.class);
        mockItemInfo(RepoPathFactory.create("cool-repo", "cool-folder"), folder);
        when(conflictsGuard.tryToLock("cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);
        when(conflictsGuard.tryToLock("auto-trashcan/cool-repo/cool-folder", 0, TimeUnit.SECONDS)).thenReturn(true);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals("{\"error\":\"item not found.\"}", restResponse.getEntity().toString());
        assertEquals(SC_BAD_REQUEST, restResponse.getResponseCode());
    }

    @Test
    void testDeleteFolderWhileTheParentFolderInDeleteProgress() {
        boolean folder = true;
        generateArtifactForDeletion(true, "cool-repo", "cool-folder/a");
        mockItemInfo(RepoPathFactory.create("cool-repo", "cool-folder/a"), folder);
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        when(conflictsGuard.isLocked("cool-repo/cool-folder/")).thenReturn(true);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals(
                "{\"feedbackMsg\":{\"warn\":\"Can't delete folder: cool-repo/cool-folder/a, there is a running " +
                        "delete/restore process on the same folder.\"},\"is_override\":false}",
                restResponse.getEntity().toString());
        assertEquals(SC_CONFLICT, restResponse.getResponseCode());
    }

    @Test
    void testDeleteFolderWhileTheChildrenFolderInDeleteProgress() {
        boolean folder = true;
        generateArtifactForDeletion(true, "cool-repo", "cool-folder");
        mockItemInfo(RepoPathFactory.create("cool-repo", "cool-folder"), folder);
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        when(conflictsGuard.keySet()).thenReturn(Set.of("cool-repo/cool-folder/a"));

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals(
                "{\"feedbackMsg\":{\"warn\":\"Can't delete folder: cool-repo/cool-folder, there is a running " +
                        "delete/restore process on the same folder.\"},\"is_override\":false}",
                restResponse.getEntity().toString());
        assertEquals(SC_CONFLICT, restResponse.getResponseCode());
    }

    @Test
    void testDeleteRepoContent() throws InterruptedException {
        boolean force = false;
        boolean folder = true;
        boolean lockingSuccess = true;
        RepoPath repoPath = RepoPathFactory.create("repoKey");
        generateArtifactForDeletion(force, repoPath.getRepoKey());
        mockItemInfo(repoPath, folder);
        mockConflictsGuard(repoPath, lockingSuccess);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals(SC_OK, restResponse.getResponseCode());
    }

    @Test
    void testDeleteRepoContentOverrideTrashcan() throws InterruptedException {
        boolean force = true;
        boolean folder = true;
        boolean lockSuccess = true;
        RepoPath repoPath = RepoPathFactory.create("repoKey");
        RepoPath trashRepoPath = RepoPathFactory.create(TRASH_REPO_KEY, repoPath.toPath());

        generateArtifactForDeletion(force, repoPath.getRepoKey());

        mockItemInfo(repoPath, folder);
        mockConflictsGuard(repoPath, lockSuccess);

        mockTrashItemInfo(trashRepoPath, folder);
        mockConflictsGuard(trashRepoPath, lockSuccess);
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals(SC_OK, restResponse.getResponseCode());

    }

    /**
     * Non-admins cannot delete {@code .jfrog/} internal metadata, even in a repository they can otherwise
     * delete from; the request is rejected with 404 before any undeploy is attempted.
     */
    @Test
    void testDeleteInternalMetadataIsBlockedForNonAdmin() {
        generateArtifactForDeletion(false, "cool-repo", INTERNAL_METADATA_PATH);
        when(securityService.isAdmin()).thenReturn(false);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals(SC_NOT_FOUND, restResponse.getResponseCode());
        assertEquals(blockedMessage(INTERNAL_METADATA_PATH), restResponse.getFeedbackMsg().getError());
        verify(internalRepositoryService, never()).undeployMultiTransaction(any());
    }

    /**
     * The guard checks the normalized path, so a variant that only resolves to {@code .jfrog/...} after
     * slash-trimming is blocked too, and the reported path is the normalized one.
     */
    @Test
    void testDeleteInternalMetadataWithLeadingSlashIsBlockedForNonAdmin() {
        generateArtifactForDeletion(false, "cool-repo", "/" + INTERNAL_METADATA_PATH);
        when(securityService.isAdmin()).thenReturn(false);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        assertEquals(SC_NOT_FOUND, restResponse.getResponseCode());
        assertEquals(blockedMessage(INTERNAL_METADATA_PATH), restResponse.getFeedbackMsg().getError());
        verify(internalRepositoryService, never()).undeployMultiTransaction(any());
    }

    /**
     * Admins retain the ability to delete internal metadata.
     */
    @Test
    void testDeleteInternalMetadataIsAllowedForAdmin() {
        mockSuccessfulInternalMetadataDelete();
        when(securityService.isAdmin()).thenReturn(true);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        verify(internalRepositoryService)
                .undeployMultiTransaction(RepoPathFactory.create("cool-repo", INTERNAL_METADATA_PATH));
        assertEquals(SC_OK, restResponse.getResponseCode());
    }

    /**
     * With {@code delete.block.internal.metadata.enabled} off the guard is bypassed entirely, keeping the
     * block reversible in production.
     */
    @Test
    void testDeleteInternalMetadataNotBlockedWhenFlagDisabled() {
        setStringSystemProperty(ConstantValues.deleteBlockInternalMetadata, "false");
        mockSuccessfulInternalMetadataDelete();
        when(securityService.isAdmin()).thenReturn(false);

        deleteArtifactService.execute(artifactoryRestRequest, restResponse);

        verify(internalRepositoryService)
                .undeployMultiTransaction(RepoPathFactory.create("cool-repo", INTERNAL_METADATA_PATH));
        assertEquals(SC_OK, restResponse.getResponseCode());
    }

    private static String blockedMessage(String path) {
        return "Internal metadata request detected for '" + RepoPathFactory.create("cool-repo", path) + "'.";
    }

    private void mockSuccessfulInternalMetadataDelete() {
        RepoPath repoPath = RepoPathFactory.create("cool-repo", INTERNAL_METADATA_PATH);
        generateArtifactForDeletion(false, "cool-repo", INTERNAL_METADATA_PATH);
        mockItemInfo(repoPath);
        when(internalRepositoryService.undeployMultiTransaction(any())).thenReturn(statusHolder);
        when(internalRepositoryService.getItemInfo(RepoPathFactory.create(TRASH_REPO_KEY, repoPath.toPath())))
                .thenThrow(ItemNotFoundRuntimeException.class);
    }

    private void mockTrashItemInfo(RepoPath repoPath, boolean folder) {
        when(trashedItemInfo.getRepoPath()).thenReturn(repoPath);
        when(trashedItemInfo.isFolder()).thenReturn(folder);
        when(internalRepositoryService.getItemInfo(repoPath)).thenReturn(trashedItemInfo);
    }

    private void mockConflictsGuard(RepoPath repoPath, boolean locked) throws InterruptedException {
        when(conflictsGuard.tryToLock(repoPath.toPath(),0, TimeUnit.SECONDS)).thenReturn(locked);
    }

    private void mockItemInfo(RepoPath repoPath) {
        mockItemInfo(repoPath, false);
    }

    private void mockItemInfo(RepoPath repoPath, boolean folder) {
        when(itemInfo.getRepoPath()).thenReturn(repoPath);
        when(internalRepositoryService.getItemInfo(repoPath)).thenReturn(itemInfo);
        when(itemInfo.isFolder()).thenReturn(folder);
    }

    private void generateArtifactForDeletion(boolean force, String repoKey) {
        generateArtifactForDeletion(force, repoKey, "");
    }

    private void generateArtifactForDeletion(boolean force, String repoKey, String path) {
        DeleteArtifact deleteArtifact = new DeleteArtifact();
        deleteArtifact.setForce(force);
        deleteArtifact.setRepoKey(repoKey);
        deleteArtifact.setPath(path);
        when((DeleteArtifact) artifactoryRestRequest.getImodel()).thenReturn(deleteArtifact);
    }
}