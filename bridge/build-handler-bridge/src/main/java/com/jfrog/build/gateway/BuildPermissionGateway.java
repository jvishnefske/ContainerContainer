package com.jfrog.build.gateway;

import com.google.common.collect.ImmutableSetMultimap;
import com.jfrog.build.acl.model.api.permission.BuildPermissionTargetInfo;
import com.jfrog.build.acl.model.api.permission.BuildPermissionsModel;
import com.jfrog.build.acl.model.api.permission.EffectivePermission;
import com.jfrog.build.acl.model.api.permission.PrincipalEffectivePermissions;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.security.AceInfo;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.BuildAcl;
import org.artifactory.security.BuildPermissionTarget;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.util.AolUtils;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static org.artifactory.aol.AolService.SUPER_USER_NAME;
import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;
import static org.artifactory.mime.NamingUtils.isViewable;
import static org.artifactory.security.ArtifactoryPermission.fromDisplayName;
import static org.jfrog.client.util.PathUtils.getExtension;

@AllArgsConstructor
public class BuildPermissionGateway {

    private final AuthorizationService authorizationService;
    private final UserGroupService userGroupService;

    public boolean hasPermission(
            @NonNull PermissionScopeResourceType type,
            @NonNull String permission,
            @NonNull String path) {
        try {
            RepoPath repoPath = RepoPathFactory.create(path);
            ArtifactoryPermission permissionType = fromDisplayName(permission);
            return authorizationService.hasPermission(type, repoPath, permissionType);
        } catch (Exception e) {
            String message = format("Failed to verify %s/%s permission for path: %s", type, permission, path);
            throw internalServerException(message, e);
        }
    }

    @NonNull
    public List<String> getArtifactActions(
            @NonNull String repoKey,
            @NonNull String path) {
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        if (!authorizationService.canRead(repoPath)) {
            return List.of();
        }
        List<String> actions = new LinkedList<>();
        actions.add("Download");
        actions.add("ShowInTree");
        if (isViewable(path) || "class".equals(getExtension(path))) {
            actions.add("View");
        }
        return actions;
    }

    @NonNull
    public BuildPermissionsModel getBuildEffectivePermissions(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        return toBuildPermissionsModel(
                authorizationService.getBuildPathAcls(buildName, buildNumber, buildStarted, buildRepo));
    }

