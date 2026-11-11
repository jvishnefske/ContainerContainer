package org.artifactory.security;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.ha.message.HaBaseMessage;
import org.artifactory.addon.ha.workitem.HaAclMessageWorkItem;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.PermissionHeuristicScore;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.logging.throttle.LogThrottleMarker;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.storage.security.service.AclCache;
import org.artifactory.storage.security.service.AclStoreService;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.PathMatcher;
import org.artifactory.util.StopWatchUtil;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.common.StreamSupportUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.api.security.PermissionHeuristicScore.admin;
import static org.artifactory.api.security.PermissionHeuristicScore.readNotAllowed;
import static org.artifactory.permissions.PermissionsCacheType.LEGACY;
import static org.artifactory.permissions.PermissionsCacheType.Profiles.PERMISSIONS_CACHE_LEGACY;
import static org.artifactory.repo.RepoPath.REMOTE_CACHE_SUFFIX;
import static org.artifactory.security.PermissionTarget.ANY_DISTRIBUTION_REPO;
import static org.artifactory.security.PermissionTarget.ANY_LOCAL_REPO;
import static org.artifactory.security.PermissionTarget.ANY_REMOTE_REPO;
import static org.artifactory.security.PermissionTarget.ANY_REPO;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundlesV2RepoKey;

