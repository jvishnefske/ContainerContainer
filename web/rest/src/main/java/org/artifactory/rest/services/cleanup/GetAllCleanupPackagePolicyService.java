package org.artifactory.rest.services.cleanup;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cleanup.CleanupPolicyAddon;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicy;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.services.cleanup.mapper.PackageCleanupPolicyDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Oleksandr Legenkiy
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetAllCleanupPackagePolicyService implements RestService<PackageCleanupPolicy> {

    private final AddonsManager addonsManager;

    @Autowired
    public GetAllCleanupPackagePolicyService(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest<PackageCleanupPolicy> request, RestResponse response) {
        List<PackageCleanupPolicy> policyModels =
                addonsManager.addonByType(CleanupPolicyAddon.class).getPackagePolicies(null);
        response.iModel(policyModels.stream().map(PackageCleanupPolicyDtoMapper::toDto).toList());
    }
}
