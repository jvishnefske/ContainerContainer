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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.rpm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.yum.ArtifactRpmMetadata;
import org.artifactory.addon.yum.YumAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.rpm.RpmArtifactInfo;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Chen Keinan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RpmViewService implements RestService {

    private final RepositoryService repositoryService;
    private final AuthorizationService authorizationService;
    private final SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        RpmArtifactInfo rpmArtifactInfo = (RpmArtifactInfo) request.getImodel();
        // get rpm meta data
        RepoPath repoPath = RepoPathFactory.create(rpmArtifactInfo.getRepoKey(), rpmArtifactInfo.getPath());
        if (repositoryService.isVirtualRepoExist(repoPath.getRepoKey())) {
            repoPath = repositoryService.getVirtualFileInfo(repoPath).getRepoPath();
        }
        // read permission checks
        if (!authorizationService.canRead(repoPath)) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }
        ArtifactRpmMetadata rpmMetadata = getArtifactRpmMetadata(repoPath);
        if (rpmMetadata == null) {
            return;
        }
        RpmArtifactInfo rpmReturnMeta = new RpmArtifactInfo(rpmMetadata);
        // update response
        response.iModel(rpmReturnMeta);

    }

    /**
     * get Rpm meta data
     *
     * @param repoPath - rpm repo path
     */
    private ArtifactRpmMetadata getArtifactRpmMetadata(RepoPath repoPath) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        YumAddon yumAddon = addonsManager.addonByType(YumAddon.class);
        FileInfo itemInfo = (FileInfo) repositoryService.getItemInfo(repoPath);
        return yumAddon.getRpmMetadata(itemInfo);
    }
}
