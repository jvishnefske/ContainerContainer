package org.artifactory.rest.services.cleanup;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cleanup.CleanupPolicyAddon;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicy;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.resource.cleanup.dto.PackageCleanupPolicyDto;
import org.artifactory.rest.services.cleanup.mapper.PackageCleanupPolicyDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Oleksandr Legenkiy
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CreateCleanupPackagePolicyService implements RestService<PackageCleanupPolicyDto> {

    private final AddonsManager addonsManager;

    @Autowired
    public CreateCleanupPackagePolicyService(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest<PackageCleanupPolicyDto> request, RestResponse response) {
        PackageCleanupPolicyDto dto = request.getImodel();
        String projectKey = dto.projectKey();
        PackageCleanupPolicy policyToCreate = PackageCleanupPolicyDtoMapper.fromDto(dto);
        PackageCleanupPolicy createdPolicy =
                addonsManager.addonByType(CleanupPolicyAddon.class).addPackagePolicy(policyToCreate, projectKey);
        response.iModel(PackageCleanupPolicyDtoMapper.toDto(createdPolicy));
        response.responseCode(HttpServletResponse.SC_CREATED);
    }
}
