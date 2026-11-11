package org.artifactory.repo.webdav.methods;

import org.apache.http.HttpStatus;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.InternalArtifactoryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeleteMethodTest {

    @Mock
    private InternalRepositoryService repoService;
    @Mock
    private PropertiesService propertiesService;
    @Mock
    private SecurityService securityService;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void canHandle() {
        var deleteMethod = newDeleteMethod();
        assertTrue(deleteMethod.canHandle("delete"));
        assertFalse(deleteMethod.canHandle("put"));
    }

    @Test
    void handleNoRepo() throws IOException {
        DeleteMethod deleteMethod = newDeleteMethod();
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoPath()).thenReturn(RepoPathFactory.create("some/path"));
        InternalArtifactoryResponse response = new InternalArtifactoryResponse();
        deleteMethod.handle(request, response);
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
    }

    @Test
    void handleInternalMetadata() throws IOException {
        DeleteMethod deleteMethod = newDeleteMethod();
        when(repoService.localFederatedOrCachedRepositoryByKey("some")).thenReturn(mock(LocalRepo.class));
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoPath()).thenReturn(RepoPathFactory.create("some/.jfrog/path"));
        InternalArtifactoryResponse response = new InternalArtifactoryResponse();
        deleteMethod.handle(request, response);
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
        assertEquals("Internal metadata request detected for 'some:.jfrog/path'.", response.getStatusMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void handleItem(boolean atomic) throws IOException {
        DeleteMethod deleteMethod = newDeleteMethod();
        when(repoService.localFederatedOrCachedRepositoryByKey("some")).thenReturn(mock(LocalRepo.class));
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        if (atomic) {
            when(request.getParameter("atomic")).thenReturn("true");
        }
        RepoPath repoPath = RepoPathFactory.create("some/path");
        when(request.getRepoPath()).thenReturn(repoPath);
        InternalArtifactoryResponse response = new InternalArtifactoryResponse();
        if (atomic) {
            when(repoService.undeploy(repoPath)).thenReturn(new BasicStatusHolder());
        } else {
            when(repoService.undeployMultiTransaction(repoPath)).thenReturn(new BasicStatusHolder());
        }
        deleteMethod.handle(request, response);
        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());
    }

    @Test
    void handleMetadataAsAdmin() throws IOException {
        DeleteMethod deleteMethod = newDeleteMethod();
        when(repoService.localFederatedOrCachedRepositoryByKey("some")).thenReturn(mock(LocalRepo.class));
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        RepoPath repoPath = RepoPathFactory.create("some/.jfrog/path");
        when(request.getRepoPath()).thenReturn(repoPath);
        InternalArtifactoryResponse response = new InternalArtifactoryResponse();
        when(securityService.isAdmin()).thenReturn(true);
        when(repoService.undeployMultiTransaction(repoPath)).thenReturn(new BasicStatusHolder());
        deleteMethod.handle(request, response);
        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());
    }

    @Test
    void handleProperties() throws IOException {
        DeleteMethod deleteMethod = newDeleteMethod();
        when(repoService.localFederatedOrCachedRepositoryByKey("some")).thenReturn(mock(LocalRepo.class));
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        RepoPath repoPath = RepoPathFactory.create("some/path:properties");
        RepoPath itemRepoPath = RepoPathFactory.create("some/path");
        when(request.getRepoPath()).thenReturn(repoPath);
        InternalArtifactoryResponse response = new InternalArtifactoryResponse();
        when(propertiesService.removeProperties(itemRepoPath)).thenReturn(true);
        deleteMethod.handle(request, response);
        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());
    }

    @Test
    void deleteItems_handlesErrorAtomic() throws IOException {
        DeleteMethod deleteMethod = newDeleteMethod();
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getParameter("atomic")).thenReturn("true");
        RepoPath repoPath = RepoPathFactory.create("some/path");
        when(request.getRepoPath()).thenReturn(repoPath);

        InternalArtifactoryResponse response = new InternalArtifactoryResponse();

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = mock(Logger.class);
        statusHolder.error("internal error", HttpStatus.SC_INTERNAL_SERVER_ERROR, logger);

        when(repoService.undeploy(repoPath)).thenReturn(statusHolder);

        // Call deleteItems directly to exercise the branch
        deleteMethod.deleteItems(request, response, repoPath);

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertEquals("internal error", response.getStatusMessage());
    }

    @Test
    void deleteItems_handlesNotFoundNonAtomic() throws IOException {
        DeleteMethod deleteMethod = newDeleteMethod();
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        // no atomic param -> non-atomic
        RepoPath repoPath = RepoPathFactory.create("some/path");
        when(request.getRepoPath()).thenReturn(repoPath);

        InternalArtifactoryResponse response = new InternalArtifactoryResponse();

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = mock(Logger.class);
        // set a status (not an error) with 404 status code to trigger the 404 branch
        statusHolder.status("not found", HttpStatus.SC_NOT_FOUND, logger);

        when(repoService.undeployMultiTransaction(repoPath)).thenReturn(statusHolder);

        deleteMethod.deleteItems(request, response, repoPath);

        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
        assertEquals("not found", response.getStatusMessage());
    }

    @Test
    void deleteItems_handlesNotFoundAtomic() throws IOException {
        DeleteMethod deleteMethod = newDeleteMethod();
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getParameter("atomic")).thenReturn("true");
        RepoPath repoPath = RepoPathFactory.create("some/path");
        when(request.getRepoPath()).thenReturn(repoPath);

        InternalArtifactoryResponse response = new InternalArtifactoryResponse();

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = mock(Logger.class);
        // set a status (not an error) with 404 status code to trigger the 404 branch
        statusHolder.status("not found-atomic", HttpStatus.SC_NOT_FOUND, logger);

        when(repoService.undeploy(repoPath)).thenReturn(statusHolder);

        deleteMethod.deleteItems(request, response, repoPath);

        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
        assertEquals("not found-atomic", response.getStatusMessage());
    }

    private DeleteMethod newDeleteMethod() {
        var deleteMethod = new DeleteMethod();
        deleteMethod.setRepoService(repoService);
        deleteMethod.setPropertiesService(propertiesService);
        deleteMethod.setSecurityService(securityService);
        return deleteMethod;
    }
}
