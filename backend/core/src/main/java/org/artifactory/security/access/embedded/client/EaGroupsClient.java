/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.core.EaGroup;
import org.jfrog.access.rest.group.Group;
import org.jfrog.access.rest.group.GroupRequest;
import org.jfrog.access.rest.group.GroupResponse;
import org.jfrog.access.rest.group.Groups;
import org.jfrog.access.rest.group.GroupsResponse;
import org.jfrog.access.rest.group.ManageGroupMembersRequest;
import org.jfrog.access.rest.group.UpdateGroupRequest;
import org.jfrog.access.rest.user.UserResponse;
import org.jfrog.access.rest.user.Users;
import org.jfrog.access.rest.user.UsersResponse;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code groups()} surface: group CRUD and membership over the store. The {@code artifactory_admin} custom
 * datum maps to the group's admin column (see {@link EaModels}).
 */
final class EaGroupsClient {

    private final EaRuntime rt;

    EaGroupsClient(EaRuntime rt) {
        this.rt = rt;
    }

    /**
     * Creates a group.
     *
     * @param request the group
     * @return the stored group
     */
    public Group createGroup(GroupRequest request) {
        EaGroup group = EaErrors.orThrow(EaModels.fromGroupRequest(request, rt.now(), null));
        return EaModels.toGroup(EaErrors.orThrow(rt.store().createGroup(group)));
    }

    /**
     * Reads a group.
     *
     * @param name the name
     * @return the group, or empty
     */
    public Optional<Group> findGroupByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return EaErrors.orThrow(rt.store().getGroup(name)).map(EaModels::toGroup);
    }

    /**
     * Lists every group.
     *
     * @return the groups
     */
    public Groups findGroups() {
        return wrap(EaErrors.orThrow(rt.store().listGroups()).stream().map(EaModels::toGroup).toList());
    }

    /**
     * Lists the named groups that exist.
     *
     * @param names the names
     * @return the groups, by name
     */
    public Groups findGroupsByNames(Iterable<? extends String> names) {
        Set<String> wanted = new HashSet<>();
        names.forEach(wanted::add);
        return wrap(EaErrors.orThrow(rt.store().listGroups()).stream().filter(g -> wanted.contains(g.name()))
                .map(EaModels::toGroup).toList());
    }

    /**
     * Maps external ids to group names.
     *
     * @param externalIds the external ids
     * @return external id to group name, for the ids that match a group
     */
    public Map<String, String> findGroupsNameByExternalIds(List<String> externalIds) {
        Set<String> wanted = new HashSet<>(externalIds);
        Map<String, String> out = new LinkedHashMap<>();
        for (EaGroup g : EaErrors.orThrow(rt.store().listGroups())) {
            if (g.externalId() != null && wanted.contains(g.externalId())) {
                out.putIfAbsent(g.externalId(), g.name());
            }
        }
        return out;
    }

    /**
     * Deletes a group; memberships go with it.
     *
     * @param name the name
     */
    public void deleteGroup(String name) {
        EaErrors.orThrow(rt.store().deleteGroup(name));
    }

    /**
     * Replaces a group; the creation time is kept.
     *
     * @param request the new state
     * @return the stored group
     */
    public Group replaceGroup(GroupRequest request) {
        EaGroup current = require(request.getName());
        EaGroup group = EaErrors.orThrow(EaModels.fromGroupRequest(request, rt.now(), current.created()));
        return EaModels.toGroup(EaErrors.orThrow(rt.store().updateGroup(group)));
    }

    /**
     * Applies a merge patch to a group.
     *
     * @param request the patch
     * @return the stored group
     */
    public Group updateGroup(UpdateGroupRequest request) {
        EaGroup current = require(request.getName());
        EaGroup patched = EaErrors.orThrow(EaModels.applyGroupPatch(current, request.toJsonMerge(), rt.now()));
        return EaModels.toGroup(EaErrors.orThrow(rt.store().updateGroup(patched)));
    }

    /**
     * Lists a group's members.
     *
     * @param name the group
     * @return the members
     */
    public Users findGroupUsers(String name) {
        List<UserResponse> users = EaErrors.orThrow(rt.store().listUsersInGroup(name)).stream()
                .map(EaModels::toUser).toList();
        return new UsersResponse().users(users);
    }

    /**
     * Adds and removes members.
     *
     * @param name    the group
     * @param request the users to add and remove
     */
    public void manageGroupUsers(String name, ManageGroupMembersRequest request) {
        Set<String> add = request.getUsersToAdd();
        Set<String> remove = request.getUsersToRemove();
        if (add != null && !add.isEmpty()) {
            EaErrors.orThrow(rt.store().addGroupMembers(name, Set.copyOf(add)));
        }
        if (remove != null && !remove.isEmpty()) {
            EaErrors.orThrow(rt.store().removeGroupMembers(name, Set.copyOf(remove)));
        }
    }

    private EaGroup require(String name) {
        if (name == null) {
            throw EaErrors.http(EaErrors.BAD_REQUEST, "group name is missing");
        }
        return EaErrors.orThrow(rt.store().getGroup(name))
                .orElseThrow(() -> EaErrors.http(EaErrors.NOT_FOUND, "no such group: " + name));
    }

    private static Groups wrap(List<GroupResponse> groups) {
        return new GroupsResponse().groups(groups);
    }
}
