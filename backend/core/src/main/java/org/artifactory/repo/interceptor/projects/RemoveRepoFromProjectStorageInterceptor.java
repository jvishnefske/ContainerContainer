package org.artifactory.repo.interceptor.projects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.exception.AccessClientStateException;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.springframework.stereotype.Component;

/**
 * Handles repository removal from Projects (managed in Access) for local, remote and virtual repositories
 * Occurs after all the repository content was deleted, otherwise content deletion would have failed due to insufficient
 * permissions
 *
 * @author Mor Merhav
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoveRepoFromProjectStorageInterceptor extends StorageInterceptorAdapter {

    private final ProjectsService projectsService;

    @Override
    public void afterRepositoryContentDelete(String repoKey, MutableStatusHolder statusHolder) {
        try {
            log.info("Repo: '{}' was deleted, removing from project", repoKey);
            projectsService.removeRepositoryFromCurrentProject(repoKey);
        } catch (AccessClientStateException e) {
            statusHolder.warn(
                    ("Failed to remove repo '%s' from a project association: {}. Will be handled by " +
                            "SyncResourcesWithAccessJob").formatted(repoKey), e, log);
            throw e;
        } catch (Exception e) {
            statusHolder.warn("Failed to remove repo '%s' from a project association: %s"
                    .formatted(repoKey, e.getMessage()), e, log);
            throw e;
        }
    }
}