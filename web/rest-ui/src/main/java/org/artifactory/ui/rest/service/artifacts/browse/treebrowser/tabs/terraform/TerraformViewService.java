package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.terraform;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.terraform.TerraformAddon;
import org.artifactory.addon.terraform.TerraformMetadataInfo;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.terraform.TerraformArtifactInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Service for fetching the Terraform metadata for the Info tab in the UI
 *
 * @author BarakH
 */
@Component
public class TerraformViewService implements RestService {

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private RepositoryService repositoryService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        TerraformArtifactInfo terraformArtifactInfo = (TerraformArtifactInfo) request.getImodel();
        RepoPath repoPath = RepoPathFactory.create(terraformArtifactInfo.getRepoKey(), terraformArtifactInfo.getPath());
        if (repositoryService.isVirtualRepoExist(repoPath.getRepoKey())) {
            repoPath = repositoryService.getVirtualFileInfo(repoPath).getRepoPath();
        }
        // Assert permission checks
        if (!authorizationService.canRead(repoPath)) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }
        // get Terraform module Metadata
        TerraformArtifactInfo moduleMetadata = getTerraformMetaData(terraformArtifactInfo, repoPath);
        // Update response with model data
        response.iModel(moduleMetadata);
    }

    /**
     * @return Terraform Metadata model
     */
    private TerraformArtifactInfo getTerraformMetaData(TerraformArtifactInfo terraformArtifactInfo, RepoPath repoPath) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        TerraformAddon terraformAddon = addonsManager.addonByType(TerraformAddon.class);
        if (terraformAddon != null) {
            TerraformMetadataInfo metadata = terraformAddon.getTerraformMetadataIntoUiModel(repoPath);
            if (metadata != null) {
                terraformArtifactInfo.setTerraformInfo(metadata.getTerraformInfo());
                terraformArtifactInfo.setSubModules(metadata.getSubModules());
                terraformArtifactInfo.setModulesDependencies(metadata.getModulesDependencies());
                terraformArtifactInfo.setProvidersDependencies(metadata.getProvidersDependencies());
                terraformArtifactInfo.setInputs(metadata.getInputs());
                terraformArtifactInfo.setOutputs(metadata.getOutputs());
                terraformArtifactInfo.setReadme(metadata.getReadme());
            }
        }
        return terraformArtifactInfo;
    }
}
