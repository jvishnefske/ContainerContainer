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

import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.watch.ArtifactWatchAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.model.artifact.BaseArtifact;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Chen Keinan
 */
@Component
public class WatchStatusService implements RestService {

    @Autowired
    private RepositoryService repositoryService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        ArtifactWatchAddon watchAddon = ContextHelper.get().beanForType(AddonsManager.class).addonByType(
                ArtifactWatchAddon.class);
        String path = request.getQueryParamByKey("path");
        String repoKey = request.getQueryParamByKey("repoKey");
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        LocalRepoDescriptor localRepoDescriptor = repositoryService.localFederatedOrCachedRepoDescriptorByKey(repoKey);
        if (localRepoDescriptor != null && addonsManager.isAddonSupported(AddonType.WATCH)) {
            // get watch status
            BaseArtifact watchStatus = getWatchStatus(watchAddon, repoPath);
            // update status
            response.iModel(watchStatus);
        }
    }

    /**
     * return watch status
     *
     * @param watchAddon - watch add onn
     * @param repoPath   - repo path
     * @return watch status
     */
    private BaseArtifact getWatchStatus(ArtifactWatchAddon watchAddon, RepoPath repoPath) {
        return switch (watchAddon.getWatchStatus(repoPath)) {
            case WATCHING -> new BaseArtifact("Unwatch");
            case NOT_WATCHING -> new BaseArtifact("Watch");
            case NOT_ALLOWED -> null;
        };
    }
}
