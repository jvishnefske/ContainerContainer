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

package org.artifactory.ui.rest.service.onboarding;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.eula.EulaService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryCommonDbPropertiesService;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.onboarding.ArtifactoryInitStatusModel;
import org.artifactory.util.AolUtils;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.jfrog.access.rest.user.LoginRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static org.artifactory.api.security.SecurityService.DEFAULT_ADMIN_PASSWORD;
import static org.artifactory.repo.system.FirstStartupHelper.onlyDefaultReposExist;

/**
 * @author nadavy
 */
@Component
public class GetArtifactoryInitStatusService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(GetArtifactoryInitStatusService.class);
    private static final ArtifactoryServiceVersion ADMIN_ONBOARDING_ARTIFACTORY_VERSION =
            ArtifactoryServiceVersionProvider.v7100m001.get();

    private AddonsManager addonsManager;
    private CentralConfigService configService;
    private UserGroupService userGroupService;
    private AccessService accessService;
    private EulaService eulaService;
    private ArtifactoryCommonDbPropertiesService artifactoryCommonDbPropertiesService;

    @Autowired
    public GetArtifactoryInitStatusService(AddonsManager addonsManager,
            CentralConfigService configService, UserGroupService userGroupService, AccessService accessService,
            EulaService eulaService, ArtifactoryCommonDbPropertiesService artifactoryCommonDbPropertiesService) {
        this.addonsManager = addonsManager;
        this.configService = configService;
        this.userGroupService = userGroupService;
        this.accessService = accessService;
        this.eulaService = eulaService;
        this.artifactoryCommonDbPropertiesService = artifactoryCommonDbPropertiesService;
    }

    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        if (ConstantValues.onboardingInitStatusRequireAuth.getBoolean() && isAnonymousCaller()) {
            response.responseCode(HttpStatus.SC_UNAUTHORIZED).error("Authentication required");
            return;
        }
        boolean hasOnlyDefaultRepos = onlyDefaultReposExist();
        boolean licenseInstalled = addonsManager.isLicenseInstalled();
        boolean hasDefaultPassword = false;
        // The "hasDefaultPassword" probe is meaningless on SaaS (AOL) and triggers false-positive
        // exposure findings from external scanners; never run it there.
        if (!AolUtils.isAol()) {
            String username = userGroupService.currentUser().getUsername();
            try {
                accessService.getAccessClient().cachedAuth().authenticate(
                        new LoginRequest().username(username)
                                .password(DEFAULT_ADMIN_PASSWORD));
                hasDefaultPassword = true;
            } catch (Exception e) {
                log.debug("Unable to authenticate user: '{}'", username, e);
            }
        }
        boolean hasProxies = !configService.getDescriptor().getProxies().isEmpty();
        boolean skipWizard = ConstantValues.skipOnboardingWizard.getBoolean();
        boolean eulaRequired = eulaService.isRequired();
        boolean adminUserOnboarding = !shouldSkipOnboardingWizard();
        response.iModel(new ArtifactoryInitStatusModel(hasOnlyDefaultRepos, licenseInstalled,
                hasDefaultPassword, hasProxies, skipWizard, eulaRequired, adminUserOnboarding));
    }

    private static boolean isAnonymousCaller() {
        Authentication authentication = AuthenticationHelper.getAuthentication();
        return authentication == null || UserInfo.isAnonymous(authentication.getName());
    }

    protected boolean shouldSkipOnboardingWizard() {
        List<DbVersionInfo> dbVersionsInfo = artifactoryCommonDbPropertiesService.getAllDbVersionInfo();
        return dbVersionsInfo.stream()
                .filter(Objects::nonNull)
                .map(dbVersionInfo -> ArtifactoryServiceVersionProvider.get(
                        dbVersionInfo.getArtifactoryVersion(),
                        dbVersionInfo.getArtifactoryRevision()
                ))
                .anyMatch(artifactoryVersion -> artifactoryVersion.before(ADMIN_ONBOARDING_ARTIFACTORY_VERSION));
    }
}
