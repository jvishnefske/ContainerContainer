package org.artifactory.repo.validator.buildinfo;

import org.artifactory.api.security.SecurityService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.artifactory.descriptor.repo.RepoType.BuildInfo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteProjectBuildInfoConfigChangeValidatorTest {

    @Mock
    private ProjectsService projectsService;
    @Mock
    private SecurityService securityService;
    @InjectMocks
    private DeleteProjectBuildInfoConfigChangeValidator validator;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void validate_cannotDeleteBuildInfoRepoOfExistingProject() {
        String buildInfoRepoKey = "project-build-info";
        String projectKey = "project";
        RepoConfigChange change = RepoConfigChange.deleteOf(LocalRepoConfig.create(buildInfoRepoKey, BuildInfo));
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(change);

        when(projectsService.getProjectRepositoriesKeys(projectKey, true)).thenReturn(Set.of("repo1"));

        ConfigurationException configurationException =
                assertThrows(ConfigurationException.class, () -> validator.validate(repoConfigChanges));

        assertEquals(SC_BAD_REQUEST, configurationException.getStatusCode());
        assertEquals(
                "Cannot delete build info repo: '%s' of existing project: '%s'".formatted(buildInfoRepoKey, projectKey),
                configurationException.getMessage());
    }

    @Test
    void validate_ableToDeleteBuildInfoRepoProjectAlreadyDeleted() {
        String buildInfoRepoKey = "project-build-info";
        String projectKey = "project";
        RepoConfigChange change = RepoConfigChange.deleteOf(LocalRepoConfig.create(buildInfoRepoKey, BuildInfo));
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(change);

        when(projectsService.getProjectRepositoriesKeys(projectKey, true)).thenReturn(Set.of());

        assertDoesNotThrow(() -> validator.validate(repoConfigChanges));

        verify(projectsService).getProjectRepositoriesKeys(projectKey, true);
    }

    @Test
    void validate_ableToDeleteNonProjectRelatedRepo() {
        String buildInfoRepoKey = "nonProjectRepo";
        String projectKey = "project";
        RepoConfigChange change = RepoConfigChange.deleteOf(LocalRepoConfig.create(buildInfoRepoKey, BuildInfo));
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(change);

        when(projectsService.getProjectRepositoriesKeys(projectKey, true)).thenReturn(Set.of());

        assertDoesNotThrow(() -> validator.validate(repoConfigChanges));
    }

    @Test
    void validate_noBuildInfoRepoIsDeleted_noException() {
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(List.of());
        assertDoesNotThrow(() -> validator.validate(repoConfigChanges));
    }
}