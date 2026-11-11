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

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.rest.common.model.BaseModel;
import org.jfrog.common.config.validation.NotUnicode;

/**
 * @author BarakH
 */
public class BasicGeneralConfig extends BaseModel {

    @NotUnicode
    protected String customUrlBase;
    // Used only for GET config to signal the UI whether to display the download redirect
    protected Boolean hasCloudProviderSupportingRedirection;

    public BasicGeneralConfig() {
    }

    public BasicGeneralConfig(CentralConfigDescriptor mutableDescriptor) {
        customUrlBase = mutableDescriptor.getUrlBase();
        hasCloudProviderSupportingRedirection = isCloudProviderSupportingRedirectionConfigured();
    }

    private Boolean isCloudProviderSupportingRedirectionConfigured() {
        return ContextHelper.get()
                .beanForType(AddonsManager.class)
                .isCloudProviderSupportingRedirectionConfigured() ? true : null;
    }

    public String getCustomUrlBase() {
        return customUrlBase;
    }

    public void setCustomUrlBase(String customUrlBase) {
        this.customUrlBase = customUrlBase;
    }

    public Boolean getHasCloudProviderSupportingRedirection() {
        return hasCloudProviderSupportingRedirection;
    }

    public void setHasCloudProviderSupportingRedirection(Boolean hasCloudProviderSupportingRedirection) {
        this.hasCloudProviderSupportingRedirection = hasCloudProviderSupportingRedirection;
    }
}