/**
 * <b>
 * This is the legacy implementation using the Artifactory permissions cache.
 * In the future, this class will be replaced by the new implementation using the Access client permissions cache.
 * See {@link AuthorizationServiceImpl}.
 * </b>
 * <p>
 * All authorization related code was moved here from SecurityServiceImpl as part of the
 * SecurityService/AuthorizationService separation.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
@Service
@Profile(PERMISSIONS_CACHE_LEGACY)
public class LegacyAuthorizationServiceImpl extends AuthorizationServiceBase implements AuthorizationService {

    private AclStoreService aclStoreService;

    @Autowired
    public void setAclStoreService(AclStoreService aclStoreService) {
        this.aclStoreService = aclStoreService;
    }

    @PostConstruct
    public void postConstruct() {
        log.info("Active permissions cache type: {}", LEGACY);
    }

    @Override
    protected Optional<AccessPrincipal> getSynthesizedAccessPrincipal() {
        log.trace("Returning empty access principal because legacy auth is used");
        return Optional.empty();
    }

    @Override
    public boolean canManage(PermissionTarget target) {
        return hasManagePermissionOnPermissionTarget(target);
    }

    @SneakyThrows
    @Override
    public boolean hasRepoPermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific) {
        if (securityService.isAdmin()) {
            log.debug("hasRepoPermission - Returning true due to admin user");
            return true;
        }
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication == null) {
            log.debug("hasRepoPermission - Returning false due to null authentication");
            return false;
        }
        SimpleUser user = getSimpleUser(authentication);
        var aclCache = aclStoreService.getAclCache();
        log.debug("aclCache = {}", aclCache);
        return hasPermission(aclCache, null, permission, excludeInternalProjectSpecific, user);
    }

    private boolean hasPermission(AclCache<? extends RepoPermissionTarget> aclCache, @Nullable String repoKey,
            ArtifactoryPermission permission, boolean excludeInternalProjectSpecific, SimpleUser user) {
        if (isPermissionGrantedForUser(aclCache, repoKey, permission, excludeInternalProjectSpecific,
                user.getUsername())) {
            return true;
        }
        Set<UserGroupInfo> groups = user.getDescriptor().getGroupsReference();
        return isPermissionGrantedForGroups(aclCache, repoKey, permission, excludeInternalProjectSpecific, groups);
    }

    @SneakyThrows
    private <T extends RepoPermissionTarget> boolean isPermissionGrantedForUser(AclCache<T> aclCache,
            @Nullable String repoKey, ArtifactoryPermission permission, boolean excludeInternalProjectSpecific,
            String username) {
        log.trace("Checking repo permission for user: {}", username);
        StopWatch stopWatch = new StopWatch();
        boolean permissionGrantedForUser = StopWatchUtil.measureTime(stopWatch, () -> {
            var userResultMap = aclCache.getUserResultMap();
            var resourceToPermissionsMap = userResultMap.get(username);
            if (resourceToPermissionsMap == null) {
                return false;
            }
            return resourceToPermissionsMap.values().stream().flatMap(Set::stream)
                    .filter(principalPermission -> !excludeInternalProjectSpecific ||
                            isNotInternalProjectSpecificAcl(principalPermission.getAccessIdentifier()))
                    .filter(principalPermission -> repoKey == null ||
                            principalPermission.getPermissionTarget().getRepoKeys().contains(repoKey))
                    .anyMatch(principalPermission -> isGranted(principalPermission.getAce(), permission, username));
        });
        log.trace("Checking user permission took: {} milliseconds and result is: {}", stopWatch.getTotalTimeMillis(),
                permissionGrantedForUser);
        return permissionGrantedForUser;
    }

    @SneakyThrows
    private <T extends RepoPermissionTarget> boolean isPermissionGrantedForGroups(AclCache<T> aclCache,
            @Nullable String repoKey, ArtifactoryPermission permission, boolean excludeInternalProjectSpecific,
            Set<UserGroupInfo> groups) {
        log.trace("Checking permission for groups. Groups set size: {}", groups.size());
        StopWatch stopWatch = new StopWatch();
        var groupResultMap = aclCache.getGroupResultMap();
        log.trace("Number of overall groups in cache: {}", groupResultMap.size());
        boolean permissionGrantedForGroups = StopWatchUtil.measureTime(stopWatch, () ->
                groups.stream().anyMatch(grp -> {
                    var resourceToPermissionMap = groupResultMap.get(grp.getGroupName());
                    if (resourceToPermissionMap == null) {
                        return false;
                    }
                    log.trace("Checking group: {} permission set size: {}", grp, resourceToPermissionMap.size());
                    return resourceToPermissionMap.values().stream().flatMap(Set::stream)
                            .filter(principalPermission -> !excludeInternalProjectSpecific ||
                                    isNotInternalProjectSpecificAcl(principalPermission.getAccessIdentifier()))
                            .filter(principalPermission -> principalPermission.getAce().isGroup())
                            .filter(principalPermission -> repoKey == null ||
                                    principalPermission.getPermissionTarget().getRepoKeys().contains(repoKey))
                            .anyMatch(principalPermission -> isGranted(principalPermission.getAce(), permission,
                                    grp.getGroupName()));
                }));
        log.trace("Checking user groups permission took: {} milliseconds and result is: {}",
                stopWatch.getTotalTimeMillis(), permissionGrantedForGroups);
        return permissionGrantedForGroups;
    }

    @Override
    public boolean hasBuildPermission(@Nullable String buildRepo, ArtifactoryPermission permission,
            boolean excludeInternalProjectSpecific) {
        if (securityService.isAdmin()) {
            log.debug("hasBuildPermission - Returning true due to admin user");
            return true;
        }
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication == null) {
            log.debug("hasBuildPermission - Returning false due to null authentication");
            return false;
        }
        SimpleUser user = getSimpleUser(authentication);
        var buildsAclCache = aclStoreService.getBuildsAclCache();
        log.debug("buildsAclCache = {}", buildsAclCache);
        return hasPermission(buildsAclCache, buildRepo, permission, excludeInternalProjectSpecific, user);
    }

    @Override
    public boolean hasReleaseBundlePermission(ArtifactoryPermission permission, boolean excludeInternalProjectSpecific,
            boolean getByLicense) {
        if (securityService.isAdmin()) {
            log.debug("hasReleaseBundlePermission - Returning true due to admin user");
            return true;
        }
        List<ReleaseBundleAcl> allReleaseBundleAcls = getAllReleaseBundleAcls(getByLicense);
        log.debug("allReleaseBundleAcls = {}", allReleaseBundleAcls);
        return allReleaseBundleAcls.stream()
                .filter(rbAcl -> !excludeInternalProjectSpecific || isNotInternalProjectSpecificAcl(rbAcl))
                .anyMatch(acl -> hasPermissionOnAcl(acl, permission));
    }

    private boolean hasPermissionOnAcl(Acl<? extends PermissionTarget> acl, ArtifactoryPermission permission) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (!isAuthenticated(authentication)) {
            log.debug("hasPermissionOnAcl - Returning false due to unauthenticated user");
            return false;
        }
        // Admins has permissions on any target, if not check if has permissions on acl
        return isAdmin(authentication) || hasPermissionOnAcl(acl, permission, getSimpleUser(authentication));
    }

    @Override
    public boolean hasBuildBasicReadPermission() {
        if (buildFacade.hasBasicReadPermissionForAllBuilds()) {
            log.debug("hasBuildBasicReadPermission - Returning true");
            return true;
        }
        List<BuildAcl> allBuildAcls = getAllBuildAcls(false);
        log.debug("allBuildAcls = {}", allBuildAcls);
        return allBuildAcls.stream().anyMatch(acl -> hasPermissionOnAcl(acl, ArtifactoryPermission.READ));
    }

    @Override
    public List<RepoPermissionTarget> getRepoPermissionTargets(ArtifactoryPermission permission) {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        if (authentication == null) {
            log.debug("getRepoPermissionTargets - Returning empty list due to null authentication");
            return List.of();
        }
        SimpleUser user = getSimpleUser(authentication);
        UserInfo userInfo = user.getDescriptor();
        return new ArrayList<>(getInternalRepoPermissionTargets(userInfo, permission));
    }

    @NotNull
    private Set<RepoPermissionTarget> getInternalRepoPermissionTargets(UserInfo userInfo,
            ArtifactoryPermission permission) {
        if (userInfo.isEffectiveAdmin()) {
            log.debug("getRepoPermissionTargets - admin user, returning all repo permission targets");
            return getAllRepoAcls(permission).stream()
                    .map(RepoAcl::getPermissionTarget)
                    .collect(Collectors.toSet());
        }

        Set<UserGroupInfo> groups = userInfo.getGroupsReference();
        String username = userInfo.getUsername();
        log.trace("username = {}, groups = {}", username, groups);
        Set<RepoPermissionTarget> allPermissionTargets = Sets.newHashSet();
        List<RepoPermissionTarget> userPermissions = getRepoPermissionTargets(permission, username);
        log.trace("userPermissions = {}", userPermissions);
        allPermissionTargets.addAll(userPermissions);
        List<RepoPermissionTarget> groupPermissions = getRepoPermissionTargets(permission, groups, username);
        log.trace("groupPermissions = {}", groupPermissions);
        allPermissionTargets.addAll(groupPermissions);
        return allPermissionTargets;
    }

    @SneakyThrows
    @NotNull
    private List<RepoPermissionTarget> getRepoPermissionTargets(ArtifactoryPermission permission, String username) {
        log.trace("Collecting repo permission targets of type: {} for user: {}", permission.getDisplayName(), username);
        StopWatch stopWatch = new StopWatch();
        List<RepoPermissionTarget> permissionTargets = StopWatchUtil.measureTime(stopWatch, () -> {
            var userResultMap = aclStoreService.getAclCache().getUserResultMap();
            var resourceToPermissionsMap = userResultMap.get(username);
            log.trace("username = {}, resourceToPermissionsMap = {}", username, resourceToPermissionsMap);
            if (resourceToPermissionsMap == null) {
                return List.of();
            }
            return resourceToPermissionsMap.values().stream().flatMap(Set::stream)
                    .filter(principalPermission -> !principalPermission.getAce().isGroup() &&
                            isGranted(principalPermission.getAce(), permission, username))
                    .map(PrincipalPermission::getPermissionTarget)
                    .toList();
        });
        log.trace("Collecting user permission targets using took: {} milliseconds and collected {} permission targets",
                stopWatch.getTotalTimeMillis(), permissionTargets.size());
        return permissionTargets;
    }

    @SneakyThrows
    @NotNull
    private List<RepoPermissionTarget> getRepoPermissionTargets(ArtifactoryPermission permission,
            Set<UserGroupInfo> groups, String username) {
        StopWatch stopWatch = new StopWatch();
        log.trace("Collecting group repo permission targets of type: {} for user: {}. Number of groups: {}",
                permission.getDisplayName(), username, groups.size());
        var groupResultMap = aclStoreService.getAclCache().getGroupResultMap();

        List<RepoPermissionTarget> resultPermissionsTargets = StopWatchUtil.measureTime(stopWatch, () -> {
            List<RepoPermissionTarget> permissionTargets = Lists.newArrayList();
            for (UserGroupInfo grp : groups) {
                var resourceToPermissionMap = groupResultMap.get(grp.getGroupName());
                log.trace("group = {}, resourceToPermissionMap = {}", grp.getGroupName(), resourceToPermissionMap);
                if (resourceToPermissionMap != null) {
                    log.trace("Collecting permissions targets for group: {} permission set size: {}", grp,
                            resourceToPermissionMap.size());
                    List<RepoPermissionTarget> repoPermissionTargets =
                            resourceToPermissionMap.values().stream().flatMap(Set::stream)
                                    .filter(principalPermission -> principalPermission.getAce().isGroup() &&
                                            isGranted(principalPermission.getAce(), permission, grp.getGroupName()))
                                    .map(PrincipalPermission::getPermissionTarget)
                                    .toList();
                    permissionTargets.addAll(repoPermissionTargets);
                }
            }
            return permissionTargets;
        });
        log.trace(
                "Collecting user permission targets using took: {} milliseconds and collected {} permission targets",
                stopWatch.getTotalTimeMillis(), resultPermissionsTargets.size());

        return resultPermissionsTargets;
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
        PermissionHeuristicScore bestScore = getPermissionHeuristicScore(repoKey, simpleUser);
        log.debug("Best score for repo '{}': {}", repoKey, bestScore);
        return bestScore;
    }

    private PermissionHeuristicScore getPermissionHeuristicScore(String repoKey, SimpleUser simpleUser) {
        AclCache<RepoPermissionTarget> aclCache = aclStoreService.getAclCache();
        Set<ArtifactorySid> sids = getUserEffectiveSids(simpleUser);
        PermissionHeuristicScore bestScore = readNotAllowed;
        for (ArtifactorySid sid : sids) {
            Map<String, Map<String, Set<PrincipalPermission<RepoPermissionTarget>>>> map =
                    getAclCacheRelevantMap(aclCache, sid);
            Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> principalRepKeyToAclInfoMap =
                    map.get(sid.getPrincipal());
            // permissions for the current sid only, does not contains all ace in the Acl
            Set<PrincipalPermission<RepoPermissionTarget>> repoAclSet =
                    populateAclInfoSetByRepo(repoKey, principalRepKeyToAclInfoMap);
            log.trace("repoAclSet = {}", repoAclSet);
            // Check for READ permission per acl
            for (PrincipalPermission<RepoPermissionTarget> repoAcl : repoAclSet) {
                if (!isGranted(repoAcl, ArtifactoryPermission.READ, sid.getPrincipal())) {
                    continue;
                }

                RepoPermissionTarget pt = repoAcl.getPermissionTarget();
                log.trace("pt = {}, repoKeys = {}", pt, pt.getRepoKeys());
                // User has read access, now determine the score
                bestScore = adjustScoreIfNeeded(pt, bestScore);
            }
        }
        return bestScore;
    }

    /**
     * Aggregate all aclInfo of a repo + Any_Repo + Any_Local_Repo into one set
     */
    private Set<PrincipalPermission<RepoPermissionTarget>> populateAclInfoSetByRepo(String repoKey,
            Map<String, Set<PrincipalPermission<RepoPermissionTarget>>> principalRepKeyToAclInfoMap) {
        Set<PrincipalPermission<RepoPermissionTarget>> repoAclSet = new HashSet<>();
        if (principalRepKeyToAclInfoMap != null) {
            addAllToAclSetByRepoKey(principalRepKeyToAclInfoMap, repoAclSet, ANY_REPO);
            addAllToAclSetByRepoKey(principalRepKeyToAclInfoMap, repoAclSet, ANY_LOCAL_REPO);
            addAllToAclSetByRepoKey(principalRepKeyToAclInfoMap, repoAclSet, repoKey);
        }
        return repoAclSet;
    }

    /**
     * In case the repo AclInfoMp is not empty --> we add the relevant AclInfo from the map to the aclInfoSet
     */
    private <T extends PermissionTarget> void addAllToAclSetByRepoKey(
            Map<String, Set<PrincipalPermission<T>>> repKeyToAclInfoMap,
            Set<PrincipalPermission<T>> repoAclSet, String key) {
        if (!CollectionUtils.isNullOrEmpty(repKeyToAclInfoMap.get(key))) {
            repoAclSet.addAll(repKeyToAclInfoMap.get(key));
        }
    }

    @Override
    @Deprecated
    public List<RepoAcl> getAllRepoAcls() {
        return new ArrayList<>(aclStoreService.getAllRepoAcls());
    }

    @Override
    @Deprecated
    public List<RepoAcl> getAllRepoAcls(ArtifactoryPermission permission) {
        if (securityService.isAdmin()) {
            log.debug("getAllRepoAcls - admin user, returning all repo ACLs");
            return new ArrayList<>(aclStoreService.getAllRepoAcls());
        }
        return aclStoreService.getAllRepoAcls().stream()
                .filter(acl -> hasPermissionOnAcl(acl, permission))
                .toList();
    }

    @Override
    public List<BuildAcl> getAllBuildAcls(ArtifactoryPermission permission) {
        return aclStoreService.getAllBuildAcls().stream()
                .filter(acl -> hasPermissionOnAcl(acl, permission))
                .toList();
    }

    @Override
    public List<BuildAcl> getAllBuildAcls(boolean excludeInternal) {
        if (excludeInternal) {
            return aclStoreService.getAllBuildAcls()
                    .stream()
                    .filter(this::isNotInternalAcl)
                    .toList();
        }
        return new ArrayList<>(aclStoreService.getAllBuildAcls());
    }

    @Override
    public List<ReleaseBundleAcl> getAllReleaseBundleAcls(ArtifactoryPermission permission) {
        return getAllReleaseBundleAclsByLicense().stream()
                .filter(acl -> hasPermissionOnAcl(acl, permission))
                .toList();
    }

    @Override
    public List<ReleaseBundleAcl> getAllReleaseBundleAcls(boolean getByLicense) {
        return getByLicense ? new ArrayList<>(getAllReleaseBundleAclsByLicense()) :
                new ArrayList<>(aclStoreService.getAllReleaseBundleAcls());
    }

    @Override
    public void createAcl(Acl acl) {
        assertAdmin();
        validatePermissionNameExists(acl.getPermissionTarget().getName());
        MutableAcl<? extends PermissionTarget> compatibleAcl = createAclAdapter(acl);
        cleanupAclInfo(compatibleAcl);
        aclStoreService.createAcl(compatibleAcl);
        interceptors.onPermissionsAdd(compatibleAcl);
        notifyClusterMembers();
    }

    @Override
    public void deleteAcl(Acl acl) {
        aclStoreService.deleteAcl(acl);
        interceptors.onPermissionsDelete((MutableAcl<? extends PermissionTarget>) acl);
        notifyClusterMembers();
    }

    @Override
    public void updateAcl(Acl acl) {
        updateAcl(acl, securityService.isAdmin());
    }

    @Override
    public void updateAcl(Acl acl, boolean effectiveAdmin) {
        //Do not update internal access ACL's
        if (isInternalAcl(acl)) {
            return;
        }
        //If the editing user is not a sys-admin
        if (!effectiveAdmin) {
            //Assert that no unauthorized modifications were performed
            validateUnmodifiedPermissionTarget(acl.getPermissionTarget());
        }

        MutableAcl<? extends PermissionTarget> compatibleAcl = createAclAdapter(acl);

        // Removing empty Ace
        cleanupAclInfo(compatibleAcl);
        aclStoreService.updateAcl(compatibleAcl);
        interceptors.onPermissionsUpdate(compatibleAcl);
        notifyClusterMembers();
    }

    @Override
    public RepoAcl getRepoAcl(String permTargetName) {
        return aclStoreService.getRepoAcl(permTargetName);
    }

    @Override
    public BuildAcl getBuildAcl(String permTargetName) {
        return aclStoreService.getBuildAcl(permTargetName);
    }

    @Override
    public ReleaseBundleAcl getReleaseBundleAcl(String permTargetName) {
        return aclStoreService.getReleaseBundleAcl(permTargetName);
    }

    @Override
    public void removeAllUserAces(String username) {
        aclStoreService.removeAllUserAces(username);
    }

    @Override
    public void removeAllGroupAces(String groupName) {
        aclStoreService.removeAllGroupAces(groupName);
    }

    private boolean hasManagePermissionOnPermissionTarget(PermissionTarget permTarget) {
        Acl<? extends PermissionTarget> acl;
        String permTargetName = permTarget.getName();
        if (permTarget instanceof BuildPermissionTarget) {
            acl = aclStoreService.getBuildAcl(permTargetName);
        } else if (permTarget instanceof ReleaseBundlePermissionTarget) {
            acl = aclStoreService.getReleaseBundleAcl(permTargetName);
        } else if (permTarget instanceof RepoPermissionTarget) {
            acl = aclStoreService.getRepoAcl(permTargetName);
        } else {
            throw new IllegalStateException(
                    "Permission target '" + permTargetName + "' is not of allowed types repo/build/release-bundle");
        }
        return hasPermissionOnAcl(acl, ArtifactoryPermission.MANAGE);
    }

    /**
     * If user has EntPlus/Edge license returns all release-bundles acls from cache, otherwise returns empty collection.
     */
    Collection<ReleaseBundleAcl> getAllReleaseBundleAclsByLicense() {
        return addons.isEntitled(EntitlementType.RELEASE_BUNDLES_READ) ? aclStoreService.getAllReleaseBundleAcls() :
                new HashMap<String, ReleaseBundleAcl>().values();
    }

    private List<BuildAcl> getBuildAclsExclInternalProjectSpecific() {
        return aclStoreService.getAllBuildAcls()
                .stream()
                .filter(this::isNotInternalProjectSpecificAcl)
                .toList();
    }

    @VisibleForTesting
    boolean isNotInternalProjectSpecificAcl(@Nonnull Acl<? extends PermissionTarget> acl) {
        String accessIdentifier = acl.getAccessIdentifier();
        return isNotInternalProjectSpecificAcl(accessIdentifier);
    }

    boolean isNotInternalProjectSpecificAcl(String accessIdentifier) {
        return !StringUtils.startsWith(accessIdentifier, INTERNAL_PERMISSION_PREFIX) || StringUtils
                .startsWith(accessIdentifier, INTERNAL_PERMISSION_PREFIX + ProjectsService.DEFAULT_PROJECT);
    }

    private boolean hasPermissionOnAcl(Acl<? extends PermissionTarget> acl, ArtifactoryPermission permission,
            SimpleUser user) {
        // Admins has permissions on any target, if not check if has permissions
        if (user.isEffectiveAdmin()) {
            return true;
        }
        return isGranted(acl, permission, user.getDescriptor());
    }

    private void notifyClusterMembers() {
        HaAddon haAddon = addons.addonByType(HaAddon.class);
        HaBaseMessage haMessage = new HaBaseMessage(haAddon.getCurrentMemberServerId());
        HaAclMessageWorkItem workItem = new HaAclMessageWorkItem(haMessage);
        haAddon.notifyAsync(workItem);
    }

    private boolean isGranted(Acl<? extends PermissionTarget> acl, ArtifactoryPermission permission, UserInfo user) {
        Set<UserGroupInfo> groupInfos = user.getGroupsReference();
        for (AceInfo ace : acl.getMutableAces()) {
            if (!ace.isGroup()) {
                if (isGranted(ace, permission, user.getUsername())) {
                    return true;
                }
            } else {
                for (UserGroupInfo groupInfo : groupInfos) {
                    if (isGranted(ace, permission, groupInfo.getGroupName())) {
                        return true;
                    }
                }

            }
        }
        return false;
    }

    @VisibleForTesting
    <T extends RepoPermissionTarget> boolean isGranted(RepoPath repoPath, ArtifactoryPermission permission,
            UserInfo user) {
        Set<UserGroupInfo> groupInfos = user.getGroupsReference();
        AclCache<T> aclCache = getAclCacheByType(repoPath);
        log.trace("Checking permissions for repoPath: '{}'.", repoPath);
        Map<String, Map<String, Set<PrincipalPermission<T>>>> aclCacheUserResultMap = aclCache.getUserResultMap();
        Set<String> remoteRepoKeyAclCompatible = makeRemoteRepoKeyAclCompatible(repoPath.getRepoKey());
        if (isGrantedByUser(repoPath, permission, user, remoteRepoKeyAclCompatible, aclCacheUserResultMap)) {
            return true;
        }
        aclCacheUserResultMap = aclCache.getGroupResultMap();
        for (UserGroupInfo groupInfo : groupInfos) {
            if (getAndCheckSidPermissions(repoPath, remoteRepoKeyAclCompatible, permission, groupInfo.getGroupName(),
                    aclCacheUserResultMap)) {
                return true;
            }
        }
        return false;
    }

    private <T extends RepoPermissionTarget> boolean isGrantedByUser(RepoPath repoPath,
            ArtifactoryPermission permission,
            UserInfo user,
            Set<String> remoteRepoKeyAclCompatible,
            Map<String, Map<String, Set<PrincipalPermission<T>>>> aclCacheUserResultMap) {
        boolean isTokenScopeOnUser = (user.isIdentity() || !user.isAppliedPermissionsGroups())
                && !isProjectScopeToken();
        boolean isScopeOnUser = ConstantValues.forceUserPermissionsOverTokenScopes.getBoolean() || isTokenScopeOnUser;
        log.trace("user.isIdentity() : {}, " +
                        "user.isAppliedPermissionsGroups() : {}, " +
                        "isProjectScopeToken() : {}, " +
                        "isScopeOnUser : {}",
                user.isIdentity(), user.isAppliedPermissionsGroups(), isProjectScopeToken(), isScopeOnUser);
        boolean granted = isScopeOnUser
                && getAndCheckSidPermissions(repoPath, remoteRepoKeyAclCompatible, permission, user.getUsername(),
                aclCacheUserResultMap);
        if (ConstantValues.forceUserPermissionsOverTokenScopes.getBoolean() && !isTokenScopeOnUser) {
            log.warn(LogThrottleMarker.getInstance(),
                    "Feature flag force.user.permissions.over.token.scopes=true overrode " +
                            "the natural scope predicate: principal='{}', repoKey='{}', repoPath='{}', " +
                            "action={}, granted={}",
                    user.getUsername(), repoPath.getRepoKey(), repoPath.getPath(),
                    permission.name(), granted);
        }
        return granted;
    }

    <T extends RepoPermissionTarget> boolean isGranted(RepoPath repoPath, ArtifactoryPermission permission,
            GroupInfo groupInfo) {
        AclCache<T> aclCache = getAclCacheByType(repoPath);
        Map<String, Map<String, Set<PrincipalPermission<T>>>> map = aclCache.getGroupResultMap();
        Set<String> remoteRepoKeyAclCompatible = makeRemoteRepoKeyAclCompatible(repoPath.getRepoKey());
        return getAndCheckSidPermissions(repoPath, remoteRepoKeyAclCompatible, permission, groupInfo.getGroupName(),
                map);
    }

    @Override
    public boolean isGrantedByAccessToken(PermissionScope permissionScope) {
        return checkPermissionWithAccessToken(permissionScope, false);
    }

    @Override
    public boolean principalCanDo(@NonNull PermissionScope permissionScope) {
        return checkPermissionWithAccessToken(permissionScope, true);
    }

    private boolean isGranted(PrincipalPermission<? extends PermissionTarget> acl, ArtifactoryPermission permission,
            String principal) {
        AceInfo ace = acl.getAce();
        return isGranted(ace, permission, principal);
    }

    private boolean isGranted(AceInfo ace, ArtifactoryPermission permission, String principal) {
        return ace != null && (ace.getMask() & permission.getMask()) > 0 &&
                StringUtils.equals(principal, ace.getPrincipal());
    }

    private <T extends RepoPermissionTarget> boolean getAndCheckSidPermissions(RepoPath repoPath,
            Set<String> remoteRepoKeyAclCompatible,
            ArtifactoryPermission permission,
            String principal, Map<String, Map<String, Set<PrincipalPermission<T>>>> map) {
        Map<String, Set<PrincipalPermission<T>>> repoSidPermissions = map.get(principal);
        if (log.isTraceEnabled()) {
            log.trace("getAndCheckSidPermissions - Principal: {}, permissions: {}, repoSidPermissions: {}", principal,
                    permission, repoSidPermissions);
        }
        if (repoSidPermissions != null) {
            Set<String> remoteRepoKeyAclCompatibles = new LinkedHashSet<>();
            remoteRepoKeyAclCompatibles.addAll(remoteRepoKeyAclCompatible);
            remoteRepoKeyAclCompatibles.addAll(getRemoteLocalReposVariation(repoPath.getRepoKey()));

            if (getAndCheckAcl(repoPath.getRepoKey(), remoteRepoKeyAclCompatibles, repoSidPermissions, repoPath,
                    permission, principal)) {
                log.trace("getAndCheckSidPermissions - Permission for: '{}' is granted to: '{}'", repoPath.getRepoKey(),
                        principal);
                return true;
            }
            if (isBuildInfoOrReleaseBundleRepo(repoPath.getRepoKey())) {
                log.trace(
                        "Build/Release Bundle permissions are not contained in 'ANY REPO', no need to check - " +
                                "Permission for: '{}' is not granted to: '{}'", repoPath.getRepoKey(), principal);
                return false;
            }
            if (getAndCheckAcl(ANY_LOCAL_REPO, remoteRepoKeyAclCompatible, repoSidPermissions, repoPath, permission,
                    principal)) {
                return true;
            }
            if (getAndCheckAcl(ANY_REMOTE_REPO, remoteRepoKeyAclCompatible, repoSidPermissions, repoPath, permission,
                    principal)) {
                return true;
            }
            if (getAndCheckAcl(ANY_DISTRIBUTION_REPO, remoteRepoKeyAclCompatible, repoSidPermissions, repoPath,
                    permission, principal)) {
                return true;
            }
            return getAndCheckAcl(ANY_REPO, remoteRepoKeyAclCompatible, repoSidPermissions, repoPath, permission,
                    principal);
        }
        log.trace("getAndCheckSidPermissions - Permission for repo: '{}' not granted to: '{}'. No permissions found.",
                repoPath.getRepoKey(), principal);
        return false;
    }

    /**
     * @param repoSidPermissions Map of repoPath to aclInfo
     */
    private <T extends RepoPermissionTarget> boolean getAndCheckAcl(String checkedRepo,
            Set<String> remoteRepoKeyAclCompatible,
            Map<String, Set<PrincipalPermission<T>>> repoSidPermissions, RepoPath repoPath,
            ArtifactoryPermission permission,
            String principal) {
        if (InternalMetadataChecker.isInternalMetadata(repoPath.getPath())) {
            log.trace("Internal metadata of repo '{}' is accessible to all", repoPath.getRepoKey());
            return true;
        }

        Set<PrincipalPermission<T>> allItemPermissions =
                collectPermissions(checkedRepo, remoteRepoKeyAclCompatible, repoSidPermissions);

        if (allItemPermissions == null && MapUtils.isNotEmpty(repoSidPermissions)) {
            allItemPermissions = StreamSupportUtils.stream(remoteRepoKeyAclCompatible)
                    .map(repoSidPermissions::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }

        if (allItemPermissions != null) {
            Set<String> remoteRepoKeyAclCompatibleWithRepoKey = new LinkedHashSet<>();
            remoteRepoKeyAclCompatibleWithRepoKey.add(repoPath.getRepoKey());
            remoteRepoKeyAclCompatibleWithRepoKey.addAll(remoteRepoKeyAclCompatible);
            return permissionCheckOnAcl(allItemPermissions, repoPath, remoteRepoKeyAclCompatibleWithRepoKey, permission,
                    principal);
        }
        log.trace("getAndCheckAcl - No permissions found for repo: '{}' and principal: '{}'", checkedRepo, principal);
        return false;
    }

    /**
     * Collects all the defined permissions for given repo key and for all possible given repo key variations.
     *
     * @param checkedRepo                Given repo key
     * @param remoteRepoKeyAclCompatible List of the possible repo key variations
     * @param repoSidPermissions         Set of the all available permissions
     * @return Set of the given repo permissions
     * @see LegacyAuthorizationServiceImpl#getRemoteLocalReposVariation(java.lang.String)
     */
    @VisibleForTesting
    <T extends RepoPermissionTarget> Set<PrincipalPermission<T>> collectPermissions(String checkedRepo,
            Collection<String> remoteRepoKeyAclCompatible,
            Map<String, Set<PrincipalPermission<T>>> repoSidPermissions) {
        Set<PrincipalPermission<T>> allItemPermissions = null;

        // collect permissions for checked repo
        Set<PrincipalPermission<T>> checkedRepoPermissions = repoSidPermissions.get(checkedRepo);
        if (checkedRepoPermissions != null) {
            allItemPermissions = new LinkedHashSet<>(checkedRepoPermissions);
        }

        // if permissions for checked repo exists then add permissions for all the variation as well
        if (allItemPermissions != null && MapUtils.isNotEmpty(repoSidPermissions)) {
            for (String candidate : remoteRepoKeyAclCompatible) {
                final Set<PrincipalPermission<T>> candidatePermissions = repoSidPermissions.get(candidate);
                if (candidatePermissions != null) {
                    allItemPermissions.addAll(candidatePermissions);
                }
            }
        }
        return allItemPermissions;
    }

    <T extends RepoPermissionTarget> boolean permissionCheckOnAcl(
            Collection<PrincipalPermission<T>> principalPermissions,
            RepoPath repoPath, Collection<String> remoteRepoKeyAclCompatibleList, ArtifactoryPermission permissionType,
            String principal) {
        String path = getPathToCheckPermissionFor(repoPath);
        boolean behaveAsFolder = shouldBehaveAsFolder(repoPath, permissionType);
        for (PrincipalPermission<T> principalPermission : principalPermissions) {
            if (isGranted(principalPermission, permissionType, principal)) {
                T aclPermissionTarget = principalPermission.getPermissionTarget();
                Collection<String> repoKeys = aclPermissionTarget.getRepoKeys();
                if (hasMatchingRepoKey(repoKeys, remoteRepoKeyAclCompatibleList) &&
                        matches(aclPermissionTarget, path, behaveAsFolder)) {
                    log.trace("permissionCheckOnAcl - Permission for: '{}' is granted to: '{}'", path, principal);
                    return true;
                }
            }
        }
        log.trace("permissionCheckOnAcl - Permission for: '{}' is not granted to: '{}'", path, principal);
        return false;
    }

    private boolean shouldBehaveAsFolder(RepoPath repoPath, ArtifactoryPermission permissionType) {
        if (repoPath.isFolder()) {
            return (permissionType.getMask() &
                    (ArtifactoryPermission.READ.getMask() | ArtifactoryPermission.DEPLOY.getMask())) != 0;
        }
        return false;
    }

    private boolean hasMatchingRepoKey(Collection<String> repoKeys, Collection<String> remoteRepoKeyAclCompatibleList) {
        boolean hasAnyRepo = repoKeys.contains(ANY_REPO);
        boolean hasAnyRemoteRepo = repoKeys.contains(ANY_REMOTE_REPO);
        boolean hasAnyLocalRepo = repoKeys.contains(ANY_LOCAL_REPO);
        for (String key : remoteRepoKeyAclCompatibleList) {
            if (hasAnyRepo || (hasAnyLocalRepo && isLocal(key)) || (hasAnyRemoteRepo && isRemote(key)) ||
                    repoKeys.contains(key)) {
                return true;
            }
        }
        return false;
    }


    private boolean isRemote(String repoKey) {
        if (StringUtils.endsWith(repoKey, REMOTE_CACHE_SUFFIX)) {
            if (repositoryService.existsByType(RepoDetailsType.LOCAL, repoKey) ||
                    repositoryService.existsByType(RepoDetailsType.FEDERATED, repoKey)) {
                return false;
            }
            repoKey = StringUtils.removeEnd(repoKey, REMOTE_CACHE_SUFFIX);
        }
        return repositoryService.existsByType(RepoDetailsType.REMOTE, repoKey);
    }

    private boolean isLocal(String repoKey) {
        return repositoryService.existsByType(RepoDetailsType.LOCAL, repoKey) ||
                repositoryService.existsByType(RepoDetailsType.FEDERATED, repoKey);
    }

    private Set<String> getRemoteLocalReposVariation(String repoKey) {
        String realRepoName;
        boolean repoNameSuspectedAsRemote = repoKey.endsWith(LocalCacheRepoDescriptor.PATH_SUFFIX);
        if (repoNameSuspectedAsRemote) {
            realRepoName = repoKey.substring(0, repoKey.lastIndexOf(LocalCacheRepoDescriptor.PATH_SUFFIX));
        } else {
            realRepoName = repoKey;
        }

        boolean isRemote = repositoryService.existsByType(RepoDetailsType.REMOTE, realRepoName);

        if (!isRemote && !repoNameSuspectedAsRemote) {
            return Set.of(repoKey.concat(LocalCacheRepoDescriptor.PATH_SUFFIX));
        }

        if (isRemote && repoNameSuspectedAsRemote) {
            return Set.of(realRepoName);
        }

        /*
         * As of Access version 7.161.0, they no longer store repositories with "-cache" suffix in the permissions.
         * We blocked creation of local repositories with "-cache" suffix in Artifactory long time ago but there is
         * still a possibility that customers have such repositories. In order to not break permissions for such
         * legacy repositories, we need to apply the same logic as for remote repositories with "-cache" suffix.
         * For example:
         * If there is a local repository "example-repo-cache", we need to also check permissions for "example-repo".
         */
        boolean isLocal = repositoryService.existsByType(RepoDetailsType.LOCAL, repoKey) ||
                repositoryService.existsByType(RepoDetailsType.FEDERATED, repoKey);

        if (isLocal) {
            return Set.of(realRepoName);
        }

        return Set.of();
    }

    private Set<String> makeRemoteRepoKeyAclCompatible(String repoKey) {
        return makeRemoteRepoKeysAclCompatible(Set.of(repoKey));
    }

    private <T extends RepoPermissionTarget> AclCache<T> getAclCacheByType(RepoPath repoPath) {
        String repoKey = repoPath.getRepoKey();
        if (isBuildInfoRepo(repoKey)) {
            return aclStoreService.getBuildsAclCache();
        }
        if (isReleaseBundlesRepoV1(repoKey)) {
            if (addons.isEntitled(EntitlementType.RELEASE_BUNDLES_READ)) {
                return aclStoreService.getReleaseBundlesAclCache();
            }
            return new AclCache<>(Maps.newHashMap(), Maps.newHashMap());
        } else if (isReleaseBundlesV2RepoKey(repoKey)) {
            return aclStoreService.getReleaseBundlesAclCache();
        }
        return aclStoreService.getAclCache();
    }

    /**
     * Checks whether the sid is per user or group and returns the relevant map from the acl cache
     *
     * @return Map of user/group name TO Map of repoPath/buildName to aclInfo
     */
    private <T extends RepoPermissionTarget> Map<String, Map<String,
            Set<PrincipalPermission<T>>>> getAclCacheRelevantMap(AclCache<T> aclCache, ArtifactorySid sid) {
        Map<String, Map<String, Set<PrincipalPermission<T>>>> map;
        if (sid.isGroup()) {
            map = aclCache.getGroupResultMap();
        } else {
            map = aclCache.getUserResultMap();
        }
        return map;
    }

    private static boolean matches(RepoPermissionTarget aclPermissionTarget, String path, boolean folder) {
        return PathMatcher.matches(path, aclPermissionTarget.getIncludes(), aclPermissionTarget.getExcludes(), folder);
    }
}
