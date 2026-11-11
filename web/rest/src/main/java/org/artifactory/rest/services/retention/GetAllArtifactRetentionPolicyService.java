package org.artifactory.rest.services.retention;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.retention.ArtifactRetentionPolicyModel;
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

import java.util.List;

/**
 * @author Omri Ziv
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetAllArtifactRetentionPolicyService implements RestService {

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private FeatureAdoptionService faService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        ArtifactRetentionAddon artifactRetentionAddon = addonsManager.addonByType(ArtifactRetentionAddon.class);
        List<ArtifactRetentionPolicyModel> policyModels = artifactRetentionAddon.getPolicies();
        response.iModel(policyModels);
        faService.trackAdoption(FeatureAdoptionFeature.COLD_STORAGE,
                FeatureAdoptionAction.VISITED, true,
                ColdStorageLabels.ARCHIVING_POLICY_LIST_PAGE_UI_ADMIN);
    }
}
