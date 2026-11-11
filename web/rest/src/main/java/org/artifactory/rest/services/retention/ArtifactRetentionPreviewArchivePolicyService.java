package org.artifactory.rest.services.retention;

import org.apache.http.HttpStatus;
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

import static java.util.Objects.isNull;

/**
 * @author Tamar Rosen
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactRetentionPreviewArchivePolicyService implements RestService<ArtifactRetentionPolicyBaseModel> {

    @Override
    public void execute(ArtifactoryRestRequest<ArtifactRetentionPolicyBaseModel> previewRequest,
            RestResponse response) {
        ArtifactRetentionPolicyBaseModel policy = previewRequest.getImodel();
        if (isNull(policy)) {
            response.error("Cannot preview archive policy - policy model is missing")
                    .responseCode(HttpStatus.SC_BAD_REQUEST);
        }
        ArtifactRetentionPreviewSummary previewSummary =
                ContextHelper.get().beanForType(AddonsManager.class).addonByType(ArtifactRetentionAddon.class)
                        .previewByPolicy(policy);
        response.iModel(previewSummary);
    }
}


