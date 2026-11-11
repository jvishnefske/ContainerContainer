package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.federation.FederationReposResponse;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.apache.http.HttpStatus.SC_OK;

/**
 * @author Daniel Simantov
 */
@Component
public class DiscoverMirrorBaseUrlAndRepos implements RestService<FederationConfigModel> {

    @Override
    public void execute(ArtifactoryRestRequest<FederationConfigModel> request, RestResponse response) {
        FederationConfigModel imodel = request.getImodel();
        FederatedRepoService federatedRepoService = ContextHelper.get().beanForType(FederatedRepoService.class);
        List<FederationReposResponse> reposFromRemote =
                federatedRepoService.getBaseUrlAndReposFromRemote(imodel.mirrorsToUrls(), imodel.getProxy());
        response.responseCode(SC_OK).iModel(reposFromRemote);
    }
}
