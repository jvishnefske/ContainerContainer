package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.pub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.pub.PubAddon;
import org.artifactory.addon.pub.PubMetadataInfo;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.pub.PubArtifactInfo;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Shahaf Golan
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PubViewService implements RestService<PubArtifactInfo> {

    private final AuthorizationService authorizationService;
    private final SecurityService securityService;
    private final RepositoryService repositoryService;

    @Override
    public void execute(ArtifactoryRestRequest<PubArtifactInfo> request, RestResponse response) {
        PubArtifactInfo artifactInfo = request.getImodel();
        RepoPath repoPath = RepoPathFactory.create(artifactInfo.getRepoKey(), artifactInfo.getPath());
        if (repositoryService.isVirtualRepoExist(repoPath.getRepoKey())) {
            repoPath = repositoryService.getVirtualFileInfo(repoPath).getRepoPath();
        }
        // read permission checks
        if (!authorizationService.canRead(repoPath)) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }
        PubArtifactInfo metadata = getPubMetadata(artifactInfo, repoPath);
        response.iModel(metadata);
    }

    private PubArtifactInfo getPubMetadata(PubArtifactInfo artifactInfo, RepoPath repoPath) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        PubAddon addon = addonsManager.addonByType(PubAddon.class);
        if (addon != null) {
            PubMetadataInfo metadata = addon.getPubMetadataToUiModel(repoPath);
            artifactInfo.setInfo(metadata.getInfo());
            artifactInfo.setDependencies(metadata.getDependencies());
            artifactInfo.setDevDependencies(metadata.getDevDependencies());
            artifactInfo.setEnvironment(metadata.getEnvironment());
        }
        return artifactInfo;
    }
}