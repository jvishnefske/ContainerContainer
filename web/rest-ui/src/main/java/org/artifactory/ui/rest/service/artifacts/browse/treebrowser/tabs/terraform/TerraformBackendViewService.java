package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.terraform;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.terraform.TerraformAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.terraform.TerraformStateInfo;
import org.jfrog.client.util.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Service for fetching the Terraform State content for the artifact info tab in the UI
 *
 * @author BarakH
 */
@Component
public class TerraformBackendViewService implements RestService {

    String PROP_CREATOR = "terraform.creator";
    String PROP_CREATED = "terraform.time.created";

    private AuthorizationService authorizationService;

    private PropertiesService propertiesService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        TerraformStateInfo terraformStateInfo = (TerraformStateInfo) request.getImodel();
        RepoPath repoPath = RepoPathFactory.create(terraformStateInfo.getRepoKey(), terraformStateInfo.getPath());
        // Assert permission checks
        if (!authorizationService.canRead(repoPath)) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }
        // get Terraform state content
        TerraformStateInfo stateContent = getTerraformStateContent(terraformStateInfo, repoPath);
        stateContent.setInfo(new TerraformStateInfo.TerraformStateInfoMetadata());
        ArtifactoryContext context = ContextHelper.get();
        CentralConfigService centralConfig = context.getCentralConfig();
        // Fill creator and created time fields
        Properties properties = propertiesService.getProperties(repoPath);
        stateContent.getInfo().setCreatedBy(properties.getFirst(PROP_CREATOR));
        String createdAt = properties.getFirst(PROP_CREATED);
        if (createdAt != null) {
            stateContent.getInfo().setCreatedAt(centralConfig.format(Long.parseLong(createdAt)));
        }
        stateContent.getInfo().setId(PathUtils.stripExtension(repoPath.getName()));
        if (repoPath.getParent() != null) {
            stateContent.getInfo().setWorkspace(repoPath.getParent().getName());
        }
        // Update response with model data
        response.iModel(stateContent);
    }

    private TerraformStateInfo getTerraformStateContent(TerraformStateInfo terraformStateInfo, RepoPath repoPath) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        TerraformAddon terraformAddon = addonsManager.addonByType(TerraformAddon.class);
        if (terraformAddon != null) {
            String stateContent = terraformAddon.getTerraformStateFileContent(repoPath);
            terraformStateInfo.setContent(stateContent);
        }
        return terraformStateInfo;
    }

    @Autowired
    public void setAuthorizationService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Autowired
    public void setPropertiesService(PropertiesService propertiesService) {
        this.propertiesService = propertiesService;
    }
}
