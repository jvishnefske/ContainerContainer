/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2016 JFrog Ltd.
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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.permission;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.UserInfo;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.permission.PrincipalEffectivePermissions;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.permission.RepoPermissionsModel;
import org.artifactory.ui.rest.service.common.EffectivePermissionHelper;
import org.artifactory.ui.rest.service.distribution.GetReleaseBundleEffectivePermissionService;
import org.artifactory.util.AolUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;

/**
 * Used in Effective Permissions tab per artifact for repositories (including build-info repositories)
 *
 * @author Chen Keinan
 */
@Slf4j
@Component
public class GetRepoEffectivePermissionService implements RestService {

    private BuildFacade buildFacade;
    private AuthorizationService authService;
    private RepositoryService repoService;
    private RepositoryConfigService repositoryConfigService;
    private GetReleaseBundleEffectivePermissionService releaseBundleEffectivePermissionService;
    private UserGroupService userGroupService;
    private EffectivePermissionHelper effectivePermissionHelper;

    @Autowired
    public GetRepoEffectivePermissionService(
            BuildFacade buildFacade,
            AuthorizationService authService,
            RepositoryService repoService,
            UserGroupService userGroupService,
            GetReleaseBundleEffectivePermissionService releaseBundleEffectivePermissionService,
            RepositoryConfigService repositoryConfigService) {
        this.buildFacade = buildFacade;
        this.authService = authService;
        this.repoService = repoService;
        this.userGroupService = userGroupService;
        this.effectivePermissionHelper = new EffectivePermissionHelper(userGroupService);
        this.releaseBundleEffectivePermissionService = releaseBundleEffectivePermissionService;
        this.repositoryConfigService = repositoryConfigService;
    }

    public void execute(ArtifactoryRestRequest artifactoryRequest, RestResponse artifactoryResponse) {
        String path = artifactoryRequest.getQueryParamByKey("path");
        String repoKey = artifactoryRequest.getQueryParamByKey("repoKey");
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);

        boolean isReleaseBundleRepoExist =
                repositoryConfigService.existsByType(RepoDetailsType.RELEASE_BUNDLE, repoKey);
        if (isReleaseBundleRepoExist) {
            handleReleaseBundleEffectivePermissions(artifactoryResponse, repoPath);
            return;
        }
        LocalRepoDescriptor localRepoDescriptor =
                repoService.localOrFederatedRepoDescriptorByKey(repoPath.getRepoKey());
        if (localRepoDescriptor != null && RepoType.BuildInfo.equals(localRepoDescriptor.getType())) {
            handleBuildEffectivePermissions(artifactoryResponse, repoPath);
            return;
        }

        if (localRepoDescriptor != null && localRepoDescriptor.getKey().equals(LOG_SHIPPING_REPO_KEY)) {
            handleLogShippingEffectivePermissions(artifactoryResponse, repoPath);
            return;
        }