    /**
     * Computes the effective permissions for a build-info repository path: the repository root aggregates every
     * build ACL, while a specific path is restricted to the ACLs covering it. Used by the repository Effective
     * Permissions tab for build-info repositories.
     */
    @NonNull
    public BuildPermissionsModel getBuildEffectivePermissionsByRepoPath(
            @NonNull String repoKey,
            @NonNull String path) {
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);
        List<BuildAcl> acls = repoPath.isRoot()
                ? authorizationService.getAllBuildAcls(true)
                : authorizationService.getBuildPathAcls(repoPath, true);
        return toBuildPermissionsModel(acls);
    }

    @NonNull
    private BuildPermissionsModel toBuildPermissionsModel(@NonNull List<BuildAcl> acls) {
        Map<String, PrincipalEffectivePermissions> userPermissions = new HashMap<>();
        Map<String, PrincipalEffectivePermissions> groupPermissions = new HashMap<>();
        addAdmins(userPermissions, groupPermissions, AolUtils.isAol());
        acls.forEach(acl -> addAclAces(acl, userPermissions, groupPermissions));
        grantGroupUsersPermissions(groupPermissions, userPermissions);
        List<BuildPermissionTargetInfo> permissionTargets = acls.stream().map(this::toPermissionTargetInfo).toList();
        return new BuildPermissionsModel(
                new ArrayList<>(userPermissions.values()),
                new ArrayList<>(groupPermissions.values()),
                permissionTargets
        );
    }

    @NonNull
    public List<String> getBuildPermissionsByUser(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String username) {
        Set<String> userGroups;
        try {
            userGroups = userGroupService.findUser(username).getGroups().stream()
                    .map(UserGroupInfo::getGroupName)
                    .collect(Collectors.toSet());
        } catch (UsernameNotFoundException e) {
            return List.of();
        }
        return authorizationService.getBuildPathAcls(buildName, buildNumber, buildStarted, buildRepo).stream()
                .filter(acl -> userInAcl(acl, username, userGroups))
                .map(acl -> acl.getPermissionTarget().getName())
                .toList();
    }

    /**
     * Returns the names of the build's permission targets that grant the given group access (a group ACE naming it).
     * Mirrors the legacy {@code EffectivePermissionHelper#getPermissionByEntity} flow for the group case.
     */
    @NonNull
    public List<String> getBuildPermissionsByGroup(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String groupName) {
        return authorizationService.getBuildPathAcls(buildName, buildNumber, buildStarted, buildRepo).stream()
                .filter(acl -> aceMatchesGroup(acl, groupName))
                .map(acl -> acl.getPermissionTarget().getName())
                .toList();
    }

    private boolean userInAcl(
            @NonNull BuildAcl acl,
            @NonNull String username,
            @NonNull Set<String> userGroups) {
        return aceMatchesPrincipal(acl, username) || aceMatchesUserGroup(acl, userGroups);
    }

    private boolean aceMatchesGroup(
            @NonNull BuildAcl acl,
            @NonNull String groupName) {
        return acl.getMutableAces().stream()
                .anyMatch(ace -> ace.getPrincipal().equals(groupName) && ace.isGroup());
    }

    private boolean aceMatchesUserGroup(
            @NonNull BuildAcl acl,
            @NonNull Set<String> userGroups) {
        return acl.getMutableAces().stream()
                .anyMatch(ace -> ace.isGroup() && userGroups.contains(ace.getPrincipal()));
    }

    private boolean aceMatchesPrincipal(
            @NonNull BuildAcl acl,
            @NonNull String principal) {
        return acl.getMutableAces().stream()
                .anyMatch(ace -> ace.getPrincipal().equals(principal) && !ace.isGroup());
    }

    private void addAdmins(
            @NonNull Map<String, PrincipalEffectivePermissions> userPermissions,
            @NonNull Map<String, PrincipalEffectivePermissions> groupPermissions,
            boolean isAol) {
        Map<String, Boolean> adminUsers = userGroupService.getAllUsersAndAdminStatus(true);
        if (isAol) {
            adminUsers.remove(SUPER_USER_NAME);
        }
        adminUsers.keySet().forEach(adminUser -> grantAdmin(adminUser, userPermissions));
        userGroupService.getAllAdminGroupsNames().forEach(adminGroup -> grantAdmin(adminGroup, groupPermissions));
    }

    private void grantAdmin(
            @NonNull String principal,
            @NonNull Map<String, PrincipalEffectivePermissions> permissions) {
        PrincipalEffectivePermissions principalPermissions =
                permissions.computeIfAbsent(principal, PrincipalEffectivePermissions::new);
        principalPermissions.getPermission().grantAdmin();
        principalPermissions.setAdmin(true);
    }

    private void addAclAces(
            @NonNull BuildAcl acl,
            @NonNull Map<String, PrincipalEffectivePermissions> userPermissions,
            @NonNull Map<String, PrincipalEffectivePermissions> groupPermissions) {
        String permissionTargetName = acl.getPermissionTarget().getName();
        acl.getMutableAces().forEach(ace -> {
            Map<String, PrincipalEffectivePermissions> target = ace.isGroup() ? groupPermissions : userPermissions;
            PrincipalEffectivePermissions principalPermissions =
                    target.computeIfAbsent(ace.getPrincipal(), PrincipalEffectivePermissions::new);
            aggregateAce(principalPermissions.getPermission(), ace);
            principalPermissions.advancePermissionTargetsCount();
            addPermissionTargetWithCap(principalPermissions, permissionTargetName);
        });
    }

    private void aggregateAce(
            @NonNull EffectivePermission permission,
            @NonNull AceInfo ace) {
        permission.setRead(permission.isRead() || ace.canRead());
        permission.setDistribute(permission.isDistribute() || ace.canDistribute());
        permission.setAnnotate(permission.isAnnotate() || ace.canAnnotate());
        permission.setDeploy(permission.isDeploy() || ace.canDeploy());
        permission.setDelete(permission.isDelete() || ace.canDelete());
        permission.setManagedXrayMeta(permission.isManagedXrayMeta() || ace.canManagedXrayMeta());
    }

    private void grantGroupUsersPermissions(
            @NonNull Map<String, PrincipalEffectivePermissions> groupPermissions,
            @NonNull Map<String, PrincipalEffectivePermissions> userPermissions) {
        ImmutableSetMultimap<String, String> usersInGroups = userGroupService.getAllUsersInGroups();
        if (usersInGroups == null || usersInGroups.isEmpty()) {
            return;
        }
        Map<String, Collection<String>> groupToUsers = usersInGroups.inverse().asMap();
        for (PrincipalEffectivePermissions groupPermission : groupPermissions.values()) {
            Collection<String> groupUsers = groupToUsers.get(groupPermission.getPrincipal());
            if (!groupPermission.isAdmin() && nonNull(groupUsers)) {
                groupUsers.forEach(user -> copyPermissionsToUser(groupPermission, user, userPermissions));
            }
        }
    }

    private void copyPermissionsToUser(
            @NonNull PrincipalEffectivePermissions groupPermission,
            @NonNull String username,
            @NonNull Map<String, PrincipalEffectivePermissions> userPermissions) {
        PrincipalEffectivePermissions userPermission =
                userPermissions.computeIfAbsent(username, PrincipalEffectivePermissions::new);
        userPermission.getPermission().aggregate(groupPermission.getPermission());
        for (String permissionTargetToAdd : groupPermission.getPermissionTargets()) {
            addPermissionTargetWithCap(userPermission, permissionTargetToAdd);
        }
    }

    private void addPermissionTargetWithCap(
            @NonNull PrincipalEffectivePermissions permission,
            @NonNull String permissionTargetName) {
        if (!permission.isPermissionTargetsCap()) {
            permission.getPermissionTargets().add(permissionTargetName);
        }
    }

    @NonNull
    private BuildPermissionTargetInfo toPermissionTargetInfo(@NonNull BuildAcl acl) {
        BuildPermissionTarget permissionTarget = acl.getPermissionTarget();
        Set<String> groups = new HashSet<>();
        Set<String> users = new HashSet<>();
        acl.getMutableAces().forEach(ace -> {
            if (ace.isGroup()) {
                groups.add(ace.getPrincipal());
            } else {
                users.add(ace.getPrincipal());
            }
        });
        BuildPermissionTargetInfo info = new BuildPermissionTargetInfo();
        info.setPermissionName(permissionTarget.getName());
        info.setGroups(groups);
        info.setUsers(users);
        info.setIncludePatterns(permissionTarget.getIncludes());
        info.setExcludePatterns(permissionTarget.getExcludes());
        return info;
    }
}
