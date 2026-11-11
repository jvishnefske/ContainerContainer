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
import org.artifactory.addon.nuget.UiNuGetAddon;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.profile.PackageProfile;
import org.artifactory.common.repo.PackageType;

import java.util.Arrays;

/**
 * @author Chen Keinan
 */
@JsonTypeName("NuGet")
public class NuGetIndexCalculator extends BaseIndexCalculator {

    private final boolean legacyProfile;
    public NuGetIndexCalculator() {
        legacyProfile = !Arrays.asList(environment.getActiveProfiles()).contains(PackageProfile.NEW_NUGET_IMPL);
    }

    @Override
    public void calculateIndex() throws Exception {
        if (legacyProfile) {
            UiNuGetAddon nugetAddon = addonsManager.addonByType(UiNuGetAddon.class);
            if (nugetAddon != null) {
                nugetAddon.requestAsyncReindexNuPkgs(getRepoKey());
                if (ConstantValues.nuGetSymbolServerSupportEnabled.getBoolean()) {
                    nugetAddon.requestAsyncReindexSNuPkgs(getRepoKey());
                }
            }
        } else {
            packageResourceService.reindex(PackageType.NUGET, getRepoKey(), null);
        }
    }
}
