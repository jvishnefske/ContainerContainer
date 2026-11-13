/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.artifactory.security.access.embedded.store.EaChange;
import org.jfrog.access.client.permission.GrpcAffectedEntitiesResponse;
import org.jfrog.access.common.AffectedEntitiesType;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.common.SubjectFQN;
import org.jfrog.access.rest.permission.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The {@code grpcPermissions()} surface: the ACL-cache load ({@link #findPermissionsByResourceType}) and the
 * change stream ({@link #onPermissionsAffected}).
 * <p>
 * {@code onPermissionsAffected} subscribes the consumer to the {@code EaChangeBus}, which delivers after commit on
 * its own thread. Events map as follows: {@code PermissionsAffected} becomes an {@code INVALIDATE} event with the
 * user and group names; {@code EverythingChanged} becomes {@code FULL_INVALIDATION}; {@code TokenRevoked} for a
 * user subject becomes an {@code INVALIDATE} event naming that user, so Artifactory evicts the user's cached
 * authentication. A consumer that throws is logged and stays subscribed. Each call adds a subscription, as the
 * stock client does; Artifactory subscribes once per client update.
 */
final class EaGrpcPermissionsClient {

    private static final Logger log = LoggerFactory.getLogger(EaGrpcPermissionsClient.class);

    private final EaRuntime rt;
    private final EaPermissionsClient permissions;

    EaGrpcPermissionsClient(EaRuntime rt, EaPermissionsClient permissions) {
        this.rt = rt;
        this.permissions = permissions;
    }

    /**
     * Subscribes a consumer to permission-affecting changes.
     *
     * @param consumer the consumer
     */
    public void onPermissionsAffected(Consumer<GrpcAffectedEntitiesResponse> consumer) {
        rt.bus().subscribe(change -> toEvent(change).ifPresent(event -> {
            try {
                consumer.accept(event);
            } catch (RuntimeException e) {
                log.warn("Permissions-affected consumer failed", e);
            }
        }));
    }

    /**
     * Lists the permissions that name any of the users or groups.
     *
     * @param userNames  the users
     * @param groupNames the groups
     * @return the permissions
     */
    public List<Permission> listByUserGroups(Collection<String> userNames, Collection<String> groupNames) {
        return new ArrayList<>(permissions.mentioning(userNames, groupNames));
    }

    /**
     * Lists the permissions of a resource type.
     *
     * @param type the resource type
     * @return the permissions
     */
    public List<Permission> findPermissionsByResourceType(ResourceType type) {
        return new ArrayList<>(permissions.list(p -> p.resourceType().equals(type.getKey())));
    }

    /**
     * Maps a bus change to the event Artifactory consumes.
     *
     * @param change the change
     * @return the event, or empty if the change concerns no cached principal
     */
    static Optional<GrpcAffectedEntitiesResponse> toEvent(EaChange change) {
        return switch (change) {
            case EaChange.PermissionsAffected p -> Optional.of(new Affected(List.of(),
                    List.copyOf(p.userNames()), List.copyOf(p.groupNames()), AffectedEntitiesType.INVALIDATE));
            case EaChange.EverythingChanged e -> Optional.of(new Affected(List.of(), List.of(), List.of(),
                    AffectedEntitiesType.FULL_INVALIDATION));
            case EaChange.TokenRevoked t -> userOf(t.subject()).map(user -> new Affected(List.of(),
                    List.of(user), List.of(), AffectedEntitiesType.INVALIDATE));
        };
    }

    private static Optional<String> userOf(String subject) {
        try {
            return SubjectFQN.isUserTokenSubject(subject) ? Optional.of(SubjectFQN.extractUsername(subject))
                    : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** An affected-entities event. */
    record Affected(List<String> getPermissionIds, List<String> getUserNames, List<String> getGroupNames,
            AffectedEntitiesType getType) implements GrpcAffectedEntitiesResponse {
    }
}
