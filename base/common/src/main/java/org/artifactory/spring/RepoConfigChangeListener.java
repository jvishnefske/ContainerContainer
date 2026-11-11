package org.artifactory.spring;

import org.artifactory.repo.ReferencedConfigChange;
import org.artifactory.repo.RepoConfigReload;
import org.artifactory.repo.RepoConfigChangesReload;
import org.artifactory.repo.RepoDetailsType;

import java.util.List;
import java.util.Map;

/**
 * Listen and act on repository configuration related changes. A change can be direct repository configuration change
 * or repository creation/update/deletion or referenced configuration change, such as proxies, layouts, property sets
 * etc.
 *
 * @author Mor Merhav
 */
public interface RepoConfigChangeListener {

    /**
     * Listens on repository configuration changes (repository create/update/delete actions).
     *
     * @param repoConfigChangesReload contains repository configuration changes including the action, old and new config
     */
    default void onRepoConfigReload(RepoConfigChangesReload repoConfigChangesReload) {
        onRepoConfigReload(repoConfigChangesReload.toRepoConfigReloadMap());
    }

    /**
     * This API has been deprecated due to clunky and difficult usage.
     *
     * @deprecated Implement {@link #onRepoConfigReload(RepoConfigChangesReload)} instead.
     */
    @Deprecated(since = "7.109.x")
    default void onRepoConfigReload(Map<RepoDetailsType, List<RepoConfigReload>> repoConfigReload) {

    }

    /**
     * Reload repositories on system configuration changes (proxy, layouts, property sets etc). This is triggered
     * only if the reference key remain the same but its actual content was changed
     *
     * @param affectedRepos List of repository types to the actual change for which are affected by the change
     */
    default void reloadOnReferencedConfigChange(List<ReferencedConfigChange> affectedRepos) {

    }
}