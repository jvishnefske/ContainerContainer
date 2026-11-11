package org.artifactory.repo.confighandler.manager;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.ReferencedConfigChange;
import org.artifactory.repo.confighandler.ConfigChangeEventContext;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.RepoConfigChangeListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * @author Mor Merhav
 * Standard strategy to be used by
 * {@link org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl}.
 * Performs update via {@link org.artifactory.api.repo.RepositoryService} and reload via
 * {@link InternalRepositoryService} and notifies listeners
 * {@link RepoConfigChangeListener#reloadOnReferencedConfigChange(List)}
 * @see org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl
 * @see InternalRepositoryService
 * @see RepoConfigChangeListener
 */
class StandardUpdateOrReloadStrategy implements UpdateOrReloadStrategy {

    private final InternalRepositoryService repositoryService;
    private Collection<RepoConfigChangeListener> repoConfigChangeListeners;

    StandardUpdateOrReloadStrategy(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
        this.repoConfigChangeListeners = new ArrayList<>();
    }

    @Override
    public void updateOrReload(ConfigChangeEventContext context) {
        updateRepoConfigsIfNeeded(context);
        reloadRepoIfNeeded(context);
    }

    @Override
    public void init() {
        this.repoConfigChangeListeners = new HashSet<>(
                ContextHelper.get().beansForType(RepoConfigChangeListener.class).values());
    }

    /**
     * Update repositories after a config descriptor change
     */
    private void updateRepoConfigsIfNeeded(ConfigChangeEventContext configChangeEventContext) {
        Map<String, RepoConfig> reposToUpdate = configChangeEventContext.getReposToUpdate();
        if (reposToUpdate != null && !reposToUpdate.isEmpty()) {
            repositoryService.bulkUpsertRepoConfigs(new ArrayList<>(reposToUpdate.values()), true);
        }
    }

    /**
     * Reload repositories after a config descriptor change which does not require repo-config changes
     */
    private void reloadRepoIfNeeded(ConfigChangeEventContext configChangeEventContext) {
        if (configChangeEventContext.getReposToReload().isEmpty()) {
            return;
        }
        List<ReferencedConfigChange> referencedConfigChanges = configChangeEventContext.getReposToReload().entrySet()
                .stream()
                .map(entry -> new ReferencedConfigChange(entry.getKey(), entry.getValue()))
                .toList();

        // Reload the repositories first
        repositoryService.reloadOnReferencedConfigChange(referencedConfigChanges);
        repoConfigChangeListeners.stream()
                .filter(listener -> !(listener instanceof InternalRepositoryService))
                .forEach(listener -> listener.reloadOnReferencedConfigChange(referencedConfigChanges));
    }
}