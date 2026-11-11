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

package org.artifactory.layout;

import org.artifactory.addon.AddonsManager;
import org.artifactory.config.ConfigurationChangesInterceptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Fred Simon
 */
@Component
public class ReverseProxyConfigurationChangesInterceptor implements ConfigurationChangesInterceptor {
    private final AddonsManager addonsManager;

    @Autowired
    public ReverseProxyConfigurationChangesInterceptor(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void onBeforeSave(CentralConfigDescriptor newDescriptor) {
        if (addonsManager.getArtifactoryRunningMode().isOss() && newDescriptor.getReverseProxies() != null &&
                !newDescriptor.getReverseProxies().isEmpty()) {
            newDescriptor.getReverseProxies().clear();
        }
    }

    void handleReverseProxySettings(CentralConfigDescriptor newDescriptor) {
        if (addonsManager.getArtifactoryRunningMode().isOss() && newDescriptor.getReverseProxies() != null &&
                !newDescriptor.getReverseProxies().isEmpty()) {
            newDescriptor.getReverseProxies().clear();
        }
    }
}
