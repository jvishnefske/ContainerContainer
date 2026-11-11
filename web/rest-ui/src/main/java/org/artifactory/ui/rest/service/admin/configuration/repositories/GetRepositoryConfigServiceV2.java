package org.artifactory.ui.rest.service.admin.configuration.repositories;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.storage.fs.service.RepoStatsService;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigResponse;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.ProjectRepo;
import org.artifactory.ui.rest.service.admin.configuration.repositories.mapper.RepoConfigMapper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.ProjectRepoHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.RepoConfigSortingUtil;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.ui.utils.RequestUtils.isFedAttrOptIn;

@Component
@RequiredArgsConstructor
public class GetRepositoryConfigServiceV2 implements RestService<RepoConfigRequest> {

    private final ProjectRepoHelper projectRepoHelper;
    private final RepoConfigMapper repoConfigMapper;
    private final RepoStatsService repoStatsService;
    private final RepositoryService repositoryService;
    private final RepoConfigFilterService repoConfigFilterService;
    private final RepoConfigValidator repoConfigValidator;
    private final SecurityService securityService;
    private final FederatedRepoService federatedRepoService;

    @SneakyThrows
    @Override
    public void execute(ArtifactoryRestRequest<RepoConfigRequest> request, RestResponse response) {
        RepoConfigRequest repoConfigRequest = request.getImodel();
        repoConfigRequest.setFedAttrOptIn(isFedAttrOptIn(request));
        List<RepoConfigResponse> repoConfigResponses = getPage(repoConfigRequest);

        response.iModelList(repoConfigResponses)
                .responseCode(HttpStatus.SC_OK);
    }

    public List<RepoConfigResponse> getPage(RepoConfigRequest repoConfigRequest) throws RepoConfigException {
        repoConfigValidator.validateProjectContext(repoConfigRequest);
        if (!hasPermissions(repoConfigRequest)) {
            throw new AuthorizationRestException();
        }
        long offset = getOffsetOrDefault(repoConfigRequest);
        long limit = getLimitOrDefault(repoConfigRequest);

        Set<String> inActiveFederatedRepos = federatedRepoService.getInconsistentRepos();
        Map<String, List<ProjectRepo>> allProjectRepos = projectRepoHelper.getAllProjectRepos(repoConfigRequest);

        List<RepoConfig> filtered = repositoryService.getAllRepoConfigs().stream()
                .filter(repo -> repoConfigFilterService.match(repo, repoConfigRequest,
                        allProjectRepos, inActiveFederatedRepos))
                .toList();

        Map<String, RepoStatDto> repoStats = getRepoStats(filtered);

        return filtered.stream()
                .sorted(RepoConfigSortingUtil.buildComparator(repoConfigRequest, repoStats))
                .skip(offset)
                .limit(limit)
                .map(repoConfig ->
                        repoConfigMapper.toResponse(repoConfig, repoStats.get(repoConfig.getKey()),
                                allProjectRepos, inActiveFederatedRepos))
                .toList();
    }

    private Map<String, RepoStatDto> getRepoStats(List<RepoConfig> repoConfigs) {
        Set<String> repoKeys = repoConfigs.stream()
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());
        return repoStatsService.getByKeys(repoKeys)
                .stream()
                .collect(Collectors.toMap(RepoStatDto::getRepoKey, dto -> dto));
    }

    private long getOffsetOrDefault(RepoConfigRequest repoConfigRequest) {
        return repoConfigRequest.getOffset() != null ? repoConfigRequest.getOffset() : 0;
    }

    private long getLimitOrDefault(RepoConfigRequest repoConfigRequest) {
        return repoConfigRequest.getLimit() != null ? repoConfigRequest.getLimit() :
                ConstantValues.repoConfigPageDefaultSize.getLong();
    }

    private boolean hasPermissions(RepoConfigRequest repoConfigRequest) {
        if (repoConfigRequest.isProjectContext()) {
            String projectKey = repoConfigRequest.getProjectKeys().stream().findFirst().get();
            return securityService.isProjectAdmin(projectKey);
        }
        return securityService.isAdmin();
    }
}