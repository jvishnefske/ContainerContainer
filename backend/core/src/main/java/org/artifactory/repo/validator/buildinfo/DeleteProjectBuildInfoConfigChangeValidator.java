package org.artifactory.repo.validator.buildinfo;

import com.jfrog.build.acl.util.BuildInfoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.SecurityService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeValidator;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.artifactory.descriptor.repo.RepoType.BuildInfo;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoDetailsType.LOCAL;

/**
 * Validates that a build info repository cannot be deleted if it is associated with a project.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteProjectBuildInfoConfigChangeValidator implements RepoConfigChangeValidator {

    private static final String CANNOT_DELETE_PROJECT_BUILD_INFO_REPO =
            "Cannot delete build info repo: '%s' of existing project: '%s'";
    private final ProjectsService projectsService;
    private final SecurityService securityService;

    @Override
    public void validate(RepoConfigChanges repoConfigChanges) {
        List<RepoConfigChange> buildInfoRepoDeleteChanges = repoConfigChanges.filterBy(REPO_DELETE, LOCAL, BuildInfo);
        if (!buildInfoRepoDeleteChanges.isEmpty()) {
            for (RepoConfigChange buildinfoDeleteChange : buildInfoRepoDeleteChanges) {
                String buildInfoRepoKey = buildinfoDeleteChange.getRepoKey();
                String projectKey = BuildInfoUtils.getAssociatedProjectKey(buildInfoRepoKey);
                if (StringUtils.isNotBlank(projectKey) && projectContainsAnyRepositories(projectKey)) {
                    String warnMsg = CANNOT_DELETE_PROJECT_BUILD_INFO_REPO.formatted(buildInfoRepoKey, projectKey);
                    throw new ConfigurationException(warnMsg);
                }
            }
        }
    }

    private boolean projectContainsAnyRepositories(String projectKey) {
        Set<String> projectRepositoriesKeys = projectsService.getProjectRepositoriesKeys(projectKey, true);
        return !projectRepositoriesKeys.isEmpty();
    }
}