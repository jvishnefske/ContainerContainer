package org.artifactory.rest.services.retention;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.retention.ArtifactRetentionPolicyBaseModel;
import org.artifactory.addon.retention.ArtifactRetentionPreviewSummary;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author volodymyrm
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactRetentionPreviewByPolicyKeyService implements RestService<ArtifactRetentionPolicyBaseModel> {
    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String policyKey = request.getPathParamByKey("policyKey");
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        ArtifactRetentionPreviewSummary artifactRetentionPreviewSummary =
                addonsManager.addonByType(ArtifactRetentionAddon.class).previewByPolicyKey(policyKey);
        response.iModel(artifactRetentionPreviewSummary);
    }
}