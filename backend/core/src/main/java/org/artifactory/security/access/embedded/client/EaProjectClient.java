/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.jfrog.access.client.project.EnvironmentEntity;
import org.jfrog.access.client.project.ProjectEntity;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.jfrog.access.client.project.RoleActions;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.token.JwtAccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code cachedGrpcProject()} surface, for a server with only the implicit {@code default} project.
 * <p>
 * There are no projects to list, no project admins and no project roles, so every query answers "none": empty
 * lists and maps, {@code null} for a project lookup, {@code false} for every admin or role check. Resource
 * assignments to projects are accepted and dropped (logged at DEBUG), because every resource belongs to
 * {@code default}. The global environments are {@value #DEV} and {@value #PROD}, the platform defaults, so
 * repository environment settings validate.
 */
final class EaProjectClient {

    private static final Logger log = LoggerFactory.getLogger(EaProjectClient.class);

    static final String DEV = "DEV";
    static final String PROD = "PROD";

    /**
     * Looks up a project.
     *
     * @param projectKey the key
     * @return {@code null}: no project exists besides {@code default}
     */
    public ProjectSlimEntity getProjectSlimEntity(String projectKey) {
        return null;
    }

    /**
     * Drops a resource assignment.
     *
     * @param projectKey   the project
     * @param type         the resource type
     * @param resourceName the resource
     * @param environments the environments
     * @param isNew        whether the resource is new
     */
    public void addProjectResource(String projectKey, ResourceType type, String resourceName,
            Set<String> environments, boolean isNew) {
        log.debug("Ignoring project assignment of {} {} to {}", type, resourceName, projectKey);
    }

    /**
     * Drops a resource assignment.
     *
     * @param projectKey the project
     * @param type       the resource type
     * @param resource   the resource
     * @param isNew      whether the resource is new
     */
    public void upsertProjectResource(String projectKey, ResourceType type, ProjectResourceEntity resource,
            boolean isNew) {
        log.debug("Ignoring project assignment of a {} to {}", type, projectKey);
    }

    /**
     * Drops a resource removal.
     *
     * @param projectKey   the project
     * @param type         the resource type
     * @param resourceName the resource
     */
    public void removeProjectResource(String projectKey, ResourceType type, String resourceName) {
        log.debug("Ignoring project removal of {} {} from {}", type, resourceName, projectKey);
    }

    /**
     * Drops a resource sync.
     *
     * @param type      the resource type
     * @param resources the resources
     */
    public void syncResources(ResourceType type, Set<String> resources) {
        log.debug("Ignoring project resource sync of {} {}", resources == null ? 0 : resources.size(), type);
    }

    /**
     * Lists a project's resources.
     *
     * @param projectKey  the project
     * @param type        the resource type
     * @param ignoreCache ignored
     * @return an empty map
     */
    public Map<String, ProjectResourceEntity> getProjectResourceEntitiesMap(String projectKey, ResourceType type,
            boolean ignoreCache) {
        return Map.of();
    }

    /**
     * Finds the project of a resource.
     *
     * @param resourceName the resource
     * @param type         the resource type
     * @return {@code null}: no resource belongs to a named project
     */
    public String getProjectForResource(String resourceName, ResourceType type) {
        return null;
    }

    /**
     * Finds the projects of resources.
     *
     * @param resourceNames the resources
     * @param type          the resource type
     * @param refreshCache  ignored
     * @return an empty map
     */
    public Map<String, String> getProjectForResourceMap(Set<String> resourceNames, ResourceType type,
            boolean refreshCache) {
        return Map.of();
    }

    /**
     * Lists the projects a user manages.
     *
     * @param username the user
     * @param groups   the user's groups
     * @return an empty list
     */
    public List<String> getAllProjectsManagedByUser(String username, Collection<String> groups) {
        return List.of();
    }

    /**
     * Lists project keys.
     *
     * @return an empty list
     */
    public List<String> getAllProjectKeys() {
        return List.of();
    }

    /**
     * Returns a project's display name.
     *
     * @param projectKey the project
     * @return {@code null}
     */
    public String getProjectDisplayName(String projectKey) {
        return null;
    }

    /**
     * Lists projects.
     *
     * @return an empty list
     */
    public List<ProjectEntity> getAllProjects() {
        return List.of();
    }

    /**
     * Checks a project role action.
     *
     * @param username   the user
     * @param projectKey the project
     * @param groups     the groups
     * @param action     the action
     * @return {@code false}
     */
    public boolean canDoAction(String username, String projectKey, Collection<String> groups, RoleActions action) {
        return false;
    }

    /**
     * Checks project admin.
     *
     * @param username   the user
     * @param projectKey the project
     * @param groups     the groups
     * @return {@code false}
     */
    public boolean isProjectAdmin(String username, String projectKey, Collection<String> groups) {
        return false;
    }

    /**
     * Checks project admin by token scope.
     *
     * @param token the token
     * @return {@code false}
     */
    public boolean isAnyProjectAdminByRoleScope(JwtAccessToken token) {
        return false;
    }

    /**
     * Checks project admin of a project by token scope.
     *
     * @param token      the token
     * @param projectKey the project
     * @return {@code false}
     */
    public boolean isProjectAdminByRoleScopeAndProjectKey(JwtAccessToken token, String projectKey) {
        return false;
    }

    /**
     * Checks a project admin's rights on a resource.
     *
     * @param resourceName the resource
     * @param type         the resource type
     * @param actions      the actions
     * @param username     the user
     * @param groups       the groups
     * @return {@code false}
     */
    public boolean canProjectAdminDoOnResource(String resourceName, ResourceType type,
            Collection<RoleActions> actions, String username, Collection<String> groups) {
        return false;
    }

    /**
     * Lists the global environments.
     *
     * @return {@value #DEV} and {@value #PROD}
     */
    public List<String> getGlobalEnvironments() {
        return List.of(DEV, PROD);
    }

    /**
     * Lists a project's environments.
     *
     * @param projectKey the project
     * @return an empty list
     */
    public List<String> getEnvironments(String projectKey) {
        return List.of();
    }

    /**
     * Lists the environment entities of a resource.
     *
     * @param resourceName the resource
     * @param type         the resource type key
     * @return an empty list
     */
    public List<EnvironmentEntity> getEnvironmentsEntity(String resourceName, String type) {
        return List.of();
    }

    /**
     * Lists the projects of a user and groups.
     *
     * @param username the user
     * @param groups   the groups
     * @return an empty list
     */
    public List<String> getProjectKeysByUsernameAndGroupNames(String username, Collection<String> groups) {
        return List.of();
    }
}
