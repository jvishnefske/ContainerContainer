package org.artifactory.ui.rest.service.setmeup;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.setmeup.SetMeUpModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.utils.RequestUtils;
import org.artifactory.util.AolUtils;
import org.artifactory.util.HttpUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.api.request.ArtifactoryHeaders.ORIGINAL_CLIENT_URI;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.security.ArtifactoryPermission.DEPLOY;

/**
 * @author chen Keinan
 * @author Lior Hasson
 */
@AllArgsConstructor
@Component
@Slf4j
public class GetSetMeUpService implements RestService<Void> {

    public static final Set<RepoDetailsType> REPOS_TYPES = Set.of(LOCAL, FEDERATED, REMOTE, VIRTUAL);

    private final AuthorizationService authorizationService;
    private final AddonsManager addonsManager;
    private final CentralConfigService centralConfigService;
    private final RepositoryConfigService repositoryConfigService;
    private final ProjectsService projectsService;

    @Override
    public void execute(ArtifactoryRestRequest<Void> request, RestResponse response) {
        SetMeUpModel setMeUpModel = createSetMeUpModel(request);
        response.iModel(setMeUpModel);
    }

    SetMeUpModel createSetMeUpModel(ArtifactoryRestRequest<Void> request) {
        SetMeUpModel setMeUpModel = new SetMeUpModel();
        setRepoKeyTypes(request, setMeUpModel);
        // We want to skip the forward host, as we want the response to be leading straight to Artifactory
        setBaseUrl(request, setMeUpModel);
        setNpmBaseUrl(request, setMeUpModel);
        setServerDetails(setMeUpModel);
        setMeUpModel.setSkipDockerDns(AolUtils.skipDockerDns());
        return setMeUpModel;
    }

    private void setRepoKeyTypes(ArtifactoryRestRequest<Void> request, SetMeUpModel setMeUpModel) {
        String excludeRepoTypes = request.getQueryParamByKey("excludeRepoTypes");
        if (StringUtils.isBlank(excludeRepoTypes) || !StringUtils.equals(excludeRepoTypes, "true")) {
            String projectKey = RequestUtils.getProjectKeyFromRequest(request);
            List<RepoKeyType> repoKeyTypes = getRepoKeyTypes(projectKey);
            setMeUpModel.setRepoKeyTypes(repoKeyTypes);
        }
    }

    private void setBaseUrl(ArtifactoryRestRequest<Void> request, SetMeUpModel setMeUpModel) {
        String servletContextUrl =
                HttpUtils.getServletContextUrlArtifactoryPreferred(request.getServletRequest());
        setMeUpModel.setBaseUrl(servletContextUrl);
    }

    private static void setNpmBaseUrl(ArtifactoryRestRequest<Void> request, SetMeUpModel setMeUpModel) {
        String originalClientBaseUrl = request.getServletRequest().getHeader(ORIGINAL_CLIENT_URI);
        if (isNotBlank(originalClientBaseUrl)) {
            setMeUpModel.setNpmBaseUrl(originalClientBaseUrl);
        }
    }

    private void setServerDetails(SetMeUpModel setMeUpModel) {
        setMeUpModel.setServerId(centralConfigService.getServerName());
        setMeUpModel.setHostname(ContextHelper.get().beanForType(AddonsManager.class).addonByType(CoreAddons.class)
                .getArtifactoryServerName());
    }

    /**
     * get user keys type list
     *
     * @return - list of user keys types
     */
    private List<RepoKeyType> getRepoKeyTypes(@NonNull String projectKey) {
        List<RepoConfig> permittedRepositories = getUserPermittedRepositories(projectKey);
        List<RepoKeyType> repoKeyTypes = new ArrayList<>();
        Map<String, Boolean> canDeployCache = new HashMap<>();
        permittedRepositories.forEach(repoConfig -> {
            RepoKeyType repoKeyType = new RepoKeyType(repoConfig.getPackageType(), repoConfig.getKey());
            repoKeyType.setCanRead(true);
            updateCanDeploy(repoConfig, repoKeyType, canDeployCache);
            if (repoConfig instanceof LocalRepoConfig) {
                repoKeyType.setIsLocal(true);
            }
            if (repoConfig instanceof FederatedRepoConfig) {
                repoKeyType.setIsFederated(true);
            }
            if (repoConfig instanceof RemoteRepoConfig) {
                repoKeyType.setIsRemote(true);
            }
            if (repoConfig instanceof VirtualRepoConfig virtualRepoConfig) {
                repoKeyType.setIsVirtual(true);
                if (virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef() != null) {
                    repoKeyType.setIsDefaultDeploymentConfigured(true);
                }
            }
            repoKeyTypes.add(repoKeyType);
        });
        return repoKeyTypes.stream().sorted(comparing(RepoKeyType::getRepoKey)).toList();
    }

    private void updateCanDeploy(RepoConfig userRepo, RepoKeyType repoKeyType,
            Map<String, Boolean> canDeployToRepoCache) {
        repoKeyType.setCanDeploy(false);
        if (authorizationService.userHasPermissionsOnRepositoryRoot(userRepo.getKey(), Set.of(DEPLOY),
                canDeployToRepoCache)) {
            repoKeyType.setCanDeploy(true);
        }
    }

    private List<RepoConfig> getUserPermittedRepositories(String projectKey) {
        Map<String, Boolean> canAccessRepoCache = new HashMap<>();
        Stream<RepoConfig> result =
                repositoryConfigService.getRepoConfigsByTypes(REPOS_TYPES).stream()
                        .filter(repoConfig -> !SystemRepos.SystemRepoConfig.isInvisibleSystemRepo(repoConfig.getKey()));
        if (!ProjectsUtils.isDefaultProject(projectKey)) {
            Set<String> projectRepoKeys = projectsService.getProjectRepositoriesKeys(projectKey, true);
            result = result.filter(repo -> projectRepoKeys.contains(repo.getKey()));
        }
        return result.filter(
                repoConfig -> authorizationService.userHasPermissionsOnRepositoryRoot(repoConfig.getKey(),
                        Set.of(ArtifactoryPermission.values()), canAccessRepoCache)).toList();
    }
}
