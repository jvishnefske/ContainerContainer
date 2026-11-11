package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.common.ConstantValues;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedBuildInfoDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.apache.http.HttpStatus.SC_OK;

@Component
public class GetFederatedBuildInfoDetailsService implements RestService<String> {

    @Autowired
    private BuildFacade buildFacade;

    @Autowired
    private FederatedRepoService federatedRepoService;

    @Autowired
    private InternalRepositoryService repositoryService;

    @Override
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        String projectKey = request.getImodel();
        String repoKey = buildFacade.getBuildInfoRepoKeyByProject(projectKey, true);
        boolean isFederated = federatedRepoService.isProjectBuildInfoRepoFederated(projectKey);
        if (!isFederated && ConstantValues.repositoriesFederationsAttributeEnabled.getBoolean()) {
            RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(repoKey);
            isFederated = repoConfig != null && repoConfig.getBaseConfig().isFederated();
        }
        FederatedBuildInfoDetails federatedBuildInfoDetails = new FederatedBuildInfoDetails();
        setBuildInfoDetailsToModel(isFederated, repoKey, federatedBuildInfoDetails);
        response.responseCode(SC_OK).iModel(federatedBuildInfoDetails);
    }

    private void setBuildInfoDetailsToModel(boolean isFederatedRepo, String repoKey,
            FederatedBuildInfoDetails buildInfoDetails) {
        buildInfoDetails.setBuildInfoRepo(repoKey);
        buildInfoDetails.setEnableConvert(!isFederatedRepo);
    }
}
