package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.cargo;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cargo.CargoAddon;
import org.artifactory.addon.cargo.CargoMetadataInfo;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.cargo.CargoArtifactInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Volodymyr Shulga
 */
@Component
@Slf4j
public class CargoViewService implements RestService<CargoArtifactInfo> {

    private AuthorizationService authorizationService;
    private SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest<CargoArtifactInfo> request, RestResponse response) {
        CargoArtifactInfo artifactInfo = request.getImodel();
        RepoPath repoPath = RepoPathFactory.create(artifactInfo.getRepoKey(), artifactInfo.getPath());

        // read permission checks
        if (!authorizationService.canRead(repoPath)) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }
        CargoArtifactInfo metadata = getCargoMetadata(artifactInfo, repoPath);
        response.iModel(metadata);
    }

    private CargoArtifactInfo getCargoMetadata(CargoArtifactInfo artifactInfo, RepoPath repoPath) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        CargoAddon addon = addonsManager.addonByType(CargoAddon.class);
        if (addon != null) {
            CargoMetadataInfo metadata = addon.getCargoMetadataToUiModel(repoPath);
            artifactInfo.setInfo(metadata.getInfo());
            artifactInfo.setDependencies(metadata.getDependencies());
        }
        return artifactInfo;
    }

    @Autowired
    public void setAuthorizationService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }
}
