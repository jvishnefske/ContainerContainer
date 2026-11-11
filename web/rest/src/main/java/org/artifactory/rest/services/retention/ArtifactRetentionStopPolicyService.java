package org.artifactory.rest.services.retention;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.common.BasicStatusHolder;
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
public class ArtifactRetentionStopPolicyService implements RestService {
    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String policyKey = request.getPathParamByKey("policyKey");
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        addonsManager.addonByType(ArtifactRetentionAddon.class).markPolicyExecutionAsStopped(policyKey, statusHolder);
        if (statusHolder.hasErrors()) {
            response.responseCode(statusHolder.getStatusCode()).error(statusHolder.getStatusMsg());
            return;
        }
        response.info("Stop action was submitted successfully and will be processed in the background.");
    }
}