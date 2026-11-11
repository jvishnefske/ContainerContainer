package org.artifactory.ui.rest.service.utils.repositories;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.AutoCompleteRepoConfigRecord;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.RepoKeyAutoCompleteComparator;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.jfrog.commons.continues.ContinueIndexPage;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;
import org.jfrog.commons.continues.util.PagingUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_CONTINUE_STATE_INIT_VALUE;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_CONTINUE_STATE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_STAGES_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_LIMIT_DEFAULT_VALUE;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_LIMIT_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_PACKAGE_TYPE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_PROJECTS_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_REPO_TYPE_PARAM;
import static org.artifactory.ui.rest.resource.utils.AutoCompleteConstants.AUTO_COMPLETE_SEARCH_PARAM;
import static org.jfrog.commons.continues.util.ContinueResultIndexUtils.getLastIndex;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class AutoCompleteRepositoriesServiceImpl implements RestService<AutoCompleteRepoConfigRecord> {
    private RepositoryConfigService repositoryConfigService;
    private ProjectsService projectsService;

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService,
            ProjectsService projectsService) {
        this.repositoryConfigService = repositoryConfigService;
        this.projectsService = projectsService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String search = request.getQueryParamByKey(AUTO_COMPLETE_SEARCH_PARAM);
        List<String> repoTypes = request.getQueryParamListByKey(AUTO_COMPLETE_REPO_TYPE_PARAM);
        List<String> packageTypes = request.getQueryParamListByKey(AUTO_COMPLETE_PACKAGE_TYPE_PARAM);
        String projectKeys = request.getQueryParamByKey(AUTO_COMPLETE_PROJECTS_PARAM);
        List<String> projectsKeysList =
                StringUtils.isNotBlank(projectKeys) ? List.of(projectKeys.split(",")) : new ArrayList<>();
        String stages = request.getQueryParamByKey(AUTO_COMPLETE_STAGES_PARAM);
        List<String> stagesList =
                StringUtils.isNotBlank(stages) ? List.of(stages.split(",")) : new ArrayList<>();
        String limitStr = request.getQueryParamByKey(AUTO_COMPLETE_LIMIT_PARAM);
        String continueState = request.getQueryParamByKey(AUTO_COMPLETE_CONTINUE_STATE_PARAM);

        if (StringUtils.isNotBlank(limitStr) && (!StringUtils.isNumeric(limitStr) || Long.parseLong(limitStr) < 0)) {
            response.error("Limit must be a number and greater than 0");
            return;
        }

        if (StringUtils.isNotBlank(continueState) && (!StringUtils.isNumeric(continueState) ||
                Long.parseLong(continueState) < 0)) {
            response.error("ContinueState must be a number and greater than 0");
            return;
        }

        repoTypes = filterIneligibleRepoTypes(repoTypes);
        validateProjectsAccess(projectsKeysList);

        List<RepoDetailsType> repoDetailsTypeList = repoTypes.stream().
                map(RepoDetailsType::byNativeName).filter(Objects::nonNull).toList();

        List<RepoType> packageTypeList = packageTypes.stream().map(RepoType::fromType).toList();
        Long limit = StringUtils.isEmpty(limitStr) ? Long.parseLong(AUTO_COMPLETE_LIMIT_DEFAULT_VALUE)
                : Long.parseLong(limitStr);
        Long continueStateLong = StringUtils.isEmpty(continueState) ? AUTO_COMPLETE_CONTINUE_STATE_INIT_VALUE :
                Long.parseLong(continueState);

        ContinueIndexPage cip = new ContinueIndexPage(continueStateLong, limit);
        List<AutoCompleteRepoConfigRecord> repos =
                repositoryConfigService.getAllRepoConfigsBySearchPattern(search).stream()
                        .sorted(new RepoKeyAutoCompleteComparator(search)).map(AutoCompleteRepoConfigRecord::valueOf)
                        .toList();

        List<AutoCompleteRepoConfigRecord> filteredRepos =
                filteredReposByParams(repos, repoDetailsTypeList, packageTypeList,
                        projectsKeysList, stagesList);

        Long totalRequiredRepos = null;

        if (continueStateLong.equals(AUTO_COMPLETE_CONTINUE_STATE_INIT_VALUE)) {
            //add to response a total amount of requested repos
            totalRequiredRepos = (long) filteredRepos.size();
        }

        ContinueResult<AutoCompleteRepoConfigRecord> cr = PagingUtils.getPagingFromMultipleFunctions(cip,
                getFetchFunctions(filteredRepos));
        cr.setTotalRequiredDataAmount(totalRequiredRepos);
        response.iModel(cr);
    }

    /**
     * Do not serve federated repos if the federated cleanup feature is disabled
     *
     * @param repoTypes user input repo types
     */
    private List<String> filterIneligibleRepoTypes(List<String> repoTypes) {
        if (!ConstantValues.policyCleanupFederatedPackageEnabled.getBoolean()) {
            return repoTypes.stream().filter(type -> !type.equalsIgnoreCase(RepoDetailsType.FEDERATED.name())).toList();
        }
        return repoTypes;
    }

    private List<AutoCompleteRepoConfigRecord> filteredReposByParams(List<AutoCompleteRepoConfigRecord> repos,
            List<RepoDetailsType> repoTypes, List<RepoType> packageTypes,
            List<String> projectsKeysList, List<String> stagesList) {
        boolean filterByRepoType = !repoTypes.isEmpty();
        boolean filterByPackageType = !packageTypes.isEmpty();
        if (repos.isEmpty()) {
            return Collections.emptyList();
        }

        List<AutoCompleteRepoConfigRecord> filteredRepos = repos.stream().filter(repoConfig -> {
            if (filterByRepoType) {
                RepoDetailsType repoType = repoConfig.repoType();
                if (!repoTypes.contains(repoType)) {
                    return false;
                }
            }
            if (filterByPackageType) {
                RepoType packageType = repoConfig.packageType();
                if (!packageTypes.contains(packageType)) {
                    return false;
                }
            }
            return true;
        }).toList();
        filteredRepos = filterReposByProjects(filteredRepos, projectsKeysList);
        return filterReposByStages(filteredRepos, stagesList);
    }

    private void validateProjectsAccess(List<String> projectsKeysList) {
        for (String projectKey : projectsKeysList) {
            if (!projectsService.isProjectAdmin(projectKey)) {
                throw new AuthorizationRestException();
            }
        }
    }

    private List<AutoCompleteRepoConfigRecord> filterReposByProjects(
            List<AutoCompleteRepoConfigRecord> repos,
            List<String> projectsKeysList) {
        if (projectsKeysList.isEmpty()) {
            return repos;
        }
        Set<String> repoKeys = getRepoKeys(repos);
        Map<String, String> repoKeyToProjectMap = getRepoKeyToProjectMap(repoKeys);
        Set<String> filteredRepoKeys =
                getFilteredRepoKeys(repoKeyToProjectMap, projectsKeysList);
        return repos.stream()
                .filter(repo -> filteredRepoKeys.contains(repo.repoKey())).toList();
    }

    private List<AutoCompleteRepoConfigRecord> filterReposByStages(List<AutoCompleteRepoConfigRecord> repos,
            List<String> stagesList) {
        if (stagesList.isEmpty()) {
            return repos;
        }

        Set<String> normalizedSelected = stagesList.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(env -> !env.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (normalizedSelected.isEmpty()) {
            return repos;
        }

        return repos.stream()
                .map(repo -> enrichWithMatchedStages(
                        repo, normalizedSelected))
                .filter(Objects::nonNull)
                .toList();
    }

    @Nullable
    private AutoCompleteRepoConfigRecord enrichWithMatchedStages(AutoCompleteRepoConfigRecord repo,
            Set<String> normalizedSelected) {
        Set<String> repoEnvs = projectsService
                .getEnvironmentEntities(repo.repoKey()).stream()
                .map(env -> env.getName().toLowerCase())
                .collect(Collectors.toSet());

        List<String> matched = normalizedSelected.stream()
                .filter(repoEnvs::contains)
                .toList();

        if (matched.isEmpty()) {
            return null;
        }
        return repo.withStages(new ArrayList<>(matched));
    }

    private Set<String> getRepoKeys(List<AutoCompleteRepoConfigRecord> repos) {
        return repos.stream().map(AutoCompleteRepoConfigRecord::repoKey).collect(Collectors.toSet());
    }

    private Map<String, String> getRepoKeyToProjectMap(Set<String> repoKeys) {
        return projectsService.getRepoKeyToProjectMap(repoKeys, false);
    }

    private Set<String> getFilteredRepoKeys(Map<String, String> repoKeyToProjectMap, List<String> projectsKeysList) {
        return repoKeyToProjectMap.entrySet().stream().filter(entry -> projectsKeysList.contains(entry.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    public List<FetchFunction<AutoCompleteRepoConfigRecord>>
            getFetchFunctions(List<AutoCompleteRepoConfigRecord> repoConfigs) {
        return List.of(new FetchFunction<>((skip, limit) -> {
            List<AutoCompleteRepoConfigRecord> offsetRepoConfigs =
                    repoConfigs.stream().skip(skip).limit(limit).toList();
            int lastIndex = getLastIndex(repoConfigs, offsetRepoConfigs, limit);
            return new ContinueResult<>(lastIndex, offsetRepoConfigs);
        }, (long) repoConfigs.size()));
    }
}
