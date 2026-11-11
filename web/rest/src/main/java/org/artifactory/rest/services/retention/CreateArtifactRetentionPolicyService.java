package org.artifactory.rest.services.retention;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.retention.ArtifactRetentionPolicyBaseModel;
import org.artifactory.api.metrics.FeatureAdoptionAction;
import org.artifactory.api.metrics.FeatureAdoptionFeature;
import org.artifactory.metrics.framework.adoption.FeatureAdoptionService;
import org.artifactory.metrics.framework.adoption.coldstorage.ColdStorageLabels;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Omri Ziv
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CreateArtifactRetentionPolicyService implements RestService<ArtifactRetentionPolicyBaseModel> {

    private final AddonsManager addonsManager;
    private final FeatureAdoptionService faService;

    @Autowired
    public CreateArtifactRetentionPolicyService(AddonsManager addonsManager, FeatureAdoptionService faService) {
        this.addonsManager = addonsManager;
        this.faService = faService;
    }

    @Override
    public void execute(ArtifactoryRestRequest<ArtifactRetentionPolicyBaseModel> request, RestResponse response) {
        try {
            ArtifactRetentionPolicyBaseModel policy = request.getImodel();
            String policyKey = policy.getKey();
            ArtifactRetentionPolicyBaseModel policyRetrieved = addonsManager.addonByType(ArtifactRetentionAddon.class)
                    .addPolicy(policyKey, policy);
            response.info("Successfully added retention policy " + policyRetrieved.getKey());
            faService.trackAdoption(FeatureAdoptionFeature.COLD_STORAGE,
                    FeatureAdoptionAction.CREATED, true, ColdStorageLabels.ARCHIVING_POLICY_UI_ADMIN);
            response.responseCode(HttpServletResponse.SC_CREATED);
        } catch (Exception e) {
            faService.trackAdoption(FeatureAdoptionFeature.COLD_STORAGE,
                    FeatureAdoptionAction.CREATED, false, ColdStorageLabels.ARCHIVING_POLICY_UI_ADMIN);
            throw e;
        }
    }
}
