/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.service.onboarding;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.onboarding.CreateQuickReposModel;
import org.artifactory.ui.rest.model.onboarding.QuickRepositoryModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.CreateRepositoryConfigService;
import org.artifactory.ui.utils.DefaultRepoCreator;
import org.jfrog.security.util.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for creating default repositories for repo types stated in CreateDefaultReposModel
 *
 * @author nadavy
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
public class CreateQuickReposService implements RestService<CreateQuickReposModel> {

    private CreateRepositoryConfigService createRepoService;
    private RepositoryConfigService repositoryConfigService;
    private AddonsManager addonsManager;
    private ProjectsService projectsService;
    private CentralConfigService centralConfigService;
    private final DefaultRepoCreator defaultRepoCreator = new DefaultRepoCreator();

    @Override
    public void execute(ArtifactoryRestRequest<CreateQuickReposModel> request, RestResponse response) {
        CreateQuickReposModel reposToCreate = request.getImodel();
        createDefaultRepositoriesFromModel(response, reposToCreate);
    }

    /**
     * Create default repositories as defined in defaultRepository.json
     */
    private void createDefaultRepositoriesFromModel(RestResponse<CreateDefaultReposResponseModel> response,
            CreateQuickReposModel reposToCreate) {
        CreateDefaultReposResponseModel createDefaultReposResponseModel = new CreateDefaultReposResponseModel();
        boolean isSupportRemoteRepositories = !addonsManager.isEntitled(EntitlementType.EDGE);
        List<QuickRepositoryModel> repositories = reposToCreate.getRepositories();
        repositories.forEach(model -> projectsService
                .validateRepoConfigIsMatchingToProjectSpec(model.getProject(), model.getRepoName(), model.getEnvs()));
        validateProjects(repositories);
        List<RepoConfig> repoConfigs = repositories.stream()
                .map(repoModel -> defaultRepoCreator.createQuickRepo(createRepoService, response, repoModel,
                        createDefaultReposResponseModel, isSupportRemoteRepositories)).toList();
        if (createDefaultReposResponseModel.isValid()) {
            handleValidRepoCreation(createDefaultReposResponseModel);
            try {
                // TODO [M.M] This usage as well as repo config collecting can be removed after middle phase removal
                repositoryConfigService.bulkInsertRepoConfigs(repoConfigs,
                        true, true, true);
            } catch (RuntimeException e) {
                removeRepositoriesFromProject(createDefaultReposResponseModel);
                String errorMessage = "Failed to quick create repositories. Details: " + e.getMessage();
                log.warn(errorMessage, e);
                throw e;
            }
            response.iModel(createDefaultReposResponseModel).responseCode(HttpStatus.SC_CREATED);
        } else {
            response.responseCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void removeRepositoriesFromProject(CreateDefaultReposResponseModel reposModel) {
        reposModel.getProjectReposToAdd().forEach(projectIdentifier -> {
            try {
                projectsService.removeRepositoryFromCurrentProject(projectIdentifier.repoKey());
            } catch (Exception e) {
                log.error("Failed to remove repository from project: '{}'", projectIdentifier, e);
            }
        });
    }

    private void validateProjects(List<QuickRepositoryModel> repositories) {
        Map<String, Set<String>> projectRepos = new HashMap<>();
        repositories.forEach(repo -> projectRepos.computeIfAbsent(repo.getProject(), k -> new HashSet<>())
                .add(repo.getRepoName()));
        projectRepos.keySet().stream()
                .map(project -> new Pair<>(project, projectsService.getProjectRepositoriesKeys(project, false)))
                .forEach(pair -> projectRepos.get(pair.getFirst()).addAll(pair.getSecond()));
        repositories.stream()
                .filter(r -> RepositoryType.VIRTUAL.equals(r.getRepoType()))
                .forEach(virtual -> assertIncludedRepositoriesInSameProject(projectRepos, virtual));
    }

    private void assertIncludedRepositoriesInSameProject(Map<String, Set<String>> projectRepos,
            QuickRepositoryModel virtual) {
        if (ProjectsUtils.isDefaultProject(virtual.getProject())) {
            // projectsService#getProjectRepositoriesKeys will not include non-project associated repos.
            return;
        }
        Set<String> repos = projectRepos.get(virtual.getProject());
        Set<String> includedRepos = new HashSet<>();
        List<String> localRepositories = virtual.getIncludedLocalRepositories();
        if (localRepositories != null) {
            includedRepos.addAll(localRepositories);
        }
        List<String> remoteRepositories = virtual.getIncludedRemoteRepositories();
        if (remoteRepositories != null) {
            includedRepos.addAll(remoteRepositories);
        }
        includedRepos.removeIf(repos::contains); // remove repos newly assigned to the project
        Set<String> projectRepositoriesKeys =
                projectsService.getProjectRepositoriesKeys(virtual.getProject(), false);
        includedRepos.removeAll(projectRepositoriesKeys); // remove existing repos from project
        if (!includedRepos.isEmpty()) { // if something remains, it's associated to other project
            var error = String.format("Cannot create virtual repository '%s' in project '%s', " +
                            "one or more included repositories are not in the same project",
                    virtual.getRepoName(), virtual.getProject());
            log.debug("{} - {}", error, includedRepos);
            throw new IllegalArgumentException(error);
        }
    }

    /**
     * Handles valid repo creation from REST UI -
     * save and reload config descriptor
     * and export settings to yaml (if from onboarding)
     */
    private void handleValidRepoCreation(CreateDefaultReposResponseModel reposModel) {
        MutableCentralConfigDescriptor updatedConfigDescriptor = defaultRepoCreator.getConfigDescriptor();
        int numberOfDiffs = centralConfigService.findDiff(centralConfigService.getDescriptor(),
                updatedConfigDescriptor).getNumberOfDiffs();
        if (numberOfDiffs > 0) {
            centralConfigService
                    .saveEditedDescriptorAndReload(updatedConfigDescriptor);
        }
        reposModel.getProjectReposToAdd().forEach(this::addRepositoryToProject);
    }

    private void addRepositoryToProject(CreateDefaultReposResponseModel.ProjectIdentifier projectIdentifier) {
        String repoKey = projectIdentifier.repoKey();
        String projectKey = projectIdentifier.projectKey();
        Set<String> environments = projectIdentifier.envs();
        projectsService.upsertProjectRepository(repoKey, projectKey, environments);
    }

    @Autowired
    void setCreateRepoService(CreateRepositoryConfigService createRepoService) {
        this.createRepoService = createRepoService;
    }

    @Autowired
    void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }
}
