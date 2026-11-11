package org.artifactory.metrics.providers.features;

import lombok.NonNull;
import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.projects.ProjectsService;
import org.jfrog.access.client.project.ProjectEntity;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectResourcesEntity;
import org.jfrog.access.rest.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Oleksandr Legenkiy
 */
@Component
public class ProjectsFeature implements CallHomeFeature {

    private static final String REPO_RESOURCE_KEY = "repo";
    private static final String DEFAULT_PROJECT_KEY = "default";

    private static final String MAIN_FEATURE_NAME = "projects";
    private static final String TOTAL_PROJECTS_NO = "total_projects";
    private static final String PROJECT_KEY = "project_key";
    private static final String PROJECT_NAME = "project_name";
    private static final String ADMINS_CAN_CREATE_REPOS = "project_admins_can_create_repositories";
    private static final String PROJECT_ADMINS = "project_admins";
    private static final String USERS = "users";
    private static final String GROUPS = "groups";
    private static final String STORAGE_QUOTA = "storage_quota";
    private static final String STORAGE_QUOTA_ENABLED = "storage_quota_enabled";
    private static final String STORAGE_QUOTA_LIMIT_BYTES = "storage_quota_limit_bytes";
    private static final String RESOURCES = "resources";
    private static final String ENVIRONMENTS = "environments";
    private static final String SHARED_WITH_PROJECTS = "shared_with_projects";
    private static final String PROJECT_SHARED_REPOS = "project_shared_repos";

    private final ProjectsService projectsService;

    @Autowired
    public ProjectsFeature(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Override
    public FeatureGroup getFeature() {
        FeatureGroup feature = new FeatureGroup(MAIN_FEATURE_NAME);
        // Get the complete list of all project entities.
        List<ProjectEntity> entities = projectsService.getAllProjectsEntities();
        // Add a "Total number of projects" attribute. Exclude the default project from the count.
        int totalProjects = entities.isEmpty() ? 0 : entities.size() - 1;
        feature.addFeatureAttribute(TOTAL_PROJECTS_NO, totalProjects);
        // Add features for all projects.
        for (ProjectEntity project : entities) {
            if (!DEFAULT_PROJECT_KEY.equals(project.getProjectKey())) {
                populateProjectFeature(feature, project);
            }
        }
        return feature;
    }

    private void populateProjectFeature(@NonNull FeatureGroup parentFeature, @NonNull ProjectEntity project) {
        // Create a project feature.
        FeatureGroup projectFeature = new FeatureGroup(project.getName());
        parentFeature.addFeature(projectFeature); // Add project description to the main feature.
        // Attribute project key.
        projectFeature.addFeatureAttribute(PROJECT_KEY, project.getProjectKey());
        // Attribute project name.
        projectFeature.addFeatureAttribute(PROJECT_NAME, project.getName());
        // Attribute project admins can create repositories.
        projectFeature.addFeatureAttribute(ADMINS_CAN_CREATE_REPOS, project.isProjectAdminCanManageResources());
        // Attribute number of project admins.
        Set<User> admins = projectsService.getProjectAdmins(project);
        projectFeature.addFeatureAttribute(PROJECT_ADMINS, admins.size());
        // Attribute number of user members.
        projectFeature.addFeatureAttribute(USERS, project.getUsers().keySet().size());
        // Attribute number of group members.
        projectFeature.addFeatureAttribute(GROUPS, project.getGroups().keySet().size());
        // Create "Storage Quota" feature.
        FeatureGroup storageQuotaFeature = new FeatureGroup(STORAGE_QUOTA);
        projectFeature.addFeature(storageQuotaFeature);
        storageQuotaFeature.addFeatureAttribute(STORAGE_QUOTA_ENABLED, project.getStorageQuotaBytes() > 0);
        storageQuotaFeature.addFeatureAttribute(STORAGE_QUOTA_LIMIT_BYTES, project.getStorageQuotaBytes());
        // Init shared repos counter.
        int sharedReposCounter = 0;
        // Create "resources" feature.
        FeatureGroup resourcesFeature = new FeatureGroup(RESOURCES);
        projectFeature.addFeature(resourcesFeature);
        Map<String, ProjectResourcesEntity> resourcesMap = project.getResources();
        for (Map.Entry<String, ProjectResourcesEntity> resourceEntry : resourcesMap.entrySet()) {
            // Create "resourceKey" feature such as "repo" etc.
            FeatureGroup resourceKeyFeature = new FeatureGroup(resourceEntry.getKey());
            resourcesFeature.addFeature(resourceKeyFeature);
            for (ProjectResourceEntity projResEntity : resourceEntry.getValue().getResources().values()) {
                // Create an actual resource feature.
                FeatureGroup resourceFeature = new FeatureGroup(projResEntity.getResourceName());
                resourceKeyFeature.addFeature(resourceFeature);
                // Populate environments info.
                resourceFeature.setFeatureAttribute(
                        Collections.singletonMap(ENVIRONMENTS, projResEntity.getEnvironments()));
                // Populate shared with projects info.
                if (!projResEntity.getSharedWithProjects().isEmpty()) {
                    resourceFeature.setFeatureAttribute(
                            Collections.singletonMap(SHARED_WITH_PROJECTS, projResEntity.getSharedWithProjects()));
                }
                // Update the shared repos counter.
                if (REPO_RESOURCE_KEY.equals(resourceEntry.getKey()) &&
                        !projResEntity.getSharedWithProjects().isEmpty()) {
                    sharedReposCounter++;
                }
            }
        }
        // Add project shared repos counter attribute
        projectFeature.addFeatureAttribute(PROJECT_SHARED_REPOS, sharedReposCounter);
    }

}
