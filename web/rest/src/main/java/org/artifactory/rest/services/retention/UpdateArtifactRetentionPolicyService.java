package org.artifactory.rest.services.retention;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.retention.ArtifactRetentionPolicyBaseModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Omri Ziv
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UpdateArtifactRetentionPolicyService implements RestService<ArtifactRetentionPolicyBaseModel> {

    @Autowired
    private AddonsManager addonsManager;

    @Override
    public void execute(ArtifactoryRestRequest<ArtifactRetentionPolicyBaseModel> request, RestResponse response) {
        ArtifactRetentionPolicyBaseModel policy = request.getImodel();
        String policyKey = policy.getKey();
        ArtifactRetentionPolicyBaseModel policyRetrieved = addonsManager.addonByType(ArtifactRetentionAddon.class)
                .updatePolicy(policyKey, policy);
        response.iModel(policyRetrieved);
    }
}
