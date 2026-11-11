package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.common.ConstantValues;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_GONE;
import static org.apache.http.HttpStatus.SC_OK;

/**
 * @author Alexei Vainshtein
 */
@Component
public class MigrateLocalToFederatedRepository implements RestService<String> {
    private static final Logger log = LoggerFactory.getLogger(MigrateLocalToFederatedRepository.class);
    public final String SUCCESSFULLY = "Migration finished successfully";
    private final FederatedRepoService federatedRepoService;

    @Autowired
    public MigrateLocalToFederatedRepository(FederatedRepoService federatedRepoService) {
        this.federatedRepoService = federatedRepoService;
    }

    @Override
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        if (ConstantValues.repositoriesFederationsAttributeEnabled.getBoolean()) {
            response.error("Migration to federated repository type is deprecated.");
            response.responseCode(SC_GONE).buildResponse();
            return;
        }
        String repoKey = request.getImodel();
        try {
            federatedRepoService.migrate(repoKey);
        } catch (BadRequestException e) {
            log.error("Migration of '{}' from local to federated repo failed due to: {}", repoKey, e.getMessage());
            log.debug("Migration of from local to federated repo failed due to:", e);
            response.error(e.getMessage()).responseCode(SC_BAD_REQUEST).buildResponse();
            return;
        }
        response.info(SUCCESSFULLY).responseCode(SC_OK).buildResponse();
    }
}
