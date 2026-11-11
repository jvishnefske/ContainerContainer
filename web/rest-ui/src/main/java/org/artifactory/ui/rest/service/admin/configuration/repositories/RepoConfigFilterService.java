package org.artifactory.ui.rest.service.admin.configuration.repositories;

import lombok.RequiredArgsConstructor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.ProjectRepo;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoProjectAssociation;
import org.artifactory.ui.rest.service.admin.configuration.repositories.mapper.RepoConfigMapper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.ProjectRepoHelper;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.FederatedMemberUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isHiddenRepo;

@Service
@RequiredArgsConstructor
public class RepoConfigFilterService {

    private final ProjectRepoHelper projectRepoHelper;
    private final RepoConfigMapper repoConfigMapper;

    public boolean match(RepoConfig repo, RepoConfigRequest request, Map<String,
            List<ProjectRepo>> allProjectRepos, Set<String> inActiveFederatedRepos) {
        return matchRepoKeys(repo, request)
                && matchRepoTypes(repo, request)
                && matchPackageTypes(repo, request)
                && matchUrl(repo, request)
                && matchRepoKeySearch(repo, request)
                && matchActiveStatus(repo, request, inActiveFederatedRepos)
                && matchReplications(repo, request)
                && matchFederated(repo, request)
                && matchNonSystemRepo(repo)
                && matchNonHiddenRepos(repo)
                && matchNonPipeInfoRepos(repo)
                && matchProjectAndEnvironment(repo, request, allProjectRepos);
    }

    private boolean matchRepoKeys(RepoConfig repo, RepoConfigRequest request) {
        List<String> repoKeys = request.getRepoKeys();
        return repoKeys == null || (!repoKeys.isEmpty() && repoKeys.contains(repo.getKey()));
    }

    private boolean matchRepoTypes(RepoConfig repo, RepoConfigRequest request) {
        return CollectionUtils.isNullOrEmpty(request.getRepoTypes())
                || request.getRepoTypes().contains(repo.getRepoType());
    }

    private boolean matchPackageTypes(RepoConfig repo, RepoConfigRequest request) {
        return CollectionUtils.isNullOrEmpty(request.getPackageTypes())
                || request.getPackageTypes().contains(repo.getPackageType());
    }

    private boolean matchUrl(RepoConfig repo, RepoConfigRequest request) {
        if (request.getUrl() == null) {
            return true;
        }
        return repo.getUrl() != null && repo.getUrl().contains(request.getUrl());
    }

    private boolean matchRepoKeySearch(RepoConfig repo, RepoConfigRequest request) {
        return request.getRepoKeyToSearch() == null
                || repo.getKey().toLowerCase().contains(request.getRepoKeyToSearch().toLowerCase());
    }

    private boolean matchActiveStatus(RepoConfig repo, RepoConfigRequest request, Set<String> inActiveFederatedRepos) {
        if (request.getInactive() == null) {
            return true;
        }
        boolean isActive = repoConfigMapper.isActive(repo, inActiveFederatedRepos);
        return !isActive == request.getInactive();
    }

    private boolean matchReplications(RepoConfig repo, RepoConfigRequest request) {
        if (request.getReplications() == null) {
            return true;
        }
        boolean hasReplication = repoConfigMapper.hasReplication(repo);
        return hasReplication == request.getReplications();
    }

    private boolean matchFederated(RepoConfig repo, RepoConfigRequest request) {
        if (request.getFederated() == null) {
            return true;
        }
        if (request.isFedAttrOptIn()) {
            var fed = repo.getBaseConfig().getFederationConfig();
            fed.setMembers(FederatedMemberUtils.membersWithSelfMember(fed.getMembers(), repo.getKey()));
        }
        return repo.isFederated() == request.getFederated();
    }


    private boolean matchNonSystemRepo(RepoConfig repo) {
        if (SystemRepos.BUILDINFO_REPO_KEY.equals(repo.getKey())) {
            return true;
        }
        return !SystemRepos.SystemRepoConfig.isSystemRepo(repo.getKey());
    }

