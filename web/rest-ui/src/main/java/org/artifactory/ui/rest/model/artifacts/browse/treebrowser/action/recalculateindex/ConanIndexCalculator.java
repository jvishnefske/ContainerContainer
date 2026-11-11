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
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.conan.ConanAddon;
import org.artifactory.descriptor.repo.RepoType;

/**
 * @author Yuval Reches
 */
@JsonTypeName("Conan")
public class ConanIndexCalculator extends BaseIndexCalculator {

    @Override
    public void calculateIndex() throws Exception {
        ConanAddon conanAddon = addonsManager.addonByType(ConanAddon.class);
        String repoKey = getRepoKey();
        if (conanAddon != null && assertRepoType(repoKey, RepoType.Conan)) {
            if (StringUtils.isBlank(getPath())) {
                conanAddon.reindexRepo(repoKey);
            } else {
                conanAddon.reindexPath(repoKey, getPath());
            }
        }
    }
}
