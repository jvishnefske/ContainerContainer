package org.artifactory.repo.interceptor;

import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.spring.RepoConfigChangeListener;

import java.util.List;
import java.util.Map;

/**
 * @author Tamar Rosen
 */
public interface RepoConfigChangeInterceptor {

    /**
     * This API has been deprecated due to clunky and difficult usage.
     * @deprecated Implement {@link #onBeforeSave(RepoConfigChanges)} instead.
     */
    @Deprecated(since = "7.104.x")
    default void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {

    }

    /**
     * Performs an on before interception on the provided repository config changes.
     * Depending on certain conditions, might mutate the provided map by adding, updating or removing a
     * {@link RepoConfigChange}.
     * Should be implemented by interceptors agnostic to import
     *
     * @param repoConfigChanges contains A mutable map of repository configuration changes including the action, old
     *                                 and new config, grouped by repo type, import context and exposes convenience API
     */
    default void onBeforeSave(RepoConfigChanges repoConfigChanges) {
        onBeforeSave(repoConfigChanges.getMappedByType());
    }

    /**
     * Called after all {@link RepoConfigChangeListener} have been reloaded.
     *
     * @param repoConfigChanges Map of repository configuration changes including the action, old and new config,
     *                          grouped by repo type.
     */
    default void onAfterSave(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        onAfterSave(new RepoConfigChanges(repoConfigChanges, false));
    }

    default void onAfterSave(RepoConfigChanges repoConfigChanges) {

    }
}