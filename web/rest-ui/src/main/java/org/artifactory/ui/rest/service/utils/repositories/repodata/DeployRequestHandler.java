package org.artifactory.ui.rest.service.utils.repositories.repodata;

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.model.utils.repositories.RepositoriesData;
import org.artifactory.util.bundles.BundleConfigurationUtil;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;

@AllArgsConstructor
@Slf4j
@Component
public class DeployRequestHandler<T> extends AbstractGetRepositoriesDataHandler<T> {
    private static final String DEPLOY_QUERY_PARAM = "deploy";
    private final CentralConfigService centralConfigService;

    @Override
    public void handle(ArtifactoryRestRequest<T> request, RestResponse response, String projectKey) {
        RepositoriesData repositoriesData = new RepositoriesData();
        repositoriesData.setFileUploadMaxSizeMb(centralConfigService.getDescriptor().getFileUploadMaxSizeMb());
        long limit = getLimit(request);
        if (limit > 0) {
            String keySearch = getKeySearch(request);
            Comparator<RepoConfig> comparator = getRepoConfigComparator(request, keySearch);
            setRepoKeyTypes(repositoriesData, projectKey, keySearch, comparator, limit);
        }
        response.iModel(repositoriesData);
    }

    private void setRepoKeyTypes(RepositoriesData repositoriesData, String projectKey, String keySearch,
            Comparator<RepoConfig> comparator, long limit) {
        Stream<RepoConfig> repoConfigs =
                repoService.getRepoConfigsBySearchPattern(Set.of(LOCAL, FEDERATED, VIRTUAL), projectKey,
                        keySearch).stream().sorted(comparator);
        List<RepoKeyType> repositoriesDataForDeploy = getRepositoriesDataForDeploy(repoConfigs).limit(limit).toList();
        repositoriesData.setRepoTypesList(repositoriesDataForDeploy);
    }

    /**
     * return repositories data for deploy (like for user, but include virtual repos with configured default
     * deployment local repo)
     */
    private Stream<RepoKeyType> getRepositoriesDataForDeploy(Stream<RepoConfig> repoConfigStream) {
        boolean isAdmin = securityService.isAdmin();
        Map<String, RepoLayout> layoutCache = new HashMap<>();
        if (isAdmin) {
            return repoConfigStream
                    .filter(this::isValidRepoForDeploy)
                    .map(repoConfig -> createRepoKeyTypeWithLayout(repoConfig, layoutCache));
        }
        Map<String, Boolean> canDeployCache = new HashMap<>();
        return repoConfigStream.filter(this::isValidRepoForDeploy)
                .filter(repoConfig -> {
                    String repoKey;
                    RepoConfig targetRepoConfig;
                    if (repoConfig.isReal()) {
                        repoKey = repoConfig.getKey();
                        targetRepoConfig = repoConfig;
                    } else {
                        repoKey = ((VirtualRepoConfig) repoConfig).getRepoTypeConfig().getDefaultDeploymentRepoRef();
                        targetRepoConfig = repoService.getRealRepoConfigByKey(repoKey);
                    }
                    return canDeployCache.computeIfAbsent(repoKey, key -> hasDeployPermission(targetRepoConfig));
                })
                .map(repoConfig -> createRepoKeyTypeWithLayout(repoConfig, layoutCache));
    }

    @VisibleForTesting
    boolean isValidRepoForDeploy(RepoConfig repoConfig) {
        return (!SystemRepos.isInTransitRepo(repoConfig.getKey())) &&
                (!BundleConfigurationUtil.isReleaseBundlesV2DistributionRepoKey(repoConfig.getKey())) &&
                (repoConfig.isReal() || ((VirtualRepoConfig) repoConfig).getRepoTypeConfig()
                        .getDefaultDeploymentRepoRef() != null);
    }

    private RepoKeyType createRepoKeyTypeWithLayout(RepoConfig repoConfig, Map<String, RepoLayout> layoutCache) {
        RepoKeyType repoKeyType = new RepoKeyType(repoConfig.getPackageType(), repoConfig.getKey());
        String repoLayoutRef = repoConfig.getBaseConfig().getRepoLayoutRef();
        if (repoLayoutRef != null) {
            RepoLayout layout = layoutCache.computeIfAbsent(repoLayoutRef,
                    key -> centralConfigService.getDescriptor().getRepoLayout(key));
            if (layout != null) {
                repoKeyType.setLayoutPattern(layout.getArtifactPathPattern());
                repoKeyType.setLayoutFolderItegRevRegex(layout.getFolderIntegrationRevisionRegExp());
                repoKeyType.setLayoutFileItegRevRegex(layout.getFileIntegrationRevisionRegExp());
            }
        }
        return repoKeyType;
    }

    @Override
    public String getHandledQueryParam() {
        return DEPLOY_QUERY_PARAM;
    }
}