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

package org.artifactory.api.security;

import com.google.common.collect.Multimap;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.artifactory.api.util.Idempotent;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.common.Lock;
import org.artifactory.security.AceInfo;
import org.artifactory.security.Acl;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.ArtifactoryResourceType;
import org.artifactory.security.BuildAcl;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.MutableRepoAcl;
import org.artifactory.security.PermissionTarget;
import org.artifactory.security.ReleaseBundleAcl;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.RepoPermissionTarget;
import org.artifactory.security.UserInfo;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.security.common.AuthorizationRoles;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * These are the usage of security data and logged in user methods.
 *
 * @author Fred Simon
 */
public interface AuthorizationService {

    String INTERNAL_PERMISSION_PREFIX = "INTERNAL_";
    String ROLE_USER = AuthorizationRoles.ROLE_USER;
    String ROLE_ADMIN = AuthorizationRoles.ROLE_ADMIN;

    /**
     * @return True if the current user can read the specified path.
     */
    @Idempotent
    boolean canRead(RepoPath path);

    /**
     * @return True if the user can read the specified path.
     */
    @Idempotent
    boolean canRead(UserInfo user, RepoPath path);

    /**
     * @return True if users in the group can read the specified path.
     */
    @Idempotent
    boolean canRead(GroupInfo group, RepoPath path);

    /**
     * @return True if the current user can annotate the specified path.
     */
    @Idempotent
    boolean canAnnotate(RepoPath repoPath);

    /**
     * @return True if the user can annotate the specified path.
     */
    @Idempotent
    boolean canAnnotate(UserInfo user, RepoPath path);

    /**
     * @return True if users in the group can annotate the specified path.
     */
    @Idempotent
    boolean canAnnotate(GroupInfo group, RepoPath path);

    /**
     * @return True if the current user can delete the specified path.
     */
    @Idempotent
    boolean canDelete(RepoPath path);

    /**
     * @return True if the user can delete the specified path.
     */
    @Idempotent
    boolean canDelete(UserInfo user, RepoPath path);

    /**
     * @return True if users in the group can delete the specified path.
     */
    @Idempotent
    boolean canDelete(GroupInfo group, RepoPath path);

    /**
     * @return True if the current user can deploy to the specified path.
     */
    @Idempotent
    boolean canDeploy(RepoPath path);

    /**
     * @return True if the user can deploy to the specified path.
     */
    @Idempotent
    boolean canDeploy(UserInfo user, RepoPath path);

    /**
     * @return True if users in the group can deploy to the specified path.
     */
    @Idempotent
    boolean canDeploy(GroupInfo group, RepoPath path);

    /**
     * @return True if the current user has admin permissions on a target info that includes this path..
     */
    @Idempotent
    boolean canManage(RepoPath path);

    /**
     * @return True if the user can administer the specified path.
     */
    @Idempotent
    boolean canManage(UserInfo user, RepoPath path);

    /**
     * @return True if users in the group can administer the specified path.
     */
    @Idempotent
    boolean canManage(GroupInfo group, RepoPath path);

    /**
     * @param target The permission target to check.
     * @return True if the current logged in user is admin OR has manage permission on the permission target
     */
    @Idempotent
    boolean canManage(PermissionTarget target);

    /**
     * Indicates if the current user has the given repo permission, no matter the target
     *
     * @param permission Permission to check
     * @return True if the current user has such permission. False if not
     */
    @Idempotent
    boolean hasRepoPermission(ArtifactoryPermission permission);

