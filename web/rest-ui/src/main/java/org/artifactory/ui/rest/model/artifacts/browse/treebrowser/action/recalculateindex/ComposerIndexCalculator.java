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
import org.artifactory.addon.composer.ComposerAddon;
import org.artifactory.common.profile.PackageProfile;
import org.artifactory.common.repo.PackageType;

import java.util.Arrays;

/**
 * @author Shay Bagants
 */
@JsonTypeName("Composer")
public class ComposerIndexCalculator extends BaseIndexCalculator {

    private final boolean legacyProfile;

    public ComposerIndexCalculator() {
        legacyProfile = !Arrays.asList(environment.getActiveProfiles()).contains(PackageProfile.NEW_COMPOSER_IMPL);
    }

    @Override
    public void calculateIndex() throws Exception {
        if (legacyProfile) {
            ComposerAddon composerAddon = addonsManager.addonByType(ComposerAddon.class);
            composerAddon.recalculateAll(getRepoKey(), true);
        } else {
            packageResourceService.reindex(PackageType.COMPOSER, getRepoKey(), null);
        }
    }
}
