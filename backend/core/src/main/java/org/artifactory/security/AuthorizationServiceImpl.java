package org.artifactory.security;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.PermissionHeuristicScore;
import org.artifactory.logging.throttle.LogThrottleMarker;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.storage.db.security.service.access.AclMapper;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.AccessPrincipalImpl;
import org.jfrog.access.common.Action;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.rest.permission.Permission;
import org.jfrog.access.rest.user.User;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyList;
import static org.artifactory.api.security.PermissionHeuristicScore.admin;
import static org.artifactory.api.security.PermissionHeuristicScore.readNotAllowed;
import static org.artifactory.entitlement.EntitlementType.RELEASE_BUNDLES_READ;
import static org.artifactory.permissions.PermissionsCacheType.ACCESS;
import static org.artifactory.permissions.PermissionsCacheType.Profiles.PERMISSIONS_CACHE_ACCESS;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.security.ArtifactoryPermission.READ;
import static org.artifactory.security.PermissionTarget.ANY_LOCAL_REPO;
import static org.artifactory.security.PermissionTarget.ANY_REPO;
import static org.artifactory.util.RepoPathUtils.removeDashCache;
import static org.jfrog.access.common.ResourceType.BUILD;
import static org.jfrog.access.common.ResourceType.RELEASE_BUNDLE;
import static org.jfrog.access.common.ResourceType.REPO;

