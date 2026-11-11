package org.artifactory.ui.rest.service.setmeup;

import com.google.common.collect.ImmutableMap;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.TerraformType;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.util.TerraformPackageTypeConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

import static org.artifactory.security.ArtifactoryPermission.READ;

/**
 * @author Tomer Nir
 */
@Component
public class GetTerraformConfigSnippetService implements RestService<String> {
    private final InternalRepositoryService repositoryService;
    private final AuthorizationService authService;
    private static final String REGISTRY_TYPE = "registryType";
    private static final String PROVIDER_REGISTRY_PROTOCOL = "providerRegistryProtocol";
    private static final String PROVIDER_NETWORK_MIRROR = "providerNetworkMirror";
    private static final String MODULE = "module";

    @Autowired
    public GetTerraformConfigSnippetService(InternalRepositoryService repositoryService,
            AuthorizationService authService) {
        this.repositoryService = repositoryService;
        this.authService = authService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String repoKey = request.getPathParamByKey("repoKey");
        validateRepo(repoKey);
        TerraformType terraformType = TerraformPackageTypeConfigMapper.INSTANCE.map(
                repositoryService.getRepositoryConfigByKey(repoKey).getPackageTypeConfig()).getTerraformType();
        boolean hasPrimaryKey = hasPrimaryKeyConfigured(repoKey);
        if (TerraformType.PROVIDER.equals(terraformType)) {
            response.iModel(ImmutableMap.of(REGISTRY_TYPE,
                    hasPrimaryKey ? PROVIDER_REGISTRY_PROTOCOL : PROVIDER_NETWORK_MIRROR));
        } else if (TerraformType.MODULE.equals(terraformType)) {
            response.iModel(ImmutableMap.of(REGISTRY_TYPE, MODULE));
        } else {
            response.error("Unknown Terraform type");
        }
    }

    /**
     * Validates the existence and the user read permission of a Terraform local/federated repo.
     *
     * @param repoKey The repository key to validate.
     * @throws NotFoundException   If the repository does not exist or lacks read permissions.
     * @throws BadRequestException If the repository is virtual or remote.
     */
    private void validateRepo(String repoKey) {
        if (!repositoryService.existsByPackageType(RepoType.Terraform, repoKey) ||
                !authService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(READ), null)) {
            throw new NotFoundException("Repository " + repoKey + " does not exist");
        }
        if (repositoryService.localOrFederatedRepositoryByKey(repoKey) == null) {
            throw new BadRequestException(
                    "Operation not supported for " + repoKey + " as it is a virtual/remote repository");
        }
    }

    private boolean hasPrimaryKeyConfigured(String repoKey) {
        try {
            return repositoryService.localOrFederatedRepositoryByKey(repoKey).getRepoConfig().getSecurityConfig()
                    .getPrimaryKeyPairRef() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