        if (localRepoDescriptor != null && localRepoDescriptor.getType().isPipeInfo()) {
            handlePipelinesInfoEffectivePermissions(artifactoryResponse, repoPath);
        } else {
            handleRepoEffectivePermissions(artifactoryResponse, repoPath);
        }
    }

    private void handleLogShippingEffectivePermissions(RestResponse artifactoryResponse, RepoPath repoPath) {
        if (authService.canManage(repoPath)) {
            try {
                artifactoryResponse.iModel(getLogShippingPermissionModel());
            } catch (Exception e) {
                log.warn("Artifactory was unable to get users and groups effective permission for log shipping repo.",
                        e);
                artifactoryResponse.error("Artifactory was unable to get users and groups effective permissions, " +
                        "see logs for more details.");
            }
        }
    }

    private void handleRepoEffectivePermissions(RestResponse artifactoryResponse, RepoPath repoPath) {
        if (repoService.isVirtualRepoExist(repoPath.getRepoKey())) {
            repoPath = repoService.getVirtualItemInfo(repoPath).getRepoPath();
        }
        if (authService.canManage(repoPath)) {
            try {
                artifactoryResponse.iModel(getRepoPermissionModel(repoPath));
            } catch (Exception e) {
                log.warn("Artifactory was unable to get users and groups effective permission.", e);
                artifactoryResponse
                        .error("Artifactory was unable to get users and groups effective permissions, see logs for " +
                                "more details.");
            }
        }
    }

    /**
     * Handle build-info repository effective permissions. get already-encoded repo path.
     */
    private void handleBuildEffectivePermissions(RestResponse response, RepoPath repoPath) {
        if (authService.canManage(repoPath)) {
            try {
                response.iModel(
                        buildFacade.getBuildEffectivePermissions(repoPath.getRepoKey(), repoPath.getPath()));
            } catch (Exception e) {
                log.warn("Artifactory was unable to get users and groups effective permission for build.", e);
                response.error("Artifactory was unable to get users and groups effective permissions, " +
                        "see logs for more details.");
            }
        }
    }

    /**
     * Handle release bundle repository effective permissions.
     */
    private void handleReleaseBundleEffectivePermissions(RestResponse response, RepoPath repoPath) {
        if (authService.canManage(repoPath)) {
            try {
                response.iModel(releaseBundleEffectivePermissionService.getPermissionModel(repoPath));
            } catch (Exception e) {
                log.warn("Artifactory was unable to get users and groups effective permission for release bundle.", e);
                response.error("Artifactory was unable to get users and groups effective permissions, " +
                        "see logs for more details.");
            }
        }
    }

    /**
     * Handle pipelines info repository effective permissions.
     */
    private void handlePipelinesInfoEffectivePermissions(RestResponse response, RepoPath repoPath) {
        if (authService.canManage(repoPath)) {
            try {
                response.iModel(getPipelinesInfoPermissionModel());
            } catch (Exception e) {
                log.warn("Artifactory was unable to get users and groups effective permission for Pipe Info repo.", e);
                response.error("Artifactory was unable to get users and groups effective permissions, " +
                        "see logs for more details.");
            }
        }
    }


    @VisibleForTesting
    /**
     * Find and populate repo permission model from acl cache
     */
    RepoPermissionsModel getRepoPermissionModel(RepoPath repoPath) {
        final List<RepoAcl> repoAcls = authService.getRepoPathAcls(repoPath)
                                                  .stream()
                                                  .filter(acl -> !authService.isInternalAcl(acl))
                                                  .toList();
        Map<String, PrincipalEffectivePermissions> userEffectivePermissions = Maps.newHashMap();
        Map<String, PrincipalEffectivePermissions> groupEffectivePermissions = Maps.newHashMap();

        List<UserInfo> allUsers = userGroupService.getAllUsersWithUserInfoWithOutPasswords(true);
        ImmutableSetMultimap<String, String> allUsersInGroups = userGroupService.getAllUsersInGroups(allUsers);
        addAdminsToMaps(userEffectivePermissions, groupEffectivePermissions, allUsers);
        repoAcls.forEach(aclInfo -> effectivePermissionHelper
                .addAclInfoToAclList(aclInfo, userEffectivePermissions, groupEffectivePermissions));
        effectivePermissionHelper
                .grantGroupUsersEffectivePermissions(groupEffectivePermissions, userEffectivePermissions,
                        allUsersInGroups);
        // update response with model
        return new RepoPermissionsModel(userEffectivePermissions.values(), groupEffectivePermissions.values(),
                repoAcls);
    }

    /**
     * Find and populate pipelines info permission model from acl cache
     * Only admin users are allowed to access this repo.
     */
    private RepoPermissionsModel getPipelinesInfoPermissionModel() {
        Map<String, PrincipalEffectivePermissions> userEffectivePermissions = Maps.newHashMap();
        Map<String, PrincipalEffectivePermissions> groupEffectivePermissions = Maps.newHashMap();
        addAdminsToMaps(userEffectivePermissions, groupEffectivePermissions);
        return new RepoPermissionsModel(userEffectivePermissions.values(), groupEffectivePermissions.values(),
                Collections.emptyList());
    }

    /**
     * Find and populate logshipping permission model from acl cache
     * Only admin users are allowed to access this repo and read only from it.
     */
    private RepoPermissionsModel getLogShippingPermissionModel() {
        Map<String, PrincipalEffectivePermissions> userEffectivePermissions = Maps.newHashMap();
        Map<String, PrincipalEffectivePermissions> groupEffectivePermissions = Maps.newHashMap();
        addAdminObservabilityPermissions(userEffectivePermissions, groupEffectivePermissions);
        return new RepoPermissionsModel(userEffectivePermissions.values(), groupEffectivePermissions.values(),
                Collections.emptyList());
    }

    private void addAdminObservabilityPermissions(Map<String, PrincipalEffectivePermissions> userEffectivePermissions,
            Map<String, PrincipalEffectivePermissions> groupEffectivePermissions) {
        effectivePermissionHelper
                .addAdminsToMaps(userEffectivePermissions, groupEffectivePermissions, AolUtils.isAol(), true);
    }

    private void addAdminsToMaps(Map<String, PrincipalEffectivePermissions> userEffectivePermissions,
            Map<String, PrincipalEffectivePermissions> groupEffectivePermissions) {
        effectivePermissionHelper
                .addAdminsToMaps(userEffectivePermissions, groupEffectivePermissions, AolUtils.isAol());
    }

    private void addAdminsToMaps(Map<String, PrincipalEffectivePermissions> userEffectivePermissions,
            Map<String, PrincipalEffectivePermissions> groupEffectivePermissions, List<UserInfo> allUsers) {
        effectivePermissionHelper
                .addAdminsToMaps(userEffectivePermissions, groupEffectivePermissions, AolUtils.isAol(), false,
                        allUsers);
    }

}
