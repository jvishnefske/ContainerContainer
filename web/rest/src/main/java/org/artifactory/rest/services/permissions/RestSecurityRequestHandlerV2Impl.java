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

package org.artifactory.rest.services.permissions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.jfrog.build.acl.model.BuildNameAndRepo;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.release.bundle.ReleaseBundleService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.RestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.bundle.BundleNameAndRepo;
import org.artifactory.bundle.BundleType;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.model.security.BuildPermissionTargetImpl;
import org.artifactory.model.security.MutableRepoAclImpl;
import org.artifactory.model.security.ReleaseBundlePermissionTargetImpl;
import org.artifactory.model.security.RepoPermissionTargetImpl;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.security.SecurityModelPopulator;
import org.artifactory.rest.common.util.RestUtils;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ConflictException;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.security.AceInfo;
import org.artifactory.security.Acl;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.ArtifactoryResourceType;
import org.artifactory.security.BuildAcl;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.PermissionTarget;
import org.artifactory.security.PermissionTargetAcls;
import org.artifactory.security.PermissionTargetNaming;
import org.artifactory.security.PrincipalConfiguration;
import org.artifactory.security.ReleaseBundleAcl;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.RepoPermissionTarget;
import org.artifactory.security.SecurityEntityListItem;
import org.artifactory.security.UserInfo;
import org.artifactory.security.permissions.PermissionTargetModel;
import org.artifactory.security.permissions.RepoPermissionTargetModel;
import org.artifactory.security.permissions.SecurityEntityPermissionTargetModel;
import org.artifactory.security.permissions.SecurityEntityRepoPermissionTargetModel;
import org.artifactory.storage.StorageException;
import org.artifactory.util.BasicHttpUtils;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.PathMatcher;
import org.artifactory.util.UnsupportedByLicenseException;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.common.validator.XSSValidator;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.artifactory.addon.release.bundle.ReleaseBundleAddon.ENTERPRISE_PLUS_MSG;
import static org.artifactory.api.rest.constant.SecurityRestConstants.PATH_ROOT_V2;
import static org.artifactory.api.rest.constant.SecurityRestConstants.PERMISSIONS_ROOT;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.rest.common.security.RestSecurityHelperCommon.CONFLICT_ERR_MSG;
import static org.artifactory.rest.common.security.RestSecurityHelperCommon.checkForNonExistingPrinciples;
import static org.artifactory.rest.common.security.RestSecurityHelperCommon.getAcesForAcl;
import static org.artifactory.rest.common.security.RestSecurityHelperCommon.getExistingPermissionTarget;
import static org.artifactory.rest.common.security.RestSecurityHelperCommon.getExistingPermissionTargetByLicense;
import static org.artifactory.rest.common.security.RestSecurityHelperCommon.getFirstNonExistingRepoFromList;
import static org.artifactory.rest.common.util.RestUtils.RepoType.VIRTUAL;
import static org.artifactory.security.ArtifactoryResourceType.BUILD;
import static org.artifactory.security.ArtifactoryResourceType.RELEASE_BUNDLES;
import static org.artifactory.security.ArtifactoryResourceType.REPO;
import static org.artifactory.security.PermissionTargetNaming.NAMING_DISPLAY;
import static org.artifactory.security.PermissionTargetNaming.NAMING_UI;
import static org.artifactory.util.CollectionUtils.isNullOrEmpty;

