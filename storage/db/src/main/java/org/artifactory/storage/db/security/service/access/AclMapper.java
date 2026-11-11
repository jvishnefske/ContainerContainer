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

package org.artifactory.storage.db.security.service.access;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.security.AceInfo;
import org.artifactory.security.Acl;
import org.artifactory.security.BuildAcl;
import org.artifactory.security.BuildPermissionTarget;
import org.artifactory.security.MutableAceInfo;
import org.artifactory.security.MutableBuildPermissionTarget;
import org.artifactory.security.MutableReleaseBundlePermissionTarget;
import org.artifactory.security.MutableRepoPermissionTarget;
import org.artifactory.security.PermissionTarget;
import org.artifactory.security.ReleaseBundleAcl;
import org.artifactory.security.ReleaseBundlePermissionTarget;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.RepoPermissionTarget;
import org.jfrog.access.client.permission.customdata.CustomData;
import org.jfrog.access.client.permission.customdata.RepoCustomData;
import org.jfrog.access.common.Action;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.model.PermissionPrincipalType;
import org.jfrog.access.rest.imports.ImportPermissionRequest;
import org.jfrog.access.rest.permission.Permission;
import org.jfrog.access.rest.permission.PermissionActions;
import org.jfrog.access.rest.permission.PermissionActionsRequest;
import org.jfrog.access.rest.permission.PermissionRequest;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.JsonUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jfrog.access.client.permission.customdata.CustomData.fromCustomData;

