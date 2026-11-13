/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.security.access.embedded.core.EaGroup;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.core.EaPermission;
import org.artifactory.security.access.embedded.core.EaUser;
import org.jfrog.access.common.Action;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.model.Realm;
import org.jfrog.access.model.UserStatus;
import org.jfrog.access.rest.group.GroupRequest;
import org.jfrog.access.rest.group.GroupResponse;
import org.jfrog.access.rest.permission.PermissionActionsRequest;
import org.jfrog.access.rest.permission.PermissionActionsResponse;
import org.jfrog.access.rest.permission.PermissionRequest;
import org.jfrog.access.rest.permission.PermissionResponse;
import org.jfrog.access.rest.user.CustomDatumRequest;
import org.jfrog.access.rest.user.UserRequest;
import org.jfrog.access.rest.user.UserResponse;
import org.jfrog.access.rest.user.UserWithGroupsResponse;
import org.jfrog.access.user.UserMFAStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Pure conversions between the embedded access records and the public Access client models.
 * <p>
 * User and group flags that Artifactory keeps as custom data are stored in their own columns and put back on the
 * way out: {@code artifactory_admin} maps to {@link EaUser#admin()} and {@link EaGroup#adminPrivileges()}, and
 * {@value #MUST_CHANGE_PASSWORD_KEY} maps to {@link EaUser#mustChangePassword()}. Access user fields that
 * {@link EaUser} has no column for (first and last name, the password-expired flag) are kept in custom data under
 * the reserved prefix {@value #HIDDEN_PREFIX}, which callers never see and cannot write.
 * <p>
 * Updates are merge patches: the Access {@code Update*Request} types record which fields were set, and
 * {@code toJsonMerge()} serialises only those, so a field absent from that JSON is left unchanged and a custom-data
 * entry with a {@code null} value is deleted. Password hashes are never returned.
 */
final class EaModels {

    /** The custom-data key of the IA5 must-change-password flag, as Artifactory's {@code UserMapper} uses it. */
    static final String MUST_CHANGE_PASSWORD_KEY = "must_change_password";
    /** The custom-data key of the administrator flag on users and groups. */
    static final String ADMIN_KEY = "artifactory_admin";
    /** The prefix of custom-data keys reserved for fields the records have no column for. */
    static final String HIDDEN_PREFIX = "_ea.";
    static final String FIRST_NAME = HIDDEN_PREFIX + "first_name";
    static final String LAST_NAME = HIDDEN_PREFIX + "last_name";
    static final String PASSWORD_EXPIRED = HIDDEN_PREFIX + "password_expired";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EaModels() {
    }

    // ---- users ----

    /**
     * Converts a user with group names.
     *
     * @param u the user
     * @return the Access model
     */
    static UserResponse toUser(EaUser u) {
        UserResponse r = new UserResponse(u.username());
        fillUser(r, u);
        return r.groups(new LinkedHashSet<>(u.groups()));
    }

    /**
     * Converts a user with group details.
     *
     * @param u      the user
     * @param groups the groups the user belongs to
     * @return the Access model
     */
    static UserWithGroupsResponse toUserWithGroups(EaUser u, List<EaGroup> groups) {
        UserWithGroupsResponse r = new UserWithGroupsResponse(u.username());
        fillUser(r, u);
        return r.groups(groups.stream().map(EaModels::toGroup).toList());
    }

    private static void fillUser(org.jfrog.access.rest.user.UserBase target, EaUser u) {
        Map<String, String> custom = u.customData();
        if (target instanceof UserResponse r) {
            r.email(u.email()).realm(Realm.valueOf(u.realm())).status(toStatus(u.status()))
                    .firstName(custom.get(FIRST_NAME)).lastName(custom.get(LAST_NAME))
                    .created(u.created().toEpochMilli()).modified(u.modified().toEpochMilli())
                    .lastLoginTime(millis(u.lastLoginTime())).lastLoginIp(u.lastLoginIp())
                    .passwordLastModified(millis(u.passwordLastModified()))
                    .passwordExpired(Boolean.parseBoolean(custom.get(PASSWORD_EXPIRED)))
                    .customData(visibleCustomData(u)).mfaStatus(UserMFAStatus.NONE).transientUser(false);
        } else if (target instanceof UserWithGroupsResponse r) {
            r.email(u.email()).realm(Realm.valueOf(u.realm())).status(toStatus(u.status()))
                    .firstName(custom.get(FIRST_NAME)).lastName(custom.get(LAST_NAME))
                    .created(u.created().toEpochMilli()).modified(u.modified().toEpochMilli())
                    .lastLoginTime(millis(u.lastLoginTime())).lastLoginIp(u.lastLoginIp())
                    .passwordLastModified(millis(u.passwordLastModified()))
                    .passwordExpired(Boolean.parseBoolean(custom.get(PASSWORD_EXPIRED)))
                    .customData(visibleCustomData(u)).mfaStatus(UserMFAStatus.NONE).transientUser(false);
        }
    }

    /**
     * Returns the custom data a caller sees: stored entries without the reserved ones, plus the flags kept in
     * columns.
     *
     * @param u the user
     * @return a new mutable map
     */
    static Map<String, String> visibleCustomData(EaUser u) {
        Map<String, String> out = new TreeMap<>();
        u.customData().forEach((k, v) -> {
            if (!k.startsWith(HIDDEN_PREFIX)) {
                out.put(k, v);
            }
        });
        if (u.admin()) {
            out.put(ADMIN_KEY, "true");
        }
        if (u.mustChangePassword()) {
            out.put(MUST_CHANGE_PASSWORD_KEY, "true");
        }
        return out;
    }

    /**
     * Builds a new user from a create or replace request.
     *
     * @param request   the request
     * @param now       the creation time
     * @param encoder   hashes a non-empty password
     * @param keepFrom  the stored user a replace keeps last-login and creation data from, or {@code null}
     * @return the user, or {@code Refused} for invalid input
     */
    static EaOutcome<EaUser> fromUserRequest(UserRequest request, Instant now, Function<String, String> encoder,
            EaUser keepFrom) {
        String username = request.getUsername();
        if (username == null || username.isBlank()) {
            return EaOutcome.refused("username is blank");
        }
        Map<String, String> custom = new TreeMap<>();
        Flags flags = new Flags(false, false);
        if (request.getCustomData() != null) {
            for (Map.Entry<String, CustomDatumRequest> e : request.getCustomData().entrySet()) {
                String value = e.getValue() == null ? null : e.getValue().getValue();
                flags = applyUserDatum(custom, flags, e.getKey(), value);
            }
        }
        putIfNotNull(custom, FIRST_NAME, request.getFirstName());
        putIfNotNull(custom, LAST_NAME, request.getLastName());
        String password = request.getPassword();
        String hash = password == null || password.isEmpty() ? null : encoder.apply(password);
        Instant created = keepFrom == null ? now : keepFrom.created();
        EaUser.Builder b = EaUser.builder(username, created)
                .realm(realmName(request.getRealm()))
                .status(fromStatus(request.getStatus()))
                .passwordHash(hash)
                .passwordLastModified(hash == null ? null : now)
                .mustChangePassword(flags.mustChange())
                .email(request.getEmail())
                .groups(request.getGroups() == null ? Set.of() : Set.copyOf(request.getGroups()))
                .admin(flags.admin())
                .customData(custom)
                .modified(now);
        if (keepFrom != null) {
            b.lastLoginTime(keepFrom.lastLoginTime()).lastLoginIp(keepFrom.lastLoginIp());
        }
        try {
            return EaOutcome.ok(b.build());
        } catch (IllegalArgumentException | NullPointerException e) {
            return EaOutcome.refused("invalid user: " + e.getMessage());
        }
    }

    /**
     * Applies an update request's merge patch to a stored user.
     *
     * @param current   the stored user
     * @param mergeJson the request's {@code toJsonMerge()}
     * @param now       the modification time
     * @param encoder   hashes a new password
     * @return the patched user, or {@code Refused} for invalid input
     */
    static EaOutcome<EaUser> applyUserPatch(EaUser current, String mergeJson, Instant now,
            Function<String, String> encoder) {
        JsonNode patch;
        try {
            patch = MAPPER.readTree(mergeJson == null ? "{}" : mergeJson);
        } catch (JsonProcessingException e) {
            return EaOutcome.refused("invalid update: " + e.getOriginalMessage());
        }
        Map<String, String> custom = new TreeMap<>(current.customData());
        Flags flags = new Flags(current.admin(), current.mustChangePassword());
        EaUser.Builder b = current.toBuilder().modified(now);
        if (patch.has("email")) {
            b.email(text(patch.get("email")));
        }
        if (patch.hasNonNull("realm")) {
            b.realm(patch.get("realm").asText());
        }
        if (patch.hasNonNull("status")) {
            b.status(fromStatus(UserStatus.valueOf(patch.get("status").asText())));
        }
        if (patch.has("groups")) {
            Set<String> groups = new TreeSet<>();
            JsonNode node = patch.get("groups");
            if (node != null && node.isArray()) {
                node.forEach(g -> groups.add(g.asText()));
            }
            b.groups(groups);
        }
        if (patch.has("first_name")) {
            putOrRemove(custom, FIRST_NAME, text(patch.get("first_name")));
        }
        if (patch.has("last_name")) {
            putOrRemove(custom, LAST_NAME, text(patch.get("last_name")));
        }
        JsonNode customNode = patch.get("custom_data");
        if (customNode != null && customNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = customNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode valueNode = e.getValue();
                if (valueNode != null && valueNode.isObject()) {
                    valueNode = valueNode.get("value");
                }
                flags = applyUserDatum(custom, flags, e.getKey(), text(valueNode));
            }
        }
        String password = patch.hasNonNull("password") ? patch.get("password").asText() : null;
        if (password != null && !password.isEmpty()) {
            b.passwordHash(encoder.apply(password)).passwordLastModified(now);
            if (!patch.has("password_expired")) {
                custom.remove(PASSWORD_EXPIRED);
            }
        }
        if (patch.has("password_expired")) {
            putOrRemove(custom, PASSWORD_EXPIRED, patch.get("password_expired").asBoolean(false) ? "true" : null);
        }
        b.admin(flags.admin()).mustChangePassword(flags.mustChange()).customData(custom);
        try {
            return EaOutcome.ok(b.build());
        } catch (IllegalArgumentException | NullPointerException e) {
            return EaOutcome.refused("invalid user: " + e.getMessage());
        }
    }

    private record Flags(boolean admin, boolean mustChange) {
    }

    /** Writes one custom datum: flags go to their columns, reserved keys are ignored, null deletes. */
    private static Flags applyUserDatum(Map<String, String> custom, Flags flags, String key, String value) {
        if (key == null || key.startsWith(HIDDEN_PREFIX)) {
            return flags;
        }
        if (ADMIN_KEY.equals(key)) {
            return new Flags(Boolean.parseBoolean(value), flags.mustChange());
        }
        if (MUST_CHANGE_PASSWORD_KEY.equals(key)) {
            return new Flags(flags.admin(), Boolean.parseBoolean(value));
        }
        putOrRemove(custom, key, value);
        return flags;
    }

    /**
     * Maps an embedded status to the Access status.
     *
     * @param status the status
     * @return the Access status
     */
    static UserStatus toStatus(EaUser.Status status) {
        return switch (status) {
            case ENABLED -> UserStatus.ENABLED;
            case DISABLED -> UserStatus.DISABLED;
            case LOCKED -> UserStatus.LOCKED;
        };
    }

    /**
     * Maps an Access status; {@code null} is enabled and anything not enabled or locked (for example
     * {@code invited}) is disabled, so it cannot log in.
     *
     * @param status the Access status, or {@code null}
     * @return the embedded status
     */
    static EaUser.Status fromStatus(UserStatus status) {
        if (status == null || UserStatus.ENABLED.equals(status)) {
            return EaUser.Status.ENABLED;
        }
        if (UserStatus.LOCKED.equals(status)) {
            return EaUser.Status.LOCKED;
        }
        return EaUser.Status.DISABLED;
    }

    private static String realmName(Realm realm) {
        return realm == null ? EaUser.INTERNAL_REALM : realm.getName();
    }

    // ---- groups ----

    /**
     * Converts a group.
     *
     * @param g the group
     * @return the Access model
     */
    static GroupResponse toGroup(EaGroup g) {
        Map<String, String> custom = new TreeMap<>(g.customData());
        if (g.adminPrivileges()) {
            custom.put(ADMIN_KEY, "true");
        }
        return new GroupResponse().name(g.name()).description(g.description()).autoJoin(g.autoJoin())
                .realm(Realm.valueOf(g.realm())).realmAttributes(g.realmAttributes()).externalId(g.externalId())
                .created(g.created().toEpochMilli()).modified(g.modified().toEpochMilli()).customData(custom);
    }

    /**
     * Builds a new group from a create or replace request.
     *
     * @param request the request
     * @param now     the creation time
     * @param created the creation time to keep on a replace, or {@code null}
     * @return the group, or {@code Refused} for invalid input
     */
    static EaOutcome<EaGroup> fromGroupRequest(GroupRequest request, Instant now, Instant created) {
        if (request.getName() == null || request.getName().isBlank()) {
            return EaOutcome.refused("group name is blank");
        }
        Map<String, String> custom = new TreeMap<>();
        boolean admin = false;
        if (request.getCustomData() != null) {
            for (Map.Entry<String, String> e : request.getCustomData().entrySet()) {
                if (ADMIN_KEY.equals(e.getKey())) {
                    admin = Boolean.parseBoolean(e.getValue());
                } else {
                    putOrRemove(custom, e.getKey(), e.getValue());
                }
            }
        }
        try {
            return EaOutcome.ok(new EaGroup(request.getName(), request.getDescription(), request.isAutoJoin(), admin,
                    realmName(request.getRealm()), request.getRealmAttributes(), request.getExternalId(), custom,
                    created == null ? now : created, now));
        } catch (IllegalArgumentException | NullPointerException e) {
            return EaOutcome.refused("invalid group: " + e.getMessage());
        }
    }

    /**
     * Applies an update request's merge patch to a stored group.
     *
     * @param current   the stored group
     * @param mergeJson the request's {@code toJsonMerge()}
     * @param now       the modification time
     * @return the patched group, or {@code Refused} for invalid input
     */
    static EaOutcome<EaGroup> applyGroupPatch(EaGroup current, String mergeJson, Instant now) {
        JsonNode patch;
        try {
            patch = MAPPER.readTree(mergeJson == null ? "{}" : mergeJson);
        } catch (JsonProcessingException e) {
            return EaOutcome.refused("invalid update: " + e.getOriginalMessage());
        }
        String description = patch.has("description") ? text(patch.get("description")) : current.description();
        boolean autoJoin = patch.has("auto_join") ? patch.get("auto_join").asBoolean(false) : current.autoJoin();
        String realm = patch.hasNonNull("realm") ? patch.get("realm").asText() : current.realm();
        String realmAttributes = patch.has("realm_attributes") ? text(patch.get("realm_attributes"))
                : current.realmAttributes();
        String externalId = patch.has("external_id") ? text(patch.get("external_id")) : current.externalId();
        Map<String, String> custom = new TreeMap<>(current.customData());
        boolean admin = current.adminPrivileges();
        JsonNode customNode = patch.get("custom_data");
        if (customNode != null && customNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = customNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String value = text(e.getValue());
                if (ADMIN_KEY.equals(e.getKey())) {
                    admin = Boolean.parseBoolean(value);
                } else {
                    putOrRemove(custom, e.getKey(), value);
                }
            }
        }
        try {
            return EaOutcome.ok(new EaGroup(current.name(), description, autoJoin, admin, realm, realmAttributes,
                    externalId, custom, current.created(), now));
        } catch (IllegalArgumentException | NullPointerException e) {
            return EaOutcome.refused("invalid group: " + e.getMessage());
        }
    }

    // ---- permissions ----

    /**
     * Converts a permission. Its name doubles as its id.
     *
     * @param p the permission
     * @return the Access model
     */
    static PermissionResponse toPermission(EaPermission p) {
        return new PermissionResponse().name(p.name()).id(p.name()).displayName(p.displayName())
                .resourceType(ResourceType.fromKey(p.resourceType())).serviceId(p.serviceId())
                .created(p.created().toEpochMilli()).modified(p.modified().toEpochMilli())
                .customData(p.customData()).implicit(p.implicit())
                .actions(PermissionActionsResponse.builder()
                        .userActions(toActionLists(p.userActions()))
                        .groupActions(toActionLists(p.groupActions()))
                        .build());
    }

    /**
     * Builds a permission from a create or replace request.
     *
     * @param request     the request
     * @param name        the permission name to use
     * @param serviceId   the service id to use when the request has none
     * @param now         the modification time
     * @param created     the creation time to keep, or {@code null} for a new permission
     * @param implicit    the implicit flag to keep
     * @return the permission, or {@code Refused} for invalid input
     */
    static EaOutcome<EaPermission> fromPermissionRequest(PermissionRequest request, String name, String serviceId,
            Instant now, Instant created, boolean implicit) {
        if (request.getResourceType() == null) {
            return EaOutcome.refused("resource type is missing");
        }
        if (request.getDisplayName() == null || request.getDisplayName().isBlank()) {
            return EaOutcome.refused("display name is blank");
        }
        PermissionActionsRequest actions = request.getActions();
        try {
            return EaOutcome.ok(new EaPermission(name, request.getDisplayName(), request.getResourceType().getKey(),
                    request.getServiceId() == null ? serviceId : request.getServiceId(), request.getCustomData(),
                    actions == null ? Map.of() : fromActionLists(actions.getUserActions()),
                    actions == null ? Map.of() : fromActionLists(actions.getGroupActions()),
                    implicit, created == null ? now : created, now));
        } catch (IllegalArgumentException | NullPointerException e) {
            return EaOutcome.refused("invalid permission: " + e.getMessage());
        }
    }

    /**
     * Applies an update request's merge patch to a stored permission. {@code actions.users} and
     * {@code actions.groups}, when present and not {@code null}, replace the whole map.
     *
     * @param current   the stored permission
     * @param mergeJson the request's {@code toJsonMerge()}
     * @param now       the modification time
     * @return the patched permission, or {@code Refused} for invalid input
     */
    static EaOutcome<EaPermission> applyPermissionPatch(EaPermission current, String mergeJson, Instant now) {
        JsonNode patch;
        try {
            patch = MAPPER.readTree(mergeJson == null ? "{}" : mergeJson);
        } catch (JsonProcessingException e) {
            return EaOutcome.refused("invalid update: " + e.getOriginalMessage());
        }
        String displayName = patch.hasNonNull("display_name") ? patch.get("display_name").asText()
                : current.displayName();
        String customData = patch.has("custom_data") ? text(patch.get("custom_data")) : current.customData();
        Map<String, Set<String>> users = current.userActions();
        Map<String, Set<String>> groups = current.groupActions();
        JsonNode actions = patch.get("actions");
        try {
            if (actions != null && actions.isObject()) {
                if (actions.hasNonNull("users")) {
                    users = actionCodes(actions.get("users"));
                }
                if (actions.hasNonNull("groups")) {
                    groups = actionCodes(actions.get("groups"));
                }
            }
            return EaOutcome.ok(new EaPermission(current.name(), displayName, current.resourceType(),
                    current.serviceId(), customData, users, groups, current.implicit(), current.created(), now));
        } catch (IllegalArgumentException | NullPointerException e) {
            return EaOutcome.refused("invalid permission: " + e.getMessage());
        }
    }

    private static Map<String, Set<String>> actionCodes(JsonNode principals) {
        Map<String, Set<String>> out = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = principals.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            Set<String> set = new TreeSet<>();
            e.getValue().forEach(code -> set.add(Action.fromCode(code.asText()).name()));
            out.put(e.getKey(), set);
        }
        return out;
    }

    private static Map<String, List<Action>> toActionLists(Map<String, Set<String>> source) {
        Map<String, List<Action>> out = new TreeMap<>();
        source.forEach((principal, actions) -> {
            List<Action> list = new ArrayList<>();
            actions.forEach(a -> list.add(Action.valueOf(a)));
            out.put(principal, list);
        });
        return out;
    }

    private static Map<String, Set<String>> fromActionLists(Map<String, List<Action>> source) {
        Map<String, Set<String>> out = new TreeMap<>();
        if (source != null) {
            source.forEach((principal, actions) -> {
                Set<String> set = new TreeSet<>();
                if (actions != null) {
                    actions.forEach(a -> set.add(a.name()));
                }
                out.put(principal, set);
            });
        }
        return out;
    }

    // ---- helpers ----

    private static long millis(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static void putIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static void putOrRemove(Map<String, String> map, String key, String value) {
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }
}