    private boolean matchNonHiddenRepos(RepoConfig repo) {
        return !isHiddenRepo(repo.getKey(), false);
    }

    private boolean matchNonPipeInfoRepos(RepoConfig repo) {
        return repo.getPackageType() != RepoType.PipeInfo;
    }

    private boolean matchProjectAndEnvironment(RepoConfig repoConfig, RepoConfigRequest request,
                                               Map<String, List<ProjectRepo>> allProjectRepos) {
        Set<String> projectKeys = request.getProjectKeys();
        RepoProjectAssociation association = getAssociationOrDefault(request);
        List<ProjectRepo> projectRepos = allProjectRepos.get(repoConfig.getKey());

        if (withoutProjectFilter(projectKeys, association)
                || defaultProjectWithUnAssignedAndNotSharedAssociation(projectRepos, association)) {
            return matchEnvironments(repoConfig, request.getEnvironments(), projectRepos);
        }
        if (CollectionUtils.isNullOrEmpty(projectRepos)) {
            return false;
        }

        List<ProjectRepo> reposSharedAndAssignedProjectsFilteredByProjectKeys = projectRepos.stream()
                .filter(projectKeyResource -> CollectionUtils.isNullOrEmpty(projectKeys)
                        || projectKeys.contains(projectKeyResource.projectKey()))
                .toList();

        return !reposSharedAndAssignedProjectsFilteredByProjectKeys.isEmpty()
                && matchesAssociation(association, reposSharedAndAssignedProjectsFilteredByProjectKeys)
                && matchEnvironments(repoConfig, request.getEnvironments(),
                reposSharedAndAssignedProjectsFilteredByProjectKeys);
    }

    private boolean matchesAssociation(RepoProjectAssociation repoProjectAssociation,
                                       List<ProjectRepo> reposSharedAndAssignedProjects) {
        boolean hasNonDefaultProjects = reposSharedAndAssignedProjects.stream()
                .anyMatch(project -> !ProjectsService.DEFAULT_PROJECT.equals(project.projectKey()));

        return switch (repoProjectAssociation) {
            case ALL -> true;
            case UN_ASSIGNED_AND_NOT_SHARED -> false;
            case ASSIGNED_AND_SHARED -> hasNonDefaultProjects;
            case ASSIGNED -> projectRepoHelper.hasAssignedStatus(reposSharedAndAssignedProjects);
            case UN_ASSIGNED -> !projectRepoHelper.hasAssignedStatus(reposSharedAndAssignedProjects);
            case SHARED -> projectRepoHelper.hasSharedStatus(reposSharedAndAssignedProjects);
        };
    }

    private boolean matchEnvironments(RepoConfig repo, Set<String> environments,
                                      List<ProjectRepo> reposSharedAndAssignedProjects) {
        if (environments == null) {
            return true;
        }
        Set<String> reposEnvironments = projectRepoHelper.getEnvironments(repo, reposSharedAndAssignedProjects);
        return environments.stream().anyMatch(reposEnvironments::contains);
    }

    private boolean withoutProjectFilter(Set<String> projectKeys, RepoProjectAssociation association) {
        return CollectionUtils.isNullOrEmpty(projectKeys)
                && association != RepoProjectAssociation.UN_ASSIGNED_AND_NOT_SHARED
                && association != RepoProjectAssociation.UN_ASSIGNED;
    }

    private boolean defaultProjectWithUnAssignedAndNotSharedAssociation(List<ProjectRepo> projectRepos,
                                                                        RepoProjectAssociation association) {
        return CollectionUtils.isNullOrEmpty(projectRepos)
                && (association == RepoProjectAssociation.UN_ASSIGNED_AND_NOT_SHARED
                || association == RepoProjectAssociation.UN_ASSIGNED);
    }

    private RepoProjectAssociation getAssociationOrDefault(RepoConfigRequest request) {
        return request.getProjectAssociation() != null ?
                request.getProjectAssociation() : RepoProjectAssociation.ALL;
    }
}
