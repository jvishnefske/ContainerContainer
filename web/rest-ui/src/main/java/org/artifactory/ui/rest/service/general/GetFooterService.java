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

package org.artifactory.ui.rest.service.general;

import lombok.RequiredArgsConstructor;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.AddonsWebManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.subscriptions.SubscriptionsService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.platform.PlatformSystemMessageConfig;
import org.artifactory.rest.common.model.xray.XrayConfigModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.SimpleUser;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.ui.rest.model.general.BaseFooterImpl;
import org.artifactory.ui.rest.model.general.Footer;
import org.artifactory.ui.rest.model.general.FooterImpl;
import org.artifactory.util.AolUtils;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Optional;

/**
 * @author Chen Keinan
 */
@Component
@RequiredArgsConstructor
public class GetFooterService implements RestService {

    private final CentralConfigService centralConfigService;
    private final SecurityService securityService;
    private final MetadataEventService metadataEventService;
    private final PlatformConfigService platformConfigService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        Footer footer;
        String buildNumber;
        String licenseInfo;

        if (ConstantValues.showDetailedFooterInStandaloneMode.getBoolean()) {
            licenseInfo = getFooterLicenseInfo();
            buildNumber = getBuildNum();
        } else {
            licenseInfo = "";
            buildNumber = "";
        }

        PlatformGeneralConfig platformGeneralConfig = platformConfigService.getPlatformGeneralConfig();
        PlatformSystemMessageConfig platformSystemMessageConfig =
                platformConfigService.getPlatformSystemMessageConfig();
        //even when the anon access disabled, there are exceptions where we allow anon access login.
        if (securityService.isAnonymous() && !securityService.isAnonAccessEnabled()) {
            footer = new BaseFooterImpl(getServer(), isUserLogo(), platformGeneralConfig.getLogo(), isHaConfigured(),
                    isAol(), getPlatformId(), getAddonManager().isPartnerLicense());
        } else {
            footer = new FooterImpl(licenseInfo, buildNumber, isAol(), isDedicatedAol(), isHttpSsoEnabledAOL(),
                    isUserLogo(), platformGeneralConfig.getLogo(), getServer(), getAolSubscriptionType(),
                    platformSystemMessageConfig, platformGeneralConfig.isHelpLinksEnabled(), getCurrentServerId(),
                    isTrashDisabled(), isXrayConfigured(), isXrayEnabled(), isHaConfigured(),
                    isCloudProviderConfigured(), getPlatformId(), isTreebrowserFolderCompact(),
                    platformGeneralConfig.getDateFormat(), metadataEventService.isMigrating(),
                    getUserEmail(), ConstantValues.showDetailedFooterInStandaloneMode.getBoolean(),
                    getAddonManager().isPartnerLicense());
        }
        response.iModel(footer);
    }

    String getPlatformId() {
        return getAddonManager().getPlatformId();
    }

    private String getCurrentServerId() {
        AddonsManager addonsManager = getAddonManager();
        HaCommonAddon haCommonAddon = addonsManager.addonByType(HaCommonAddon.class);
        return haCommonAddon.getCurrentMemberServerId();
    }

    AddonsManager getAddonManager() {
        return ContextHelper.get().beanForType(AddonsManager.class);
    }

    boolean isAol() {
        return AolUtils.isAol();
    }

    private boolean isCloudProviderConfigured() {
        return ContextHelper.get().beanForType(ArtifactoryBinaryService.class).isCloudProviderConfigured();
    }

    private SubscriptionType getAolSubscriptionType() {
        return ContextHelper.get().beanForType(SubscriptionsService.class).getSubscriptionType();
    }

    private boolean isDedicatedAol() {
        return this.isAol() &&
                ArtifactoryHome.get().getArtifactoryProperties().getBooleanProperty(ConstantValues.aolDedicatedServer);
    }

    private boolean isHttpSsoEnabledAOL() {
        return ConstantValues.aolSecurityHttpSsoEnabled.getBoolean();
    }

    private boolean isTreebrowserFolderCompact() {
        return ConstantValues.treebrowserFolderCompact.getBoolean();
    }

    private boolean isTrashDisabled() {
        TrashcanConfigDescriptor trashcanConfig = centralConfigService.getDescriptor().getTrashcanConfig();
        return !trashcanConfig.isEnabled();
    }

    private String getBuildNum() {
        CoreAddons addon = getAddonManager().addonByType(CoreAddons.class);
        return addon.getBuildNum();
    }

    private String getFooterLicenseInfo() {
        AddonsWebManager addonsManager = ContextHelper.get().beanForType(AddonsWebManager.class);
        return addonsManager.getFooterMessage();
    }

    private boolean isUserLogo() {
        String logoDir = ContextHelper.get().getArtifactoryHome().getLogoDir().getAbsolutePath();
        File sourceFile = new File(logoDir, "logo");
        // Indicated if the file exists
        return sourceFile.canRead();
    }

    private String getServer() {
        return centralConfigService.getDescriptor().getServerName();
    }

    private boolean isXrayConfigured() {
        XrayConfigModel xrayConfigModel = new XrayConfigModel(centralConfigService.getDescriptor().getXrayConfig());
        return xrayConfigModel.getXrayBaseUrl() != null;
    }

    private boolean isXrayEnabled() {
        XrayDescriptor xrayConfig = centralConfigService.getDescriptor().getXrayConfig();
        return xrayConfig != null && xrayConfig.isEnabled();
    }

    private boolean isHaConfigured() {
        return ArtifactoryHome.get().isHaConfigured();
    }

    String getUserEmail() {
        return Optional.ofNullable(AuthenticationHelper.getAuthentication())
                .map(Authentication::getPrincipal)
                .map(SimpleUser.class::cast)
                .map(SimpleUser::getEmail)
                .orElse(null);
    }
}
