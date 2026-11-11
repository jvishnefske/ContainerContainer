package org.artifactory.ui.rest.service.artifacts.deploy;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactUploadServiceTest {
    @Spy
    private ArtifactUploadService artifactUploadService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    private AutoCloseable closable;

    @BeforeEach
    public void before() {
        this.closable = MockitoAnnotations.openMocks(this);
        when(securityService.currentUsername()).thenReturn("user");
        ReflectionTestUtils.setField(artifactUploadService, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(artifactUploadService, "securityService", securityService);

    }

    @AfterEach
    public void after() throws Exception {
        this.closable.close();
    }

    @Test
    void testNoDeployableRepo() {
        when(repositoryService.hasAnyDeployableRepo()).thenReturn(false);
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        artifactUploadService.execute(mock(ArtifactoryRestRequest.class), response);
        assertTrue(response.getFeedbackMsg().getError().contains("has no deploy permissions on any local repository"));
    }
}
