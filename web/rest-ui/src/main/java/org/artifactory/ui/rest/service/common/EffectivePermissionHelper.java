package org.artifactory.ui.rest.service.common;

import com.google.common.collect.ImmutableSetMultimap;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.security.AceInfo;
import org.artifactory.security.Acl;
import org.artifactory.security.PermissionTarget;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.permission.EffectivePermission;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.permission.PrincipalEffectivePermissions;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.StreamSupportUtils;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.aol.AolService.SUPER_USER_NAME;

/**
 * Shared code of Effective Permission services of Repo / Build
 *
 * @author Nadav Yogev
 */
public class EffectivePermissionHelper {

    private final UserGroupService userGroupService;
    private ImmutableSetMultimap<String, String> userInGroups;

    public EffectivePermissionHelper(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    public void addAdminsToMaps(Map<String, PrincipalEffectivePermissions> userEffectivePermissions,
            Map<String, PrincipalEffectivePermissions> groupEffectivePermissions, boolean isAol) {
        addAdminsToMaps(userEffectivePermissions, groupEffectivePermissions, isAol, false);
    }

    public void addAdminsToMaps(Map<String, PrincipalEffectivePermissions> userEffectivePermissions,
            Map<String, PrincipalEffectivePermissions> groupEffectivePermissions, boolean isAol,
            boolean isObservability) {
        addAdminsToMaps(userEffectivePermissions, groupEffectivePermissions, isAol, isObservability, null);
    }

    /**
     * Add effective admins and groups to the permission model
     */
    public void addAdminsToMaps(Map<String, PrincipalEffectivePermissions> userEffectivePermissions,
            Map<String, PrincipalEffectivePermissions> groupEffectivePermissions, boolean isAol,
            boolean isObservability, List<UserInfo> allUsers) {
        Map<String, Boolean> allAdminUsers = allUsers == null
                ? userGroupService.getAllUsersAndAdminStatus(true)
                : userGroupService.filterUsersByAdminStatus(true, allUsers);
        if (isAol) {
            allAdminUsers.remove(SUPER_USER_NAME);
        }
        allAdminUsers.keySet().forEach(adminUser ->
                changeAdminPermissions(adminUser, userEffectivePermissions, isObservability));
        List<String> allAdminGroups = userGroupService.getAllAdminGroupsNames();
        allAdminGroups.forEach(adminGroup ->
                changeAdminPermissions(adminGroup, groupEffectivePermissions, isObservability));
    }

    /**
     * Change user/group permission to admin in the result map
     */
    private void changeAdminPermissions(String adminUser,
            Map<String, PrincipalEffectivePermissions> aceListInfo, boolean isObservability) {
        aceListInfo.putIfAbsent(adminUser, new PrincipalEffectivePermissions(adminUser));
        PrincipalEffectivePermissions permissions = aceListInfo.get(adminUser);
        if (isObservability) {
            permissions.getPermission().setRead(true);
            permissions.getPermission().setDelete(true);
        } else {
            permissions.getPermission().setAdmin();
        }
        permissions.setAdmin(true);
    }

    /**
     * Get all ACL ACEs and get their info
     */
    public void addAclInfoToAclList(Acl<? extends PermissionTarget> acl,
            Map<String, PrincipalEffectivePermissions> userEffectivePermissions,
            Map<String, PrincipalEffectivePermissions> groupEffectivePermissions) {
        String permissionTargetName = acl.getPermissionTarget().getName();
        acl.getMutableAces().forEach(aceInfo -> addAceInfo(aceInfo, permissionTargetName, userEffectivePermissions,
                groupEffectivePermissions));
    }


    /**
     * Add ace principal to the relevant map with his permissions
     */
    private void addAceInfo(AceInfo aceInfo, String permissionTargetName,
            Map<String, PrincipalEffectivePermissions> userEffectivePermissions,
            Map<String, PrincipalEffectivePermissions> groupEffectivePermissions) {
        if (aceInfo.isGroup()) {
            addPermissionsToPrincipal(aceInfo.getPrincipal(), permissionTargetName, aceInfo, groupEffectivePermissions);
        } else {
            addPermissionsToPrincipal(aceInfo.getPrincipal(), permissionTargetName, aceInfo, userEffectivePermissions);
        }
    }

    /**
     * Add the aceInfo permissions to the principal in the aceListInfo map.
     */
    private void addPermissionsToPrincipal(String principal, String permissionTargetName, AceInfo aceInfo,
            Map<String, PrincipalEffectivePermissions> aceListInfo) {
        aceListInfo.putIfAbsent(principal, new PrincipalEffectivePermissions(principal));
        PrincipalEffectivePermissions principalEffectivePermissions = aceListInfo.get(principal);
        EffectivePermission effectivePermission = principalEffectivePermissions.getPermission();
        effectivePermission.aggregatePermissions(aceInfo);
        addPermissionTargetsWithCap(principalEffectivePermissions, permissionTargetName);
        aceListInfo.put(principal, principalEffectivePermissions);
    }

    /**
     * Add permission targets to permission info until cap has been reached
     */
    private void addPermissionTargetsWithCap(PrincipalEffectivePermissions permissionInfo,
            List<String> permissionTargetsToAdd) {
        List<String> permissionTargets = permissionInfo.getPermissionTargets();
        for (String permissionToAdd : permissionTargetsToAdd) {
            if (!permissionInfo.isPermissionTargetsCap()) {
                permissionTargets.add(permissionToAdd);
                if (permissionInfo.isPermissionTargetsCap()) {
                    permissionInfo.setPermissionTargetsCap(true);
                    return;
                }
            }
        }
    }

    /**
     * Add permission target to permission info
     */
    private void addPermissionTargetsWithCap(PrincipalEffectivePermissions permissionInfo,
            String permissionTargetToAdd) {
        permissionInfo.advancePermissionTargetsCount();
        if (!permissionInfo.isPermissionTargetsCap()) {
            permissionInfo.getPermissionTargets().add(permissionTargetToAdd);
            if (permissionInfo.isPermissionTargetsCap()) {
                permissionInfo.setPermissionTargetsCap(true);
            }
        }
    }

    /**
     * Give all the users of all the groups each group's permissions
     */
    public void grantGroupUsersEffectivePermissions(
            Map<String, PrincipalEffectivePermissions> groupEffectivePermissions,
            Map<String, PrincipalEffectivePermissions> userEffectivePermissions) {
        userInGroups = userGroupService.getAllUsersInGroups();
        if (Objects.isNull(userInGroups) ||
                CollectionUtils.isNullOrEmpty(userInGroups.entries())) {
            return;
        }
        Map<String, Collection<String>> groupUserMap = userInGroups.inverse().asMap();
        StreamSupportUtils.mapEntriesStream(groupEffectivePermissions)
                .filter(grpToPath -> !grpToPath.getValue().isAdmin())
                .flatMap(grpToPath -> groupPathToUserPath(grpToPath, groupUserMap))
                .forEach(usrToPath -> copyPermissionsToUser(usrToPath.getRight(), usrToPath.getLeft(),
                        userEffectivePermissions));
    }

    /**
     * Give all the users of all the groups each group's permissions
     */
    public void grantGroupUsersEffectivePermissions(
            Map<String, PrincipalEffectivePermissions> groupEffectivePermissions,
            Map<String, PrincipalEffectivePermissions> userEffectivePermissions,
            ImmutableSetMultimap<String, String> allUsersInGroups) {
        userInGroups = allUsersInGroups;
        if (Objects.isNull(userInGroups) ||
                CollectionUtils.isNullOrEmpty(userInGroups.entries())) {
            return;
        }
        Map<String, Collection<String>> groupUserMap = userInGroups.inverse().asMap();
        StreamSupportUtils.mapEntriesStream(groupEffectivePermissions)
                .filter(grpToPath -> !grpToPath.getValue().isAdmin())
                .flatMap(grpToPath -> groupPathToUserPath(grpToPath, groupUserMap))
                .forEach(usrToPath -> copyPermissionsToUser(usrToPath.getRight(), usrToPath.getLeft(),
                        userEffectivePermissions));
    }

    private Stream<Pair<String, PrincipalEffectivePermissions>> groupPathToUserPath(
            Map.Entry<String, PrincipalEffectivePermissions> grpToPath,
            Map<String, Collection<String>> groupUserMap) {
        return StreamSupportUtils
                .stream(groupUserMap.get(grpToPath.getKey()))
                .map(user -> Pair.of(user, grpToPath.getValue()));
    }

    /**
     * Copy permission from a group permission to user permissions
     *
     * @param groupPermissionsInfo     Group permission info to copy from
     * @param username                 User to copy permissions to
     * @param userEffectivePermissions User permissions map to add new permission info
     */
    private void copyPermissionsToUser(PrincipalEffectivePermissions groupPermissionsInfo, String username,
            Map<String, PrincipalEffectivePermissions> userEffectivePermissions) {
        userEffectivePermissions.putIfAbsent(username, new PrincipalEffectivePermissions(username));
        PrincipalEffectivePermissions userPermissionInfo = userEffectivePermissions.get(username);
        EffectivePermission effectivePermission = userPermissionInfo.getPermission();
        EffectivePermission groupPermission = groupPermissionsInfo.getPermission();
        effectivePermission.aggregatePermissions(groupPermission);
        addPermissionTargetsWithCap(userPermissionInfo, groupPermissionsInfo.getPermissionTargets());
        userPermissionInfo.setPermission(effectivePermission);
    }

    //                   ***
    //      Effective Permission By Entity part
    //                   ***

    public List<String> getPermissionByEntity(List<? extends Acl<? extends PermissionTarget>> acls, boolean isGroup,
            String entityName) {
        if (isGroup) {
            return acls.stream().filter(acl -> entityNameInAce(acl, entityName, true))
                    .map(acl -> acl.getPermissionTarget().getName())
                    .collect(Collectors.toList());
        } else {
            try {
                Set<String> userGroups = getUserGroups(entityName);
                return acls.stream().filter(acl -> userInAcl(acl, entityName, userGroups))
                        .map(acl -> acl.getPermissionTarget().getName())
                        .collect(Collectors.toList());
            } catch (UsernameNotFoundException e) {
                return null;
            }
        }
    }

    /**
     * Returns true if user or the user's group is in any of the ACL's ACEs, false otherwise
     */
    private boolean userInAcl(Acl<? extends PermissionTarget> acl, String userName, Set<String> groupNames) {
        return entityNameInAce(acl, userName, false) || userGroupInAce(acl, groupNames);
    }

    /**
     * Returns true if any ACL's ACE contains any of the user's group
     */
    private boolean userGroupInAce(Acl<? extends PermissionTarget> acl, Set<String> groupNames) {
        return acl.getMutableAces().stream()
                .anyMatch(ace -> ace.isGroup() && groupNames.contains(ace.getPrincipal()));
    }

    /**
     * Returns true if the entity is in any of the ACL's ACEs, false otherwise
     */
    private boolean entityNameInAce(Acl<? extends PermissionTarget> acl, String userName, boolean isGroup) {
        return acl.getMutableAces().stream()
                .anyMatch(ace -> ace.getPrincipal().equals(userName) && ace.isGroup() == isGroup);
    }

    private Set<String> getUserGroups(String entityName) {
        return userGroupService.findUser(entityName)
                .getGroups()
                .stream()
                .map(UserGroupInfo::getGroupName)
                .collect(Collectors.toSet());
    }

}
