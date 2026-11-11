package org.artifactory.ui.rest.service.distribution;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.jackson.JacksonWriter;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.ui.rest.model.distribution.ReleaseBundleReposIModel;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.artifactory.entitlement.EntitlementType.RELEASE_BUNDLES_READ;
import static org.artifactory.entitlement.EntitlementType.RELEASE_BUNDLES_WRITE;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.security.ArtifactoryPermission.MANAGE;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;

/**
 * Used in specific Permission page to retrieve the list of release-bundle repositories.
 *
 * @author Inbar Tal
 */
@Slf4j
@Component
@AllArgsConstructor
public class GetReleaseBundleRepositoriesService implements RestService<ReleaseBundleReposIModel> {

    private final AddonsManager addonsManager;
    private final RepositoryService repositoryService;
    private final RepositoryConfigService repositoryConfigService;
    private final AuthorizationService authorizationService;

    @Override
    @SuppressWarnings("rawtypes")
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        if (!authorizationService.hasReleaseBundlePermission(MANAGE, false, false)) {
            throw new ForbiddenException("Only admin or users with manage permissions can access this endpoint");
        }
        List<String> releaseBundleRepos = new ArrayList<>();
        populateReleaseBundleRepoKeysV1(releaseBundleRepos);
        populateReleaseBundleRepoKeysV2(releaseBundleRepos);
        populateReleaseBundleRepositoriesResponse(response, releaseBundleRepos);
    }

    private void populateReleaseBundleRepoKeysV1(@NonNull List<String> repoKeys) {
        if (addonsManager.isEntitled(RELEASE_BUNDLES_WRITE)) {
            populateReleaseBundleAllRepoKeysV1(repoKeys);
            if (repoKeys.isEmpty()) {
                populateReleaseBundleDefaultRepoKeyV1(repoKeys);
            }
        } else if (addonsManager.isEntitled(RELEASE_BUNDLES_READ)) {
            populateReleaseBundleDefaultRepoKeyV1(repoKeys);
        }
    }

    private void populateReleaseBundleAllRepoKeysV1(@NonNull List<String> repoKeys) {
        repositoryConfigService.getRepoConfigKeysByType(Set.of(RepoDetailsType.RELEASE_BUNDLE)).stream()
                .forEach(repoKeys::add);
    }

    private void populateReleaseBundleDefaultRepoKeyV1(@NonNull List<String> repoKeys) {
        repoKeys.add(RELEASE_BUNDLE_REPO_KEY);
    }

    private void populateReleaseBundleRepoKeysV2(@NonNull List<String> repoKeys) {
        populateReleaseBundleAllRepoKeysV2(repoKeys);
        if (!repoKeys.contains(DEFAULT_RBV2_REPO)) {
            repoKeys.add(DEFAULT_RBV2_REPO);
        }
    }

    private void populateReleaseBundleAllRepoKeysV2(@NonNull List<String> repoKeys) {
        repositoryService.getLocalRepoConfigsIncludingInfoRepos().stream()
                .filter(repoConfig -> repoConfig.getPackageType() == RepoType.ReleaseBundles)
                .map(LocalConfig::getKey)
                .filter(repoKey -> repoKey.contains(DEFAULT_RBV2_REPO))
                .forEach(repoKeys::add);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void populateReleaseBundleRepositoriesResponse(
            @NonNull RestResponse response,
            @NonNull List<String> releaseBundleRepos) throws IllegalArgumentException {
        ReleaseBundleReposIModel releaseBundleReposIModel = new ReleaseBundleReposIModel();
        releaseBundleReposIModel.setRepositories(releaseBundleRepos);
        try {
            response.iModel(JacksonWriter.serialize(releaseBundleReposIModel));
        } catch (IOException e) {
            log.error("Failed to serialize response ", e);
            throw new IllegalArgumentException(e);
        }
    }
}
