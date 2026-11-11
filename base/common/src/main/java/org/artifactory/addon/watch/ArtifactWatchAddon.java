/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.addon.watch;

import org.artifactory.common.addon.Addon;
import org.artifactory.fs.WatchersInfo;
import org.artifactory.repo.RepoPath;
import org.jfrog.security.util.Pair;

import java.util.Map;

/**
 * @author mamo
 */
public interface ArtifactWatchAddon extends Addon {

    default Map<RepoPath, WatchersInfo> getAllWatchers(RepoPath repoPath) {
        return null;
    }

    /**
     * remove watcher by name and repo path
     *
     * @param repoPath  - artifact repo path
     * @param watchUser - watcher name
     */
    default void removeWatcher(RepoPath repoPath, String watchUser) {
    }

    /**
     * remove watchers in bulk, requires manage permission on each repo path
     *
     * @param repoPathMap - map of watcher name to artifact repo path
     */
    default void removeWatcherAsManager(Map<String, RepoPath> repoPathMap) {
    }

    /**
     * add watcher by name and repo path
     *
     * @param repoPath        - artifact repo path
     * @param watcherUsername - watcher name
     */
    default void addWatcher(RepoPath repoPath, String watcherUsername) {
    }

    default Pair<RepoPath, WatchersInfo> getNearestWatchDefinition(RepoPath repoPath, String userName) {
        return null;
    }

    /**
     * get watches for repo path
     *
     * @param repoPath - repo path
     * @return watches for repo path
     */
    default WatchersInfo getWatchers(RepoPath repoPath) {
        return null;
    }

    /**
     * Returns the watch status for the current session user on the given path.
     *
     * <p>This method couples the authorization decision and the membership lookup: callers
     * cannot learn whether a user is watching without the auth check running. Use this in
     * all read-only UI/REST flows that need to render Watch/Unwatch affordances.
     *
     * <p>OSS default returns {@link WatchStatus#NOT_ALLOWED} (addon not loaded).
     */
    default WatchStatus getWatchStatus(RepoPath repoPath) {
        return WatchStatus.NOT_ALLOWED;
    }
}
