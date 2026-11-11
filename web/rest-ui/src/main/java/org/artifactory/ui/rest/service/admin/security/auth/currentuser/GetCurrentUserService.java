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

package org.artifactory.ui.rest.service.admin.security.auth.currentuser;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.OssAddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.md.Properties;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.artifactory.ui.rest.model.admin.security.general.SecurityConfig;
import org.artifactory.ui.rest.model.admin.security.user.BaseUser;
import org.artifactory.ui.rest.service.admin.security.general.GetSecurityConfigService;
import org.jfrog.access.client.model.CrowdSetting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.artifactory.addon.sso.crowd.CrowdAddon.CROWD_NEXT_VALIDATION_HEADER;
import static org.artifactory.api.security.UserGroupService.UI_VIEW_BLOCKED_USER_PROP;
import static org.artifactory.security.props.auth.OauthManager.OAUTH_INFO_PROPERTY;
import static org.artifactory.ui.rest.resource.admin.security.auth.AuthResource.WITH_GROUPS_PARAM;


/**
 * @author Chen Keinan
 */
@Component
public class GetCurrentUserService implements RestService {

    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private UserGroupService userGroupService;
    @Autowired
    private AuthorizationService authService;
    @Autowired
    private GetSecurityConfigService getSecurityConfigService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private AccessService accessService;
    @Autowired
    private RepositoryService repositoryService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        boolean proWithoutLicense = !(addonsManager instanceof OssAddonsManager) && !addonsManager.isLicenseInstalled();
        boolean offlineMode = true;
        CentralConfigDescriptor descriptor = ContextHelper.get().getCentralConfig().getDescriptor();
        if (ConstantValues.versionQueryEnabled.getBoolean() && !descriptor.isOfflineMode()) {
            offlineMode = false;
        }
        Optional<CrowdSetting> crowdSettings = accessService.getAccessAuthSettingsClient().getCrowdSetting();
        if (crowdSettings.isPresent() && crowdSettings.get().isEnable()) {
            long validationInterval = crowdSettings.get().getSessionValidationInterval();
            response.getServletResponse().addHeader(CROWD_NEXT_VALIDATION_HEADER, String.valueOf(validationInterval));
        }
        getSecurityConfigService.execute(request, response);
        SecurityConfig securityConfig = (SecurityConfig) response.getIModel();
        // Determines whether to display the 'permission' item in UI admin menu
        boolean isBuildBasicView = authService.hasBuildBasicReadPermission();
        BaseUser user = getUserInformation(proWithoutLicense, offlineMode, securityConfig, isBuildBasicView);
        if (Boolean.parseBoolean(request.getQueryParamByKey(WITH_GROUPS_PARAM))) {
            addUserGroups(user);
        }
        if (!StringUtils.isBlank(securityService.currentUsername())) {
            Integer userPasswordDaysLeft = securityService.getUserPasswordDaysLeft(securityService.currentUsername());
            if (userPasswordDaysLeft != null) {
                user.setCurrentPasswordValidFor(userPasswordDaysLeft);
            }
        }
        response.iModel(user);
    }

    private BaseUser getUserInformation(boolean proWithoutLicense, boolean offlineMode, SecurityConfig securityConfig,
            boolean isBuildBasicView) {
        UserInfo userInfo = userGroupService.currentUser();
        BaseUser baseUser = new BaseUser(userInfo.getUsername(), securityService.isAdmin());
        baseUser.setCanCreateReleaseBundle(authService.hasReleaseBundlePermission(ArtifactoryPermission.DEPLOY));
        baseUser.setCanCreateReleaseBundleV2(authService.hasReleaseBundlePermission(
                ArtifactoryPermission.DEPLOY, false, false));
        baseUser.setCanDeploy(baseUser.isCanCreateReleaseBundle() || hasDeployPermission());
        baseUser.setCanManage(authService.hasPermission(ArtifactoryPermission.MANAGE, true)
                || securityService.isResourceManager()
                || proWithoutLicense);
        baseUser.setBuildBasicView(isBuildBasicView);
        baseUser.setProfileUpdatable(securityService.isUpdatableProfile());
        baseUser.setProWithoutLicense(proWithoutLicense);
        baseUser.setAnonAccessEnabled(securityConfig.getAnonAccessEnabled());
        baseUser.setRequireProfileUnlock(securityService.requireProfileUnlock(null, null));
        baseUser.setRequireProfilePassword(securityService.requireProfilePassword(userInfo));
        baseUser.setOfflineMode(offlineMode);
        baseUser.setExistsInDB(!securityService.isTransientUser());
        baseUser.setRealm(securityService.getAuthenticatedRealm());
        baseUser.setPolicyViewer(userInfo.isPolicyViewer());
        baseUser.setPolicyManager(userInfo.isPolicyManager());
        baseUser.setWatchManager(userInfo.isWatchManager());
        baseUser.setReportsManager(userInfo.isReportsManager());
        baseUser.setAuthProviderName(getAuthProviderFromAuthentication());
        baseUser.setMfaStatus(userGroupService.getMfaStatus());
        Properties xrayProps = userGroupService.findPropertiesForUser(userInfo);
        String uiBlockedUserProp = xrayProps.getFirst(UI_VIEW_BLOCKED_USER_PROP);
        if (Boolean.parseBoolean(uiBlockedUserProp)) {
            baseUser.setdisableUIAccess(true);
        }
        return baseUser;
    }

    @Nullable
    private String getAuthProviderFromAuthentication() {
        return Optional.ofNullable(AuthenticationHelper.getAuthentication())
                .filter(PropsAuthenticationToken.class::isInstance)
                .map(auth -> ((PropsAuthenticationToken) auth).getPropsKey())
                .filter(String.class::isInstance)
                .filter(prop -> StringUtils.startsWith((String) prop, OAUTH_INFO_PROPERTY))
                .map(prop -> StringUtils.removeStart((String) prop, OAUTH_INFO_PROPERTY))
                .orElse(null);
    }

    /**
     * Load the user groups from the current session, if it exists. If the current session was populated with external
     * realm groups, and aren't on the storage, they will be here. The node server uses this endpoint to later create
     * an access token that contains the list of groups bound to the current session.
     */
    private void addUserGroups(BaseUser user) {
        if (securityService.isAuthenticated()) {
            SimpleUser simpleUser = (SimpleUser) AuthenticationHelper.getAuthentication().getPrincipal();
            user.setGroups(simpleUser.getDescriptor().getGroups().stream()
                    .map(UserGroupInfo::getGroupName)
                    .collect(Collectors.toSet()));
        }
    }

    private boolean hasDeployPermission() {
        return repositoryService.hasAnyDeployableRepo() ||
                authService.hasBuildPermission(null, ArtifactoryPermission.DEPLOY);
    }
}