/**
 * <b>
 * This is the new implementation using the Access client permissions cache.
 * </b>
 * <p>
 * This service is intended to interact with Access for permission related checks.
 * If you are about to add code that doesn't use Access client - It shouldn't be here.
 * For users/groups related stuff, use UserGroupService.
 * For general security stuff, use SecurityService.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
@Service
@Profile(PERMISSIONS_CACHE_ACCESS)
public class AuthorizationServiceImpl extends AuthorizationServiceBase implements AuthorizationService {

    private static final String REPO_TYPE_ATTRIBUTE = "repoType";
    private static final Map<String, List<String>> REPO_TYPE_LOCAL_ATTRS =
            Map.of(REPO_TYPE_ATTRIBUTE, List.of("local"));
    private static final Map<String, List<String>> REPO_TYPE_REMOTE_ATTRS =
            Map.of(REPO_TYPE_ATTRIBUTE, List.of("remote"));

    @PostConstruct
    public void postConstruct() {
        log.info("Active permissions cache type: {}", ACCESS);
    }

    @Override
    public boolean canManage(PermissionTarget target) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            log.debug("canManage - Returning false due to unauthenticated user");
            return false;
        }
        if (isAdmin(authentication)) {
            log.debug("canManage - Returning true due to admin user");
            return true;
        }
        AccessPrincipal principal = createAccessPrincipal();
        if (principal == null) {
            log.debug("canManage - Returning false due to null principal");
            return false;
        }
        ResourceType resourceType;
        if (target instanceof BuildPermissionTarget) {
            resourceType = BUILD;
        } else if (target instanceof ReleaseBundlePermissionTarget) {
            resourceType = RELEASE_BUNDLE;
        } else if (target instanceof RepoPermissionTarget) {
            resourceType = REPO;
        } else {
            log.warn("Unknown permission target type: {}", target.getClass().getName());
            return false;
        }
        return accessService.getAccessClient().permissions()
                .findPrincipalPermissionsByResourceTypeAndAction(principal, resourceType, Action.MANAGE, false)
                .getPermissions()
                .stream()
                .map(Permission::getDisplayName)
                .anyMatch(target.getName()::equals);
    }

    @Override
    public boolean hasRepoPermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            log.debug("hasRepoPermission - Returning false due to unauthenticated user");
            return false;
        }
        if (isAdmin(authentication)) {
            log.debug("hasRepoPermission - Returning true due to admin user");
            return true;
        }
        AccessPrincipal principal = createAccessPrincipal();
        if (principal == null) {
            log.debug("hasRepoPermission - Returning false due to null principal");
            return false;
        }
        log.debug("hasRepoPermission - permission = {}, excludeInternal = {}",
                permission, excludeInternalProjectSpecific);
        return isRepoActionPermittedForPrincipal(permission, excludeInternalProjectSpecific, principal);
    }

    private boolean isRepoActionPermittedForPrincipal(ArtifactoryPermission permission, boolean excludeInternal,
            AccessPrincipal principal) {
        var repoPermissionTargets = accessService.getAccessClient().permissions()
                .findPrincipalPermissionsByResourceTypeAndAction(principal, REPO, toAction(permission),
                        !excludeInternal)
                .getPermissions()
                .stream()
                .map(AclMapper::toArtifactoryRepoAcl)
                .map(RepoAcl.class::cast)
                .map(RepoAcl::getPermissionTarget)
                .toList();
        log.debug("repoPermissionTargets = {}", repoPermissionTargets);
        return !repoPermissionTargets.isEmpty();
    }

    @Override
    public boolean hasBuildPermission(@Nullable String buildRepo, ArtifactoryPermission permission,
            boolean excludeInternalProjectSpecific) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            log.debug("hasBuildPermission - Returning false due to unauthenticated user");
            return false;
        }
        if (isAdmin(authentication)) {
            log.debug("hasBuildPermission - Returning true due to admin user");
            return true;
        }
        AccessPrincipal principal = createAccessPrincipal();
        if (principal == null) {
            log.debug("hasBuildPermission - Returning false due to null principal");
            return false;
        }
        log.debug("hasBuildPermission - buildRepo = {}, permission = {}, excludeInternal = {}",
                buildRepo, permission, excludeInternalProjectSpecific);
        return isBuildActionPermittedForPrincipal(buildRepo, permission, excludeInternalProjectSpecific, principal);
    }

    private boolean isBuildActionPermittedForPrincipal(@Nullable String buildRepo, ArtifactoryPermission permission,
            boolean excludeInternal, AccessPrincipal principal) {
        var buildPermissionTargets = accessService.getAccessClient().permissions()
                .findPrincipalPermissionsByResourceTypeAndAction(principal, BUILD, toAction(permission),
                        !excludeInternal)
                .getPermissions()
                .stream()
                .map(AclMapper::toArtifactoryBuildAcl)
                .map(BuildAcl.class::cast)
                .map(BuildAcl::getPermissionTarget)
                .filter(pt -> StringUtils.isEmpty(buildRepo) || pt.getRepoKeys().contains(buildRepo))
                .toList();
        log.debug("buildPermissionTargets = {}", buildPermissionTargets);
        return !buildPermissionTargets.isEmpty();
    }

    @Override
    public boolean hasReleaseBundlePermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific,
            boolean getByLicense) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            log.debug("hasReleaseBundlePermission - Returning false due to unauthenticated user");
            return false;
        }
        if (isAdmin(authentication)) {
            log.debug("hasReleaseBundlePermission - Returning true due to admin user");
            return true;
        }
        if (getByLicense && !addons.isEntitled(RELEASE_BUNDLES_READ)) {
            log.debug("hasReleaseBundlePermission - Returning false due to not entitled");
            return false;
        }
        AccessPrincipal principal = createAccessPrincipal();
        if (principal == null) {
            log.debug("hasReleaseBundlePermission - Returning false due to null principal");
            return false;
        }
        log.debug("hasReleaseBundlePermission - permission = {}, excludeInternal = {}, byLicense = {}",
                permission, excludeInternalProjectSpecific, getByLicense);
        return isReleaseBundleActionPermittedForPrincipal(permission, excludeInternalProjectSpecific, principal);
    }

    private boolean isReleaseBundleActionPermittedForPrincipal(ArtifactoryPermission permission,
            boolean excludeInternal, AccessPrincipal principal) {
        var releaseBundlePermissionTargets = accessService.getAccessClient().permissions()
                .findPrincipalPermissionsByResourceTypeAndAction(principal, RELEASE_BUNDLE, toAction(permission),
                        !excludeInternal)
                .getPermissions()
                .stream()
                .map(AclMapper::toArtifactoryReleaseBundleAcl)
                .map(ReleaseBundleAcl.class::cast)
                .map(ReleaseBundleAcl::getPermissionTarget)
                .toList();
        log.debug("releaseBundlePermissionTargets = {}", releaseBundlePermissionTargets);
        return !releaseBundlePermissionTargets.isEmpty();
    }

    @Override
    public boolean hasBuildBasicReadPermission() {
        if (buildFacade.hasBasicReadPermissionForAllBuilds()) {
            log.debug("hasBuildBasicReadPermission - Returning true");
            return true;
        }
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            log.debug("hasBuildBasicReadPermission - Returning false due to unauthenticated user");
            return false;
        }
        AccessPrincipal principal = createAccessPrincipal();
        if (principal == null) {
            log.debug("hasBuildBasicReadPermission - Returning false due to null principal");
            return false;
        }
        var buildPermissionTargets = accessService.getAccessClient().permissions()
                .findPrincipalPermissionsByResourceTypeAndAction(principal, BUILD, Action.READ, true)
                .getPermissions()
                .stream()
                .map(AclMapper::toArtifactoryBuildAcl)
                .map(BuildAcl.class::cast)
                .map(BuildAcl::getPermissionTarget)
                .toList();
        log.debug("buildPermissionTargets = {}", buildPermissionTargets);
        return !buildPermissionTargets.isEmpty();
    }

    @Override
    public List<RepoPermissionTarget> getRepoPermissionTargets(ArtifactoryPermission permission) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication == null) {
            log.debug("getRepoPermissionTargets - Returning empty list due to null authentication");
            return emptyList();
        }
        UserInfo userInfo = getSimpleUser(authentication).getDescriptor();
        if (userInfo.isEffectiveAdmin()) {
            log.debug("getRepoPermissionTargets - admin user, returning all repo permission targets");
            return getAllRepoAcls(permission).stream()
                    .map(RepoAcl::getPermissionTarget)
                    .toList();
        }
        AccessPrincipal principal = createAccessPrincipal();
        if (principal == null) {
            log.debug("getRepoPermissionTargets - Returning empty list due to null principal");
            return emptyList();
        }
        log.debug("getRepoPermissionTargets - permission = {}", permission);
        return accessService.getAccessClient().permissions()
                .findPrincipalPermissionsByResourceTypeAndAction(principal, REPO, toAction(permission), true)
                .getPermissions()
                .stream()
                .map(AclMapper::toArtifactoryRepoAcl)
                .map(RepoAcl.class::cast)
                .map(RepoAcl::getPermissionTarget)
                .toList();
    }

    /**
     * Getting a score per repo in the PermissionHeuristicScore range:
     * Rating is by the readability of the repo based on the current user permissions.
     * Highest is admin --> readAll --> readWithExclusion --> readNotAllowed
     * If there is read access:
     * In case repo permission target has no exclusion AND inclusion is default (ANY_PATH)  --> readAll
     * else -->  readWithExclusion
     *
     * @return Score of the repo in the PermissionHeuristicScore
     */
    @Override
    public PermissionHeuristicScore getStrongestReadPermissionTarget(String repoKey) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        SimpleUser simpleUser = getSimpleUser(authentication);
        if (simpleUser.isEffectiveAdmin()) {
            log.debug("The user is admin, returning admin permission heuristic score");
            return admin;
        }
        return getPermissionHeuristicScore(repoKey);
    }

    private PermissionHeuristicScore getPermissionHeuristicScore(String repoKey) {
        PermissionHeuristicScore bestScore = readNotAllowed;
        List<RepoPermissionTarget> repoPermissionTargets = getRepoPermissionTargets(READ);
        log.debug("repoPermissionTargets = {}", repoPermissionTargets);
        for (RepoPermissionTarget pt : repoPermissionTargets) {
            Set<String> repoKeys = pt.getRepoKeys();
            log.trace("pt = {}, repoKeys = {}", pt, repoKeys);
            if (repoKeys.contains(ANY_REPO) || repoKeys.contains(ANY_LOCAL_REPO) || repoKeys.contains(repoKey)) {
                bestScore = adjustScoreIfNeeded(pt, bestScore);
            }
        }
        log.debug("Best score for repo '{}': {}", repoKey, bestScore);
        return bestScore;
    }

    @Override
    public List<RepoAcl> getAllRepoAcls() {
        return accessService.getAccessClient().grpcPermissions().findPermissionsByResourceType(REPO)
                .stream()
                .map(AclMapper::toArtifactoryRepoAcl)
                .map(RepoAcl.class::cast)
                .toList();
    }

    @Override
    public List<RepoAcl> getAllRepoAcls(ArtifactoryPermission permission) {
        if (securityService.isAdmin()) {
            log.debug("getAllRepoAcls - admin user, returning all repo ACLs");
            return getAllRepoAcls();
        }
        AccessPrincipal principal = createAccessPrincipal();
        if (principal == null) {
            log.debug("getAllRepoAcls - Returning empty list due to null principal");
            return emptyList();
        }
        log.debug("getAllRepoAcls - permission = {}", permission);
        return accessService.getAccessClient().permissions()
                .findPrincipalPermissionsByResourceTypeAndAction(principal, REPO, toAction(permission), true)
                .getPermissions()
                .stream()
                .map(AclMapper::toArtifactoryRepoAcl)
                .map(RepoAcl.class::cast)
                .toList();
    }

    @Override
    public List<BuildAcl> getAllBuildAcls(ArtifactoryPermission permission) {
        if (securityService.isAdmin()) {
            log.debug("getAllBuildAcls - admin user, returning all build ACLs");
            return getAllBuildAcls(false);
        }
        AccessPrincipal principal = createAccessPrincipal();
        if (principal == null) {
            log.debug("getAllBuildAcls - Returning empty list due to null principal");
            return emptyList();
        }
        log.debug("getAllBuildAcls - permission = {}", permission);
        return accessService.getAccessClient().permissions()
                .findPrincipalPermissionsByResourceTypeAndAction(principal, BUILD, toAction(permission), true)
                .getPermissions()
                .stream()
                .map(AclMapper::toArtifactoryBuildAcl)
                .map(BuildAcl.class::cast)
                .toList();
    }

    @Override
    public List<BuildAcl> getAllBuildAcls(boolean excludeInternal) {
        return accessService.getAccessClient().grpcPermissions().findPermissionsByResourceType(BUILD)
                .stream()
                .map(AclMapper::toArtifactoryBuildAcl)
                .map(BuildAcl.class::cast)
                .filter(excludeInternal ? this::isNotInternalAcl : acl -> true)
                .toList();
    }

    @Override
    public List<ReleaseBundleAcl> getAllReleaseBundleAcls(ArtifactoryPermission permission) {
        if (!addons.isEntitled(RELEASE_BUNDLES_READ)) {
            log.debug("getAllReleaseBundleAcls - not entitled, returning empty list");
            return emptyList();
        }
        if (securityService.isAdmin()) {
            log.debug("getAllReleaseBundleAcls - admin user, returning all release bundle ACLs");
            return getAllReleaseBundleAcls(true);
        }
        AccessPrincipal principal = createAccessPrincipal();
        if (principal == null) {
            log.debug("getAllReleaseBundleAcls - Returning empty list due to null principal");
            return emptyList();
        }
        log.debug("getAllReleaseBundleAcls - permission = {}", permission);
        return accessService.getAccessClient().permissions()
                .findPrincipalPermissionsByResourceTypeAndAction(principal, RELEASE_BUNDLE, toAction(permission), true)
                .getPermissions()
                .stream()
                .map(AclMapper::toArtifactoryReleaseBundleAcl)
                .map(ReleaseBundleAcl.class::cast)
                .toList();
    }

    @Override
    public List<ReleaseBundleAcl> getAllReleaseBundleAcls(boolean getByLicense) {
        if (getByLicense && !addons.isEntitled(RELEASE_BUNDLES_READ)) {
            log.debug("getAllReleaseBundleAcls - not entitled, returning empty list");
            return emptyList();
        }
        return accessService.getAccessClient().grpcPermissions().findPermissionsByResourceType(RELEASE_BUNDLE)
                .stream()
                .map(AclMapper::toArtifactoryReleaseBundleAcl)
                .map(ReleaseBundleAcl.class::cast)
                .toList();
    }

    @Override
    public void createAcl(Acl acl) {
        assertAdmin();
        validatePermissionNameExists(acl.getPermissionTarget().getName());
        MutableAcl<? extends PermissionTarget> compatibleAcl = createAclAdapter(acl);
        cleanupAclInfo(compatibleAcl);
        createAclInternal(compatibleAcl);
        interceptors.onPermissionsAdd(compatibleAcl);
    }

    @Override
    public void deleteAcl(Acl acl) {
        deleteAclInternal(acl);
        interceptors.onPermissionsDelete(acl);
    }

    @Override
    public void updateAcl(Acl acl) {
        updateAcl(acl, securityService.isAdmin());
    }

    @Override
    public void updateAcl(Acl acl, boolean effectiveAdmin) {
        if (isInternalAcl(acl)) {
            return;
        }
        if (!effectiveAdmin) {
            validateUnmodifiedPermissionTarget(acl.getPermissionTarget());
        }
        MutableAcl<? extends PermissionTarget> compatibleAcl = createAclAdapter(acl);
        cleanupAclInfo(compatibleAcl);
        updateAclInternal(compatibleAcl);
        interceptors.onPermissionsUpdate(compatibleAcl);
    }

    @Override
    public RepoAcl getRepoAcl(String permTargetName) {
        return accessService.getAccessClient().permissions()
                .findByResourceTypeAndDisplayName(REPO, permTargetName)
                .map(AclMapper::toArtifactoryRepoAcl)
                .map(RepoAcl.class::cast)
                .orElse(null);
    }

    @Override
    public BuildAcl getBuildAcl(String permTargetName) {
        return accessService.getAccessClient().permissions()
                .findByResourceTypeAndDisplayName(BUILD, permTargetName)
                .map(AclMapper::toArtifactoryBuildAcl)
                .map(BuildAcl.class::cast)
                .orElse(null);
    }

    @Override
    public ReleaseBundleAcl getReleaseBundleAcl(String permTargetName) {
        return accessService.getAccessClient().permissions()
                .findByResourceTypeAndDisplayName(RELEASE_BUNDLE, permTargetName)
                .map(AclMapper::toArtifactoryReleaseBundleAcl)
                .map(ReleaseBundleAcl.class::cast)
                .orElse(null);
    }

    @Override
    public void removeAllUserAces(String username) {
        /*
         * In the old implementation, when a user was removed, all the user's permissions were removed from the cache.
         * This does nothing in the new implementation, and will be removed in the future.
         */
    }

    @Override
    public void removeAllGroupAces(String groupName) {
        /*
         * In the old implementation, when a group was removed, all the user's permissions were removed from the cache.
         * This does nothing in the new implementation, and will be removed in the future.
         */
    }

    @Override
    <T extends RepoPermissionTarget> boolean isGranted(RepoPath repoPath, ArtifactoryPermission permission,
            UserInfo userInfo) {
        // Make internal metadata of repo accessible to all (mirror Legacy bypass)
        if (InternalMetadataChecker.isInternalMetadata(repoPath.getPath())) {
            log.trace("Internal metadata of repo '{}' is accessible to all", repoPath.getRepoKey());
            return true;
        }
        if (isProjectScopeToken()) {
            log.debug("isGranted - Returning false due to project scope token");
            return false;
        }
        if (isReleaseBundleV1NotEntitled(repoPath)) {
            log.debug("isGranted - Returning false due to release bundle v1 repo and not entitled");
            return false;
        }
        AccessPrincipal principal = createAccessPrincipal(userInfo);
        if (principal == null) {
            log.debug("isGranted - Returning false due to null principal");
            return false;
        }
        org.jfrog.access.common.v2.Action action = toPermissionScopeAction(permission);
        PermissionScope scope = createArtifactPermissionScope(repoPath, action);
        log.debug("isGranted - repoPath = {}, permission = {}, action = {}", repoPath, permission, action);
        // Deprecated 3-arg form on purpose: the 2-arg overload defaults useAdvancedCache=false, which skips
        // the advanced cache that does the repoType wildcard matching this impl relies on. Keep passing true.
        return accessService.getAccessClient().cachedAuth().principalCanDo(principal, scope, true);
    }

    @Override
    <T extends RepoPermissionTarget> boolean isGranted(RepoPath repoPath, ArtifactoryPermission permission,
            GroupInfo groupInfo) {
        // Make internal metadata of repo accessible to all (mirror Legacy bypass)
        if (InternalMetadataChecker.isInternalMetadata(repoPath.getPath())) {
            log.trace("Internal metadata of repo '{}' is accessible to all", repoPath.getRepoKey());
            return true;
        }
        if (isReleaseBundleV1NotEntitled(repoPath)) {
            log.debug("isGranted - Returning false due to release bundle v1 repo and not entitled");
            return false;
        }
        String groupName = groupInfo.getGroupName();
        AccessPrincipal principal = AccessPrincipalImpl.create(null, List.of(groupName));
        org.jfrog.access.common.v2.Action action = toPermissionScopeAction(permission);
        PermissionScope scope = createArtifactPermissionScope(repoPath, action);
        log.debug("isGranted - repoPath = {}, permission = {}, groupName = {}, action = {}",
                repoPath, permission, groupName, action);
        // See the user overload above: the deprecated 3-arg form forces useAdvancedCache=true.
        return accessService.getAccessClient().cachedAuth().principalCanDo(principal, scope, true);
    }

    private boolean isReleaseBundleV1NotEntitled(RepoPath repoPath) {
        return isReleaseBundlesRepoV1(repoPath.getRepoKey()) && !addons.isEntitled(RELEASE_BUNDLES_READ);
    }

    @Override
    public boolean isGrantedByAccessToken(PermissionScope permissionScope) {
        return checkPermissionWithAccessToken(permissionScope, true);
    }

    @Override
    public boolean principalCanDo(@NonNull PermissionScope permissionScope) {
        return checkPermissionWithAccessToken(permissionScope, true);
    }

    @Override
    protected Optional<AccessPrincipal> getSynthesizedAccessPrincipal() {
        return Optional.ofNullable(createAccessPrincipal());
    }

    @Nullable
    private AccessPrincipal createAccessPrincipal() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication) || !(authentication.getPrincipal() instanceof SimpleUser)) {
            log.trace("Cannot create AccessPrincipal. Current authentication principal is not a SimpleUser. Returning" +
                      " null");
            return null;
        }
        UserInfo userInfo = getSimpleUser(authentication).getDescriptor();
        return createAccessPrincipal(userInfo);
    }

    @Nullable
    private AccessPrincipal createAccessPrincipal(UserInfo userInfo) {
        List<String> groups = userInfo.getGroups().stream()
                .map(UserGroupInfo::getGroupName)
                .toList();
        log.trace("groups = {}", groups);
        if (isTokenScopeOnGroupsOnly(userInfo)) {
            return AccessPrincipalImpl.create(null, groups);
        }
        String username = userInfo.getUsername();
        log.trace("username = {}", username);
        User user = accessService.getAccessClient().cachedUsers().findUserByUsername(username, true).orElse(null);
        if (user == null) {
            if (groups.isEmpty()) {
                log.warn(LogThrottleMarker.getInstance(),
                        "createAccessPrincipal: degraded principal, returning null. " +
                                "Access did not resolve username='{}' and no token-scope groups present. " +
                                "Subsequent permission checks for this caller will fail-closed in this impl.",
                        username);
                return null;
            }
            log.warn(LogThrottleMarker.getInstance(),
                    "createAccessPrincipal: degraded principal, building groups-only. " +
                            "Access did not resolve username='{}'; falling back to {} group(s) from authentication.",
                    username, groups.size());
            return AccessPrincipalImpl.create(null, groups);
        }
        // The user is resolved by Access; it resolves the user's group memberships and the associated
        // permissions internally, so we intentionally pass no groups here and avoid blending the
        // authentication-cache group snapshot into the principal.
        return AccessPrincipalImpl.create(user, List.of());
    }

    /**
     * When the current user comes from an {@code applied-permissions/groups} access token, its username is the
     * token *creator* (e.g. "admin"), not the effective principal. In that case the principal must be built
     * from the token-scope groups only and the username must NOT be resolved through Access. Identity tokens
     * keep the user-scope behavior.
     *
     * <p>Note: unlike {@link LegacyAuthorizationServiceImpl}, this implementation intentionally does not honor
     * the {@code force.user.permissions.over.token.scopes} legacy kill-switch. The flag has defaulted to
     * {@code false} for a long time and is no longer needed.
     */
    private boolean isTokenScopeOnGroupsOnly(UserInfo userInfo) {
        return userInfo.isAppliedPermissionsGroups() && !userInfo.isIdentity();
    }

    @VisibleForTesting
    Action toAction(ArtifactoryPermission artifactoryPermission) {
        return switch (artifactoryPermission) {
            case READ -> Action.READ;
            case DEPLOY -> Action.DEPLOY;
            case ANNOTATE -> Action.ANNOTATE;
            case DELETE -> Action.DELETE;
            case MANAGE -> Action.MANAGE;
            case MANAGED_XRAY_META -> Action.MANAGE_XRAY_MD;
            case DISTRIBUTE -> Action.DISTRIBUTE;
        };
    }

    @VisibleForTesting
    org.jfrog.access.common.v2.Action toPermissionScopeAction(ArtifactoryPermission artifactoryPermission) {
        return switch (artifactoryPermission) {
            case READ -> org.jfrog.access.common.v2.Action.READ;
            case DEPLOY -> org.jfrog.access.common.v2.Action.WRITE;
            case ANNOTATE -> org.jfrog.access.common.v2.Action.ANNOTATE;
            case DELETE -> org.jfrog.access.common.v2.Action.DELETE;
            case MANAGE -> org.jfrog.access.common.v2.Action.MANAGE;
            case MANAGED_XRAY_META -> org.jfrog.access.common.v2.Action.SCAN;
            case DISTRIBUTE -> org.jfrog.access.common.v2.Action.EXECUTE;
        };
    }

    private PermissionScope createArtifactPermissionScope(RepoPath repoPath,
            org.jfrog.access.common.v2.Action action) {
        String repoKey = repoPath.getRepoKey();
        if (isBuildInfoRepo(repoKey)) {
            var buildRepoPath = RepoPathFactory.create(repoKey, getPathToCheckPermissionFor(repoPath));
            var scope = createPermissionScope(PermissionScopeResourceType.BUILD, buildRepoPath, action);
            log.debug("Created build permission scope: {}", scope);
            return scope;
        }
        if (isReleaseBundleRepo(repoKey)) {
            var scope = createPermissionScope(PermissionScopeResourceType.RELEASE_BUNDLE, repoPath, action);
            log.debug("Created release bundle permission scope: {}", scope);
            return scope;
        }
        if (isLocal(repoKey)) {
            // repoType=local lets the client match the ANY / ANY LOCAL wildcards (JA-21681).
            var scope = createPermissionScope(PermissionScopeResourceType.ARTIFACT, repoPath, action,
                    REPO_TYPE_LOCAL_ATTRS);
            log.debug("Created artifact permission scope for local repo: {}", scope);
            return scope;
        } else if (isRemote(repoKey)) {
            // repoType=remote lets the client match ANY / ANY REMOTE (JA-21681) and alias the
            // bare<->"-cache" key itself (JA-21845), so no explicit "-cache" scope is needed.
            var scope = createPermissionScope(PermissionScopeResourceType.ARTIFACT, repoPath, action,
                    REPO_TYPE_REMOTE_ATTRS);
            log.debug("Created artifact permission scope for remote repo: {}", scope);
            return scope;
        }
        // Unknown type (virtual/distribution/deleted): no repoType. The client still matches a plain "ANY"
        // grant (isTargetMatch/isAny check contains("ANY") first), but not the typed ANY LOCAL / ANY REMOTE.
        log.trace("Could not determine if the repository '{}' is local or remote", repoKey);
        var scope = createPermissionScope(PermissionScopeResourceType.ARTIFACT, repoPath, action);
        log.debug("Created artifact permission scope for repo of unknown type: {}", scope);
        return scope;
    }

    private boolean isLocal(String repoKey) {
        return repositoryService.existsByType(LOCAL, repoKey) ||
                repositoryService.existsByType(FEDERATED, repoKey);
    }

    private boolean isRemote(String repoKey) {
        return repositoryService.existsByType(REMOTE, removeDashCache(repoKey)) ||
                repositoryService.existsByType(REMOTE, repoKey);
    }
}
