package org.artifactory.ui.rest.service.artifacts.search;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.DeleteArtifact;
import org.artifactory.ui.rest.model.artifacts.search.DeleteArtifactsModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Covers the {@code .jfrog/} internal-metadata guard on the bulk delete endpoint
 * ({@code POST /ui/api/v1/ui/artifactsearch/deleteArtifact}).
 *
 * @author Dvir Twina
 */
class DeleteArtifactsServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "cool-repo";
    private static final String REGULAR_PATH = "a/b/c.txt";
    private static final String INTERNAL_METADATA_PATH = ".jfrog/locks/victim.txt/lock";

    @InjectMocks
    private DeleteArtifactsService<DeleteArtifactsModel> deleteArtifactsService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryRestRequest<DeleteArtifactsModel> artifactoryRestRequest;
    private AutoCloseable autoCloseable;
    private ArtifactoryRestResponse<?> restResponse;

    @BeforeEach
    void setup() {
        autoCloseable = openMocks(this);
        restResponse = new ArtifactoryRestResponse<>();
    }

    @AfterEach
    void clear() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testDeleteRegularArtifactSucceeds() {
        mockRequest(REGULAR_PATH);
        when(repositoryService.undeploy(any(), anyBoolean(), anyBoolean())).thenReturn(new BasicStatusHolder());

        deleteArtifactsService.execute(artifactoryRestRequest, restResponse);

        verify(repositoryService).undeploy(repoPath(REGULAR_PATH), true, true);
    }

    /**
     * Non-admins cannot delete {@code .jfrog/} internal metadata; the request is rejected with 404 before
     * any undeploy is attempted.
     */
    @Test
    void testDeleteInternalMetadataIsBlockedForNonAdmin() {
        mockRequest(INTERNAL_METADATA_PATH);
        when(securityService.isAdmin()).thenReturn(false);

        deleteArtifactsService.execute(artifactoryRestRequest, restResponse);

        assertEquals(SC_NOT_FOUND, restResponse.getResponseCode());
        assertEquals(blockedMessage(INTERNAL_METADATA_PATH), restResponse.getFeedbackMsg().getError());
        verify(repositoryService, never()).undeploy(any(), anyBoolean(), anyBoolean());
    }

    /**
     * The guard checks the normalized path, so a variant that only resolves to {@code .jfrog/...} after
     * slash-trimming is blocked too, and the reported path is the normalized one.
     */
    @Test
    void testDeleteInternalMetadataWithLeadingSlashIsBlockedForNonAdmin() {
        mockRequest("/" + INTERNAL_METADATA_PATH);
        when(securityService.isAdmin()).thenReturn(false);

        deleteArtifactsService.execute(artifactoryRestRequest, restResponse);

        assertEquals(SC_NOT_FOUND, restResponse.getResponseCode());
        assertEquals(blockedMessage(INTERNAL_METADATA_PATH), restResponse.getFeedbackMsg().getError());
        verify(repositoryService, never()).undeploy(any(), anyBoolean(), anyBoolean());
    }

    /**
     * A single blocked entry rejects the whole batch, so the legitimate artifacts alongside it survive
     * untouched rather than the delete being partially applied. The message names the offending entry.
     */
    @Test
    void testOneInternalMetadataEntryBlocksTheEntireBatch() {
        mockRequest(REGULAR_PATH, INTERNAL_METADATA_PATH);
        when(securityService.isAdmin()).thenReturn(false);

        deleteArtifactsService.execute(artifactoryRestRequest, restResponse);

        assertEquals(SC_NOT_FOUND, restResponse.getResponseCode());
        assertEquals(blockedMessage(INTERNAL_METADATA_PATH), restResponse.getFeedbackMsg().getError());
        verify(repositoryService, never()).undeploy(repoPath(REGULAR_PATH), true, true);
    }

    /**
     * Admins retain the ability to delete internal metadata.
     */
    @Test
    void testDeleteInternalMetadataIsAllowedForAdmin() {
        mockRequest(INTERNAL_METADATA_PATH);
        when(securityService.isAdmin()).thenReturn(true);
        when(repositoryService.undeploy(any(), anyBoolean(), anyBoolean())).thenReturn(new BasicStatusHolder());

        deleteArtifactsService.execute(artifactoryRestRequest, restResponse);

        verify(repositoryService).undeploy(repoPath(INTERNAL_METADATA_PATH), true, true);
    }

    /**
     * With {@code delete.block.internal.metadata.enabled} off the guard is bypassed entirely, keeping the
     * block reversible in production.
     */
    @Test
    void testDeleteInternalMetadataNotBlockedWhenFlagDisabled() {
        setStringSystemProperty(ConstantValues.deleteBlockInternalMetadata, "false");
        mockRequest(INTERNAL_METADATA_PATH);
        when(repositoryService.undeploy(any(), anyBoolean(), anyBoolean())).thenReturn(new BasicStatusHolder());

        deleteArtifactsService.execute(artifactoryRestRequest, restResponse);

        verify(repositoryService).undeploy(repoPath(INTERNAL_METADATA_PATH), true, true);
    }

    private void mockRequest(String... paths) {
        DeleteArtifactsModel model = new DeleteArtifactsModel();
        model.setArtifacts(List.of(paths).stream().map(DeleteArtifactsServiceTest::artifact).toList());
        when(artifactoryRestRequest.getImodel()).thenReturn(model);
    }

    private static DeleteArtifact artifact(String path) {
        DeleteArtifact deleteArtifact = new DeleteArtifact();
        deleteArtifact.setRepoKey(REPO_KEY);
        deleteArtifact.setPath(path);
        return deleteArtifact;
    }

    private static RepoPath repoPath(String path) {
        return RepoPathFactory.create(REPO_KEY, path);
    }

    private static String blockedMessage(String path) {
        return "Internal metadata request detected for '" + repoPath(path) + "'. No artifacts were deleted.";
    }
}
