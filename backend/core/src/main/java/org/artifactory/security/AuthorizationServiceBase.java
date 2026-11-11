package org.artifactory.security;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jfrog.build.acl.model.BuildRepoPath;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.security.AuthorizationException;
import org.artifactory.api.security.PermissionHeuristicScore;
import org.artifactory.api.security.SecurityService;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.io.checksum.ChecksumUtils;
import org.artifactory.model.security.MutableRepoAclImpl;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.access.ArtifactoryRepoPathScopeToken;
import org.artifactory.security.interceptor.SecurityConfigurationChangesInterceptors;
import org.artifactory.security.signature.SignedUrlAuthenticationToken;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.util.AlreadyExistsException;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.PathMatcher;
import org.artifactory.util.RepoPathUtils;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.permission.PermissionsClient;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.v2.Action;
import org.jfrog.access.token.JwtAccessToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.jfrog.build.acl.util.BuildInfoUtils.generateBuildJsonRepoPath;
import static org.artifactory.addon.observability.ObservabilityScopeToken.LOG_SHIPPING_WRITE;
import static org.artifactory.addon.observability.ObservabilityScopeToken.USAGE_READ;
import static org.artifactory.addon.observability.ObservabilityScopeToken.USAGE_WRITE;
import static org.artifactory.api.security.AuthorizationService.INTERNAL_PERMISSION_PREFIX;
import static org.artifactory.api.security.PermissionHeuristicScore.readAll;
import static org.artifactory.api.security.PermissionHeuristicScore.readWithExclusion;
import static org.artifactory.api.security.SecurityService.USER_SYSTEM;
import static org.artifactory.common.ConstantValues.authProjectScopeTokenAllMatch;
import static org.artifactory.descriptor.repo.RepoType.PipeInfo;
import static org.artifactory.repo.config.RepoConfigDefaultValues.POLICY_REPORTS_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.FULL_SYNC_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.GRID_INFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.artifactory.repo.trash.TrashService.TRASH_KEY;
import static org.artifactory.security.ArtifactoryPermission.ANNOTATE;
import static org.artifactory.security.ArtifactoryPermission.DELETE;
import static org.artifactory.security.ArtifactoryPermission.DEPLOY;
import static org.artifactory.security.ArtifactoryPermission.DISTRIBUTE;
import static org.artifactory.security.ArtifactoryPermission.MANAGE;
import static org.artifactory.security.ArtifactoryPermission.READ;
import static org.artifactory.security.PermissionTarget.ANY_LOCAL_REPO;
import static org.artifactory.security.PermissionTarget.ANY_PATH;
import static org.artifactory.security.PermissionTarget.ANY_REMOTE_REPO;
import static org.artifactory.security.PermissionTarget.ANY_REPO;
import static org.artifactory.security.permissionscope.PermissionScopeToken.INTERNAL_SUPPORT_BUNDLE_DELETE;
import static org.artifactory.security.permissionscope.PermissionScopeToken.INTERNAL_SUPPORT_BUNDLE_READ;
import static org.artifactory.security.permissionscope.PermissionScopeToken.INTERNAL_SUPPORT_BUNDLE_WRITE;
import static org.artifactory.storage.db.security.service.access.AclMapper.aclToAccessPermission;
import static org.artifactory.util.RepoPathUtils.removeDashCache;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundlesV2RepoKey;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
public abstract class AuthorizationServiceBase {
    static final String ACCESS_PRINCIPAL_ATTRIBUTE = "access_principal";
    AddonsManager addons;
    AccessService accessService;
    SecurityService securityService;
    InternalRepositoryService repositoryService;
    ProjectsService projectsService;
    BuildFacade buildFacade;
    UserGroupStoreService userGroupStoreService;
    SecurityConfigurationChangesInterceptors interceptors;

    @Autowired
    public void setAddons(AddonsManager addons) {
        this.addons = addons;
    }

    @Autowired
    public void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setRepositoryService(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setBuildFacade(BuildFacade buildFacade) {
        this.buildFacade = buildFacade;
    }

    @Autowired
    public void setUserGroupStoreService(UserGroupStoreService userGroupStoreService) {
        this.userGroupStoreService = userGroupStoreService;
    }

    @Autowired
    public void setInterceptors(SecurityConfigurationChangesInterceptors interceptors) {
        this.interceptors = interceptors;
    }

    public boolean canRead(RepoPath repoPath) {
        return hasPermission(PermissionScopeResourceType.ARTIFACT, repoPath, READ);
    }

    public boolean canRead(UserInfo user, RepoPath path) {
        return hasPermission(new SimpleUser(user), path, READ);
    }

    public boolean canRead(GroupInfo group, RepoPath path) {
        return hasPermission(group, path, READ);
    }

    public boolean canAnnotate(RepoPath repoPath) {
        return hasPermission(PermissionScopeResourceType.ARTIFACT, repoPath, ANNOTATE);
    }

    public boolean canAnnotate(UserInfo user, RepoPath path) {
        return hasPermission(new SimpleUser(user), path, ANNOTATE);
    }

    public boolean canAnnotate(GroupInfo group, RepoPath path) {
        return hasPermission(group, path, ANNOTATE);
    }

    public boolean canDelete(RepoPath repoPath) {
        return hasPermission(PermissionScopeResourceType.ARTIFACT, repoPath, DELETE);
    }

    public boolean canDelete(UserInfo user, RepoPath path) {
        return hasPermission(new SimpleUser(user), path, DELETE);
    }

    public boolean canDelete(GroupInfo group, RepoPath path) {
        return hasPermission(group, path, DELETE);
    }

    public boolean canDeploy(RepoPath repoPath) {
        return hasPermission(PermissionScopeResourceType.ARTIFACT, repoPath, DEPLOY);
    }

    public boolean canDeploy(UserInfo user, RepoPath path) {
        return hasPermission(new SimpleUser(user), path, DEPLOY);
    }

    public boolean canDeploy(GroupInfo group, RepoPath path) {
        return hasPermission(group, path, DEPLOY);
    }

    public boolean canManage(RepoPath repoPath) {
        return hasPermission(PermissionScopeResourceType.ARTIFACT, repoPath, MANAGE);
    }

    public boolean canManage(UserInfo user, RepoPath path) {
        return hasPermission(new SimpleUser(user), path, MANAGE);
    }

    public boolean canManage(GroupInfo group, RepoPath path) {
        return hasPermission(group, path, MANAGE);
    }

    public boolean hasPermission(ArtifactoryPermission permission) {
        return hasPermission(permission, false);
    }

    public boolean hasPermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific) {
        return hasRepoPermission(permission, excludeInternalProjectSpecific) ||
                hasBuildPermission(null, permission, excludeInternalProjectSpecific) ||
                hasReleaseBundlePermission(permission, excludeInternalProjectSpecific);
    }

