package org.artifactory.rest.resource.maven;

import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.maven.MavenMetadataService;
import org.artifactory.api.maven.MavenService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class MavenResourceTest {

    private static final String MAVEN_GAV_PATH = "mvnbfla/com/test/proj";

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private MavenMetadataService mavenMetadataService;
    @Mock
    private MavenService mavenService;
    @Mock
    private AuthorizationService authorizationService;

    private MavenResource mavenResource;
    private AutoCloseable mocks;

    @BeforeEach
    void init() {
        mocks = openMocks(this);
        mavenResource = new MavenResource(addonsManager, repositoryService, mavenMetadataService, mavenService,
                authorizationService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void calculateMetadata_whenUserCannotDeploy_returnsForbidden() {
        when(repositoryService.exists(any(RepoPath.class))).thenReturn(true);
        when(authorizationService.canDeploy(any(RepoPath.class))).thenReturn(false);

        Response response = mavenResource.calculateMetadata(MAVEN_GAV_PATH, false);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(mavenMetadataService, never()).calculateMavenMetadataAsync(any());
    }

    @Test
    void calculateMetadata_whenUserCanDeploy_dispatchesCalculation() {
        when(repositoryService.exists(any(RepoPath.class))).thenReturn(true);
        when(authorizationService.canDeploy(any(RepoPath.class))).thenReturn(true);
        LocalConfig localRepo = mock(LocalConfig.class);
        when(localRepo.getPackageType()).thenReturn(RepoType.Maven);
        when(repositoryService.localOrFederatedRepoConfigByKey("mvnbfla")).thenReturn(localRepo);

        Response response = mavenResource.calculateMetadata(MAVEN_GAV_PATH, false);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(mavenMetadataService).calculateMavenMetadataAsync(any());
    }
}
