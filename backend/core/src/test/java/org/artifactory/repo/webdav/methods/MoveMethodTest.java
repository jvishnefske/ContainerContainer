package org.artifactory.repo.webdav.methods;

import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.InternalArtifactoryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoveMethodTest {

    private static final String CONTEXT_URL = "http://localhost/ctx";

    @Mock
    private AuthorizationService authService;
    @Mock
    private InternalRepositoryService repoService;
    @InjectMocks
    private MoveMethod moveMethod;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactorySystemProperties properties = mock(ArtifactorySystemProperties.class);
        when(properties.getBooleanProperty(ConstantValues.moveCopyBlockInternalMetadataTarget)).thenReturn(true);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(properties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryHome.unbind();
    }

    @Test
    void canHandle() {
        assertTrue(moveMethod.canHandle("move"));
        assertFalse(moveMethod.canHandle("put"));
    }

    @Test
    void handleInternalMetadataTargetBlocked() throws IOException {
        InternalArtifactoryResponse response = handleMoveTo("http://localhost/ctx/target-repo/.jfrog/evil.txt");

        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
        assertEquals("Internal metadata request blocked", response.getStatusMessage());
    }

    @Test
    void handleLeadingSlashInternalMetadataTargetBlocked() throws IOException {
        // A destination that only normalizes to .jfrog/... after slash-trimming must still be blocked.
        InternalArtifactoryResponse response = handleMoveTo("http://localhost/ctx//target-repo//.jfrog/evil.txt");

        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
        assertEquals("Internal metadata request blocked", response.getStatusMessage());
    }

    @Test
    void handleNonInternalMetadataTargetPassesGuard() throws IOException {
        // The guard must not block a normal target: flow reaches the permission check, which denies with 403.
        when(authService.canDelete(any())).thenReturn(false);

        InternalArtifactoryResponse response = handleMoveTo("http://localhost/ctx/target-repo/normal/file.txt");

        assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatus());
    }

    private InternalArtifactoryResponse handleMoveTo(String destination) throws IOException {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoPath()).thenReturn(RepoPathFactory.create("src-repo/path/file.txt"));
        when(request.getHeader("Destination")).thenReturn(destination);
        when(request.getServletContextUrl()).thenReturn(CONTEXT_URL);
        InternalArtifactoryResponse response = new InternalArtifactoryResponse();
        moveMethod.handle(request, response);
        return response;
    }
}
