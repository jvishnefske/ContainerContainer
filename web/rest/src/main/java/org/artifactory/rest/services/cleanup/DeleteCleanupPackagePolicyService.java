package org.artifactory.rest.services.cleanup;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cleanup.CleanupPolicyAddon;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Oleksandr Legenkiy
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DeleteCleanupPackagePolicyService implements RestService<String> {

    private final AddonsManager addonsManager;

    @Autowired
    public DeleteCleanupPackagePolicyService(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        String policyKey = request.getImodel();
        addonsManager.addonByType(CleanupPolicyAddon.class).deletePackagePolicy(policyKey);
        response.info("Package Cleanup Policy " + policyKey + " was deleted successfully.");
    }
}
