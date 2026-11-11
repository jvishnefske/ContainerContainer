package org.artifactory.ui.rest.service.admin.security.keys.keypairs;

import org.artifactory.addon.common.keys.KeyPairService;
import org.artifactory.api.rest.keys.KeyPairModel;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Omri Ziv
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UpdatePublicKeyService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(UpdatePublicKeyService.class);

    private final KeyPairService keyPairService;

    @Autowired
    public UpdatePublicKeyService(KeyPairService keyPairService) {
        this.keyPairService = keyPairService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        KeyPairModel requestModel = (KeyPairModel) request.getImodel();
        log.debug("Updating Key Pair request for {}", requestModel.getPairName());
        KeyPairDescriptor keyPair = keyPairService.getKeyPairByName(requestModel.getPairName());
        if (keyPair == null) {
            response.error("No such key pair '" + requestModel.getPairName() + "'");
        }
        try {
            keyPairService.updateKeyPair(requestModel);
            response.info("Successfully updated Key Pair " + requestModel.getPairName());
        } catch (Exception e) {
            response.error("Failed to create a Key Pair: " + e.getMessage());
        }
    }
}
