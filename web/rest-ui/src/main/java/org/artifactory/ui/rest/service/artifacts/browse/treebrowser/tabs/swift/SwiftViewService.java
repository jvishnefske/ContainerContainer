package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.swift;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.swift.SwiftAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Natalie Kirillov
 */
@Component
public class SwiftViewService implements RestService {

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private AddonsManager addonsManager;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        BaseArtifactInfo artifactInfo = (BaseArtifactInfo) request.getImodel();
        RepoPath repoPath = RepoPathFactory.create(artifactInfo.getRepoKey(), artifactInfo.getPath());
        if (repositoryService.isVirtualRepoExist(repoPath.getRepoKey())) {
            repoPath = repositoryService.getVirtualFileInfo(repoPath).getRepoPath();
        }

        if (!authorizationService.canRead(repoPath)) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }
        response.iModel(addonsManager.addonByType(SwiftAddon.class).getReleaseInfo(repoPath));
    }
}
