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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.artifactory.addon.yum.YumAddon;
import org.artifactory.model.RepoConfig;

/**
 * @author Chen Keinan
 */
@JsonTypeName("YUM")
public class YumIndexCalculator extends BaseIndexCalculator {

    @Override
    public void calculateIndex() throws Exception {
        YumAddon yumAddon = addonsManager.addonByType(YumAddon.class);
        RepoConfig repoConfig = repositoryConfigService.getLocalOrFederatedRepoConfigByKey(getRepoKey());
        if (yumAddon != null) {
            // send a UI calc event with a null passphrase; if it exists in the config descriptor we'll get it later
            yumAddon.requestRpmMetadataCalculation(repoConfig, null, true);
        }
    }
}
