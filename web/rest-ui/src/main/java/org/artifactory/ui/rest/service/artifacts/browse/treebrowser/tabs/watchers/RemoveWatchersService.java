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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.watchers;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.watch.ArtifactWatchAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.watchers.DeleteWatcher;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.watchers.DeleteWatchersModel;
import org.jfrog.common.StreamSupportUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Chen Keinan
 */
@Component
public class RemoveWatchersService<T extends DeleteWatchersModel> implements RestService<T> {

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        Map<String, RepoPath> watchRepoPathMap = createRepoPathPerWatch(request);
        removeWatchers(watchRepoPathMap);
        successResponse(response, watchRepoPathMap);
    }

    private Map<String, RepoPath> createRepoPathPerWatch(ArtifactoryRestRequest<T> request) {
        T imodel = request.getImodel();
        return StreamSupportUtils.stream(imodel.getWatches())
                .collect(Collectors.toMap(DeleteWatcher::getName,
                        watcher -> InternalRepoPathFactory.create(watcher.getRepoKey(), watcher.getPath())));

    }

    private void removeWatchers(Map<String, RepoPath> repoPathMap) {
        ArtifactWatchAddon artifactWatchAddon = ContextHelper.get().beanForType(AddonsManager.class).addonByType(
                ArtifactWatchAddon.class);
        artifactWatchAddon.removeWatcherAsManager(repoPathMap);
    }

    private void successResponse(RestResponse response, Map<String, RepoPath> repoPathMap) {
        if (repoPathMap.size() > 1) {
            response.info("Successfully removed " + repoPathMap.size() + " watchers");
        } else if (repoPathMap.size() == 1) {
            String watchName = StreamSupportUtils.mapEntriesStream(repoPathMap)
                    .iterator().next().getKey();
            response.info("Successfully removed watcher '" + watchName + "'");
        }
    }

}