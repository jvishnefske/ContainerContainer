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

package org.artifactory.ui.rest.model.admin.configuration.generalconfig;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.platform.PlatformSystemMessageConfig;
import org.artifactory.rest.common.model.BaseModel;

/**
 * @author Yoaz Menda
 */
public class PlatformConfig extends BaseModel {

    private String serverName;
    private String customUrlBase;
    private Boolean forceBaseUrl;
    private String dateFormat;
    private Boolean helpLinksEnabled;
    private String logoUrl;
    private Boolean systemMessageEnabled;
    private String systemMessageTitle;
    private String systemMessageTitleColor;
    private String systemMessage;

    public PlatformConfig() {
    }

    public PlatformConfig(CentralConfigDescriptor mutableDescriptor) {
        serverName = mutableDescriptor.getServerName();
        customUrlBase = mutableDescriptor.getUrlBase();
        forceBaseUrl = ContextHelper.get().getRepositoryService().isForceBaseUrl();

        PlatformGeneralConfig platformGeneralConfig = getPlatformConfigService().getPlatformGeneralConfig();
        dateFormat = platformGeneralConfig.getDateFormat();
        helpLinksEnabled = platformGeneralConfig.isHelpLinksEnabled();
        logoUrl = platformGeneralConfig.getLogo();

        PlatformSystemMessageConfig platformSystemMessageConfig =
                getPlatformConfigService().getPlatformSystemMessageConfig();
        systemMessageEnabled = platformSystemMessageConfig.isSystemMessageEnabled();
        systemMessageTitle = platformSystemMessageConfig.getSystemMessageTitle();
        systemMessageTitleColor = platformSystemMessageConfig.getSystemMessageTitleColor();
        systemMessage = platformSystemMessageConfig.getSystemMessage();
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public Boolean isHelpLinksEnabled() {
        return helpLinksEnabled;
    }

    public void setHelpLinksEnabled(Boolean helpLinksEnabled) {
        this.helpLinksEnabled = helpLinksEnabled;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public Boolean isSystemMessageEnabled() {
        return systemMessageEnabled;
    }

    public void setSystemMessageEnabled(Boolean systemMessageEnabled) {
        this.systemMessageEnabled = systemMessageEnabled;
    }

    public String getSystemMessageTitle() {
        return systemMessageTitle;
    }

    public void setSystemMessageTitle(String systemMessageTitle) {
        this.systemMessageTitle = systemMessageTitle;
    }

    public String getSystemMessageTitleColor() {
        return systemMessageTitleColor;
    }

    public void setSystemMessageTitleColor(String systemMessageTitleColor) {
        this.systemMessageTitleColor = systemMessageTitleColor;
    }

    public String getSystemMessage() {
        return systemMessage;
    }

    public void setSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getCustomUrlBase() {
        return customUrlBase;
    }

    public void setCustomUrlBase(String customUrlBase) {
        this.customUrlBase = customUrlBase;
    }

    public Boolean getForceBaseUrl() {
        return forceBaseUrl;
    }

    public void setForceBaseUrl(Boolean forceBaseUrl) {
        this.forceBaseUrl = forceBaseUrl;
    }

    private PlatformConfigService getPlatformConfigService() {
        return ContextHelper.get().beanForType(PlatformConfigService.class);
    }
}