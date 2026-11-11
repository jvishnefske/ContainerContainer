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

package org.artifactory.ui.rest.model.general;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.platform.PlatformSystemMessageConfig;
import org.jfrog.common.platform.subscription.SubscriptionType;


/**
 * @author Chen Keinan
 */
@Getter
@Setter
public class FooterImpl extends BaseFooterImpl implements Footer {
    private String buildNumber;
    private String licenseInfo;
    private boolean isDedicatedAol;
    private boolean isHttpSsoEnabledAOL;
    private boolean helpLinksEnabled;
    private boolean systemMessageEnabled;
    private String systemMessageTitle;
    private String systemMessageTitleColor;
    private String systemMessage;
    private String serverId;
    private boolean trashDisabled;
    private boolean xrayConfigured;
    private boolean xrayEnabled;
    private boolean metadataMigrationInProgress;
    private boolean isCloudProviderConfigured;
    private boolean treebrowserFolderCompact;
    private String dateFormat;
    private String userEmail;
    private String subscriptionTierType;
    // show version and license version in standalone mode
    private boolean showDetailedFooter;

    public FooterImpl(String licenseInfo, String buildNumber, boolean isAol, boolean isDedicatedAol,
            boolean isHttpSsoEnabledAOL, boolean userLogo, String logoUrl, String serverName,
            SubscriptionType subscriptionType, PlatformSystemMessageConfig systemMessageDescriptor,
            boolean helpLinksEnabled, String serverId, boolean trashDisabled, boolean xrayConfigured,
            boolean xrayEnabled, boolean haConfigured, boolean isCloudProviderConfigured, String platformId,
            boolean treebrowserFolderCompact, String dateFormat, boolean metadataMigrationInProgress,
            String userEmail, boolean showDetailedFooter, boolean isPartnerLicense) {
        super(serverName, userLogo, logoUrl, haConfigured, isAol, platformId, isPartnerLicense);
        this.licenseInfo = licenseInfo;
        this.buildNumber = buildNumber;
        this.isDedicatedAol = isDedicatedAol;
        this.isHttpSsoEnabledAOL = isHttpSsoEnabledAOL;
        this.trashDisabled = trashDisabled;
        this.systemMessageEnabled = systemMessageDescriptor.isSystemMessageEnabled();
        this.systemMessageTitle = systemMessageDescriptor.getSystemMessageTitle();
        this.systemMessageTitleColor = systemMessageDescriptor.getSystemMessageTitleColor();
        this.systemMessage = systemMessageDescriptor.getSystemMessage();
        this.helpLinksEnabled = helpLinksEnabled;
        this.serverId = serverId;
        this.xrayConfigured = xrayConfigured;
        this.xrayEnabled = xrayEnabled;
        this.isCloudProviderConfigured = isCloudProviderConfigured;
        this.metadataMigrationInProgress = metadataMigrationInProgress;
        this.treebrowserFolderCompact = treebrowserFolderCompact;
        this.dateFormat = dateFormat;
        this.userEmail = userEmail;
        if (subscriptionType != null) {
            this.subscriptionTierType = subscriptionType.getString();
        }
        this.showDetailedFooter = showDetailedFooter;
    }

    @JsonProperty("isDedicatedAol")
    public boolean isDedicatedAol() {
        return isDedicatedAol;
    }

    @JsonProperty("isCloudProviderConfigured")
    public boolean isCloudProviderConfigured() {
        return isCloudProviderConfigured;
    }
}