/**
 * Common methods for CRUD actions on permission targets via REST api and ui.
 *
 * @author Yuval Reches
 * @author Dan Feldman
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestSecurityRequestHandlerV2Impl implements InternalRestSecurityRequestHandlerV2 {

    public static final String MISSING_PARTS_ERR_MSG = "Permission target request missing repositories, builds and " +
            "release bundles. Must have at least one.";
    public static final String ALREADY_EXISTS_ERR_MSG =
            "Can't create permission target '%s' for type %s. It already exists.";
    private static final String REPO_NOT_ALLOWED_ERR_MSG =
            "Setting repository permissions on %s Repository is not allowed.";
    public static final String ENTERPRISE_PLUS_ERR_MSG =
            "Creating permission target with release bundles " + ENTERPRISE_PLUS_MSG;

    private final AuthorizationService authorizationService;
    private final UserGroupService userGroupService;
    private final CentralConfigService configService;
    private final SecurityService securityService;
    private final BuildFacade buildFacade;
    private final ReleaseBundleService releaseBundleService;
    private final RepositoryService repositoryService;

    /*
     * Public methods
     */
    @Override
    public boolean isPermissionTargetExists(String entityKey) throws ForbiddenWebAppException, NotFoundException {
        PermissionTargetAcls permissionExist = getExistingPermissionTargetByLicense(authorizationService, entityKey);
        RepoAcl repoAcl = permissionExist.getRepoAcl();
        BuildAcl buildAcl = permissionExist.getBuildAcl();
        ReleaseBundleAcl releaseBundleAcl = permissionExist.getReleaseBundleAcl();

        if (repoAcl == null && buildAcl == null && releaseBundleAcl == null) {
            return false;
        }

        // look for at least one existing acl that the user has manage permission for
        boolean repoExistsAndAllowed = isAclExistsAndAllowed(repoAcl);
        boolean buildExistsAndAllowed = isAclExistsAndAllowed(buildAcl);
        boolean releaseBundleExistsAndAllowed = isAclExistsAndAllowed(releaseBundleAcl);

        if (repoExistsAndAllowed || buildExistsAndAllowed || releaseBundleExistsAndAllowed) {
            return true;
        }
        // if we got here all sections are either doesn't exist or exists and denied.
        boolean repoAclDenied = repoAcl != null;
        boolean buildAclDenied = buildAcl != null;
        boolean releaseBundleAclDenied = releaseBundleAcl != null;

        throwForbiddenPermissionErrorMessage(entityKey, getDeniedPermissionTypesMessage(repoAclDenied, buildAclDenied,
                releaseBundleAclDenied));
        return false;
    }

    @Override
    public List<PermissionTargetModel> getAllPermissionTargets(PermissionTargetNaming outputMode) {
        Map<String, RepoAcl> keyToRepoAcl = getAllReposAcls();
        Map<String, BuildAcl> keyToBuildAcl = getAllBuildsAcls();
        Map<String, ReleaseBundleAcl> keyToReleaseBundleAcl = getAllReleaseBundleAcls();
        // Aggregate same-name repo, build and release-bundle acls into one model, since we got only user-manageable
        // permissions no need to validate
        List<PermissionTargetModel> repoBuildAndReleaseBundlePermissions = collectRepoBuildAndReleaseBundlePermissions(
                keyToRepoAcl, keyToBuildAcl, keyToReleaseBundleAcl, outputMode);

        addBuildAndReleaseBundlePermissions(repoBuildAndReleaseBundlePermissions, keyToBuildAcl,
                keyToReleaseBundleAcl, outputMode);

        // Adding all the release bundle acls that did not mach any repo and build acl name
        keyToReleaseBundleAcl.keySet().stream()
                .map(aclKey -> toPermissionTargetModel(null, null, keyToReleaseBundleAcl.get(aclKey), outputMode))
                .forEach(repoBuildAndReleaseBundlePermissions::add);
        return repoBuildAndReleaseBundlePermissions;
    }

    @Override
    public Set<SecurityEntityListItem> getAllPermissionTargets(HttpServletRequest request) {
        return getAllPermissionTargets(NAMING_DISPLAY).stream()
                .map(permission -> getPermissionListItem(request, permission))
                .collect(Collectors.toSet());
    }

    @Override
    public PermissionTargetModel getPermissionTarget(String entityKey, PermissionTargetNaming outputMode)
            throws ForbiddenWebAppException, NotFoundException {
        RepoAcl repoAcl = authorizationService.getRepoAcl(entityKey);
        BuildAcl buildAcl = authorizationService.getBuildAcl(entityKey);
        ReleaseBundleAcl releaseBundleAcl = authorizationService.getReleaseBundleAcl(entityKey);

        if (repoAcl == null && buildAcl == null && releaseBundleAcl == null) {
            return null;
        }
        boolean repoExistsAndAllowed = isAclExistsAndAllowed(repoAcl);
        boolean buildExistsAndAllowed = isAclExistsAndAllowed(buildAcl);
        boolean releaseBundleExistsAndAllowed = isAclExistsAndAllowed(releaseBundleAcl);

        if (!repoExistsAndAllowed && !buildExistsAndAllowed && !releaseBundleExistsAndAllowed) {
            // if we got here all sections are either doesn't exist or exists and denied.
            throwForbiddenPermissionErrorMessage(entityKey, getDeniedPermissionTypesMessage(repoAcl != null,
                    buildAcl != null, releaseBundleAcl != null));
        }
        // Removing the acls from response in case no manage permission
        log.debug("Checking for existence of acl entities and removing them from response if needed");
        repoAcl = repoExistsAndAllowed ? repoAcl : null;
        buildAcl = buildExistsAndAllowed ? buildAcl : null;
        releaseBundleAcl = releaseBundleExistsAndAllowed ? releaseBundleAcl : null;

        return toPermissionTargetModel(repoAcl, buildAcl, releaseBundleAcl, outputMode);
    }

    @Override
    public void createPermissionTarget(String entityKey, PermissionTargetModel permissionTargetModel,
            PermissionTargetNaming inputMode) {
        if (!securityService.isAdmin()) {
            throw new ForbiddenWebAppException("Only an admin user can create permission targets.");
        }
        validateRepositoryType(permissionTargetModel);
        validateIncomingPermissionTarget(entityKey, permissionTargetModel);

        // In case permission already exists we throw error
        validatePermissionsExistenceForCreate(entityKey, permissionTargetModel);
        // Validate the user input is valid
        validatePermissionTargetSections(permissionTargetModel, inputMode, null);

        Acl repoAcl = createRepoAclModel(entityKey, permissionTargetModel, null, inputMode);
        Acl buildAcl = createBuildAclModel(entityKey, permissionTargetModel, null, inputMode);
        Acl releaseBundleAcl = createReleaseBundleAclModel(entityKey, permissionTargetModel, null, inputMode);
        // Create the ACLs in access
        getTxMe().createAcl(repoAcl, buildAcl, releaseBundleAcl);
    }

    @Override
    public void updatePermissionTarget(String entityKey, PermissionTargetModel permissionTargetModel,
            PermissionTargetNaming inputMode) {
        validateRepositoryType(permissionTargetModel);
        validateIncomingPermissionTarget(entityKey, permissionTargetModel);

        PermissionTargetAcls existingPermission = getExistingPermissionTargetByLicense(authorizationService, entityKey);
        RepoAcl existingRepoAcl = existingPermission.getRepoAcl();
        BuildAcl existingBuildAcl = existingPermission.getBuildAcl();
        ReleaseBundleAcl existingReleaseBundleAcl = existingPermission.getReleaseBundleAcl();

        //What permission targets came in with the model?
        boolean hasRepoPermissionInRequest = hasRepoPermission(permissionTargetModel);
        boolean hasBuildPermissionInRequest = hasBuildPermission(permissionTargetModel);
        boolean hasReleaseBundlePermissionInRequest = hasReleaseBundlePermission(permissionTargetModel);

        validateUpdateActionPermissions(entityKey, existingRepoAcl, existingBuildAcl, existingReleaseBundleAcl,
                hasRepoPermissionInRequest, hasBuildPermissionInRequest, hasReleaseBundlePermissionInRequest);

        //Validate incoming request for discrepancies in model
        validatePermissionTargetSections(permissionTargetModel, inputMode, existingPermission);

        Acl repoAcl = createRepoAclModel(entityKey, permissionTargetModel, existingRepoAcl, inputMode);
        Acl buildAcl = createBuildAclModel(entityKey, permissionTargetModel, existingBuildAcl, inputMode);
        Acl releaseBundleAcl = createReleaseBundleAclModel(entityKey, permissionTargetModel, existingReleaseBundleAcl,
                inputMode);

        boolean repoAclExists = existingRepoAcl != null;
        boolean buildAclExists = existingBuildAcl != null;
        boolean releaseBundleExists = existingReleaseBundleAcl != null;

        //if acl didn't exist before and exists in request - add
        List<Acl> toAdd = getAclAccordingToExistence(repoAcl, buildAcl, releaseBundleAcl, !repoAclExists,
                !buildAclExists, !releaseBundleExists);
        //if acl existed before and exists also in request - update
        List<Acl> toUpdate = getAclAccordingToExistence(repoAcl, buildAcl, releaseBundleAcl, repoAclExists,
                buildAclExists, releaseBundleExists);
        //if acl existed before and do not exist in request - delete
        List<Acl> toDelete = getAclsToDeleteIfNeeded(entityKey, existingRepoAcl, existingBuildAcl,
                existingReleaseBundleAcl,
                hasRepoPermissionInRequest, hasBuildPermissionInRequest, hasReleaseBundlePermissionInRequest);

        getTxMe().updateAcl(toUpdate, toDelete, toAdd);
    }

    @Override
    public void deletePermissionTarget(String entityKey) {
        PermissionTargetAcls existingPermission = getExistingPermissionTarget(authorizationService, entityKey);
        RepoAcl repoAcl = existingPermission.getRepoAcl();
        BuildAcl buildAcl = existingPermission.getBuildAcl();
        ReleaseBundleAcl releaseBundleAcl = existingPermission.getReleaseBundleAcl();

        boolean repoAclMissing = repoAcl == null;
        boolean buildAclMissing = buildAcl == null;
        boolean releaseBundleAclMissing = releaseBundleAcl == null;

        if (repoAclMissing && buildAclMissing && releaseBundleAclMissing) {
            throw new NotFoundException("No such permission target '" + entityKey + "'");
        }
        //nulls filtered by underlying call
        getTxMe().deleteAcl(repoAcl, buildAcl, releaseBundleAcl);
    }

    @Override
    public List<String> getBuildsPerPatterns(List<String> includePatterns, List<String> excludePatterns,
            String buildRepo) {
        return getAllBuildsInPermission(includePatterns, excludePatterns, buildRepo);
    }

    @Override
    public List<String> getReleaseBundlesByReposAndPatterns(
            List<String> repos,
            List<String> includePatterns,
            List<String> excludePatterns,
            @Nullable BundleType bundleType) {

        List<BundleNameAndRepo> bundlesByRepo =
                filterBundlesByRepos(releaseBundleService.getAllCompletedBundles(bundleType), repos);
        return getAllReleaseBundlesInPermission(bundlesByRepo, includePatterns, excludePatterns);
    }

    @Override
    public List<SecurityEntityPermissionTargetModel> getUserPermissionsSecurityEntity(String userName) {
        validateUser(userName);
        return SecurityModelPopulator.getSecurityEntityPermissionTargetModelForRest(
                getPermissionTargetsForUserByResourceType(userName, REPO),
                getPermissionTargetsForUserByResourceType(userName, BUILD),
                getPermissionTargetsForUserByResourceType(userName, RELEASE_BUNDLES));
    }

    @Override
    public List<SecurityEntityPermissionTargetModel> getGroupPermissionsSecurityEntity(String groupName) {
        GroupInfo groupInfo = userGroupService.findGroup(groupName);
        if (groupInfo == null) {
            throw new NotFoundException();
        }
        return SecurityModelPopulator.getSecurityEntityPermissionTargetModelForRest(
                getPermissionTargetsForGroupByResourceType(groupName, REPO),
                getPermissionTargetsForGroupByResourceType(groupName, BUILD),
                getPermissionTargetsForGroupByResourceType(groupName, RELEASE_BUNDLES));
    }

    /*
     * Actions
     */

    /**
     * @throws BadRequestException on error creating an acl.
     */
    public void createAcl(Acl... acls) {
        try {
            Stream.of(acls)
                    .filter(Objects::nonNull)
                    .forEach(acl -> authorizationService.createAcl(acl));
        } catch (StorageException e) {
            log.warn("Failed to create permission: {}", e.getCause().getMessage());
            log.debug("Failed to create permission: {}", e);
            throw new BadRequestException("Failed to create permission: " + e.getCause().getMessage());
        }
    }

    /**
     * @param toUpdate - acls to update
     * @param toDelete - to maintain atomicity this is also the correct place to delete any acls if a request contained
     *                 null value for a pre-existing acl while it contained another one to update.
     * @param toAdd    - acls to add
     * @throws BadRequestException on error creating an acl.
     */
    public void updateAcl(List<Acl> toUpdate, List<Acl> toDelete, List<Acl> toAdd) throws BadRequestException {
        try {
            toDelete.stream()
                    .filter(Objects::nonNull)
                    .forEach(authorizationService::deleteAcl);
            toUpdate.stream()
                    .filter(Objects::nonNull)
                    .forEach(authorizationService::updateAcl);
            toAdd.stream()
                    .filter(Objects::nonNull)
                    .forEach(authorizationService::createAcl);
        } catch (StorageException e) {
            log.warn("Failed to update permission: {}", e.getCause().getMessage());
            log.debug("Failed to update permission: {}", e);
            throw new BadRequestException("Failed to update permission: " + e.getCause().getMessage());
        }
    }

    /**
     * @throws BadRequestException on error creating an acl.
     */
    public void deleteAcl(Acl... acls) throws BadRequestException {
        try {
            Stream.of(acls)
                    .filter(Objects::nonNull)
                    .forEach(acl -> authorizationService.deleteAcl(acl));
        } catch (StorageException e) {
            log.warn("Failed to delete permission: {}", e.getCause().getMessage());
            log.debug("Failed to delete permission: {}", e);
            throw new BadRequestException("Failed to delete permission: " + e.getCause().getMessage());
        }
    }

    @VisibleForTesting
    Map<String, RepoAcl> getAllReposAcls() {
        return authorizationService.getAllRepoAcls(ArtifactoryPermission.MANAGE).stream()
                .filter(acl -> !authorizationService.isInternalAcl(acl))
                .collect(Collectors.toMap(repoAcl -> repoAcl.getPermissionTarget().getName(), repoAcl -> repoAcl));
    }

    @VisibleForTesting
    Map<String, BuildAcl> getAllBuildsAcls() {
        return authorizationService.getAllBuildAcls(ArtifactoryPermission.MANAGE).stream()
                .filter(acl -> !authorizationService.isInternalAcl(acl))
                .collect(Collectors.toMap(buildAcl -> buildAcl.getPermissionTarget().getName(), buildAcl -> buildAcl));
    }

    @VisibleForTesting
    Map<String, ReleaseBundleAcl> getAllReleaseBundleAcls() {
        return authorizationService.getAllReleaseBundleAcls(ArtifactoryPermission.MANAGE).stream()
                .filter(acl -> !authorizationService.isInternalAcl(acl))
                .collect(Collectors.toMap(releaseBundleAcl -> releaseBundleAcl.getPermissionTarget().getName(),
                        releaseBundleAcl -> releaseBundleAcl));
    }

    private List<PermissionTargetModel> collectRepoBuildAndReleaseBundlePermissions(Map<String, RepoAcl> keyToRepoAcl,
            Map<String, BuildAcl> keyToBuildAcl, Map<String, ReleaseBundleAcl> keyToReleaseBundleAcl,
            PermissionTargetNaming outputMode) {
        return keyToRepoAcl.keySet().stream()
                .map(aclKey -> toPermissionTargetModel(keyToRepoAcl.get(aclKey), keyToBuildAcl.get(aclKey),
                        keyToReleaseBundleAcl.get(aclKey), outputMode))
                .peek(permission -> {
                    keyToBuildAcl.remove(permission.getName());
                    keyToReleaseBundleAcl.remove(permission.getName());
                }).collect(Collectors.toList());
    }

    private void addBuildAndReleaseBundlePermissions(
            List<PermissionTargetModel> repoBuildAndReleaseBundlePermissions, Map<String, BuildAcl> keyToBuildAcl,
            Map<String, ReleaseBundleAcl> keyToReleaseBundleAcl, PermissionTargetNaming outputMode) {
        keyToBuildAcl.keySet().stream()
                .map(aclKey -> toPermissionTargetModel(null, keyToBuildAcl.get(aclKey),
                        keyToReleaseBundleAcl.get(aclKey), outputMode))
                .peek(permission -> keyToReleaseBundleAcl.remove(permission.getName()))
                .forEach(repoBuildAndReleaseBundlePermissions::add);
    }

    private SecurityEntityListItem getPermissionListItem(HttpServletRequest request, PermissionTargetModel permission) {
        String name = permission.getName();
        return new SecurityEntityListItem(name, generatePermissionUri(request, name));
    }

    private static String generatePermissionUri(HttpServletRequest request, String permissionName) {
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        return Joiner.on("/").join(servletContextUrl, RestConstants.PATH_API, PATH_ROOT_V2, PERMISSIONS_ROOT,
                BasicHttpUtils.encodeQuery(permissionName));
    }

    private PermissionTargetModel toPermissionTargetModel(RepoAcl repoAcl, BuildAcl buildAcl,
            ReleaseBundleAcl releaseBundleAcl, PermissionTargetNaming outputMode) {
        if (repoAcl != null) {
            MutableRepoAclImpl toModifyAcl = new MutableRepoAclImpl(repoAcl);
            repoAcl = authorizationService.convertNewAclCachedRepoKeysToRemote(toModifyAcl);
        }
        return SecurityModelPopulator.getPermissionTargetModelV2(repoAcl, buildAcl, releaseBundleAcl, outputMode);
    }

    //On the update list we add to the list if the acl already exists, on the create list we add only if it does not
    private ArrayList<Acl> getAclAccordingToExistence(Acl repoAcl, Acl buildAcl, Acl releaseBundleAcl,
            boolean shouldAddRepoToList, boolean shouldAddBuildToList, boolean shouldAddReleaseBundleToList) {
        ArrayList<Acl> aclList = Lists.newArrayList();
        if (shouldAddRepoToList && repoAcl != null) {
            aclList.add(repoAcl);
        }
        if (shouldAddBuildToList && buildAcl != null) {
            aclList.add(buildAcl);
        }
        if (shouldAddReleaseBundleToList && releaseBundleAcl != null) {
            aclList.add(releaseBundleAcl);
        }
        return aclList;
    }

    private List<String> getAllBuildsInPermission(List<String> includePatterns, List<String> excludePatterns,
            String buildRepo) {
        return buildFacade.getBuildsInfoInternally(buildRepo).stream()
                .map(BuildNameAndRepo::getName).filter(s -> isBuildInPermissions(includePatterns, excludePatterns, s))
                .collect(Collectors.toList());
    }

    private boolean isBuildInPermissions(List<String> includePatterns, List<String> excludePatterns, String build) {
        return PathMatcher.matches(getPathToCheckPermissionFor(build) + "/", includePatterns, excludePatterns, true);
    }

    private String getPathToCheckPermissionFor(String path) {
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("Failed to decode the path: {}. Using it as a plain text", path);
        }
        return path;
    }

    private List<String> getAllReleaseBundlesInPermission(List<BundleNameAndRepo> bundlesByRepo,
            List<String> includePatterns, List<String> excludePatterns) {
        return bundlesByRepo.stream()
                .filter(bundle -> isReleaseBundleInPermission(includePatterns, excludePatterns, bundle))
                .map(BundleNameAndRepo::getName)
                .distinct().collect(Collectors.toList());
    }

    private boolean isReleaseBundleInPermission(List<String> includePatterns, List<String> excludePatterns,
            BundleNameAndRepo bundle) {
        return PathMatcher.matches(bundle.getName() + "/", includePatterns, excludePatterns, true);
    }

    private List<BundleNameAndRepo> filterBundlesByRepos(List<BundleNameAndRepo> bundles, List<String> repos) {
        if (!repos.isEmpty()) {
            return bundles.stream().filter(bundle -> repos.contains(bundle.getStoringRepo()))
                    .collect(Collectors.toList());
        } else {
            return bundles;
        }
    }

    private void setPermissionTargetFields(RepoPermissionTargetImpl newPermissionTarget,
            RepoPermissionTargetModel permissionTargetFromRequest) {
        newPermissionTarget.setRepoKeys(permissionTargetFromRequest.getRepositories());
        newPermissionTarget.setIncludes(permissionTargetFromRequest.getIncludePatterns());
        newPermissionTarget.setExcludes(permissionTargetFromRequest.getExcludePatterns());
    }

    /**
     * Request is treated as delete of old ACLs in case the section is missing in the model and the ACL exists,
     * but treat as actual delete only if the user has manage on it
     *
     * @return the acls to delete if required, they will be passed to the {@link #updateAcl} method to maintain
     * atomicity
     */
    private List<Acl> getAclsToDeleteIfNeeded(String entityKey, RepoAcl existingRepoAcl, BuildAcl existingBuildAcl,
            ReleaseBundleAcl existingReleaseBundleAcl, boolean hasRepoPermissionInRequest,
            boolean hasBuildPermissionInRequest, boolean hasReleaseBundlePermissionInRequest) {
        List<Acl> toDelete = Lists.newArrayList();

        if ((existingRepoAcl != null) && !hasRepoPermissionInRequest &&
                authorizationService.canManage(existingRepoAcl.getPermissionTarget())) {
            log.debug("Will delete permission {} of type repo as its missing in user request but exists in DB",
                    entityKey);
            toDelete.add(existingRepoAcl);
        }
        if ((existingBuildAcl != null) && !hasBuildPermissionInRequest &&
                authorizationService.canManage(existingBuildAcl.getPermissionTarget())) {
            log.debug("Will delete permission {} of type build as its missing in user request but exists in DB",
                    entityKey);
            toDelete.add(existingBuildAcl);
        }
        if ((existingReleaseBundleAcl != null) && !hasReleaseBundlePermissionInRequest &&
                authorizationService.canManage(existingReleaseBundleAcl.getPermissionTarget())) {
            log.debug(
                    "Will delete permission {} of type release bundle as its missing in user request but exists in DB",
                    entityKey);
            toDelete.add(existingReleaseBundleAcl);
        }
        return toDelete;
    }

    private Map<String, SecurityEntityRepoPermissionTargetModel> getPermissionTargetsForGroupByResourceType(
            String groupName, ArtifactoryResourceType type) {
        List<String> groups = Collections.singletonList(groupName);
        return StreamSupportUtils.multimapEntriesStream(authorizationService.getGroupsPermissions(groups, type))
                .collect(Collectors.toMap(entry -> entry.getKey().getName(),
                        entry -> SecurityModelPopulator.getSecurityEntityRepoPermissionTargetModelV2(entry.getValue(),
                                (RepoPermissionTarget) entry.getKey())));

    }

    private Map<String, SecurityEntityRepoPermissionTargetModel> getPermissionTargetsForUserByResourceType(
            String userName, ArtifactoryResourceType type) {
        return StreamSupportUtils.mapEntriesStream(
                        authorizationService.getUserPermissionAndItsGroups(userName, type).asMap())
                .collect(Collectors.toMap(entry -> entry.getKey().getName(),
                        entry -> SecurityModelPopulator.getSecurityEntityRepoPermissionTargetModelV2(entry.getValue(),
                                (RepoPermissionTarget) entry.getKey())));
    }

    private InternalRestSecurityRequestHandlerV2 getTxMe() {
        return ContextHelper.get().beanForType(InternalRestSecurityRequestHandlerV2.class);
    }


    /*
     * Model
     */

    /**
     * In case Repositories section in user request is empty --> return null
     */
    @Nullable
    private Acl createRepoAclModel(String entityKey, PermissionTargetModel permissionTargetModel,
            @Nullable RepoAcl previousAcl, PermissionTargetNaming inputMode) {
        if (!hasRepoPermission(permissionTargetModel)) {
            return null;
        }
        validateRepoAclModifications(previousAcl, permissionTargetModel.getRepo().getRepositories());
        String currentUsername = securityService.currentUsername();
        // Permission Target
        RepoPermissionTargetImpl repoPermissionTarget = new RepoPermissionTargetImpl(entityKey);
        RepoPermissionTargetModel repositories = permissionTargetModel.getRepo();
        setPermissionTargetFields(repoPermissionTarget, repositories);
        Set<AceInfo> aceInfos = getAcesForAclAndValidateActions(inputMode, repositories.getActions());
        if (previousAcl != null) {
            return InfoFactoryHolder.get()
                    .createRepoAcl(repoPermissionTarget, aceInfos, currentUsername,
                            ClockUtils.epochMillis(), previousAcl.getAccessIdentifier());
        }
        return InfoFactoryHolder.get()
                .createRepoAcl(repoPermissionTarget, aceInfos, currentUsername,
                        ClockUtils.epochMillis());
    }

    /**
     * In case Builds section in user request is empty --> return null
     */
    @Nullable
    private Acl createBuildAclModel(String entityKey, PermissionTargetModel permissionTargetModel,
            @Nullable BuildAcl previousAcl, PermissionTargetNaming inputMode) {
        if (!hasBuildPermission(permissionTargetModel)) {
            return null;
        }
        validateAclPatternsModifications(previousAcl, permissionTargetModel.getBuild());
        String currentUsername = securityService.currentUsername();
        // Permission Target
        BuildPermissionTargetImpl buildPermissionTarget = new BuildPermissionTargetImpl(entityKey);
        RepoPermissionTargetModel builds = permissionTargetModel.getBuild();
        setPermissionTargetFields(buildPermissionTarget, builds);
        Set<AceInfo> aceInfos = getAcesForAclAndValidateActions(inputMode, builds.getActions());
        if (previousAcl != null) {
            return InfoFactoryHolder.get()
                    .createBuildAcl(buildPermissionTarget, aceInfos, currentUsername,
                            ClockUtils.epochMillis(), previousAcl.getAccessIdentifier());
        }
        return InfoFactoryHolder.get()
                .createBuildAcl(buildPermissionTarget, aceInfos, currentUsername,
                        ClockUtils.epochMillis());
    }

    private Acl createReleaseBundleAclModel(String entityKey, PermissionTargetModel permissionTargetModel,
            @Nullable ReleaseBundleAcl previousAcl, PermissionTargetNaming inputMode) {
        //if its UI request we shouldn't have release bundle in permission target model
        if (!hasReleaseBundlePermission(permissionTargetModel)) {
            return null;
        }
        validateAclPatternsModifications(previousAcl, permissionTargetModel.getReleaseBundle());
        String currentUsername = securityService.currentUsername();
        // Permission Target
        ReleaseBundlePermissionTargetImpl releaseBundlePermissionTarget = new ReleaseBundlePermissionTargetImpl(
                entityKey);
        RepoPermissionTargetModel releaseBundle = permissionTargetModel.getReleaseBundle();
        setPermissionTargetFields(releaseBundlePermissionTarget, releaseBundle);
        Set<AceInfo> aceInfos = getAcesForAclAndValidateActions(inputMode, releaseBundle.getActions());
        if (previousAcl != null) {
            return InfoFactoryHolder.get()
                    .createReleaseBundleAcl(releaseBundlePermissionTarget, aceInfos, currentUsername,
                            ClockUtils.epochMillis(), previousAcl.getAccessIdentifier());
        }
        return InfoFactoryHolder.get()
                .createReleaseBundleAcl(releaseBundlePermissionTarget, aceInfos, currentUsername,
                        ClockUtils.epochMillis());
    }

    /*
     * Assertions and validations
     */

    /**
     * A set of validations on what can the user actually change based on what came in with the response.
     * A second set of validations on the content itself is performed later inside {@link #createRepoAclModel}
     * and {@link #createBuildAclModel}
     */
    private void validateUpdateActionPermissions(String entityKey, RepoAcl existingRepoAcl, BuildAcl existingBuildAcl,
            ReleaseBundleAcl existingReleaseBundleAcl, boolean hasRepoPermissionInRequest,
            boolean hasBuildPermissionInRequest, boolean hasReleaseBundlePermissionInRequest) {
        //What can this user change?
        boolean repoExistsAndDenied = isAclExistsAndDenied(existingRepoAcl);
        boolean buildExistsAndDenied = isAclExistsAndDenied(existingBuildAcl);
        boolean releaseBundleExistsAndDenied = isAclExistsAndDenied(existingReleaseBundleAcl);

        //If any model that the user can't manage came in with the request, fail it.
        if (repoExistsAndDenied && hasRepoPermissionInRequest) {
            throwForbiddenPermissionErrorMessage(entityKey, getDeniedTypeMsg(ArtifactoryResourceType.REPO));
        } else if (buildExistsAndDenied && hasBuildPermissionInRequest) {
            throwForbiddenPermissionErrorMessage(entityKey, getDeniedTypeMsg(ArtifactoryResourceType.BUILD));
        } else if (releaseBundleExistsAndDenied && hasReleaseBundlePermissionInRequest) {
            throwForbiddenPermissionErrorMessage(entityKey, getDeniedTypeMsg(ArtifactoryResourceType.RELEASE_BUNDLES));
        }
        // At this point user can either manage both and both exists, or the one they can't manage isn't present anyway
    }

    /**
     * Try to get Ace set for Acl and validate that the user request is with valid permissions.
     *
     * @return a set of converted Aces into internal naming
     * @throws BadRequestException in case the user request contains invalid permissions
     */
    private Set<AceInfo> getAcesForAclAndValidateActions(PermissionTargetNaming inputMode,
            PrincipalConfiguration principals) {
        try {
            return getAcesForAcl(principals, inputMode);
        } catch (IllegalArgumentException iae) {
            throw new BadRequestException(iae.getMessage());
        }
    }

    private void diffListAndError(Collection<String> newElementsList, Collection<String> oldElementsList,
            String element) {
        //Create a copy so we don't mess up the model, its still being used.
        Set<String> newElements = new HashSet<>(newElementsList);
        newElements.removeAll(oldElementsList);
        if (isNotEmpty(newElements)) {
            throw new ForbiddenWebAppException("Manager user cannot modify " + element + " in a permission target");
        }
    }

    /**
     * @throws UnsupportedByLicenseException on insufficient license (only for release bundle section)
     * @throws ConflictException             on entity name mismatch
     * @throws BadRequestException           on no entities given
     */
    private void validateIncomingPermissionTarget(String entityKey, PermissionTargetModel permissionTargetModel) {
        String name = permissionTargetModel.getName();
        if (isNotBlank(name) && !entityKey.equals(name)) {
            throw new ConflictException(CONFLICT_ERR_MSG);
        }
        Optional<String> possibleXssError = XSSValidator.validate("Name", name);
        if (possibleXssError.isPresent()) {
            throw new BadRequestException(possibleXssError.get());
        }
        if (!hasRepoPermission(permissionTargetModel)
                && !hasBuildPermission(permissionTargetModel)
                && !hasReleaseBundlePermission(permissionTargetModel)) {
            throw new BadRequestException(MISSING_PARTS_ERR_MSG);
        }
    }

    private void validatePermissionTargetSections(PermissionTargetModel permissionTargetModel,
            PermissionTargetNaming inputMode, PermissionTargetAcls existingPermission) {
        validateRepositoriesSection(permissionTargetModel, existingPermission);
        validateBuildsSection(permissionTargetModel, inputMode, existingPermission);
        validateReleaseBundleSection(permissionTargetModel, existingPermission);
    }

    /**
     * If user is not admin they can only touch the patterns and principals
     *
     * @param previousAcl Previous value of the acl being modified, if exists.
     * @param newRepoKeys New permission target's repos about to be sent to the update action.
     */
    private void validateRepoAclModifications(@Nullable RepoAcl previousAcl, Set<String> newRepoKeys) {
        //Manage permission already verified at this point
        if (previousAcl != null && !securityService.isAdmin()) {
            diffListAndError(repositoryService.convertCachedRepoKeysToRemote(newRepoKeys),
                    repositoryService.convertCachedRepoKeysToRemote(previousAcl.getPermissionTarget().getRepoKeys()),
                    "repositories");
        }
    }

    /**
     * Validate that we don't already have the ACLs with same entity key
     *
     * @throws BadRequestException in case of assertion error
     */
    private void validatePermissionsExistenceForCreate(String entityKey, PermissionTargetModel permissionTargetModel) {
        PermissionTargetAcls permissionExist = getExistingPermissionTarget(authorizationService, entityKey);
        if (permissionExist.getRepoAcl() != null && hasRepoPermission(permissionTargetModel)) {
            generatePermissionExistsError(entityKey, ArtifactoryResourceType.REPO.getName());
        }
        if (permissionExist.getBuildAcl() != null && hasBuildPermission(permissionTargetModel)) {
            generatePermissionExistsError(entityKey, ArtifactoryResourceType.BUILD.getName());
        }
        if (permissionExist.getReleaseBundleAcl() != null && hasReleaseBundlePermission(permissionTargetModel)) {
            generatePermissionExistsError(entityKey, ArtifactoryResourceType.RELEASE_BUNDLES.getName());
        }
    }

    /**
     * Requires at least one include or exclude release-bundle pattern.
     * Asserting repositories section for Edge and EntPlus lic and that principals exist.
     *
     * @throws BadRequestException in case of assertion errors
     */
    private void validateReleaseBundleSection(PermissionTargetModel permissionTargetModel,
            PermissionTargetAcls existingPermission) throws BadRequestException {
        RepoPermissionTargetModel releaseBundle = permissionTargetModel.getReleaseBundle();
        if (releaseBundle == null) {
            log.debug("No release bundle permission content in request for Acl {} of type {}",
                    permissionTargetModel.getName(), RELEASE_BUNDLES.getName());
            return;
        }
        // Principals validation
        checkForNonExistingPrinciples(userGroupService, releaseBundle.getActions(), existingPermission);
        // Asserting at least one include/exclude pattern exists
        validatePatterns(releaseBundle, ArtifactoryResourceType.RELEASE_BUNDLES);
        assertReleaseBundleModelRequiredEntities(releaseBundle);
    }

    /**
     * Requires at least one include or exclude build pattern.
     * Asserting build repo key is the only one exists, principals exist.
     *
     * @throws BadRequestException in case of assertion errors
     */
    private void validateBuildsSection(PermissionTargetModel permissionTargetModel,
            PermissionTargetNaming inputMode, PermissionTargetAcls existingPermission) throws BadRequestException {
        RepoPermissionTargetModel builds = permissionTargetModel.getBuild();
        if (builds == null) {
            log.debug("No build permissions content in request for Acl {} of type {}", permissionTargetModel.getName(),
                    BUILD.getName());
            return;
        }
        // Asserting common part between repo and build
        validateRepoPermissionModel(builds, existingPermission);
        validatePatterns(builds, ArtifactoryResourceType.BUILD);
        // Repo key validation
        assertBuildModelRequiredEntities(permissionTargetModel, inputMode);
    }

    /**
     * Requires repositories list and valid principals
     *
     * @throws BadRequestException in case of assertion error
     */
    private void validateRepositoriesSection(PermissionTargetModel permissionTargetModel,
            PermissionTargetAcls existingPermission)
            throws BadRequestException {
        RepoPermissionTargetModel repositories = permissionTargetModel.getRepo();
        if (repositories == null) {
            log.debug("No repo permissions content in request for Acl {} of type {}", permissionTargetModel.getName(),
                    REPO.getName());
            return;
        }
        validateRepoPermissionModel(repositories, existingPermission);
        validateNoForbiddenReposAreIncluded(repositories.getRepositories());
    }

    private void validateRepoPermissionModel(RepoPermissionTargetModel repoPermissionTargetModel,
            PermissionTargetAcls existingPermission) {
        Set<String> repositories = repoPermissionTargetModel.getRepositories();
        if (repositories == null) {
            throw new BadRequestException("Permission target request missing repositories.");
        }
        if (repositories.isEmpty()) {
            throw new BadRequestException("Permission target must contain at least one repository.");
        }
        // RepoKeys validation
        String missingRepo = getFirstNonExistingRepoFromList(repositories, configService, repositoryService);
        if (isNotBlank(missingRepo)) {
            throw new BadRequestException(
                    "Permission target contains a reference to a non-existing repository '" + missingRepo + "'.");
        }
        // Principals validation
        checkForNonExistingPrinciples(userGroupService, repoPermissionTargetModel.getActions(), existingPermission);
    }

    private void validateNoForbiddenReposAreIncluded(Set<String> includedRepos) {
        if (includedRepos.stream().anyMatch(buildFacade::isBuildInfoRepo)) {
            throw new BadRequestException(String.format(REPO_NOT_ALLOWED_ERR_MSG, "Build Info"));
        }
        boolean isReleaseBundleRepoIncluded =
                repositoryService.getRepoConfigKeysByType(Set.of(RepoDetailsType.RELEASE_BUNDLE))
                        .stream()
                        .anyMatch(includedRepos::contains);
        if (isReleaseBundleRepoIncluded) {
            throw new BadRequestException(String.format(REPO_NOT_ALLOWED_ERR_MSG, "Release Bundle"));
        }
    }

    /**
     * In build and release bundle permission targets - if the user is not admin he can only touch principals
     *
     * @param previousAcl   Previous value of the acl being modified, if exists.
     * @param newPermission New incoming permission target to validate
     */
    private void validateAclPatternsModifications(@Nullable Acl<? extends RepoPermissionTarget> previousAcl,
            RepoPermissionTargetModel newPermission) {
        if (previousAcl != null && !securityService.isAdmin()) {
            RepoPermissionTarget previousPermission = previousAcl.getPermissionTarget();
            diffListAndError(newPermission.getIncludePatterns(), previousPermission.getIncludes(), "include patterns");
            diffListAndError(newPermission.getExcludePatterns(), previousPermission.getExcludes(), "exclude patterns");
        }
    }

    /**
     * Requires that {@link BuildFacade#getBuildInfoRepoKeyByProject(String, boolean)} is the only repo key in the
     * repositories list.
     * In case request came from UI we add the repo ourselves. Otherwise throw exception.
     *
     * @throws BadRequestException on assertion error.
     */
    private void assertBuildModelRequiredEntities(PermissionTargetModel permissionTargetModel,
            PermissionTargetNaming inputMode) throws BadRequestException {
        RepoPermissionTargetModel builds = permissionTargetModel.getBuild();
        if (!isPermissionHasOneValidBuildRepo(builds)) {
            String buildRepoKey = buildFacade.getBuildInfoRepoForDefaultProject();
            if (NAMING_UI.equals(inputMode)) {
                builds.setRepositories(Set.of(buildRepoKey));
            } else {
                throw new BadRequestException(
                        "Build permission target repositories list must contain " + buildRepoKey +
                                " as the only repository key");
            }
        }
    }

    private boolean isPermissionHasOneValidBuildRepo(RepoPermissionTargetModel permissionModel) {
        if (permissionModel.getRepositories().size() != 1) {
            return false;
        }
        String buildRepo = permissionModel.getRepositories().iterator().next();
        return buildRepo != null && buildFacade.isBuildInfoRepo(buildRepo);
    }

    /**
     * Asserting repositories section for Edge and EntPlus licenses.
     *
     * @throws BadRequestException on assertion error.
     */
    private void assertReleaseBundleModelRequiredEntities(RepoPermissionTargetModel releaseBundle)
            throws BadRequestException {
        if (CollectionUtils.isNullOrEmpty(releaseBundle.getRepositories())) {
            releaseBundle.setRepositories(Set.of(RELEASE_BUNDLE_REPO_KEY));
        }
    }

    private void validatePatterns(RepoPermissionTargetModel permissionModel, ArtifactoryResourceType type) {
        if (isNullOrEmpty(permissionModel.getIncludePatterns()) &&
                isNullOrEmpty(permissionModel.getExcludePatterns())) {
            throw new BadRequestException(
                    String.format("Permission target %s must contain at least one include or exclude pattern",
                            getDeniedTypeMsg(type)));
        }
    }

    /**
     * Throws when user doesn't have manage on both permission targets, for actions that require manage on at least one
     * of them.
     *
     * @throws ForbiddenWebAppException if permission was denied for {@param entityKey}
     * @throws NotFoundException        if permission was denied for {@param entityKey} and Artifactory is set to
     *                                  hide existence
     */

    private boolean isAclExistsAndDenied(Acl<? extends PermissionTarget> acl) {
        return acl != null && !authorizationService.canManage(acl.getPermissionTarget());
    }

    private boolean isAclExistsAndAllowed(Acl<? extends PermissionTarget> acl) {
        return acl != null && authorizationService.canManage(acl.getPermissionTarget());
    }

    private boolean hasRepoPermission(PermissionTargetModel model) {
        return model != null && model.getRepo() != null;
    }

    private boolean hasBuildPermission(PermissionTargetModel model) {
        return model != null && model.getBuild() != null;
    }

    private boolean hasReleaseBundlePermission(PermissionTargetModel model) {
        return model != null && model.getReleaseBundle() != null;
    }

    private void validateUser(String userName) {
        try {
            UserInfo user = userGroupService.findUser(userName);
            if (ContextHelper.get().beanForType(AolService.class).isAolAdmin(user)) {
                throw new NotFoundException();
            }
        } catch (UsernameNotFoundException e) {
            throw new NotFoundException();
        }
    }

    /*
     * Errors
     */

    private void generatePermissionExistsError(String entityKey, String type) {
        throw new ConflictException(String.format(ALREADY_EXISTS_ERR_MSG, entityKey, type));
    }

    /**
     * @throws ForbiddenWebAppException if permission was denied for {@param entityKey}
     * @throws NotFoundException        if permission was denied for {@param entityKey} and Artifactory is set
     *                                  to hide existence of unauthorized resources.
     */
    private void throwForbiddenPermissionErrorMessage(String entityKey, String deniedType) {
        if (configService.getDescriptor().getSecurity().isHideUnauthorizedResources()) {
            log.debug("User tried to access permission {} but it is forbidden and " +
                    "isHideUnauthorizedResources is true. Returning 404 instead of 403.", entityKey, deniedType);
            throw new NotFoundException("");
        }
        throw new ForbiddenWebAppException(
                "Accessing permission target '" + entityKey + "' " + deniedType + " is forbidden.");
    }

    private String getDeniedPermissionTypesMessage(boolean repoExistsAndDenied, boolean buildExistsAndDenied,
            boolean releaseBundleExistsAndDenied) {
        String repoType = repoExistsAndDenied ? ArtifactoryResourceType.REPO.getName() + ", " : StringUtils.EMPTY;
        String buildType = buildExistsAndDenied ? ArtifactoryResourceType.BUILD.getName() + ", " : StringUtils.EMPTY;
        String releaseBundleType =
                releaseBundleExistsAndDenied ? ArtifactoryResourceType.RELEASE_BUNDLES.getName() : StringUtils.EMPTY;
        String deniedMessage = "of types: " + repoType + buildType + releaseBundleType;
        return stripEnd(deniedMessage, ", ");
    }

    private String getDeniedTypeMsg(ArtifactoryResourceType resourceType) {
        return "of type: " + resourceType.getName();
    }

    private void validateRepositoryType(PermissionTargetModel permissionTargetModel) {
        if (permissionTargetModel.getRepo() == null || permissionTargetModel.getRepo().getRepositories() == null) {
            return;
        }
        for (String repoKey : permissionTargetModel.getRepo().getRepositories()) {
            if (VIRTUAL.equals(RestUtils.repoType(repoKey))) {
                throw new BadRequestException("Virtual repository is not supported in permissions target: " + repoKey);
            }
        }
    }
}
