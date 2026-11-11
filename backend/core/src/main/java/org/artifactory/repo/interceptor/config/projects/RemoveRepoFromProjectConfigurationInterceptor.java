package org.artifactory.repo.interceptor.config.projects;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.exception.AccessClientStateException;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;

/**
 * Handles repository removal from Projects (managed in Access) for local, remote and virtual repositories
 *
 * @author andreik
 */
@Slf4j
@Component
public class RemoveRepoFromProjectConfigurationInterceptor implements RepoConfigChangeInterceptor {

    private final ProjectsService projectsService;

    @Autowired
    public RemoveRepoFromProjectConfigurationInterceptor(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        onBeforeSave(new RepoConfigChanges(mutableRepoConfigChanges));
    }

    @Override
    public void onBeforeSave(RepoConfigChanges repoConfigChanges) {
        if (isRemoveRepoFlowV1()) {
            removeDeletedRepositoriesFromProjects(repoConfigChanges);
        }
    }

    private boolean isRemoveRepoFlowV1() {
        return !ConstantValues.removeRepositoryFlowV2Enabled.getBoolean();
    }

    private void removeDeletedRepositoriesFromProjects(RepoConfigChanges repoConfigChanges) {
        repoConfigChanges.filterBy(REPO_DELETE)
                .forEach(repoConfigChange -> removeRepositoryFromProjects(repoConfigChange.getRepoKey(),
                        repoConfigChange.getRepoType()));
    }

    private void removeRepositoryFromProjects(String repoKey, RepoDetailsType repoType) {
        try {
            // for every project remove the repo
            log.debug("Repo '{}' from type '{}' was deleted, removing from Access", repoKey, repoType);
            projectsService.removeRepositoryFromCurrentProject(repoKey);
        } catch (AccessClientStateException e) {
            log.warn("Failed to remove repo '{}' from a project association: {}. Will be handled by " +
                    "SyncResourcesWithAccessJob", repoKey, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to remove repo '{}' from a project association: {}", repoKey, e.getMessage());
            log.debug("Failed to remove repo '{}' from a project association: {}", repoKey, e.getMessage(), e);
        }
    }
}