/**
 * @author Noam Shemesh
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AclMapper {

    public static Acl<RepoPermissionTarget> toArtifactoryRepoAcl(Permission permission) {
        CustomData customData = fromCustomData(permission.getCustomData());
        return InfoFactoryHolder.get().createRepoAcl(
                toRepoPermissionTarget(permission, customData),
                toAces(permission.getActions()),
                customData.getUpdatedBy(),
                permission.getModified(),
                permission.getId());
    }

    public static Acl<BuildPermissionTarget> toArtifactoryBuildAcl(Permission permission) {
        CustomData customData = fromCustomData(permission.getCustomData());
        return InfoFactoryHolder.get().createBuildAcl(
                toBuildPermissionTarget(permission, customData),
                toAces(permission.getActions()),
                customData.getUpdatedBy(),
                permission.getModified(),
                permission.getId());
    }

    public static Acl<ReleaseBundlePermissionTarget> toArtifactoryReleaseBundleAcl(Permission permission) {
        CustomData customData = fromCustomData(permission.getCustomData());
        return InfoFactoryHolder.get().createReleaseBundleAcl(
                toReleaseBundlePermissionTarget(permission, customData),
                toAces(permission.getActions()),
                customData.getUpdatedBy(),
                permission.getModified(),
                permission.getId());
    }

    private static RepoPermissionTarget toRepoPermissionTarget(Permission permission, CustomData customData) {
        RepoCustomData data = (RepoCustomData) customData;
        MutableRepoPermissionTarget target = InfoFactoryHolder.get().createRepoPermissionTarget(
                permission.getDisplayName(), new HashSet<>(data.getRepoKeys()));
        target.setExcludesPattern(data.getExcludePattern());
        target.setIncludesPattern(data.getIncludePattern());
        return target;
    }

    private static BuildPermissionTarget toBuildPermissionTarget(Permission permission, CustomData customData) {
        RepoCustomData data = (RepoCustomData) customData;
        MutableBuildPermissionTarget target = InfoFactoryHolder.get().createBuildPermissionTarget(
                permission.getDisplayName(), new HashSet<>(data.getRepoKeys()));
        target.setExcludesPattern(data.getExcludePattern());
        target.setIncludesPattern(data.getIncludePattern());
        return target;
    }

    private static ReleaseBundlePermissionTarget toReleaseBundlePermissionTarget(Permission permission,
            CustomData customData) {
        RepoCustomData data = (RepoCustomData) customData;
        MutableReleaseBundlePermissionTarget target = InfoFactoryHolder.get().createReleaseBundlePermissionTarget(
                permission.getDisplayName(), new HashSet<>(data.getRepoKeys()));
        target.setExcludesPattern(data.getExcludePattern());
        target.setIncludesPattern(data.getIncludePattern());
        return target;
    }

    private static Set<AceInfo> toAces(PermissionActions actions) {
        Set<AceInfo> result = new HashSet<>();
        addEntrySetToAce(result, convertActionsToStrings(actions.getGroupActions()), true);
        addEntrySetToAce(result, convertActionsToStrings(actions.getUserActions()), false);
        return result;
    }

    private static Map<String, List<String>> convertActionsToStrings(Map<String, List<Action>> groupActions) {
        return groupActions.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        mapEntry -> mapEntry.getValue().stream().map(Action::getCode).collect(Collectors.toList())));
    }

    private static void addEntrySetToAce(Set<AceInfo> result, Map<String, List<String>> userActions, boolean isGroup) {
        userActions.entrySet()
                .stream()
                .map(entry -> createAce(entry.getKey(), entry.getValue(), isGroup))
                .collect(Collectors.toCollection(() -> result));
    }

    private static AceInfo createAce(String name, List<String> actions, boolean isGroup) {
        MutableAceInfo ace = InfoFactoryHolder.get().createAce();
        ace.setPrincipal(name);
        ace.setPermissionsFromStrings(new HashSet<>(actions));
        ace.setGroup(isGroup);
        return ace;
    }

    public static PermissionRequest aclToAccessPermission(Acl entity, String serviceId) {
        return aclToAccessPermission(PermissionRequest.create(), entity, serviceId);
    }

    @SuppressWarnings("unchecked")
    private static <T extends PermissionRequest> T aclToAccessPermission(T object, Acl acl, String serviceId) {
        ResourceType type = getTypeFromAcl(acl);
        return (T) object
                .id(acl.getAccessIdentifier())
                .displayName(acl.getPermissionTarget().getName())
                .serviceId(serviceId)
                .resourceType(type)
                .customData(getCustomDataByType(acl))
                .actions(toActions(acl.getAces()));
    }

    /**
     * Note: Currently the only custom data type available is Repo (used by both RepoAcl and BuildAcl).
     * In the future, this is the place to add a new type
     */
    private static String getCustomDataByType(Acl<? extends PermissionTarget> acl) {
        PermissionTarget permissionTarget = acl.getPermissionTarget();
        if (permissionTarget instanceof RepoPermissionTarget) {
            return aclRepoToCustomData((RepoPermissionTarget) permissionTarget, acl.getUpdatedBy());
        }
        return null;
    }

    private static ResourceType getTypeFromAcl(Acl acl) {
        if (acl instanceof RepoAcl) {
            return ResourceType.REPO;
        }
        if (acl instanceof BuildAcl) {
            return ResourceType.BUILD;
        }
        if (acl instanceof ReleaseBundleAcl) {
            return ResourceType.RELEASE_BUNDLE;
        }
        throw new IllegalStateException("Permission target " + acl.getPermissionTarget().getName() + " has no type");
    }

    public static ImportPermissionRequest toFullAccessPermission(Acl<? extends PermissionTarget> acl, String serviceId,
            ResourceType type) {
        ImportPermissionRequest.Builder builder = ImportPermissionRequest.builder()
                .name(Optional.ofNullable(acl.getAccessIdentifier())
                        .orElse(toAccessName(serviceId, acl.getPermissionTarget().getName())))
                .displayName(acl.getPermissionTarget().getName())
                .serviceId(serviceId)
                .resourceType(type)
                .customData(getCustomDataByType(acl))
                .created(ClockUtils.epochMillis())
                .modified(ClockUtils.epochMillis());
        addAccessActions(builder, acl);
        return builder.build();
    }

    private static String toAccessName(String serviceId, String permissionName) {
        return serviceId + ":" + permissionName;
    }

    private static void addAccessActions(ImportPermissionRequest.Builder builder,
            Acl<? extends PermissionTarget> entity) {
        entity.getAces().forEach(ace -> {
            PermissionPrincipalType principalType =
                    ace.isGroup() ? PermissionPrincipalType.GROUP : PermissionPrincipalType.USER;
            ace.getPermissionsAsString()
                    .forEach(action -> builder.addAction(action, ace.getPrincipal(), principalType));
        });
    }

    private static PermissionActionsRequest toActions(Set<AceInfo> aces) {
        PermissionActionsRequest actions = PermissionActionsRequest.create();
        aces.forEach(ace -> ace.getPermissionsAsString().forEach(permission -> {
            if (ace.isGroup()) {
                actions.addGroupAction(ace.getPrincipal(), Action.fromCode(permission));
            } else {
                actions.addUserAction(ace.getPrincipal(), Action.fromCode(permission));
            }
        }));
        return actions;
    }

    private static String aclRepoToCustomData(RepoPermissionTarget permissionTarget, String updatedBy) {
        return JsonUtils.getInstance().valueToString(new RepoCustomData(updatedBy,
                permissionTarget.getExcludesPattern(),
                permissionTarget.getIncludesPattern(),
                permissionTarget.getRepoKeys().stream().toList()
        ));
    }
}
