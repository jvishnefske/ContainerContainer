package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.repo.ArtifactoryFederationServiceException;
import org.artifactory.repo.FederationPriorityUpdateRtfsResponse;
import org.artifactory.repo.RepoPriority;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationPriority;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;

/**
 * Service to update the priority of a federation repository
 * @author : Tomer Ben Kereth
 */
@Component
@Slf4j
public class UpdateFederationPriorityService  implements RestService<FederationPriority> {

    @Override
    public void execute(ArtifactoryRestRequest<FederationPriority> request, RestResponse restResponse) {
        FederationPriority federationRepoPriority = request.getImodel();
        FederatedRepoService federatedRepoService = ContextHelper.get().beanForType(FederatedRepoService.class);
        RepoPriority priority = RepoPriority.getPriority(federationRepoPriority.priorityLevel());
        try {
            FederationPriorityUpdateRtfsResponse response = federatedRepoService
                    .updateFederationPriority(federationRepoPriority.repoKey(), priority);
            restResponse.info(response.getMessage()).responseCode(OK.getStatusCode());
        } catch (ArtifactoryFederationServiceException e) {
            restResponse.error(e.getMessage()).responseCode(BAD_REQUEST.getStatusCode());
        }
    }
}