    /**
     * Indicates if the current user has the given repo permission, no matter the target
     *
     * @param permission                     Permission to check
     * @param excludeInternalProjectSpecific Indicates if internal permissions should be skipped
     * @return True if the current user has such permission. False if not
     */
    @Idempotent
    boolean hasRepoPermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific);

    /**
     * Indicates if the current user has the given build permission
     *
     * @param buildRepo  Build repo key to check permission on or null if to check on all
     * @param permission Permission to check
     * @return True if the current user has such permission. False if not
     */
    @Idempotent
    boolean hasBuildPermission(@Nullable String buildRepo, ArtifactoryPermission permission);

    /**
     * Indicates if the current user has the given build permission
     *
     * @param buildRepo                      Build repo key to check permission on or null if to check on all
     * @param permission                     Permission to check
     * @param excludeInternalProjectSpecific Indicates if internal permissions should be skipped
     * @return True if the current user has such permission. False if not
     */
    @Idempotent
    boolean hasBuildPermission(@Nullable String buildRepo, ArtifactoryPermission permission,
            boolean excludeInternalProjectSpecific);

    /**
     * Indicates if the current user has the given release bundle permission, no matter the target
     * NOTE: if the user doesn't have Edge/EntPlus license the release-bundle permission is hidden and therefore
     * return false.
     *
     * @param permission Permission to check
     * @return True if the current user has such permission. False if not
     */
    @Idempotent
    boolean hasReleaseBundlePermission(ArtifactoryPermission permission);

    /**
     * Indicates if the current user has the given release bundle permission, no matter the target
     * NOTE: if the user doesn't have Edge/EntPlus license the release-bundle permission is hidden and therefore
     * return false.
     *
     * @param permission                     Permission to check
     * @param excludeInternalProjectSpecific Indicates if internal permissions should be skipped
     * @return True if the current user has such permission. False if not
     */
    @Idempotent
    boolean hasReleaseBundlePermission(ArtifactoryPermission permission,
            boolean excludeInternalProjectSpecific);

    /**
     * Indicates if the current user has the given release bundle permission, no matter the target
     * NOTE: if the user doesn't have Edge/EntPlus license the release-bundle permission is hidden and therefore
     * return false.
     *
     * @param permission                     Permission to check
     * @param excludeInternalProjectSpecific Indicates if internal permissions should be skipped
     * @param getByLicense                   Indicates if permissions cache is license-dependent.
     * @return True if the current user has such permission. False if not
     */
    @Idempotent
    boolean hasReleaseBundlePermission(ArtifactoryPermission permission,
            boolean excludeInternalProjectSpecific, boolean getByLicense);

    /**
     * Indicates if the current user has the given permission on at least one of repo/build/release-bundles,
     * no matter the target
     *
     * @param permission Permission to check
     * @return True if the current user has such permission. False if not
     */
    @Idempotent
    boolean hasPermission(ArtifactoryPermission permission);

    /**
     * Indicates if the current user has the given permission on at least one of repo/build/release-bundles,
     * no matter the target
     *
     * @param permission                     Permission to check
     * @param excludeInternalProjectSpecific indicates if internal permissions should be skipped
     * @return True if the current user has such permission. False if not
     */
    @Idempotent
    boolean hasPermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific);

    /**
     * Indicates if the current user has the given permission of the specified type
     * for the specified path (many-to-many in terms of the resource-type/permission).
     *
     * @param type       represents the scope of the permission (e.g. Artifact/Build/Release-Bundle).
     * @param repoPath   represents the target of the permission to apply.
     * @param permission represents the kind of the permission to check.
     * @return True if the current user has such permission. False if not.
     */
    @Idempotent
    boolean hasPermission(PermissionScopeResourceType type, RepoPath repoPath, ArtifactoryPermission permission);

    /**
     * Indicates if the current user has build read permission on at least one build.
     * This is true if the user is admin or "Build Basic Read" flag is on, or he has an acl that grant him read
     * permission on a build.
     *
     * @return True if the current user has such permission. False if not
     */
    @Idempotent
    boolean hasBuildBasicReadPermission();

    /**
     * Returns a list of repo permission targets for the current logged-in user for the type of permission given.
     *
     * @param permission Type of permission to find
     * @return List of permission target info objects
     */
    @Idempotent
    List<RepoPermissionTarget> getRepoPermissionTargets(ArtifactoryPermission permission);

    /**
     * Indicates if the given user has any permissions at all, on the root repository. If it is a virtual repository
     * then if the user has a permission in any one of the real repositories associated with it will return {@code
     * true}
     *
     * @param repoKey The repository key of the repository to check the user's permissions.
     * @return Whether the user has any permissions on a repository root
     */
    @Idempotent
    boolean userHasPermissionsOnRepositoryRoot(String repoKey);

    /**
     * Checks if the current user has any permissions on the root of the specified repository.
     * This version of the method allows to pass a cache to avoid multiple checks on the same repository.
     *
     * @param repoKey     The repository key of the repository to check the user's permissions.
     * @param permissions The collection of permissions to check for.
     * @param cache       The optional cache for the previous permission checks (key is a repository name).
     * @return Whether the user has any permissions on a repository root
     */
    boolean userHasPermissionsOnRepositoryRoot(String repoKey, @Nonnull Collection<ArtifactoryPermission> permissions,
            @Nullable Map<String, Boolean> cache);

    /**
     * Getting a score per repo in the PermissionHeuristicScore range:
     * Rating is by the readability of the repo based on the current user permissions.
     */
    @Idempotent
    PermissionHeuristicScore getStrongestReadPermissionTarget(String repoKey);

    /**
     * Tells if current user is authorized to access the Trashcan.
     */
    @Idempotent
    boolean canAccessTrashcan();

    /**
     * Tells if current user is authorized to restore a specific file/folder from the Trashcan.
     */
    @Idempotent
    boolean canRestoreFromTrashcan(RepoPath repoPath);

    /**
     * Checks if a current user is authenticated with an access token and has scope permissions specified by a
     * <code>permissionScope</code> parameter.
     *
     * @param permissionScope a permission scope to check authorization for
     * @return <code>true</code> if a user is authenticated via a scoped access token and is authorized to access
     * a specified permission scope. Otherwise, <code>false</code>.
     */
    boolean isGrantedByAccessToken(PermissionScope permissionScope);

    /**
     * Checks if the current user is authorized to perform the given permission scope. this method always uses Access
     * client advanced cache
     *
     * @param permissionScope a permission scope to check authorization for
     * @return <code>true</code> if a user is authorized to perform the given permission scope. Otherwise,
     * <code>false</code>.
     */
    boolean principalCanDo(@NonNull PermissionScope permissionScope);

    /**
     * Returns the current request's Access principal when available. The principal is resolved by: (1) reading from
     * the request attribute if already set (e.g. by token auth or a previous call), (2) for Access Token
     * authentication, fetching and caching via Access, or (3) for other authenticated users, synthesizing from the
     * current user's username and groups when possible. May be empty for anonymous users or when the user cannot be
     * resolved in Access.
     *
     * @return the current Access principal, or empty if none is available
     */
    Optional<AccessPrincipal> getCurrentAccessPrincipal();

    /**
     * @return all the AclInfos of repo permissions
     */
    @Idempotent
    List<RepoAcl> getAllRepoAcls();

    /**
     * @return all the AclInfos of repo permissions for the current logged-in user for the type of permission given.
     */
    @Idempotent
    List<RepoAcl> getAllRepoAcls(ArtifactoryPermission permission);

    /**
     * @return all the AclInfos of build permissions for the current logged-in user for the type of permission given.
     */
    @Idempotent
    List<BuildAcl> getAllBuildAcls(ArtifactoryPermission permission);

    /**
     * @param excludeInternal - If True, INTERNAL permissions will be excluded
     * @return all the AclInfos of build permissions
     */
    @Idempotent
    List<BuildAcl> getAllBuildAcls(boolean excludeInternal);

    /**
     * @return all the AclInfos of release-bundle permissions for the current logged-in user for the type of
     * permission given.
     * NOTE: if user doesn't have EntPlus/Edge license we return an empty list (even if release-bundle acls exist!)
     */
    @Idempotent
    List<ReleaseBundleAcl> getAllReleaseBundleAcls(ArtifactoryPermission permission);

    /**
     * @param getByLicense if false - return all the AclInfos of release-bundle permissions, otherwise - if user
     *                     doesn't have EntPlus/Edge license return an empty list (even if release-bundle acls exist!)
     */
    @Idempotent
    List<ReleaseBundleAcl> getAllReleaseBundleAcls(boolean getByLicense);

    @Lock
    void createAcl(Acl acl);

    @Lock
    void deleteAcl(Acl acl);

    void updateAcl(Acl acl);

    /**
     * When effectiveAdmin is true, it bypass the normal isAdmin() checks and therefore it should be handled with care.
     * It was originally meant to allow a verified project admin to update ACL's.
     */
    void updateAcl(Acl acl, boolean effectiveAdmin);

    @Idempotent
    RepoAcl getRepoAcl(String permTargetName);

    @Idempotent
    BuildAcl getBuildAcl(String permTargetName);

    @Idempotent
    ReleaseBundleAcl getReleaseBundleAcl(String permTargetName);

    /**
     * Converts cached repo keys contained in the acl's permission target so that the '-cache' suffix is omitted.
     * When provided with a remote or local repository key, it will stay unchanged.
     *
     * @return a new MutableAclInfo with its permission target's repo keys modified to omit the '-cache' suffix
     */
    RepoAcl convertNewAclCachedRepoKeysToRemote(MutableRepoAcl acl);

    /**
     * return multi map permissions according to username and its groups ACL type (repo/build)
     *
     * @param type type of permissions to return
     */
    @Idempotent
    Multimap<PermissionTarget, AceInfo> getUserPermissionAndItsGroups(String username, ArtifactoryResourceType type);

    /**
     * get groups related permissions according to ACL type (repo/build)
     *
     * @param groups - groups to get permissions for
     * @param type   type of permissions to return
     * @return -map of permissions and groups access rights
     */
    @Idempotent
    Multimap<PermissionTarget, AceInfo> getGroupsPermissions(List<String> groups, ArtifactoryResourceType type);

    /**
     * Get all ACLs that has permissions on given repo path
     */
    @Idempotent
    List<RepoAcl> getRepoPathAcls(RepoPath repoPath);

    /**
     * Get all ACLs that has permissions on given build name
     */
    @Idempotent
    List<BuildAcl> getBuildPathAcls(String buildName, String buildNumber, String buildStarted, String repo);

    /**
     * Get all ACLs that has permissions on given build name. repo path is expected to be encoded
     *
     * @param excludeInternal - If True, INTERNAL permissions will be excluded
     */
    @Idempotent
    List<BuildAcl> getBuildPathAcls(RepoPath repoPath, boolean excludeInternal);

    /**
     * Get all release bundle ACLs that has permission on given path
     * NOTE: if user doesn't have EntPlus/Edge license we return an empty list (even if release-bundle acls exist!)
     */
    @Idempotent
    List<ReleaseBundleAcl> getReleaseBundleAcls(RepoPath repoPath);

    boolean isInternalAcl(Acl<? extends PermissionTarget> acl);

    void removeAllUserAces(String username);

    void removeAllGroupAces(String groupName);
}
