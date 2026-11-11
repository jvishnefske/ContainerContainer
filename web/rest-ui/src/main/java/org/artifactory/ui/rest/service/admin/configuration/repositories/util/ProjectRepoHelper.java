package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import lombok.RequiredArgsConstructor;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.ProjectRepo;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.artifactory.util.CollectionUtils;
import org.jfrog.access.client.project.EnvironmentEntity;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectResourceStatus;
import org.jfrog.access.common.ResourceType;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoProjectAssociation.ASSIGNED;

@Component
@RequiredArgsConstructor
public class ProjectRepoHelper {

    private final ProjectsService projectsService;

    public Map<String, List<ProjectRepo>> getProjectRepos(String projectKey) {
        Map<String, ProjectResourceEntity> projectResourceEntityMap = projectsService
                .getProjectResourceEntitiesMap(projectKey, ResourceType.REPO, false);
        return projectResourceEntityMap.entrySet().stream()
                .map(entry -> new ProjectRepo(
                        entry.getKey(),
                        projectKey,
                        entry.getValue()))
                .collect(Collectors.groupingBy(ProjectRepo::repoKey));
    }

    public Map<String, List<ProjectRepo>> getAllProjectRepos(RepoConfigRequest repoConfigRequest) {
        if (repoConfigRequest.isProjectContext() && repoConfigRequest.getProjectAssociation() == ASSIGNED) {
            return getProjectRepos(repoConfigRequest.getProjectKeys().stream().findFirst().get());
        }

        Map<String, Map<String, ProjectResourceEntity>> allProjectResources =
                projectsService.getAllProjectsRepositoriesMap(false);

        return allProjectResources.entrySet().stream()
                .flatMap(entry -> entry.getValue().entrySet().stream()
                        .map(projectEntry -> new ProjectRepo(
                                projectEntry.getKey(),
                                entry.getKey(),
                                projectEntry.getValue())))
                .collect(Collectors.groupingBy(ProjectRepo::repoKey));
    }

    public ProjectResourceEntity getAssignedProject(String repoKey, String projectKey,
                                                    Map<String, List<ProjectRepo>> allRepoProjects) {
        List<ProjectRepo> projectRepos = allRepoProjects.get(repoKey);
        if (projectRepos == null) {
            return null;
        }

        return projectRepos.stream()
                .filter(projectRepo -> projectKey.equals(projectRepo.projectKey())
                        && isAssignedStatus(projectRepo.resourceEntity()))
                .map(ProjectRepo::resourceEntity)
                .findFirst()
                .orElse(null);
    }

    public boolean hasAssignedStatus(List<ProjectRepo> reposSharedAndAssignedProjects) {
        return reposSharedAndAssignedProjects.stream()
                .anyMatch(entity -> isAssignedStatus(entity.resourceEntity())
                        && !ProjectsService.DEFAULT_PROJECT.equals(entity.projectKey()));
    }

    public boolean hasSharedStatus(List<ProjectRepo> reposSharedAndAssignedProjects) {
        return reposSharedAndAssignedProjects.stream()
                .anyMatch(entity -> isSharedStatus(entity.resourceEntity()));
    }

    public boolean isAssignedStatus(ProjectResourceEntity resourceEntity) {
        ProjectResourceStatus status = resourceEntity.getResourceStatus();
        return status == ProjectResourceStatus.NONE
                || status == ProjectResourceStatus.SHARED
                || status == ProjectResourceStatus.SHARED_WITH_ALL;
    }

    public boolean isSharedStatus(ProjectResourceEntity resourceEntity) {
        ProjectResourceStatus status = resourceEntity.getResourceStatus();
        return status != null && status.isTarget();
    }

    public Set<String> getEnvironments(RepoConfig repoConfig, List<ProjectRepo> reposSharedAndAssignedProjects) {
        if (CollectionUtils.isNullOrEmpty(reposSharedAndAssignedProjects)) {
            return getEnvironmentsForDefaultProject(repoConfig.getKey());
        }
        return getEnvironmentsFromProjectRepos(reposSharedAndAssignedProjects);
    }

    public Set<String> getEnvironmentsForDefaultProject(String repoKey) {
        return projectsService.getEnvironmentEntities(repoKey).stream()
               .map(EnvironmentEntity::getName)
               .collect(Collectors.toSet());
    }

    public Set<String> getEnvironmentsFromProjectRepos(List<ProjectRepo> reposSharedAndAssignedProjects) {
        return reposSharedAndAssignedProjects.stream()
                .findFirst()
                .map(project -> project.resourceEntity().getEnvironments())
                .orElseGet(HashSet::new);
    }
}
