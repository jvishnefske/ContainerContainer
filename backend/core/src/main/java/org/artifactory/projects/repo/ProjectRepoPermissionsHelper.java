package org.artifactory.projects.repo;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.api.security.SecurityService;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.exception.http.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class provides methods to check/validate that user has sufficient project-related permissions to update
 * a repository.
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
@Component
public class ProjectRepoPermissionsHelper {

    private static final String PROJ_ADMIN_VALIDATION_MESSAGE =
            "Only platform/project admins are allowed to update repositories";

    private final SecurityService securityService;
    private final ProjectsService projectsService;

    @Autowired
    public ProjectRepoPermissionsHelper(SecurityService securityService, ProjectsService projectsService) {
        this.securityService = securityService;
        this.projectsService = projectsService;
    }

    /**
     * Determines whether a project admin is not authorized to update a repository based on their permissions
     * in the current project and, if applicable, in the target project for cross-project moves.
     *
     * @param existingRepoConfig the current configuration of the repository to be updated; must not be null
     * @param inputProjectKey the target project key from the update request; may be null or blank to indicate
     *                        no project change (same-project update)
     * @return {@code true} if the project admin is not authorized to perform the update (lacks permissions);
     *         {@code false} if the project admin is authorized to perform the update
     * @throws BadRequestException if the repository is being moved to a different project and the target
     *                             project does not exist
     */
    public boolean projAdminNotAuthorizedToUpdateRepo(@Nonnull RepoConfig existingRepoConfig,
            @Nullable String inputProjectKey) {
        // Check project admins permissions to manage repo resource under currently configured project
        if (!projectsService.canDoToProjectRepoResource(RepoActionType.UPDATE, existingRepoConfig.getKey(),
                existingRepoConfig.getRepoType())) {
            log.debug("User lacks permission to manage repo '{}' in current project", existingRepoConfig.getKey());
            return true;
        }
        if (StringUtils.isBlank(inputProjectKey)) {
            return false; // No projectKey specified in input - treating as same-project update
        }
        // Check permissions if the repo is re-assigned to a different project
        String currentProjectKey = projectsService.getProjectKeyOfRepository(existingRepoConfig.getKey());
        if (!Strings.CI.equals(inputProjectKey, currentProjectKey)) {
            // Check delete permission on source repo project resource
            if (!projectsService.canDoToProjectRepoResource(RepoActionType.DELETE,
                    existingRepoConfig.getKey(), currentProjectKey, existingRepoConfig.getRepoType())) {
                log.debug("User lacks permission to delete repo '{}' from project '{}'", existingRepoConfig.getKey(),
                        currentProjectKey);
                return true;
            }
            // Make sure target project exists
            if (projectsService.getProjectSpec(inputProjectKey) == null) {
                String errorMessage = "Target project '%s' does not exist. Cannot update repo '%s'"
                        .formatted(inputProjectKey, existingRepoConfig.getKey());
                log.warn(errorMessage);
                throw new BadRequestException(errorMessage);
            }
            // Check if project admin has permissions to move repo to the target project
            boolean lacksTargetProjPermission = !projectsService.canDoToProjectRepoResource(RepoActionType.CREATE,
                    existingRepoConfig.getKey(), inputProjectKey, existingRepoConfig.getRepoType());
            if (lacksTargetProjPermission) {
                log.debug("User lacks permission to move repo '{}' to project '{}'", existingRepoConfig.getKey(),
                        inputProjectKey);
            }
            return lacksTargetProjPermission;
        }
        return false;
    }

    /**
     * Validates that the current user is a project admin (either platform admin or project admin).
     *
     * @throws AuthorizationRestException if the current user is neither
     *                                    a platform admin nor a project admin in any project
     */
    public void validateAnyProjectAdmin() {
        if (!securityService.isAnyProjectAdmin()) {
            log.warn(PROJ_ADMIN_VALIDATION_MESSAGE);
            throw new AuthorizationRestException(PROJ_ADMIN_VALIDATION_MESSAGE);
        }
    }
}
