package org.artifactory.repo.confighandler.manager;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoConfigReload;
import org.artifactory.repo.confighandler.ConfigChangeEventContext;
import org.artifactory.repo.service.RepoConfigBootstrapStorageHelper;
import org.artifactory.security.SystemAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * @author Mor Merhav
 *
 * System startup strategy update strategy used by
 * {@link org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl}.
 * If there are changes performs the following:
 * 1) Performs update via {@link RepoConfigBootstrapStorageHelper}
 * 2) Reloads repo cache.
 * 3) Propagates the repo changes to HA nodes.
 * Otherwise, exits.
 */
@SuppressWarnings("ClassCanBeRecord")
class SystemStartupUpdateOrReloadStrategy implements UpdateOrReloadStrategy {

    private final RepoConfigBootstrapStorageHelper storageHelper;

    public SystemStartupUpdateOrReloadStrategy(RepoConfigBootstrapStorageHelper storageHelper) {
        this.storageHelper = storageHelper;
    }

    @Override
    public void updateOrReload(ConfigChangeEventContext context) {
        Map<String, RepoConfig> reposToUpdateMap = context.getReposToUpdate();
        if (reposToUpdateMap.isEmpty()) {
            return;
        }
        Set<RepoConfig> repoConfigsToUpdate = new HashSet<>(reposToUpdateMap.values());
        updateRepos(repoConfigsToUpdate);
        storageHelper.reloadRepoConfigurations();
        propagateChangesToHaNodes(repoConfigsToUpdate);
    }

    @Override
    public void init() {
        // noop
    }

    private void propagateChangesToHaNodes(Set<RepoConfig> repoConfigsToUpdate) {
        RepoConfigPropagationModel propagationModel = new RepoConfigPropagationModel();
        propagationModel.setRepoConfigCreateOrUpdate(
                repoConfigsToUpdate.stream()
                        .map(re -> new RepoConfigReload(re.getKey(), re.getRepoType(), re.getPackageType(),
                                RepoConfigChange.ConfigAction.REPO_CREATE, re.getModification())).collect(
                                toSet()));
        ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaAddon.class)
                .propagateRepoConfigsChanges(propagationModel);
    }

    private void updateRepos(Set<RepoConfig> repoConfigsToUpdate) {
        doAsSystem(() -> storageHelper.bulkUpdateRepositoryConfigs(repoConfigsToUpdate));
    }

    private void doAsSystem(Runnable runnable) {
        Authentication originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(new SystemAuthenticationToken());
            runnable.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(originalAuthentication);
        }
    }
}