    public boolean hasRepoPermission(ArtifactoryPermission permission) {
        return hasRepoPermission(permission, false);
    }

    public abstract boolean hasRepoPermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific);

    public boolean hasBuildPermission(@Nullable String buildRepo, ArtifactoryPermission permission) {
        return hasBuildPermission(buildRepo, permission, false);
    }

    public abstract boolean hasBuildPermission(@Nullable String buildRepo, ArtifactoryPermission permission,
            boolean excludeInternalProjectSpecific);

    public boolean hasReleaseBundlePermission(ArtifactoryPermission permission) {
        return hasReleaseBundlePermission(permission, false);
    }

    public boolean hasReleaseBundlePermission(ArtifactoryPermission permission,
            boolean excludeInternalProjectSpecific) {
        return hasReleaseBundlePermission(permission, excludeInternalProjectSpecific, true);
    }

    public abstract boolean hasReleaseBundlePermission(ArtifactoryPermission permission,
            boolean excludeInternalProjectSpecific, boolean getByLicense);

    public boolean hasPermission(PermissionScopeResourceType resourceType, RepoPath repoPath,
            ArtifactoryPermission permission) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        String repoKey = repoPath.getRepoKey();
        if (!isAuthenticated(authentication)) {
            log.trace("Not authenticated for {}", repoPath);
            return false;
        }
        if (repoKey.equals(IN_TRANSIT_REPO_KEY) && READ.equals(permission)) {
            boolean admin = isAdmin(authentication);
            log.trace("Access for: '{}' repo read (admin only): {}", IN_TRANSIT_REPO_KEY, admin);
            return admin;
        }
        if (repoKey.equals(LOG_SHIPPING_REPO_KEY)) {
            return isLogShippingAllowed(permission);
        }
        if (repoKey.equals(USAGE_REPO_KEY)) {
            return isUsageRepoAllowed(permission);
        }
        if (repoKey.equals(FULL_SYNC_REPO_KEY)) {
            return isFullSyncRepoAllowed();
        }
        if (GRID_INFO_REPO_KEY.equals(repoKey)) {
            return isGridInfoRepoAllowed();
        }
        if (SUPPORT_BUNDLE_REPO_KEY.equals(repoKey)) {
            return isSupportBundleRepoAllowed(permission);
        }
        if (POLICY_REPORTS_REPO_KEY.equals(repoKey)) {
            boolean admin = isAdmin(authentication);
            log.trace("Access for: '{}' repo (admin only): {}", POLICY_REPORTS_REPO_KEY, admin);
            return admin;
        }
        if (isAdmin(authentication)) {
            log.trace("Granting full access for: '{}' to admin/project admin", repoPath);
            return true;
        }
        if (isPipeInfoRepo(repoKey) && !isProjectAdmin(repoPath, authentication)) {
            log.trace("Access for: '{}' repo of type: '{}' is denied (only visible for admin users)", repoKey,
                    PipeInfo);
            return false;
        }
        if (securityService.isAnonymous() && !securityService.isAnonAccessEnabled()) {
            log.trace("Access for: '{}' is denied to 'anonymous' user. Anonymous access is disabled.", repoPath);
            return false;
        }
        if (RepoPathUtils.isTrash(repoPath) && !securityService.isAdmin()) {
            return isGrantedOnTrashcan(resourceType, repoPath, permission, authentication);
        }
        boolean granted = isGranted(resourceType, repoPath, permission, authentication);
        log.trace("Access to {} for {}: {}", permission, repoPath, granted ? "Granted" : "Denied");
        return granted;
    }

    private boolean hasPermission(SimpleUser user, RepoPath repoPath, ArtifactoryPermission permission) {
        // Admins has permissions for all paths and all repositories
        if (user.isEffectiveAdmin()) {
            return true;
        }
        // Anonymous users are checked only if anonymous access is enabled
        if (user.isAnonymous() && !securityService.isAnonAccessEnabled()) {
            return false;
        }
        // Make internal metadata of repo accessible to all internal calls
        if (InternalMetadataChecker.isInternalMetadata(repoPath.getPath())) {
            return true;
        }
        return isGranted(repoPath, permission, user.getDescriptor());
    }

    private boolean hasPermission(GroupInfo group, RepoPath repoPath, ArtifactoryPermission permission) {
        if (group.isAdminPrivileges()) {
            return true;
        }
        return isGranted(repoPath, permission, group);
    }

    static boolean isAdmin(Authentication authentication) {
        return isAuthenticated(authentication) && getSimpleUser(authentication).isEffectiveAdmin();
    }

    static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    static SimpleUser getSimpleUser(Authentication authentication) {
        return (SimpleUser) authentication.getPrincipal();
    }

    private boolean isLogShippingAllowed(ArtifactoryPermission permission) {
        boolean logShippingAllowed;
        if (READ.equals(permission) || DELETE.equals(permission)) {
            logShippingAllowed = securityService.isAdmin();
            log.trace("Access for: '{}' repo read/delete (admin only): {}", LOG_SHIPPING_REPO_KEY, logShippingAllowed);
        } else {
            logShippingAllowed = USER_SYSTEM.equals(securityService.currentUsername()) ||
                    isGrantedByAccessToken(PermissionScope.parse(LOG_SHIPPING_WRITE));
            log.trace("Access for: '{} repo deploy/manage (system or scope only): {}", LOG_SHIPPING_REPO_KEY,
                    logShippingAllowed);
        }
        return logShippingAllowed;
    }

    private boolean isUsageRepoAllowed(ArtifactoryPermission permission) {
        boolean systemUser = USER_SYSTEM.equals(securityService.currentUsername());
        if (READ.equals(permission)) {
            systemUser = systemUser || isGrantedByAccessToken(PermissionScope.parse(USAGE_READ));
            log.trace("Access for: '{}' repo read/delete (system or correct scope): {}", USAGE_REPO_KEY, systemUser);
        } else {
            systemUser = systemUser || isGrantedByAccessToken(PermissionScope.parse(USAGE_WRITE));
            log.trace("Access for: '{}' repo deploy/manage (system or correct scope): {}", USAGE_REPO_KEY, systemUser);
        }
        return systemUser;
    }

    private boolean isFullSyncRepoAllowed() {
        boolean fullSyncRepoAllowed = USER_SYSTEM.equals(securityService.currentUsername());
        log.trace("Access for: '{}' (system only): {}", FULL_SYNC_REPO_KEY, fullSyncRepoAllowed);
        return fullSyncRepoAllowed;
    }

    private boolean isGridInfoRepoAllowed() {
        boolean gridInfoRepoAllowed = USER_SYSTEM.equals(securityService.currentUsername());
        log.trace("Access for: '{}' (system only): {}", GRID_INFO_REPO_KEY, gridInfoRepoAllowed);
        return gridInfoRepoAllowed;
    }

    private boolean isSupportBundleRepoAllowed(ArtifactoryPermission permission) {
        boolean supportBundleRepoAllowed;
        if (securityService.isAdmin()) {
            if (!addons.isEntitled(EntitlementType.EDGE_MIXED)) {
                supportBundleRepoAllowed = true;
                log.trace("Access for: '{}' repo granted (admin only) - Not 'Edge Mixed' license",
                        SUPPORT_BUNDLE_REPO_KEY);
            } else if (DEPLOY.equals(permission)) {
                supportBundleRepoAllowed = isInternalSupportBundleAllowed(permission);
                log.trace("Access for: '{}' on Edge (admin can deploy only with token of internal scope): {}",
                        SUPPORT_BUNDLE_REPO_KEY, supportBundleRepoAllowed);
            } else {
                supportBundleRepoAllowed = true;
            }
        } else {
            supportBundleRepoAllowed = isInternalSupportBundleAllowed(permission);
            log.trace("Access for: '{}' (non admin only with token of internal scope: {}",
                    SUPPORT_BUNDLE_REPO_KEY, supportBundleRepoAllowed);
        }
        return supportBundleRepoAllowed;
    }

    private boolean isInternalSupportBundleAllowed(ArtifactoryPermission permission) {
        if (READ.equals(permission)) {
            return isGrantedByAccessToken(INTERNAL_SUPPORT_BUNDLE_READ);
        }
        if (DEPLOY.equals(permission)) {
            return isGrantedByAccessToken(INTERNAL_SUPPORT_BUNDLE_WRITE); // check for token of service admin token
        }
        return DELETE.equals(permission) && isGrantedByAccessToken(INTERNAL_SUPPORT_BUNDLE_DELETE);
    }

    private boolean isPipeInfoRepo(String repoKey) {
        return repositoryService.existsByPackageType(PipeInfo, repoKey);
    }

    private boolean isProjectAdmin(RepoPath repoPath, Authentication authentication) {
        String project = projectsService.getProjectKeyOfRepository(repoPath.getRepoKey());
        return projectsService.isProjectAdmin(project, getSimpleUser(authentication));
    }

    /**
     * Checks if a permission is granted for a resource inside the Trashcan.
     */
    private boolean isGrantedOnTrashcan(PermissionScopeResourceType resourceType, RepoPath repoPath,
            ArtifactoryPermission permission, Authentication authentication) {
        boolean trashCanAccessAllowed = false;
        if (READ.equals(permission)) {
            if (repoPath.isRoot()) {
                trashCanAccessAllowed = canAccessTrashcan();
                log.trace("Access for: '{}' repo root: {}", TRASH_KEY, trashCanAccessAllowed);
            } else {
                trashCanAccessAllowed = canRestoreFromTrashcan(repoPath);
                log.trace("Access for: '{}' restore: {}", TRASH_KEY, trashCanAccessAllowed);
            }
        } else {
            RepoPath originRepoRootPath = RepoPathUtils.getOriginRepoPathForTrash(repoPath);
            if (originRepoRootPath != null) {
                trashCanAccessAllowed = isGranted(resourceType, originRepoRootPath, permission, authentication);
                log.trace("Access for trashcan origin repo root: {}", trashCanAccessAllowed);
            }
        }
        return trashCanAccessAllowed;
    }

    @VisibleForTesting
    boolean isGranted(PermissionScopeResourceType resourceType, RepoPath repoPath,
            ArtifactoryPermission permission, Authentication authentication) {
        if (isGranted(repoPath, permission, getSimpleUser(authentication).getDescriptor())) {
            log.trace("{} access granted: for {}", permission, repoPath);
            return true;
        }
        if (READ.equals(permission) && isGrantedByAccessToken(repoPath, authentication)) {
            log.trace("{} access granted: Granted by access token for {}", permission, repoPath);
            return true;
        }
        if ((READ == permission || DEPLOY == permission)
                && isGrantedBySignedUrl(repoPath, authentication, permission)) {
            log.trace("{} access granted: Granted by signed URL for {}", permission, repoPath);
            return true;
        }
        if (isGrantedByResourcePermissionScopedToken(resourceType, repoPath, permission, authentication)) {
            log.trace("{} access granted: Granted by scoped token for {}", permission, repoPath);
            return true;
        }
        return false;
    }

    @VisibleForTesting
    boolean isGrantedBySignedUrl(RepoPath repoPath, Authentication authentication,
            ArtifactoryPermission permission) {
        if (authentication instanceof SignedUrlAuthenticationToken signedUrlAuthenticationToken) {
            String internalRepoKey = signedUrlAuthenticationToken.getInternalRepo();
            if (internalRepoKey == null) {
                // Sign url for repo can read only
                return permission.equals(READ);
            }
            // Signed url with internal repoKey can read and deploy
            return (repoPath.getRepoKey().equals(internalRepoKey) ||
                    removeDashCache(repoPath.getRepoKey()).equals(internalRepoKey)) &&
                    (permission.equals(READ) || permission.equals(DEPLOY));
        }
        return false;
    }

    /**
     * @param resourceType the resource type to check access permission for
     * @param repoPath     the repo path to check access permission for
     * @return permission scope result if provided a non-null {@link Action}, false otherwise
     */
    @VisibleForTesting
    boolean isGrantedByResourcePermissionScopedToken(PermissionScopeResourceType resourceType,
            RepoPath repoPath, ArtifactoryPermission permission, Authentication authentication) {
        Action permissionScopeAction =
                ArtifactoryPermissionScopeAction.fromArtifactoryPermission(permission);
        if (!(authentication instanceof AccessTokenAuthentication) || permissionScopeAction == null) {
            return false;
        }
        return isGrantedByAccessToken(createPermissionScope(resourceType, repoPath, permissionScopeAction));
    }

    abstract <T extends RepoPermissionTarget> boolean isGranted(RepoPath repoPath, ArtifactoryPermission permission,
            UserInfo user);

    abstract <T extends RepoPermissionTarget> boolean isGranted(RepoPath repoPath, ArtifactoryPermission permission,
            GroupInfo groupInfo);

    PermissionScope createPermissionScope(PermissionScopeResourceType resourceType, RepoPath repoPath,
            Action permissionScopeAction) {
        return createPermissionScope(resourceType, repoPath, permissionScopeAction, null);
    }

    PermissionScope createPermissionScope(PermissionScopeResourceType resourceType, RepoPath repoPath,
            Action permissionScopeAction, @Nullable Map<String, List<String>> attributes) {
        String repoKey = removeDashCache(repoPath.getRepoKey());
        String path = StringUtils.isNotEmpty(repoPath.getPath()) ? repoPath.getPath() : null;
        return PermissionScope.create(resourceType, repoKey, path, permissionScopeAction, null, attributes);
    }

    public abstract boolean isGrantedByAccessToken(PermissionScope permissionScope);


    @SuppressWarnings("deprecation")
    boolean checkPermissionWithAccessToken(@NonNull PermissionScope permissionScope, boolean useAdvancedCache) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        AccessPrincipal principal = getPrincipalFromAccessToken(authentication);
        if (principal == null) {
            return false;
        }

        return accessService.getAccessClient().cachedAuth()
                .principalCanDo(principal, permissionScope, useAdvancedCache);
    }

    /**
     * If authentication is of type {@link AccessTokenAuthentication}, test it's scope tokens for read access from a
     * {@link ArtifactoryRepoPathScopeToken}
     */
    @VisibleForTesting
    boolean isGrantedByAccessToken(RepoPath repoPath, Authentication authentication) {
        if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
            List<String> scope = getScope(accessTokenAuthentication);
            String repoPathChecksum = calculateRepoPathCheckSum(repoPath);
            return scope.stream().anyMatch(scopeToken -> isGrantedByAccessScopeToken(scopeToken, repoPathChecksum));
        }
        return false;
    }

    private List<String> getScope(AccessTokenAuthentication authentication) {
        return authentication.getAccessToken().getScope();
    }

    private String calculateRepoPathCheckSum(RepoPath repoPath) {
        String repoPathName = repoPath.getRepoKey() + RepoPath.PATH_SEPARATOR + repoPath.getPath();
        return ChecksumUtils.calculateSha1(new ByteArrayInputStream(repoPathName.getBytes()));
    }

    /**
     * Check if the scope token is of type {@link ArtifactoryRepoPathScopeToken}, and then compare repoPath checksum
     * with token checksum
     */
    private boolean isGrantedByAccessScopeToken(String scopeToken, String repoPathChecksum) {
        return ArtifactoryRepoPathScopeToken.accepts(scopeToken) &&
                repoPathChecksum.equals(ArtifactoryRepoPathScopeToken.getPath(scopeToken));
    }

    private AccessPrincipal getPrincipalFromAccessToken(Authentication authentication) {
        HttpServletRequest request = RequestThreadLocal.getRequest();
        return getAccessPrincipalFromRequest(request)
                .orElseGet(() -> fetchAndSetAccessPrincipal(request, authentication));
    }

    private Optional<AccessPrincipal> getAccessPrincipalFromRequest(HttpServletRequest request) {
        if (request != null) {
            var attribute = request.getAttribute(ACCESS_PRINCIPAL_ATTRIBUTE);
            if (attribute instanceof AccessPrincipal principal) {
                return Optional.of(principal);
            }
        }
        return Optional.empty();
    }

    private AccessPrincipal fetchAndSetAccessPrincipal(HttpServletRequest request, Authentication authentication) {
        AccessPrincipal principal = getAccessPrincipal(authentication);
        if (request != null && principal != null) {
            request.setAttribute(ACCESS_PRINCIPAL_ATTRIBUTE, principal);
        }
        return principal;
    }


    private AccessPrincipal getAccessPrincipal(Authentication authentication) {
        if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
            String bearer = toBearer(accessTokenAuthentication.getAccessToken());
            try {
                LoginResult loginResult = accessService.getAccessClient().cachedAuth().loginByAuthHeader(bearer);
                return loginResult.getPrincipal();
            } catch (Exception e) {
                log.warn("Failed to authenticate with access token", e);
                return null;
            }
        }
        return null;
    }

    private String toBearer(JwtAccessToken token) {
        return "Bearer " + token.getTokenValue();
    }


    public Optional<AccessPrincipal> getCurrentAccessPrincipal() {
        HttpServletRequest request = RequestThreadLocal.getRequest();
        Optional<AccessPrincipal> fromRequest = getAccessPrincipalFromRequest(request);
        if (fromRequest.isPresent()) {
            log.trace("Access principal already present on request. Returning it.");
            return fromRequest;
        }
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            log.trace("No authentication on current request. Returning empty access principal.");
            return Optional.empty();
        }
        if (authentication instanceof AccessTokenAuthentication) {
            AccessPrincipal principal = fetchAndSetAccessPrincipal(request, authentication);
            if (principal != null) {
                log.trace("Successfully fetched and set access principal on request");
            }
            return Optional.ofNullable(principal);
        }
        Optional<AccessPrincipal> synthesized = getSynthesizedAccessPrincipal();
        if (synthesized.isPresent() && request != null) {
            log.trace("Successfully synthesized access principal and set it on request");
            request.setAttribute(ACCESS_PRINCIPAL_ATTRIBUTE, synthesized.get());
        }
        return synthesized;
    }

    /**
     * Synthesizes an Access principal from the current authenticated user (username and groups) when possible.
     * Used by {@link #getCurrentAccessPrincipal()} for non-token authentication flows.
     */
    protected abstract Optional<AccessPrincipal> getSynthesizedAccessPrincipal();

    public boolean canAccessTrashcan() {
        return securityService.isAdmin() || hasRepoPermission(DEPLOY) || hasRepoPermission(MANAGE);
    }

    public boolean canRestoreFromTrashcan(RepoPath repoPath) {
        if (securityService.isAdmin()) {
            return true;
        }
        RepoPath originRepoRootPath = RepoPathUtils.getOriginRepoPathForTrash(repoPath);
        return originRepoRootPath != null && (canDeploy(originRepoRootPath) || canManage(originRepoRootPath));
    }

    public RepoAcl convertNewAclCachedRepoKeysToRemote(MutableRepoAcl acl) {
        MutableRepoPermissionTarget mutablePermissionTargetInfo =
                InfoFactoryHolder.get().copyRepoPermissionTarget(acl.getPermissionTarget());
        Set<String> compatibleRepoKeys =
                repositoryService.convertCachedRepoKeysToRemote(mutablePermissionTargetInfo.getRepoKeys());
        mutablePermissionTargetInfo.setRepoKeys(compatibleRepoKeys);
        acl.setPermissionTarget(mutablePermissionTargetInfo);
        return acl;
    }

    public List<RepoAcl> getRepoPathAcls(RepoPath repoPath) {
        LocalRepoDescriptor localRepo =
                repositoryService.localFederatedOrCachedRepoDescriptorByKey(repoPath.getRepoKey());
        boolean isRemoteCache = localRepo.isCache();
        boolean isBuildRepo = isBuildInfoRepo(repoPath.getRepoKey());
        RepoPath repoPathWithoutCacheSuffix =
                InternalRepoPathFactory.create(removeDashCache(repoPath.getRepoKey()), repoPath.getPath());
        return getAllRepoAcls().stream()
                .filter(acl -> isRepoKeyInAcl(acl.getPermissionTarget().getRepoKeys(), repoPathWithoutCacheSuffix,
                        isRemoteCache, isBuildRepo))
                .filter(acl -> isRepoPathInAclPermissions(acl.getPermissionTarget(), repoPathWithoutCacheSuffix))
                .toList();
    }

    public List<BuildAcl> getBuildPathAcls(String buildName, String buildNumber, String buildStarted,
            String buildRepo) {
        String finalBuildRepo = buildFacade.normaliseBuildRepoName(buildRepo);
        BuildRepoPath buildRepoPath = generateBuildJsonRepoPath(buildName, buildNumber, buildStarted, finalBuildRepo);
        RepoPath repoPath = RepoPathFactory.create(buildRepoPath);
        return getAllBuildAcls(false).stream()
                .filter(acl -> isRepoPathInAclPermissions(acl.getPermissionTarget(), repoPath))
                .toList();
    }

    public List<BuildAcl> getBuildPathAcls(RepoPath repoPath, boolean excludeInternal) {
        return getAllBuildAcls(false).stream()
                .filter(acl -> !excludeInternal || isNotInternalAcl(acl))
                .filter(acl -> isRepoPathInAclPermissions(acl.getPermissionTarget(), repoPath))
                .toList();
    }

    public List<ReleaseBundleAcl> getReleaseBundleAcls(RepoPath repoPath) {
        return getAllReleaseBundleAcls(true).stream()
                .filter(acl -> isRepoPathInAclPermissions(acl.getPermissionTarget(), repoPath))
                .toList();
    }

    public abstract List<RepoAcl> getAllRepoAcls();

    public abstract List<BuildAcl> getAllBuildAcls(boolean excludeInternal);

    public abstract List<ReleaseBundleAcl> getAllReleaseBundleAcls(boolean getByLicense);

    public Multimap<PermissionTarget, AceInfo> getUserPermissionAndItsGroups(String username,
            ArtifactoryResourceType type) {
        Multimap<PermissionTarget, AceInfo> aceInfoMap = ArrayListMultimap.create();
        UserInfo user = userGroupStoreService.findUser(username);
        if (user != null) {
            Set<ArtifactorySid> sids = getUserEffectiveSids(new SimpleUser(user));
            getAllAclsByTypeExcludingInternal(type).forEach(acl -> addSidsPermissions(aceInfoMap, sids, acl));
        }
        return aceInfoMap;
    }

    /**
     * @param user The authentication token.
     * @return An array of sids of the current user and all it's groups.
     */
    Set<ArtifactorySid> getUserEffectiveSids(SimpleUser user) {
        Set<UserGroupInfo> groups = user.getDescriptor().getGroups();
        Set<ArtifactorySid> sids = new LinkedHashSet<>(groups.size() + 1);
        sids.add(new ArtifactorySid(user.getUsername(), false));
        for (UserGroupInfo group : groups) {
            sids.add(new ArtifactorySid(group.getGroupName(), true));
        }
        return sids;
    }

    private void addSidsPermissions(Multimap<PermissionTarget, AceInfo> aceInfoMap, Set<ArtifactorySid> sids,
            Acl<? extends PermissionTarget> acl) {
        acl.getAces().stream()
                .filter(ace -> sids.contains(new ArtifactorySid(ace.getPrincipal(), ace.isGroup())))
                .forEach(ace -> aceInfoMap.put(acl.getPermissionTarget(), ace));
    }

    public Multimap<PermissionTarget, AceInfo> getGroupsPermissions(List<String> groups, ArtifactoryResourceType type) {
        Multimap<PermissionTarget, AceInfo> aceInfoMap = HashMultimap.create();
        getAllAclsByTypeExcludingInternal(type).forEach(acl -> acl.getAces().stream()
                .filter(ace -> ace.isGroup() && groups.contains(ace.getPrincipal()))
                .forEach(ace -> aceInfoMap.put(acl.getPermissionTarget(), ace)));
        return aceInfoMap;
    }

    /**
     * Get all permission targets by type excluding the once generated by Access for Project users
     *
     * @param type {@link ArtifactoryResourceType} resource type
     * @return list of ACLs
     */
    private Collection<? extends Acl<? extends PermissionTarget>> getAllAclsByTypeExcludingInternal(
            ArtifactoryResourceType type) {
        Collection<? extends Acl<? extends PermissionTarget>> acls = switch (type) {
            case REPO -> getAllRepoAcls();
            case BUILD -> getAllBuildAcls(true);
            case RELEASE_BUNDLES -> getAllReleaseBundleAcls(true);
        };
        return acls.stream()
                .filter(this::isNotInternalAcl)
                .toList();
    }

    boolean isBuildInfoRepo(String repoKey) {
        return buildFacade.isBuildInfoRepo(repoKey);
    }

    boolean isReleaseBundlesRepoV1(String repoKey) {
        return StringUtils.equals(repoKey, RELEASE_BUNDLE_REPO_KEY) ||
                repositoryService.existsByType(RepoDetailsType.RELEASE_BUNDLE, repoKey);
    }

    boolean isReleaseBundleRepo(String repoKey) {
        return isReleaseBundlesRepoV1(repoKey) || isReleaseBundlesV2RepoKey(repoKey);
    }

    boolean isBuildInfoOrReleaseBundleRepo(String repoKey) {
        return isBuildInfoRepo(repoKey) || isReleaseBundleRepo(repoKey);
    }

    private boolean isRepoPathInAclPermissions(RepoPermissionTarget permissionTarget, RepoPath repoPath) {
        return isRepoPathInAclPermissions(permissionTarget.getIncludes(), permissionTarget.getExcludes(), repoPath);
    }

    /**
     * Checks if the permission target include/exclude match the build name
     */
    private boolean isRepoPathInAclPermissions(List<String> includePatterns, List<String> excludePatterns,
            RepoPath repoPath) {
        String path = getPathToCheckPermissionFor(repoPath);
        return PathMatcher.matches(path, includePatterns, excludePatterns, repoPath.isFolder());
    }

    /**
     * If the path being checked belongs to the build-info repo the path being checked needs to be URL decoded, since
     * build names and numbers are specially encoded upon deployment using {@code BuildRepoPathElementsEncoder}
     */
    String getPathToCheckPermissionFor(RepoPath repoPath) {
        String path = repoPath.getPath();
        if (isBuildInfoRepo(repoPath.getRepoKey())) {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        }
        return path;
    }

    /**
     * Filter Acl from the AclsCache that contain the repoKey or any ANYs keys
     */
    private boolean isRepoKeyInAcl(Set<String> repoKeys, RepoPath repoPath, boolean isRemote, boolean isBuildRepo) {
        if (isBuildRepo) {
            return repoKeys.contains(repoPath.getRepoKey());
        }
        return repoKeys.contains(repoPath.getRepoKey()) ||
                repoKeys.contains(ANY_REPO) ||
                (isRemote ? repoKeys.contains(ANY_REMOTE_REPO) : repoKeys.contains(ANY_LOCAL_REPO));
    }

    void assertAdmin() {
        if (!securityService.isAdmin()) {
            throw new SecurityException(
                    "The attempted action is permitted to users with administrative privileges only.");
        }
    }

    void validatePermissionNameExists(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("ACL name cannot be null");
        }
    }

    MutableAcl<? extends PermissionTarget> createAclAdapter(Acl acl) {
        if (acl instanceof RepoAcl) {
            return makeNewAclRemoteRepoKeysAclCompatible((MutableRepoAclImpl) acl);
        }
        return (MutableAcl<? extends PermissionTarget>) acl;
    }

    private MutableRepoAcl makeNewAclRemoteRepoKeysAclCompatible(MutableRepoAcl acl) {
        MutableRepoPermissionTarget mutablePermissionTargetInfo = InfoFactoryHolder.get().copyRepoPermissionTarget
                (acl.getPermissionTarget());
        Set<String> compatibleRepoKeys = makeRemoteRepoKeysAclCompatible(mutablePermissionTargetInfo.getRepoKeys());
        mutablePermissionTargetInfo.setRepoKeys(compatibleRepoKeys);
        acl.setPermissionTarget(mutablePermissionTargetInfo);
        return acl;
    }

    /**
     * Converts remote repo keys contained in the list to have the '-cache' suffix as acls currently
     * only support this notation.
     *
     * @return repoKeys with all remote repository keys concatenated with '-cache' suffix
     */
    Set<String> makeRemoteRepoKeysAclCompatible(Set<String> repoKeys) {
        Set<String> altered = new HashSet<>();
        for (String repoKey : repoKeys) {
            if (repositoryService.existsByType(RepoDetailsType.REMOTE, repoKey)) {
                altered.add(repoKey.concat(LocalCacheRepoDescriptor.PATH_SUFFIX));
            } else {
                altered.add(repoKey);
            }
        }
        return altered;
    }

    void cleanupAclInfo(MutableAcl<? extends PermissionTarget> acl) {
        acl.getMutableAces().removeIf(aceInfo -> aceInfo.getMask() == 0);
    }

    void createAclInternal(Acl acl) {
        cudOperation(permClient -> permClient.createPermission(aclToAccessPermission(acl, getServiceId())),
                "Could not create " + getAclDetailsForErrorLog(acl));
    }

    void deleteAclInternal(Acl acl) {
        String accessIdentifier = acl.getAccessIdentifier();
        if (accessIdentifier == null) {
            throw new IllegalStateException("Can't delete ACL without unique Access identifier");
        }
        cudOperation(permClient -> permClient.deletePermissionById(accessIdentifier),
                "Could not delete " + getAclDetailsForErrorLog(acl));
    }

    void updateAclInternal(Acl acl) {
        if (isInternalAcl(acl)) {
            return;
        }
        cudOperation(permClient -> permClient.replacePermission(aclToAccessPermission(acl, getServiceId())),
                "Could not update " + getAclDetailsForErrorLog(acl));
    }

    public boolean isInternalAcl(Acl<? extends PermissionTarget> acl) {
        String identifier = acl.getAccessIdentifier();
        return StringUtils.isNotEmpty(identifier) && identifier.startsWith(INTERNAL_PERMISSION_PREFIX);
    }

    private void cudOperation(Consumer<PermissionsClient> execute, String possibleErrorMessage) {
        try {
            execute.accept(accessService.getAccessClient().permissions());
        } catch (AccessClientHttpException e) {
            if (HttpStatus.SC_CONFLICT == e.getStatusCode()) {
                throw new AlreadyExistsException(possibleErrorMessage, e);
            }
            throw new StorageException(possibleErrorMessage, e);
        } catch (IllegalArgumentException | AccessClientException e) {
            throw new StorageException(possibleErrorMessage, e);
        }
    }

    private String getServiceId() {
        return accessService.getArtifactoryServiceId().getFormattedName();
    }

    private String getAclDetailsForErrorLog(Acl acl) {
        return "permission " + acl.getPermissionTarget().getName() + " with identifier " + acl.getAccessIdentifier();
    }

    /**
     * Validates that the edited given permission target is not different from the existing one. This method should be
     * called before an ACL is being modified by a non-sys-admin user
     *
     * @param newInfo Edited permission target
     * @throws AuthorizationException Thrown in case an unauthorized modification has occurred
     */
    <T extends PermissionTarget> void validateUnmodifiedPermissionTarget(T newInfo) throws AuthorizationException {
        if (newInfo == null) {
            return;
        }
        Acl oldAcl = getAclByPermissionTargetType(newInfo);
        if (oldAcl == null) {
            return;
        }
        PermissionTarget oldInfo = oldAcl.getPermissionTarget();
        if (oldInfo == null) {
            return;
        }
        if (newInfo instanceof RepoPermissionTarget repoPermissionTarget) {
            validateUnmodifiedRepoPermission(repoPermissionTarget, (RepoPermissionTarget) oldInfo);
        }
        Set<String> oldEntities = getEntitiesFromPermissionTargetByType(oldInfo);
        Set<String> newEntities = getEntitiesFromPermissionTargetByType(newInfo);
        Sets.SetView<String> entities = Sets.symmetricDifference(Sets.newHashSet(oldEntities),
                Sets.newHashSet(newEntities));
        if (!entities.isEmpty()) {
            alertModifiedField("repositories, builds or release-bundles");
        }
    }

    private Acl getAclByPermissionTargetType(PermissionTarget permissionTarget) {
        String permissionTargetName = permissionTarget.getName();
        if (permissionTarget instanceof BuildPermissionTarget) {
            return getBuildAcl(permissionTargetName);
        }
        if (permissionTarget instanceof ReleaseBundlePermissionTarget) {
            return getReleaseBundleAcl(permissionTargetName);
        }
        if (permissionTarget instanceof RepoPermissionTarget) {
            return getRepoAcl(permissionTargetName);
        }
        log.warn("Permission target {} is not of type repo/build/release-bundles", permissionTargetName);
        return null;
    }

    public abstract RepoAcl getRepoAcl(String permTargetName);

    public abstract BuildAcl getBuildAcl(String permTargetName);

    public abstract ReleaseBundleAcl getReleaseBundleAcl(String permTargetName);

    private Set<String> getEntitiesFromPermissionTargetByType(PermissionTarget permissionTarget) {
        if (permissionTarget instanceof BuildPermissionTarget buildPermissionTarget) {
            return buildPermissionTarget.getRepoKeys();
        }
        if (permissionTarget instanceof ReleaseBundlePermissionTarget releaseBundlePermissionTarget) {
            return releaseBundlePermissionTarget.getRepoKeys();
        }
        if (permissionTarget instanceof RepoPermissionTarget repoPermissionTarget) {
            return makeRemoteRepoKeysAclCompatible(repoPermissionTarget.getRepoKeys());
        }
        return Collections.emptySet();
    }

    private void validateUnmodifiedRepoPermission(RepoPermissionTarget newInfo, RepoPermissionTarget oldInfo) {
        Sets.SetView<String> excludes = Sets.symmetricDifference(Sets.newHashSet(oldInfo.getExcludes()),
                Sets.newHashSet(newInfo.getExcludes()));
        if (!excludes.isEmpty()) {
            alertModifiedField("excludes pattern");
        }
        if (!oldInfo.getExcludesPattern().equals(newInfo.getExcludesPattern())) {
            alertModifiedField("exclude pattern");
        }
        Sets.SetView<String> includes = Sets.symmetricDifference(Sets.newHashSet(oldInfo.getIncludes()),
                Sets.newHashSet(newInfo.getIncludes()));
        if (!includes.isEmpty()) {
            alertModifiedField("include pattern");
        }
        if (!oldInfo.getIncludesPattern().equals(newInfo.getIncludesPattern())) {
            alertModifiedField("include pattern");
        }
    }

    /**
     * Throws an AuthorizationException alerting an un-authorized change of configuration
     *
     * @param modifiedFieldName Name of modified field
     */
    private void alertModifiedField(String modifiedFieldName) {
        throw new AuthorizationException("User is not permitted to modify " + modifiedFieldName);
    }

    boolean isNotInternalAcl(@Nonnull Acl<? extends PermissionTarget> acl) {
        return !isInternalAcl(acl);
    }

    public boolean userHasPermissionsOnRepositoryRoot(String repoKey) {
        return userHasPermissionsOnRepositoryRoot(repoKey, Set.of(ArtifactoryPermission.values()), null);
    }

    public boolean userHasPermissionsOnRepositoryRoot(String repoKey,
            @Nonnull Collection<ArtifactoryPermission> permissions, @Nullable Map<String, Boolean> cache) {
        // Note: we cannot use cache.computeIfAbsent() because recursive call may modify the cache
        // and cause a ConcurrentModificationException.
        Boolean result = cache == null ? null : cache.get(repoKey);
        if (result != null) {
            return result;
        }

        boolean hasPermissions = computeUserHasPermissionsOnRepositoryRoot(repoKey, permissions, cache);
        if (cache != null) {
            cache.put(repoKey, hasPermissions);
        }
        return hasPermissions;
    }

    protected boolean computeUserHasPermissionsOnRepositoryRoot(String repoKey,
            @Nonnull Collection<ArtifactoryPermission> permissions, @Nullable Map<String, Boolean> cache) {
        Repo<?, ?> repo = this.repositoryService.repositoryByKey(repoKey);
        if (repo == null) {
            // Repo does not exists => No permissions
            return false;
        }

        // Since there is no point in checking admin permissions we will return true
        // Should be changed to isProjectAdmin check: RTFACT-24715
        if (securityService.isAdmin()) {
            return true;
        }

        // If it is a real (i.e local, remote of federated), simply check permission on root.
        if (repo.isReal()) {
            if (repo instanceof RemoteRepo) {
                RepoPath remoteRepoPath = InternalRepoPathFactory.repoRootPath(repoKey);
                repoKey = InternalRepoPathFactory.cacheRepoPath(remoteRepoPath).getRepoKey();
            }
            return hasPermissionOnRoot(repoKey, permissions, cache);
        }

        // If repository is virtual, check if user can access any of the "real" descendant repositories
        //
        // [sergeys@jfrog, 2024-05-10] Should we replace remote repositories with the corresponding local cache,
        // like we do in the code block above? It is unclear why the behavior is not consistent
        VirtualRepo virtualRepo = (VirtualRepo) repo;
        return Stream.of(virtualRepo.getResolvedLocalFederatedAndCachedRepos(), virtualRepo.getResolvedRemoteRepos())
                .flatMap(Collection::stream)
                .anyMatch(childRepo -> hasPermissionOnRoot(childRepo.getKey(), permissions, cache));
    }

    private boolean hasPermissionOnRoot(String repoKey, Collection<ArtifactoryPermission> permissions,
            @Nullable Map<String, Boolean> cache) {
        if (cache == null) {
            return hasPermissionOnRoot(repoKey, permissions);
        }
        return cache.computeIfAbsent(repoKey, a -> hasPermissionOnRoot(repoKey, permissions));
    }

    private boolean hasPermissionOnRoot(String repoKey, Collection<ArtifactoryPermission> permissions) {
        RepoPath path = InternalRepoPathFactory.repoRootPath(repoKey);
        if (CollectionUtils.isNullOrEmpty(permissions)) {
            return Arrays.stream(ArtifactoryPermission.values()).anyMatch(p -> hasPermission(path, p));
        }
        return permissions.stream().anyMatch(p -> hasPermission(path, p));
    }

    private boolean hasPermission(RepoPath repoPath, ArtifactoryPermission permission) {
        return hasPermission(PermissionScopeResourceType.ARTIFACT, repoPath, permission);
    }

    @UtilityClass
    public class ArtifactoryPermissionScopeAction {
        private static final EnumMap<ArtifactoryPermission, Action> map = new EnumMap<>(
                Map.of(
                        READ, Action.READ,
                        DEPLOY, Action.WRITE,
                        DELETE, Action.DELETE,
                        //ArtifactoryPermission.MANAGE, PermissionScopeAction.MANAGE,
                        // We cannot easily support manage permissions for now since that permission is used in
                        // Artifactory to allow/deny repository re-index.
                        // We do not want to allow that by scoped tokens since it's not aligned with the definition of
                        // Manage in the scoped token specification.
                        ANNOTATE, Action.ANNOTATE,
                        DISTRIBUTE, Action.EXECUTE
                )
        );

        public Action fromArtifactoryPermission(@Nonnull ArtifactoryPermission artifactoryPermission) {
            return map.get(artifactoryPermission);
        }
    }

    PermissionHeuristicScore adjustScoreIfNeeded(RepoPermissionTarget pt, PermissionHeuristicScore bestScore) {
        List<String> includes = pt.getIncludes();
        log.trace("includes = {}", includes);
        List<String> excludes = pt.getExcludes();
        log.trace("excludes = {}", excludes);
        if (excludes.isEmpty() && includes.size() == 1 && ANY_PATH.equals(includes.getFirst())) {
            bestScore = bestScore.ordinal() < readAll.ordinal() ? readAll : bestScore;
        } else {
            bestScore = bestScore.ordinal() < readWithExclusion.ordinal() ? readWithExclusion : bestScore;
        }
        log.trace("Best score so far: {}", bestScore);
        return bestScore;
    }

    boolean isProjectScopeToken() {
        if (AuthenticationHelper.getAuthentication() instanceof AccessTokenAuthentication token) {
            List<String> scopes = token.getAccessToken().getScope();
            Predicate<String> predicate = scope -> scope.contains("applied-permissions/roles");
            if (authProjectScopeTokenAllMatch.getBoolean()) {
                return !scopes.isEmpty() && scopes.stream().allMatch(predicate);
            }
            return scopes.stream().anyMatch(predicate);
        }
        return false;
    }
}