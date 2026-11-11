package org.artifactory.repo.interceptor;

import org.artifactory.repo.RepoConfigChange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;

/**
 * Integration point for repo CUD events on the repository config save lifecycle ({@code RepositoryConfigService}).
 * {@code onBeforeSave} fires synchronous, blocking BEFORE events; {@code onAfterSave} fires asynchronous AFTER events.
 * Emission policy and payload construction are delegated to {@link RepoConfigCudEventEmitter}.
 */
@Component
public class EventRepoConfigChangeInterceptor implements RepoConfigChangeInterceptor {

    private final RepoConfigCudEventEmitter eventEmitter;

    @Autowired
    public EventRepoConfigChangeInterceptor(RepoConfigCudEventEmitter eventEmitter) {
        this.eventEmitter = eventEmitter;
    }

    @Override
    public void onBeforeSave(RepoConfigChanges repoConfigChanges) {
        for (RepoConfigChange change : repoConfigChanges.filterBy(REPO_CREATE, REPO_UPDATE, REPO_DELETE)) {
            eventEmitter.emitBefore(change);
        }
    }

    @Override
    public void onAfterSave(RepoConfigChanges repoConfigChanges) {
        for (RepoConfigChange change : repoConfigChanges.filterBy(REPO_CREATE, REPO_UPDATE, REPO_DELETE)) {
            eventEmitter.emitAfter(change);
        }
    }
}
