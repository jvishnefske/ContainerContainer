/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.core.EaPermission;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.common.Action;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.rest.group.GroupResponse;
import org.jfrog.access.rest.permission.Permission;
import org.jfrog.access.rest.permission.PermissionRequest;
import org.jfrog.access.rest.permission.PermissionResponse;
import org.jfrog.access.rest.permission.Permissions;
import org.jfrog.access.rest.permission.PermissionsResponse;
import org.jfrog.access.rest.permission.UpdatePermissionRequest;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The {@code permissions()} surface: permission-target CRUD over the store.
 * <p>
 * A permission's name is also its id. A create request without a name or id gets a random one; Artifactory keeps
 * the returned id as the ACL's access identifier. Two permissions of one resource type may not share a display
 * name (409). {@link #lastUpdated()} is the store's {@code permissions.last_updated} stamp, which every user,
 * group and permission change advances, so Artifactory's ACL-cache polling sees every change.
 */
final class EaPermissionsClient {

    private final EaRuntime rt;

    EaPermissionsClient(EaRuntime rt) {
        this.rt = rt;
    }

    /**
     * Creates a permission.
     *
     * @param request the permission
     * @return the stored permission
     */
    public Permission createPermission(PermissionRequest request) {
        String name = request.getName() != null ? request.getName()
                : request.getId() != null ? request.getId() : UUID.randomUUID().toString();
        EaPermission p = EaErrors.orThrow(EaModels.fromPermissionRequest(request, name,
                rt.artifactoryServiceId().getFormattedName(), rt.now(), null, false));
        if (!EaErrors.orThrow(rt.store().findPermissions(p.resourceType(), p.displayName())).isEmpty()) {
            throw EaErrors.http(EaErrors.CONFLICT, "permission already exists: " + p.displayName());
        }
        return EaModels.toPermission(EaErrors.orThrow(rt.store().createPermission(p)));
    }

    /**
     * Replaces a permission, found by name, id, or resource type and display name.
     *
     * @param request the new state
     * @return the stored permission
     */
    public Permission replacePermission(PermissionRequest request) {
        EaPermission current = locate(request);
        EaPermission p = EaErrors.orThrow(EaModels.fromPermissionRequest(request, current.name(),
                current.serviceId(), rt.now(), current.created(), current.implicit()));
        return EaModels.toPermission(EaErrors.orThrow(rt.store().updatePermission(p)));
    }

    /**
     * Applies a merge patch to a permission found by name or id.
     *
     * @param request the patch
     * @return the stored permission
     */
    public Permission updatePermission(UpdatePermissionRequest request) {
        EaPermission current = locate(request);
        EaPermission p = EaErrors.orThrow(EaModels.applyPermissionPatch(current, request.toJsonMerge(), rt.now()));
        return EaModels.toPermission(EaErrors.orThrow(rt.store().updatePermission(p)));
    }

    /**
     * Reads a permission by name.
     *
     * @param name the name
     * @return the permission, or empty
     */
    public Optional<Permission> findPermissionByName(String name) {
        return get(name).map(EaModels::toPermission);
    }

    /**
     * Reads a permission by id, which is its name.
     *
     * @param id the id
     * @return the permission, or empty
     */
    public Optional<Permission> findPermissionById(String id) {
        return findPermissionByName(id);
    }

    /**
     * Reads a permission by resource type and display name.
     *
     * @param type        the resource type
     * @param displayName the display name
     * @return the permission, or empty
     */
    public Optional<Permission> findByResourceTypeAndDisplayName(ResourceType type, String displayName) {
        return byTypeAndDisplayName(type, displayName).map(EaModels::toPermission);
    }

    /**
     * Deletes a permission by resource type and display name.
     *
     * @param type        the resource type
     * @param displayName the display name
     */
    public void deleteByResourceTypeAndDisplayName(ResourceType type, String displayName) {
        EaPermission p = byTypeAndDisplayName(type, displayName).orElseThrow(
                () -> EaErrors.http(EaErrors.NOT_FOUND, "no such permission: " + displayName));
        EaErrors.orThrow(rt.store().deletePermission(p.name()));
    }

    /**
     * Deletes a permission by name.
     *
     * @param name the name
     */
    public void deletePermissionByName(String name) {
        EaErrors.orThrow(rt.store().deletePermission(name));
    }

    /**
     * Deletes a permission by id, which is its name.
     *
     * @param id the id
     */
    public void deletePermissionById(String id) {
        deletePermissionByName(id);
    }

    /**
     * Lists every permission.
     *
     * @return the permissions
     */
    public Permissions findPermissions() {
        return wrap(list(p -> true));
    }

    /**
     * Lists the permissions of a resource type.
     *
     * @param type the resource type
     * @return the permissions
     */
    public Permissions findPermissionsByResourceType(ResourceType type) {
        return wrap(list(p -> p.resourceType().equals(type.getKey())));
    }

    /**
     * Lists the permissions that name any of the users or groups.
     *
     * @param userNames  the users
     * @param groupNames the groups
     * @return the permissions
     */
    public Permissions findPermissionByUserNamesOrGroupNames(List<String> userNames, List<String> groupNames) {
        return wrap(mentioning(userNames, groupNames));
    }

    /**
     * Lists the permissions of a resource type that grant an action to a user principal or one of its groups.
     *
     * @param principal the principal; only user principals have grants
     * @param type      the resource type
     * @param action    the action
     * @param useCache  ignored
     * @return the permissions
     */
    public Permissions findPrincipalPermissionsByResourceTypeAndAction(AccessPrincipal principal,
            ResourceType type, Action action, boolean useCache) {
        if (!(principal instanceof UserPrincipal user)) {
            return wrap(List.of());
        }
        String username = user.user().getUsername();
        Set<String> groups = new HashSet<>();
        user.user().getGroups().stream().map(GroupResponse::getName).forEach(groups::add);
        String code = action.name();
        return wrap(list(p -> p.resourceType().equals(type.getKey())
                && (grants(p.userActions(), Set.of(username), code) || grants(p.groupActions(), groups, code))));
    }

    /**
     * Returns the store's change stamp.
     *
     * @return epoch milliseconds of the last user, group or permission change
     */
    public long lastUpdated() {
        return EaErrors.orThrow(rt.store().permissionsLastUpdated());
    }

    // ---- shared with the gRPC surface ----

    /**
     * Lists the permissions matching a predicate, as Access models.
     *
     * @param filter the predicate
     * @return the permissions
     */
    List<PermissionResponse> list(Predicate<EaPermission> filter) {
        return EaErrors.orThrow(rt.store().listPermissions()).stream().filter(filter).map(EaModels::toPermission)
                .toList();
    }

    /**
     * Lists the permissions that name any of the users or groups.
     *
     * @param userNames  the users, or {@code null}
     * @param groupNames the groups, or {@code null}
     * @return the permissions
     */
    List<PermissionResponse> mentioning(Collection<String> userNames, Collection<String> groupNames) {
        Set<String> users = userNames == null ? Set.of() : Set.copyOf(userNames);
        Set<String> groups = groupNames == null ? Set.of() : Set.copyOf(groupNames);
        return list(p -> users.stream().anyMatch(u -> p.mentions(u, false))
                || groups.stream().anyMatch(g -> p.mentions(g, true)));
    }

    private static boolean grants(Map<String, Set<String>> actions, Set<String> principals, String code) {
        return principals.stream().anyMatch(name -> actions.getOrDefault(name, Set.of()).contains(code));
    }

    private Optional<EaPermission> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return EaErrors.orThrow(rt.store().getPermission(name));
    }

    private Optional<EaPermission> byTypeAndDisplayName(ResourceType type, String displayName) {
        return EaErrors.orThrow(rt.store().findPermissions(type.getKey(), displayName)).stream().findFirst();
    }

    private EaPermission locate(PermissionRequest request) {
        Optional<EaPermission> found = get(request.getName()).or(() -> get(request.getId()));
        if (found.isEmpty() && request.getResourceType() != null && request.getDisplayName() != null) {
            found = byTypeAndDisplayName(request.getResourceType(), request.getDisplayName());
        }
        return found.orElseThrow(() -> EaErrors.http(EaErrors.NOT_FOUND,
                "no such permission: " + (request.getId() != null ? request.getId() : request.getDisplayName())));
    }

    private static Permissions wrap(List<PermissionResponse> list) {
        return new PermissionsResponse().permissions(list);
    }
}
