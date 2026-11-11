package org.artifactory.rest.services.retention;

import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static org.artifactory.api.rest.constant.SearchRestConstants.PARAM_POLICY_KEYS;

/**
 * @author Omri Ziv
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeleteArtifactRetentionPoliciesService implements RestService<String> {

    @Autowired
    private AddonsManager addonsManager;

    @Override
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        List<String> policyKeys = Arrays.asList(request.getQueryParamByKey(PARAM_POLICY_KEYS).split(","));
        ArtifactRetentionAddon archivingAddon = addonsManager.addonByType(ArtifactRetentionAddon.class);
        policyKeys.forEach(archivingAddon::deletePolicy);
        response.info("Successfully delete " + CollectionUtils.size(policyKeys) + "retention policies.");
    }
}
