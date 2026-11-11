package org.artifactory.projects.repo;

import org.artifactory.api.repo.RepoActionType;
import org.artifactory.api.security.SecurityService;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.exception.http.BadRequestException;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProjectRepoPermissionsHelper}
 *
 * @author Oleksandr Legenkiy
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectRepoPermissionsHelperTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private ProjectsService projectsService;

    private ProjectRepoPermissionsHelper permissionsHelper;

    @BeforeEach
    void setUp() {
        permissionsHelper = new ProjectRepoPermissionsHelper(securityService, projectsService);
    }

    @Test
    void validateAnyProjectAdmin_shouldNotThrow_whenUserIsProjectAdmin() {
        // Arrange
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        // Act & Assert - should not throw
        permissionsHelper.validateAnyProjectAdmin();
    }

    @Test
    void validateAnyProjectAdmin_shouldThrowAuthorizationException_whenUserIsNotProjectAdmin() {
        // Arrange
        when(securityService.isAnyProjectAdmin()).thenReturn(false);
        // Act & Assert
        AuthorizationRestException exception = assertThrows(AuthorizationRestException.class,
                () -> permissionsHelper.validateAnyProjectAdmin());
        assertEquals(403, exception.getResponse().getStatus());
        assertEquals("Only platform/project admins are allowed to update repositories",
                exception.getResponse().getEntity());
    }

    @Test
    void projAdminNotAuthorizedToUpdateRepo_shouldReturnFalse_whenNoProjChangeAndHasPermToCurrentProj() {
        // Arrange
        RepoConfig repo = createRepoConfig("repo1", RepoDetailsType.LOCAL);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", RepoDetailsType.LOCAL))
                .thenReturn(true);
        // Act
        boolean result = permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, null);
        // Assert
        assertFalse(result);
    }

    @Test
    void projAdminNotAuthorizedToUpdateRepo_shouldReturnTrue_whenUserLacksPermissionInCurrentProject() {
        // Arrange
        RepoConfig repo = createRepoConfig("repo1", RepoDetailsType.REMOTE);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", RepoDetailsType.REMOTE))
                .thenReturn(false);
        // Act
        boolean result = permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, "projectA");
        // Assert
        assertTrue(result);
    }

    @Test
    void projAdminNotAuthorizedToUpdateRepo_shouldReturnFalse_whenInputProjectKeyIsBlank() {
        // Arrange
        RepoConfig repo = createRepoConfig("repo1", RepoDetailsType.LOCAL);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", RepoDetailsType.LOCAL))
                .thenReturn(true);
        // Act
        boolean result = permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, "  ");
        // Assert
        assertFalse(result);
    }

    @Test
    void projAdminNotAuthorizedToUpdateRepo_shouldReturnFalse_whenProjectKeysMatchCaseInsensitive() {
        // Arrange
        RepoConfig repo = createRepoConfig("repo1", RepoDetailsType.LOCAL);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", RepoDetailsType.LOCAL))
                .thenReturn(true);
        when(projectsService.getProjectKeyOfRepository("repo1")).thenReturn("projectA");
        // Act
        boolean result = permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, "PROJECTA");
        // Assert
        assertFalse(result);
    }

    @Test
    void projAdminNotAuthorizedToUpdateRepo_shouldThrowBadRequestException_whenTargetProjectDoesNotExist() {
        // Arrange
        RepoConfig repo = createRepoConfig("repo1", RepoDetailsType.LOCAL);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", RepoDetailsType.LOCAL))
                .thenReturn(true);
        when(projectsService.getProjectKeyOfRepository("repo1")).thenReturn("projectA");
        when(projectsService.getProjectSpec("nonExistentProject")).thenReturn(null);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.DELETE, "repo1", "projectA",
                RepoDetailsType.LOCAL)).thenReturn(true);
        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, "nonExistentProject"));
        assertEquals("Target project 'nonExistentProject' does not exist. Cannot update repo 'repo1'",
                exception.getMessage());
    }

    @Test
    void projAdminNotAuthorizedToUpdateRepo_shouldReturnFalse_whenUserHasAllNeededPermissions() {
        // Arrange
        RepoConfig repo = createRepoConfig("repo1", RepoDetailsType.REMOTE);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", RepoDetailsType.REMOTE))
                .thenReturn(true);
        when(projectsService.getProjectKeyOfRepository("repo1")).thenReturn("projectA");
        ProjectSlimEntity projectSlimEntityMock = mock(ProjectSlimEntity.class);
        when(projectsService.getProjectSpec("projectB")).thenReturn(projectSlimEntityMock);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.CREATE, "repo1", "projectB",
                RepoDetailsType.REMOTE)).thenReturn(true);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.DELETE, "repo1", "projectA",
                RepoDetailsType.REMOTE)).thenReturn(true);
        // Act
        boolean result = permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, "projectB");
        // Assert
        assertFalse(result);
    }

    @Test
    void projAdminNotAuthorizedToUpdateRepo_shouldReturnTrue_whenUserHasNoDeleteOnSourceProj() {
        // Arrange
        RepoConfig repo = createRepoConfig("repo1", RepoDetailsType.REMOTE);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", RepoDetailsType.REMOTE))
                .thenReturn(true);
        when(projectsService.getProjectKeyOfRepository("repo1")).thenReturn("projectA");
        ProjectSlimEntity projectSlimEntityMock = mock(ProjectSlimEntity.class);
        when(projectsService.getProjectSpec("projectB")).thenReturn(projectSlimEntityMock);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.CREATE, "repo1", "projectB",
                RepoDetailsType.REMOTE)).thenReturn(true);
        // Act
        boolean result = permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, "projectB");
        // Assert
        assertTrue(result);
    }

    @Test
    void projAdminNotAuthorizedToUpdateRepo_shouldReturnTrue_whenUserLacksPermissionInTargetProject() {
        // Arrange
        RepoConfig repo = createRepoConfig("repo1", RepoDetailsType.REMOTE);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", RepoDetailsType.REMOTE))
                .thenReturn(true);
        when(projectsService.getProjectKeyOfRepository("repo1")).thenReturn("projectA");
        ProjectSlimEntity projectSlimEntityMock = mock(ProjectSlimEntity.class);
        when(projectsService.getProjectSpec("projectB")).thenReturn(projectSlimEntityMock);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", "projectB",
                RepoDetailsType.REMOTE)).thenReturn(false);
        // Act
        boolean result = permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, "projectB");
        // Assert
        assertTrue(result);
    }

    @ParameterizedTest
    @MethodSource("provideBlankAndNullProjectKeyScenarios")
    void projAdminNotAuthorizedToUpdateRepo_shouldReturnFalse_whenProjectKeyIsBlankOrNull(String inputProjectKey) {
        // Arrange
        RepoConfig repo = createRepoConfig("repo1", RepoDetailsType.LOCAL);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", RepoDetailsType.LOCAL))
                .thenReturn(true);
        // Act
        boolean result = permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, inputProjectKey);
        // Assert
        assertFalse(result);
    }

    @ParameterizedTest
    @MethodSource("provideCrossProjectMoveScenarios")
    void projAdminNotAuthorizedToUpdateRepo_shouldHandleCrossProjectMoves_basedOnTargetProjPermissions(
            String currentProjectKey, String targetProjectKey, boolean targetProjectExists,
            boolean hasTargetProjectPermission, boolean expectedUnauthorized) {
        // Arrange
        RepoConfig repo = createRepoConfig("repo1", RepoDetailsType.REMOTE);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, "repo1", RepoDetailsType.REMOTE))
                .thenReturn(true);
        when(projectsService.canDoToProjectRepoResource(RepoActionType.DELETE, "repo1", "projectA",
                RepoDetailsType.REMOTE)).thenReturn(true);
        when(projectsService.getProjectKeyOfRepository("repo1")).thenReturn(currentProjectKey);
        if (targetProjectExists) {
            ProjectSlimEntity projectSlimEntityMock = mock(ProjectSlimEntity.class);
            when(projectsService.getProjectSpec(targetProjectKey)).thenReturn(projectSlimEntityMock);
            when(projectsService.canDoToProjectRepoResource(RepoActionType.CREATE, "repo1", targetProjectKey,
                    RepoDetailsType.REMOTE)).thenReturn(hasTargetProjectPermission);
        } else {
            when(projectsService.getProjectSpec(targetProjectKey)).thenReturn(null);
        }
        // Act
        boolean result;
        if (!targetProjectExists) {
            assertThrows(BadRequestException.class,
                    () -> permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, targetProjectKey));
            return;
        } else {
            result = permissionsHelper.projAdminNotAuthorizedToUpdateRepo(repo, targetProjectKey);
        }
        // Assert
        assertEquals(expectedUnauthorized, result);
    }

    private RepoConfig createRepoConfig(String key, RepoDetailsType repoType) {
        RepoConfig config;
        if (repoType == RepoDetailsType.LOCAL) {
            config = mock(LocalRepoConfig.class);
        } else {
            config = mock(RemoteRepoConfig.class);
        }
        when(config.getKey()).thenReturn(key);
        when(config.getRepoType()).thenReturn(repoType);
        return config;
    }

    private static Stream<Arguments> provideBlankAndNullProjectKeyScenarios() {
        return Stream.of(
                // Null project key
                Arguments.of((String) null),
                // Blank project key
                Arguments.of(""),
                // Whitespace-only project key
                Arguments.of("   ")
        );
    }

    private static Stream<Arguments> provideCrossProjectMoveScenarios() {
        return Stream.of(
                // Cross-project move: target exists, has permission
                Arguments.of("projectA", "projectB", true, true, false),
                // Cross-project move: target exists, lacks permission
                Arguments.of("projectA", "projectB", true, false, true),
                // Cross-project move: target does not exist
                Arguments.of("projectA", "nonExistent", false, false, true)
        );
    }
}

